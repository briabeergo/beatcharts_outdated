
package ru.acted.beatcharts

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.util.Log
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_CANCEL
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS
import com.arthenica.mobileffmpeg.FFmpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.acted.beatcharts.types.*
import ru.acted.beatcharts.utils.BeatChartsUtils
import ru.acted.beatcharts.utils.BeatChartsUtils.Data.Companion.colorToHex
import ru.acted.beatcharts.viewModels.MainViewModel
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.or
import kotlin.math.floor


class DeEncodingManager {

    //THIS IS ONLY BEATSTAR CHART PROTOBUF PARSER (String fields will be read as formatted JSON. Except of interactions_id)
    private var stringLength = 0
    private var checkedBytesCount = 0
    suspend fun parseChartBytes(bytes: ByteArray, viewModel: MainViewModel?): ChartConversionResult {
        var currentTask = 0
        var currentFieldContent = 0
        var JSONLevel = -2
        //Level -2: Now reading not JSON
        //Level -1: JSON reading begin or end
        var currentFieldNumber = 0
        var JSONEndPoints = mutableListOf<Int>()
        var currentJSONFieldNumberGlobal = 0

        var doneString = ""
        var currentJSONTask = 0

        val conversionResult = ChartConversionResult()
        conversionResult.resultData = bytes.toMutableList()

        //Read every byte
        var i = 0
        while (i < bytes.size) {
            //Interact with activity
            if (i % 300 == 0) withContext(Dispatchers.Main) {
                viewModel?.let { it.pubProgressInteraction!!.value = i }
            }

            //Read current byte as string
            val currentByte = String.format("%8s", Integer.toBinaryString(java.lang.Byte.toUnsignedInt(bytes[i]))).replace(" ".toRegex(), "0")

            if (JSONLevel <= -2) {//Now reading standard protobuf fields
                //Action state machine
                when (currentTask) {
                    0 -> {//Read next field
                        //Get field data
                        currentFieldNumber = Integer.parseInt(currentByte.dropLast(3), 2)
                        val fieldType = Integer.parseInt(currentByte.drop(5), 2)
                        var fieldTypeName = ""

                        //Format type info and Set the next action
                        when (fieldType) {
                            0 -> {fieldTypeName = "Varint"; currentTask = 1} //Read numeric byte
                            1 -> {fieldTypeName = "64-bits "}
                            2 -> {fieldTypeName = "String"
                                if (currentFieldNumber != 2) {
                                    //JSON Fields started
                                    JSONLevel++
                                    currentJSONTask = 0
                                } else {
                                    //Read standard string value
                                    currentTask = 2
                                }
                            }
                            3 -> {fieldTypeName = "Start group (deprecated)"}
                            4 -> {fieldTypeName = "End group (deprecated)"}
                            5 -> {fieldTypeName = "32-bits"}
                        }

                        when (currentFieldNumber) {
                            1 -> {
                                //id
                                doneString += "\n\"id\": "
                            }
                            2 -> {
                                //interactions_id
                                doneString += "\n\"interactions_id\": "
                            }
                            5 -> {
                                //Notes
                                doneString += "\n\"notes\": "
                            }
                            6 -> {
                                //Sections
                                doneString += "\n\"sections\": "
                            }
                            7 -> {
                                //Perfects
                                doneString += "\n\"perfects\": "
                            }
                            8 -> {
                                //Speeds
                                doneString += "\n\"speeds\": "
                            }
                            9 -> {
                                //Circles
                                doneString += "\n\"circles\": "
                            }
                        }
                    }
                    1 -> {//Decoding varint
                        doneString += "${Integer.parseInt(readMSBValue(bytes, i), 2)}, "

                        //Skip checked MSB bytes (except of this byte, because 'i' will increase on this iteration end)
                        i += checkedBytesCount

                        //Set the next action (Then reading next field)
                        currentTask = 0
                    }
                    2 -> {//Get string length
                        stringLength = Integer.parseInt(readMSBValue(bytes, i), 2)

                        //Skip checked MSB bytes (except of this byte, because 'i' will increase on this iteration end)
                        i += checkedBytesCount

                        //Set the next action (Then reading all string characters. How much is contained in stringLength)
                        currentTask = 3

                        doneString += "\""
                    }
                    3 -> {//Read string value
                        doneString += Integer.parseInt(currentByte, 2).toChar().toString()

                        //Check is string field has ended or increase iteration
                        if (stringLength > 1) stringLength-- else{
                            currentTask = 0
                            doneString += "\", "
                        }
                    }
                }

            } else {//Now reading JSON formatted fields
                //Action state machine

                checkedBytesCount = 0
                var checkLevelEnded = false
                when (currentJSONTask) {
                    0 -> { //Upper JSON level and read it's length
                        JSONLevel++

                        //Set this level length (end byte point)
                        JSONEndPoints.add(JSONLevel, (Integer.parseInt(readMSBValue(bytes, i), 2) + i))

                        //Skip checked MSB bytes (except of this byte, because 'i' will increase on this iteration end)
                        i += checkedBytesCount

                        //Check if this is root level of JSON (the first key on root level is the length)
                        currentJSONTask = if (JSONLevel == 0) 0 else 1

                        doneString += "\n"
                        for (j in JSONLevel downTo 0 step 1) {
                            doneString += "  "
                        }
                        doneString += "{"
                    }
                    1 -> { //Read field key
                        val currentJSONFieldNumber = Integer.parseInt(currentByte.dropLast(3), 2)
                        currentJSONFieldNumberGlobal = currentJSONFieldNumber
                        val fieldType = Integer.parseInt(currentByte.drop(5), 2)

                        //Format type info and Set the next action
                        when (fieldType) {
                            0 -> {currentJSONTask = 2} //Read numeric byte
                            1 -> {}
                            2 -> {currentJSONTask = 0} //Up the JSON level
                            3 -> {}
                            4 -> {}
                            5 -> {currentJSONTask = 3} //Read the 32 bits float value
                        }

                        //Add spaces
                        doneString += "\n"
                        for (j in JSONLevel downTo 0 step 1) {
                            doneString += "  "
                        }

                        //Get it's content type
                        when (currentFieldNumber) {
                            5 -> {
                                //Notes
                                when (JSONLevel) {
                                    1 -> {
                                        when (currentJSONFieldNumber) {
                                            1 -> doneString += "\"note_type\": "
                                            3 -> doneString += "\"single\": "
                                            4 -> doneString += "\"long\": "
                                            6 -> doneString += "\"lane\": "
                                        }
                                    }
                                    2 -> {
                                        when (currentJSONFieldNumber) {
                                            1 -> doneString += "\"note\": "
                                            2 -> doneString += "\"swipe\": "
                                        }
                                    }
                                    3 -> {
                                        when (currentJSONFieldNumber) {
                                            1 -> doneString += "\"offset\": "
                                            3 -> doneString += "\"lane\": "
                                            13 -> doneString += "\"size\": "
                                        }
                                    }
                                }
                            }
                            6 -> {
                                //Sections
                                doneString += "\"offset\": "
                            }
                            7 -> {
                                //Perfects
                                when (currentJSONFieldNumber) {
                                    1 -> doneString += "\"offset\": "
                                    2 -> doneString += "\"multiplier\": "
                                }
                            }
                            8 -> {
                                //Speeds
                                when (currentJSONFieldNumber) {
                                    1 -> doneString += "\"offset\": "
                                    2 -> doneString += "\"multiplier\": "
                                }
                            }
                            9 -> {
                                //Circles
                                when (currentJSONFieldNumber) {
                                    1 -> doneString += "\"offset\": "
                                    4 -> doneString += "\"type\": "
                                }
                            }
                        }
                        //doneString += "$currentJSONFieldNumber: "
                    }
                    2 -> { //Read JSON varint and check isn't this JSON level has ended
                        val currentValue = Integer.parseInt(readMSBValue(bytes, i), 2)
                        doneString += "${currentValue},"

                        //NEW: Add this value to the chart class
                        conversionResult.addValueFromBundle(currentValue.toFloat(), currentFieldNumber, JSONLevel, currentJSONFieldNumberGlobal)

                        //Skip checked MSB bytes (except of this byte, because 'i' will increase on this iteration end)
                        i += checkedBytesCount

                        //Set the next action (Then reading next field)
                        currentJSONTask = 1

                        //Check if it was the last value in this JSON level
                        checkLevelEnded = true

                    }
                    3 -> { //Read JSON 32-bits float and check isn't this JSON level has ended
                        val floatBytesBuffer = ByteBuffer.wrap(bytes.copyOfRange(i, i+4)).order(ByteOrder.LITTLE_ENDIAN)
                        val currentValue = floatBytesBuffer.float
                        doneString += "${currentValue},"

                        //NEW: Add this value to the chart class
                        conversionResult.addValueFromBundle(currentValue, currentFieldNumber, JSONLevel, currentJSONFieldNumberGlobal)

                        //Skip used bytes
                        checkedBytesCount = 3
                        i += checkedBytesCount

                        //Set the next action (Then reading next field)
                        currentJSONTask = 1

                        //Check if it was the last value in this JSON level
                        checkLevelEnded = true

                    }
                }

                //Check how much levels are ended
                if (checkLevelEnded) {
                    var j = JSONLevel
                    while (j >= 0){
                        if (JSONEndPoints[j] <= i) {
                            //Add spaces and output
                            doneString += "\n"
                            for (k in JSONLevel downTo 0 step 1) {
                                doneString += "  "
                            }
                            doneString += "}, "

                            //Decrease level and remove it's point
                            JSONEndPoints.removeAt(JSONLevel)
                            JSONLevel--
                        } else break

                        //Iteration Counter
                        j--
                    }

                    //Check if root level reached. If it is - read length
                    if (JSONLevel == 0) currentJSONTask = 0

                    //Check if JSON has ended then end reading in JSON mode
                    if (JSONLevel == -1) JSONLevel--
                }
            }

            //Iteration counter
            i++
        }

        return conversionResult
    }
    private fun ChartConversionResult.addValueFromBundle(value: Float, currentFieldNumber: Int, JSONLevel: Int, currentJSONFieldNumber: Int) {
        when (currentFieldNumber) {
            5 -> {
                //Notes
                when (JSONLevel) {
                    1 -> {
                        when (currentJSONFieldNumber) {
                            1 -> {
                                chart.notes.add(Note().apply { type = value.toInt() })
                            }//doneString += "\"note_type\": "
                            //3 -> doneString += "\"single\": "
                            //4 -> doneString += "\"long\": "
                            6 -> chart.notes[chart.notes.lastIndex].lane = value.toInt()-1 //doneString += "\"lane\": "
                        }
                    }
                    2 -> {
                        when (currentJSONFieldNumber) {
                            //1 -> doneString += "\"note\": "
                            2 -> chart.notes[chart.notes.lastIndex].swipe = value.toInt() //doneString += "\"swipe\": "
                        }
                    }
                    3 -> {
                        when (currentJSONFieldNumber) {
                            1 -> chart.notes[chart.notes.lastIndex].offsets.add(NoteOffset().apply { position = value }) //doneString += "\"offset\": "
                            3 -> chart.notes[chart.notes.lastIndex].offsets[chart.notes.last().offsets.lastIndex].lane = value.toInt()-1 //doneString += "\"lane\": "
                            13 -> chart.notes[chart.notes.lastIndex].size = value.toInt() //doneString += "\"size\": "
                        }
                    }
                }
            }
            6 -> {
                //Sections
                chart.sections.add(value)
                //doneString += "\"offset\": "
            }
            7 -> {
                //Perfects
                when (currentJSONFieldNumber) {
                    1 -> chart.perfects.add(Perfect().apply { offset = value })//doneString += "\"offset\": "
                    2 -> if (chart.perfects.size > 0) chart.perfects[chart.perfects.lastIndex].multiplier = value
                            else chart.perfects.add(Perfect().apply { offset = 0F; multiplier = value })//doneString += "\"multiplier\": "
                }
            }
            8 -> {
                //Speeds
                when (currentJSONFieldNumber) {
                    1 -> chart.speeds.add(Speed().apply { offset = value })//doneString += "\"offset\": "
                    2 -> if (chart.speeds.size > 0)chart.speeds[chart.speeds.lastIndex].multiplier = value
                            else chart.speeds.add(Speed().apply { offset = 0F; multiplier = value })//doneString += "\"multiplier\": "
                }
            }
            9 -> {
                //Circles
                when (currentJSONFieldNumber) {
                    1 -> chart.effects.add(Effect().apply { offset = value })//doneString += "\"offset\": "
                    4 -> chart.effects[chart.effects.lastIndex].types.add(value.toInt())//doneString += "\"type\": "
                }
            }
        }
    }
    private fun readMSBValue(bytes: ByteArray, i: Int): String {
        //Manage global values
        checkedBytesCount = 0

        //Analyze MSB bytes
        val tempBytesStr = mutableListOf<String>()

        var byteCursor = i

        while (byteCursor < bytes.size) {
            val checkingByte = String.format("%8s", Integer.toBinaryString(java.lang.Byte.toUnsignedInt(bytes[byteCursor]))).replace(" ".toRegex(), "0")

            if (checkingByte[0] == '1') {
                //Need to check next byte
                tempBytesStr.add(checkingByte)
            } else {
                //This is last byte in the group
                if (tempBytesStr.size > 0) {
                    //Add this last byte
                    tempBytesStr.add(checkingByte)

                    //There is more than 1 byte in the group
                    var mergedBytesGroup = ""
                    for (j in tempBytesStr.size-1 downTo 0){
                        mergedBytesGroup += tempBytesStr[j].drop(1) //Drop MSB and add to other bytes
                    }

                    //Set how much bytes are checked
                    checkedBytesCount = tempBytesStr.size - 1

                    //Add result to the done string
                    return mergedBytesGroup
                } else {
                    //This is one and final byte in the group. Just add the parsed int to the done string
                    return checkingByte
                }
            }

            //Iteration Counter
            byteCursor++
        }

        //If bytes end reached (exception-safe)
        var mergedBytesGroup = ""
        for (j in tempBytesStr.size-1 downTo 0){
            mergedBytesGroup += tempBytesStr[j].drop(1) //Drop MSB and add to other bytes
        }
        return mergedBytesGroup
    }

    //Chart lines to Beatstar bundle converter
    class ChartConversionResult {
        var resultData = mutableListOf<Byte>()
        var exceptionList = mutableListOf<ChartException>()
        var chart = Chart()

        class ChartException {
            var id = 0
            var dataBundle = mutableListOf<String>()
        }
    }
    suspend fun importChartFromFile(chartLines: List<String>, viewModel: MainViewModel?): ChartConversionResult {
        var readTask = 1
        // 0 - get next bytes category
        // 1 - read song props
        // 2 - read sync options
        // 3 - read sections and effects

        val chart = Chart()

        val chartConversionResult = ChartConversionResult()

        //This value is required for stacking effects on the same position to one record in file
        var prevEffeсtPos = 0

        //Recognize whole .chart file and convert positions
        var i = -1
        while (i < chartLines.size-1){
            //Interaction with activity
            if (i % 100 == 0) withContext(Dispatchers.Main) {
                viewModel?.pubProgressInteraction!!.value = i
            }
            //Iteration counter
            i++
            //Proceed reading file
            when (readTask) {
                0 -> {
                    when (chartLines[i].trim().trimEnd()) {
                        "[Song]" -> {
                            readTask = 1
                            i++
                        }
                        "[SyncTrack]" -> {
                            readTask = 2
                            i++
                        }
                        "[Events]" -> {
                            readTask = 3
                            i++
                        }
                        "[ExpertSingle]" -> {
                            readTask = 4
                            i++
                        }
                        else -> {
                            chartConversionResult.exceptionList.add(packException(3, "$i: ''${chartLines[i].trim().trimEnd()}''"))
                            continue
                        }
                    }
                }
                1 -> {
                    //Song props skip
                    if (chartLines[i].contains("}")) readTask = 0
                }
                2 -> {
                    //Read BPM, skip TS and bracket
                    i++
                    chart.bpm = chartLines[i].substring(chartLines[i].indexOf("B")+2, chartLines[i].length-1).trim().trimEnd().toInt()/100

                    readTask = 0
                    i++
                }
                3 -> {
                    //Read effects and sections
                    if (chartLines[i].contains("}")) readTask = 0 else {
                        if (chartLines[i].contains("section")) {
                            //This is section
                            chart.sections.add(toBeatstarOffset(chartLines[i].substring(0, chartLines[i].indexOf("=")-1).trim().trimEnd().toInt(), chart.bpm))
                            if (chart.sections.size > 5) {
                                //Exception
                                chartConversionResult.exceptionList.add(packException(7))
                                continue
                            }
                        } else {
                            //This is effect
                            val effect = Effect()
                            effect.types.add(when (chartLines[i].substring(chartLines[i].indexOf("\"") + 1).dropLast(1)) {
                                "LargeDrop" -> 1
                                "BlackCirclePump" -> 2
                                "WhiteCirclePump" -> 3
                                "ThinRingBurst" -> 4
                                "ShockwaveDark" -> 5
                                "ThinRingBurstWider" -> 6
                                "RippleMid" -> 7
                                "LineBurst" -> 8
                                "LeftLineBurst" -> 9
                                "RightLineBurst" -> 10
                                "Absorb" -> 11
                                "AbsorbDarkRings" -> 12
                                "StrobeLight" -> 13
                                "Clap" -> 14
                                "ClapDarkRings" -> 15
                                "CornersFlash" -> 16
                                "LeftCornerFlash" -> 17
                                "RightCornerFlash" -> 18
                                "45Wipe" -> 19
                                "PopClaps" -> 20
                                "RightPopClap" -> 21
                                "LeftPopClap" -> 22
                                "TikTikBooms" -> 23
                                "RightBigPulse" -> 24
                                "LeftBigPulse" -> 25
                                "LeftClap" -> 26
                                "RightClap" -> 27
                                "MegaDrop" -> 28
                                "WipeDown" -> 29
                                "WipeUp" -> 30
                                "LongRippleLeft" -> 31
                                "LongRippleRight" -> 32
                                "QuickSwitchLeft" -> 33
                                "QuickSwitchRight" -> 34
                                "ChainReactionLeft" -> 35
                                "ChainReactionRight" -> 36
                                "VerticalWipe" -> 37
                                "StrobeLongFade" -> 38
                                "LargeTickPump" -> 39
                                "LightBeamRightUp" -> 40
                                "LightBeamLeftUp" -> 41
                                "TikTikBoomLeft" -> 42
                                "TikTikBoomRight" -> 43
                                "DoublePopLeft" -> 44
                                "DoublePopRight" -> 45
                                "MegaAbsorb" -> 46
                                "Waterfall" -> 47
                                "WaterfallCascade" -> 48
                                "ScatterPop" -> 49
                                "ScatterPopPulse" -> 50
                                else -> {
                                    //Unknown blue flag (exception)
                                    chartConversionResult.exceptionList.add(packException(1,
                                        chartLines[i].substring(chartLines[i].indexOf("\"") + 1).dropLast(1).trim(), //Flag content
                                        chartLines[i].substring(0, chartLines[i].indexOf("=")-1).trim())) //Flag pos
                                    continue
                                }
                            })

                            //Add this type to existing offset or create new
                            val currentEffectOffset = chartLines[i].substring(0, chartLines[i].indexOf("=")-1).trim().trimEnd().toInt()
                            if (prevEffeсtPos == currentEffectOffset) {
                                chart.effects[chart.effects.size-1].types.add(effect.types[0])
                            } else {
                                effect.offset = toBeatstarOffset(currentEffectOffset, chart.bpm)
                                chart.effects.add(effect)
                            }
                            prevEffeсtPos = currentEffectOffset
                        }
                    }
                }
                4 -> {
                    //Read notes and it's event
                    if (chartLines[i].contains("}")) readTask = 0 else {
                        if (chartLines[i].contains("= N")) {
                            //This is note
                            val note = Note()
                            val noteData = chartLines[i].substring(chartLines[i].indexOf("N")+2).split(" ")
                            //[0] - its lane
                            //[1] - its end pos if long
                            note.rawPos.add(chartLines[i].substring(0, chartLines[i].indexOf("=")-1).trim().trimEnd().toLong())

                            note.lane = noteData[0].toInt()
                            note.swipe = 0
                            //First offset data
                            note.offsets.add(NoteOffset().apply {
                                position = toBeatstarOffset(chartLines[i].substring(0, chartLines[i].indexOf("=")-1).trim().trimEnd().toInt(), chart.bpm)
                                lane = note.lane
                                rawPos = note.rawPos.first()
                            })
                            //Set note points (if this is long note there will be 2 points)
                            if (noteData[1] != "0") {
                                note.type = 2
                                note.rawPos.add(chartLines[i].substring(0, chartLines[i].indexOf("=")-1).trim().trimEnd().toLong() + noteData[1].toLong())
                                //Second offset data
                                note.offsets.add(NoteOffset().apply {
                                    position = toBeatstarOffset(noteData[1].toInt() + chartLines[i].substring(0, chartLines[i].indexOf("=")-1).trim().trimEnd().toInt(), chart.bpm)
                                    lane = note.lane
                                    rawPos = note.rawPos.last()
                                })
                            } else note.type = 1


                            chart.notes.add(note)
                        } else if (chartLines[i].contains("= E")) {
                            chartLines[i].trimEnd()
                            //This is event (note swipe direction or size prop)
                            when (chartLines[i].substring(chartLines[i].indexOf("E")+2)[0]) {
                                '/' -> {
                                    //This is note size prop
                                    when (chartLines[i].substring(chartLines[i].indexOf("/")+1)[0]) {
                                        '1', '2' -> {
                                            //Now check position
                                            if (chartLines[i].substring(0, chartLines[i].indexOf("=")-1).trim().trimEnd().toInt() == chart.notes[chart.notes.size-1].rawPos[0].toInt()) {
                                                chart.notes[chart.notes.size-1].size = chartLines[i].substring(chartLines[i].indexOf("/")+1).trim().trimEnd().toInt()
                                            } else {
                                                //There is event at $pos, but there was no note there (exception)
                                                chartConversionResult.exceptionList.add(packException(4,
                                                    chartLines[i].substring(0, chartLines[i].indexOf("=")-1).trim())) //Event Position
                                                break
                                            }
                                        }
                                        else -> {
                                            //Unknown event on $pos: $event [pos:event]
                                            chartConversionResult.exceptionList.add(packException(5,
                                                chartLines[i].substring(0, chartLines[i].indexOf("=")-1), //Flag pos
                                                chartLines[i].substring(chartLines[i].indexOf("E")+2).dropLast(1).trim())) //Event
                                            continue
                                        }
                                    }
                                }
                                'p' -> {
                                    //This is perfect prop
                                    val perfect = Perfect()
                                    perfect.offset = toBeatstarOffset(chartLines[i].substring(0, chartLines[i].indexOf("=")-1).trim().trimEnd().toInt(), chart.bpm)
                                    //Check and write multiplier
                                    if (chartLines[i].substring(chartLines[i].indexOf("p")+1).length == 3) {
                                        perfect.multiplier = (chartLines[i].substring(chartLines[i].indexOf("p")+2).toFloat()/100) + chartLines[i][chartLines[i].indexOf("p")+1].toString().toFloat()
                                    } else {
                                        //Exception
                                        chartConversionResult.exceptionList.add(packException(5,
                                            chartLines[i].substring(0, chartLines[i].indexOf("=")-1), //Flag pos
                                            chartLines[i].substring(chartLines[i].indexOf("E")+2).dropLast(1).trim())) //Event
                                        continue
                                    }
                                    chart.perfects.add(perfect)
                                }
                                's' -> {
                                    //This is speed prop
                                    val speed = Speed()
                                    speed.offset = toBeatstarOffset(chartLines[i].substring(0, chartLines[i].indexOf("=")-1).trim().trimEnd().toInt(), chart.bpm)
                                    //Check and write multiplier
                                    if (chartLines[i].substring(chartLines[i].indexOf("s")+1).length == 3) {
                                        speed.multiplier = (chartLines[i].substring(chartLines[i].indexOf("s")+2).toFloat()/100) + chartLines[i][chartLines[i].indexOf("s")+1].toString().toFloat()
                                    } else {
                                        //Exception
                                        chartConversionResult.exceptionList.add(packException(5,
                                            chartLines[i].substring(0, chartLines[i].indexOf("=")-1).trim(), //Flag pos
                                            chartLines[i].substring(chartLines[i].indexOf("E")+2).trim())) //Event
                                        continue
                                    }
                                    chart.speeds.add(speed)
                                }
                                'h' -> {
                                    //This is rail hold position change prop
                                    try {
                                        val tagPosition = chartLines[i].substring(0, chartLines[i].indexOf("=")-1).trim().toLong()
                                        var currentLane = 0
                                        var nextLane = 0
                                        chartLines[i].substring(chartLines[i].indexOf("h")+1).let {
                                            currentLane = it[0].toString().toInt()-1
                                            nextLane = it[2].toString().toInt()-1
                                        }

                                        //Check ALL notes to match position and lane (there is no better way...)
                                        var isNoteFound = false
                                        chart.notes.forEachIndexed { index, note ->
                                            if (tagPosition >= note.rawPos.first() && tagPosition <= note.rawPos.last()) {
                                                //Now check latest offset to allowed
                                                val anchorOffset = note.offsets[note.offsets.size-1]
                                                if (note.type == 5) {
                                                    if (note.offsets.last().rawPos == tagPosition) {
                                                        isNoteFound = true
                                                        //This tag should replace last offset
                                                        val lastOffset = note.offsets.last()
                                                        lastOffset.lane = nextLane - 1

                                                        chart.notes[index].offsets.removeLast()
                                                        chart.notes[index].offsets.add(lastOffset)
                                                    } else if (anchorOffset.lane == currentLane) {
                                                        isNoteFound = true
                                                        //New offset data, should be added
                                                        chart.notes[index].offsets.add(NoteOffset().apply {
                                                            position = toBeatstarOffset(tagPosition.toInt(), chart.bpm)
                                                            rawPos = tagPosition
                                                            lane = nextLane
                                                        })
                                                    }
                                                } else if (note.offsets.first().rawPos == tagPosition && note.lane == currentLane) {
                                                    chart.notes[index].type = 5
                                                    isNoteFound = true
                                                }
                                            }
                                        }

                                        if (!isNoteFound) {
                                            chartConversionResult.exceptionList.add(packException(4,
                                                chartLines[i].substring(0, chartLines[i].indexOf("=")-1).trim()))
                                            continue
                                        }
                                    } catch (e: Exception) {
                                        //Exception
                                        chartConversionResult.exceptionList.add(packException(5,
                                            chartLines[i].substring(0, chartLines[i].indexOf("=")-1).trim(), //Flag pos
                                            chartLines[i].substring(chartLines[i].indexOf("E")+2).trim())) //Event
                                        continue
                                    }
                                }
                                else -> {
                                    //Swipe direction or unknown
                                    //Split all the tags
                                    val tagsList = chartLines[i].substring(chartLines[i].indexOf("E")+2).split(",")
                                    //Trim everything
                                    tagsList.forEach {
                                        it.trim().trimEnd()
                                    }
                                    //Check all the tags
                                    for (j in tagsList.indices) {
                                        //Check every note on this position
                                        val tagPos = chartLines[i].substring(0, chartLines[i].indexOf("=")-1).trim().trimEnd().toLong()
                                        //val notesTagCandidates = chart.notes.takeWhile {it.rawPos == tagPos}
                                        val noteIndexes = mutableListOf<Int>()
                                        chart.notes.forEachIndexed { index, note ->
                                            note.rawPos.forEach {
                                                if (it == tagPos) noteIndexes.add(index)
                                            }
                                        }

                                        //Check if there is no notes at this position
                                        if (noteIndexes.isEmpty()) {
                                            //Exception
                                            chartConversionResult.exceptionList.add(packException(4,
                                                chartLines[i].substring(0, chartLines[i].indexOf("=")-1).trim()))
                                            continue
                                        }
                                        var isNoteFound = false
                                        for (k in noteIndexes.indices) {
                                            //Now check lane
                                            if (chart.notes[noteIndexes[k]].lane == tagsList[j][tagsList[j].length-1].toString().toInt()-1) {
                                                isNoteFound = true
                                                chart.notes[noteIndexes[k]].swipe = when (tagsList[j].dropLast(1)) {
                                                    "u" -> 1
                                                    "d" -> 2
                                                    "l" -> 3
                                                    "r" -> 4
                                                    "ul" -> 5
                                                    "ur" -> 6
                                                    "dl" -> 7
                                                    "dr" -> 8
                                                    else -> {
                                                        //Exception
                                                        chartConversionResult.exceptionList.add(packException(5,
                                                            chartLines[i].substring(0, chartLines[i].indexOf("=")-1).trim(),
                                                            tagsList[j].dropLast(1).trim()))
                                                        continue
                                                    }
                                                }
                                                break
                                            }
                                        }
                                        if (!isNoteFound) {
                                            //Note not found on this lane
                                            chartConversionResult.exceptionList.add(packException(6,
                                                tagsList[j][tagsList[j].length-1].toString(), //Lane
                                                chartLines[i].substring(0, chartLines[i].indexOf("=")-1).trim().trimEnd())) //Position
                                            continue
                                        }
                                    }
                                }
                            }
                        } else {
                            //File was modified or unknown
                            chartConversionResult.exceptionList.add(packException(3,
                                "$i: ''${chartLines[i].trim().trimEnd()}''"))
                            continue
                        }
                    }
                }
            }
        }

        //Sections check
        //if (chart.sections.size == 0) chartConversionResult.exceptionList.add(packException(8)) //TODO НИКАКОГО СЕКТИОНС ЧЕК

        //Exception
        if (chartConversionResult.exceptionList.size > 0) {
            return chartConversionResult
        }

        chartConversionResult.chart = chart
        return chartConversionResult
    }
    fun convertChartToBundle(chart: Chart): MutableList<Byte> {
        val chartConversionResult = ChartConversionResult()
        //Write first fields bytes -------------------------------------------------------------
        "08FC03120437372D31".asHex().forEach { chartConversionResult.resultData.add(it) }

        //Write notes bytes -------------------------------------------------------------
        val allNotesBytes = mutableListOf<Byte>()
        chart.notes.forEach { note ->
            val noteBytes = mutableListOf<Byte>()
            //Type field number and actually type (08 - key)
            "08".asHex().forEach { noteBytes.add(it) }
            noteBytes.add(note.type.toByte())

            //Write note offsets bytes
            val allOffsetsBytes = mutableListOf<Byte>()
            note.offsets.forEach { offset ->
                val offsetBytes = mutableListOf<Byte>()
                //Offset key and bytes
                "0D".asHex().forEach { offsetBytes.add(it) }
                offset.position.toBytes().forEach { offsetBytes.add(it) }
                //Lane key and bytes (in offset) (if song type is 5 - take lane from the class)
                "18".asHex().forEach { offsetBytes.add(it) }
                if (note.type == 5)
                    offsetBytes.addAll((offset.lane+1).toVarint().toMutableList()) //This is the curve hold
                    else
                    offsetBytes.addAll((note.lane+1).toVarint().toMutableList())

                //Add done offset bytes to other
                "0A".asHex().forEach { allOffsetsBytes.add(it) } //Its key
                allOffsetsBytes.addAll(offsetBytes.size.toVarint().toMutableList()) //Bytes length
                offsetBytes.forEach { allOffsetsBytes.add(it) } //Bytes
            }
            //Add swipe if needed (offsets contain this)
            if (note.swipe != 0) {
                "10".asHex().forEach { allOffsetsBytes.add(it) }
                allOffsetsBytes.addAll(note.swipe.toVarint().toMutableList())
            }

            //Add done offsets to current note bytes (72 - key for rail holds, 22 - key for holds, 1A - key for normals)
            if (note.type == 5) "72".asHex().forEach { noteBytes.add(it) } else if (note.offsets.size > 1) "22".asHex().forEach { noteBytes.add(it) } else "1A".asHex().forEach { noteBytes.add(it) }
            allOffsetsBytes.size.toVarint().forEach { noteBytes.add(it) }
            allOffsetsBytes.forEach { noteBytes.add(it) }

            //Add note's lane
            "30".asHex().forEach { noteBytes.add(it) }
            noteBytes.addAll((note.lane+1).toVarint().toMutableList())

            //Add note's size if needed
            if (note.size != 0) {
                //Add size key and value
                "68".asHex().forEach { noteBytes.add(it) }
                noteBytes.addAll(note.size.toVarint().toMutableList())
            }

            //Add this note to other
            allNotesBytes.addAll(noteBytes.size.toVarint().toMutableList()) //Bytes length (note root doesn't contain keys)
            noteBytes.forEach { allNotesBytes.add(it) }
        }
        //Add all notes bytes, it's key and length to done bytes
        "2A".asHex().forEach { chartConversionResult.resultData.add(it) }
        allNotesBytes.size.toVarint().forEach { chartConversionResult.resultData.add(it) }
        allNotesBytes.forEach { chartConversionResult.resultData.add(it) }

        //Write sections bytes (from .chart file) --------------------------------------------------------------------------------
        //Add one section at the end if there is no any of them
        if (chart.sections.size == 0)
            if (chart.notes.size > 0) chart.sections.add(chart.notes[chart.notes.lastIndex].offsets[0].position)

        val allSectionsBytes = mutableListOf<Byte>()
        chart.sections.forEach {
            //Add length and key (length is always 5)
            "050D".asHex().forEach { allSectionsBytes.add(it) }
            it.toBytes().forEach { allSectionsBytes.add(it) }
        }
        //Add sections key, its length and bytes
        "32".asHex().forEach { chartConversionResult.resultData.add(it) }
        allSectionsBytes.size.toVarint().forEach { chartConversionResult.resultData.add(it) }
        allSectionsBytes.forEach { chartConversionResult.resultData.add(it) }

        //Write perfects bytes (from options if chart doesn't contain it) -------------------------------------------------------------
        val allPerfectsBytes = mutableListOf<Byte>()
        chart.perfects.forEachIndexed { index, it ->
            val perfectBytes = mutableListOf<Byte>()
            if (index != 0) {
                //Offset key and actually offset
                "0D".asHex().forEach { perfectBytes.add(it) }
                it.offset.toBytes().forEach { perfectBytes.add(it) }
            }
            //Multiplier key and actually multiplier
            "15".asHex().forEach { perfectBytes.add(it) }
            it.multiplier.toBytes().forEach { perfectBytes.add(it) }

            //Add this perfect to others
            allPerfectsBytes.addAll(perfectBytes.size.toVarint().toMutableList())
            perfectBytes.forEach { allPerfectsBytes.add(it) }
        }
        //Add perfects key, its length and bytes
        "3A".asHex().forEach { chartConversionResult.resultData.add(it) }
        allPerfectsBytes.size.toVarint().forEach { chartConversionResult.resultData.add(it) }
        allPerfectsBytes.forEach { chartConversionResult.resultData.add(it) }

        //Write speeds bytes (from options if chart doesn't contain it) -------------------------------------------------------------
        val allSpeedsBytes = mutableListOf<Byte>()
        chart.speeds.forEachIndexed { index, it ->
            val speedBytes = mutableListOf<Byte>()
            if (index != 0) {
                //Offset key and actually offset
                "0D".asHex().forEach { speedBytes.add(it) }
                it.offset.toBytes().forEach { speedBytes.add(it) }
            }
            //Speed key and actually speed
            "15".asHex().forEach { speedBytes.add(it) }
            it.multiplier.toBytes().forEach { speedBytes.add(it) }

            //Add this speed to others
            allSpeedsBytes.addAll(speedBytes.size.toVarint().toMutableList())
            speedBytes.forEach { allSpeedsBytes.add(it) }
        }
        //Add speeds key, its length and bytes
        "42".asHex().forEach { chartConversionResult.resultData.add(it) }
        allSpeedsBytes.size.toVarint().forEach { chartConversionResult.resultData.add(it) }
        allSpeedsBytes.forEach { chartConversionResult.resultData.add(it) }

        //Write effects bytes (from .chart file) -------------------------------------------------------------
        val allEffectsBytes = mutableListOf<Byte>()
        chart.effects.forEach {
            val effectBytes = mutableListOf<Byte>()
            //Offset key and actually offset
            "0D".asHex().forEach { effectBytes.add(it) }
            it.offset.toBytes().forEach { effectBytes.add(it) }

            //Every effect key and actually offset
            it.types.forEach { effectType ->
                "20".asHex().forEach { effectBytes.add(it) }
                effectBytes.addAll(effectType.toVarint().toMutableList())
            }

            //Add this effect to others
            allEffectsBytes.addAll(effectBytes.size.toVarint().toMutableList())
            effectBytes.forEach { allEffectsBytes.add(it) }
        }
        //Add speeds key, its length and bytes
        "4A".asHex().forEach { chartConversionResult.resultData.add(it) }
        allEffectsBytes.size.toVarint().forEach { chartConversionResult.resultData.add(it) }
        allEffectsBytes.forEach { chartConversionResult.resultData.add(it) }

        chartConversionResult.resultData = packChartToBundle(chartConversionResult.resultData)

        //All done!!!--------------------------------------------
        return chartConversionResult.resultData
    }
    private fun toBeatstarOffset(pos: Int, bpm: Int): Float {
        return String.format("%.2f", pos.toFloat()/192).replace(',', '.').toFloat() //TODO implement chart resolution here
    }

    private fun packException(id: Int, vararg data: String): ChartConversionResult.ChartException {
        val exception = ChartConversionResult.ChartException()
        exception.id = id
        for (i in data) {
            exception.dataBundle.add(i)
        }
        return exception
    }

    //Pack files to budnle
    private fun packChartToBundle(bytes: MutableList<Byte>): MutableList<Byte> {
        //Header and other bundle things
        val fileData = BeatChartsUtils.FilesData.ChartData().chartFile.asHex().toMutableList()

        val appendedBytes = 16
        fileData.addAll(bytes)
        repeat(appendedBytes) { fileData.add(0) }

        //Uncompressed size of data block
        fileData.write4BytesInBigEndian((4276+bytes.size+appendedBytes).toLong().toUInt32(), 84)
        //Compressed size of data block (the same)
        fileData.write4BytesInBigEndian((4276+bytes.size+appendedBytes).toLong().toUInt32(), 88)
        //Size of uncompressed data of first asset (one in this file) (the same again)
        fileData.write4BytesInBigEndian((4276+bytes.size+appendedBytes).toLong().toUInt32(), 110)
        //Size of uncompressed data of first asset (one in this file) (and again)
        fileData.write4BytesInBigEndian((4276+bytes.size+appendedBytes).toLong().toUInt32(), 159)

        //Generate randomized id
        val newId = getRandomString(32)
        for (i in 122..153) {
            fileData[i] = newId[153-i].toByte()
        }

        //Uncompressed data size with data header in asset
        fileData.write4BytesInLittleEndian((12+bytes.size+appendedBytes).toLong().toUInt32(), 2691)
        //Size of data
        fileData.write4BytesInLittleEndian((bytes.size).toLong().toUInt32(), 4427)

        //Whole file size
        fileData.write4BytesInBigEndian((fileData.size).toLong().toUInt32(), 34)

        return fileData
    }
    fun exportWavToBundle(wemBytes: MutableList<Byte>, outputFile: File): Boolean {
        //Convert wav to wem ===================================
        //Find current header position
        var headerEndPoint = 0
        for (i in wemBytes.indices) {
            if (byteArrayOf(wemBytes[i]).decodeToString() == "d" && byteArrayOf(wemBytes[i+1]).decodeToString() == "a" && byteArrayOf(wemBytes[i+2]).decodeToString() == "t" && byteArrayOf(wemBytes[i+3]).decodeToString() == "a") {
                headerEndPoint = i+3
                break
            }
        }
        //Remove current header
        repeat(headerEndPoint+1){ wemBytes.removeAt(0) }
        //Write wem file header (that's all that needed to make it work!)
        "52494646F845490257415645666D742018000000FEFF020044AC000010B102000400100006000000023100004A554E4B040000000000000064617461".asHex().reversed().forEach { wemBytes.add(0, it) }
        //Change wem header sizes
        wemBytes.write4BytesInLittleEndian((wemBytes.size-8).toLong().toUInt32(), 4)
        wemBytes.write4BytesInLittleEndian((24).toLong().toUInt32(), 16)

        //Make bundle ===================================
        //Header and other bundle things
        val bundleHeader: MutableList<Byte>? = BeatChartsUtils.FilesData.WemData().wemFile.asHex().toMutableList()

        //Uncompressed size of data block
        bundleHeader!!.write4BytesInBigEndian((4360+wemBytes.size+16).toLong().toUInt32(), 84)
        //Compressed size of data block (the same)
        bundleHeader.write4BytesInBigEndian((4360+wemBytes.size+16).toLong().toUInt32(), 88)
        //Size of uncompressed data of first asset (one in this file) (the same again)
        bundleHeader.write4BytesInBigEndian((4360+wemBytes.size+16).toLong().toUInt32(), 110)
        //Size of uncompressed data of first asset (one in this file) (and again)
        bundleHeader.write4BytesInBigEndian((4360+wemBytes.size+16).toLong().toUInt32(), 159)

        //Generate randomized id
        val newId = getRandomString(32)
        for (i in 122..153) {
            bundleHeader[i] = newId[153-i].toByte()
        }

        //Uncompressed data size with data header in asset
        bundleHeader.write4BytesInLittleEndian((12+wemBytes.size+16+68).toLong().toUInt32(), 2691)
        //Size of data (i don't know what exactly it is)
        bundleHeader.write4BytesInLittleEndian((wemBytes.size+68-16+28).toLong().toUInt32(), 4447)
        //Size of data
        bundleHeader.write4BytesInLittleEndian((wemBytes.size+16).toLong().toUInt32(), 4511)
        //Size of data but without header of asset data block i guess...
        bundleHeader.write4BytesInLittleEndian((wemBytes.size).toLong().toUInt32(), 4503)

        //Whole file size
        bundleHeader.write4BytesInBigEndian((bundleHeader.size + wemBytes.size + 16).toLong().toUInt32(), 34)

        val outputStream = BufferedOutputStream(FileOutputStream(outputFile.absoluteFile))
        bundleHeader.forEach {
            outputStream.write(it.toInt())
        }
        wemBytes.forEach {
            outputStream.write(it.toInt())
        }
        repeat(300) { outputStream.write(0) }
        outputStream.close()
        //outputFile.appendBytes(bytes.toByteArray())

        return true
    }
    fun packWemToBundle(bytes: MutableList<Byte>): MutableList<Byte> {
        //Header and other bundle things
        val fileData = BeatChartsUtils.FilesData.WemData().wemFile.asHex().toMutableList()

        fileData.addAll(bytes)

        //Uncompressed size of data block
        fileData.write4BytesInBigEndian((4360+bytes.size).toLong().toUInt32(), 84)
        //Compressed size of data block (the same)
        fileData.write4BytesInBigEndian((4360+bytes.size).toLong().toUInt32(), 88)
        //Size of uncompressed data of first asset (one in this file) (the same again)
        fileData.write4BytesInBigEndian((4360+bytes.size).toLong().toUInt32(), 110)
        //Size of uncompressed data of first asset (one in this file) (and again)
        fileData.write4BytesInBigEndian((4360+bytes.size).toLong().toUInt32(), 159)

        //Generate randomized id
        val newId = getRandomString(32)
        for (i in 122..153) {
            fileData[i] = newId[153-i].toByte()
        }

        //Uncompressed data size with data header in asset
        fileData.write4BytesInLittleEndian((12+bytes.size).toLong().toUInt32(), 2691)
        //Size of data
        fileData.write4BytesInLittleEndian(bytes.size.toLong().toUInt32(), 4511)

        //Whole file size
        fileData.write4BytesInBigEndian(fileData.size.toLong().toUInt32(), 34)

        return fileData
    }
    fun generateRandomId(bytes: MutableList<Byte>): MutableList<Byte> {
        //Generate randomized id
        val newId = getRandomString(32)
        for (i in 122..153) {
            bytes[i] = newId[153-i].toByte()
        }
        return bytes
    }
    fun packInfoToJson(chart: Chart, chartFiles: ChartFiles, viewModel: MainViewModel?): String {
        val infoMap = mapOf<String, Any>(
            Pair("title", chartFiles.info.title.trim()),
            Pair("artist", if (viewModel != null && viewModel.offlineMode.value == false) chartFiles.info.artist.trim().trimEnd() + " //@" + viewModel.username.value else chartFiles.info.artist.trim().trimEnd() + " (BCOffline)"),
            Pair("id", BeatChartsUtils.Data.generateStringNumber(6)),
            Pair("difficulty", chartFiles.info.diff),
            Pair("bpm", chart.bpm),
            Pair("sections", 5),
            Pair("maxScore", calculateMaxScore(chart, chartFiles.info.diff)),
            Pair("type", if (chart.isDeluxe) "Promode" else "Regular")
        )
        return JSONObject(infoMap).toString()
    }
    fun packConfigToJson(chartFiles: ChartFiles): String {
        val configMap = mapOf<String, Any>(
            Pair("SongTemplate", mapOf<String, Any>(
                Pair("BaseColor", chartFiles.colors.circleGradient[0].colorToHex()),
                Pair("DarkColor", chartFiles.colors.circleGradient[1].colorToHex()),
                Pair("ColorGradient", arrayListOf<Map<String, Any>>(
                    mapOf(Pair("color", chartFiles.colors.menuGradient[0].colorToHex()), Pair("time", 0)),
                    mapOf(Pair("color", chartFiles.colors.menuGradient[1].colorToHex()), Pair("time", 1))
                )),
                Pair("CheckpointOutlineColour", chartFiles.colors.circleGradient[0].colorToHex()),
                Pair("ColorGradientInGame", arrayListOf<Map<String, Any>>(
                    mapOf(Pair("color", chartFiles.colors.sideGradient[0].colorToHex()), Pair("time", 0)),
                    mapOf(Pair("color", chartFiles.colors.sideGradient[1].colorToHex()), Pair("time", 0.5)),
                    mapOf(Pair("color", chartFiles.colors.sideGradient[2].colorToHex()), Pair("time", 1))
                )),
                Pair("StreakConfig", arrayListOf<Map<String, Any>>(
                    mapOf(Pair("glowColor", chartFiles.colors.glowColor.colorToHex()),
                        Pair("perfectBarColor", ""),
                        Pair("invertPerfectBar", /*if (chartFiles.colors.invertPerfectBar) "true" else */"false"),
                        Pair("VFXColor", chartFiles.colors.circleGradient[0].colorToHex())),
                    mapOf(Pair("glowColor", chartFiles.colors.glowColor.colorToHex()),
                        Pair("perfectBarColor", ""),
                        Pair("invertPerfectBar", /*if (chartFiles.colors.invertPerfectBar) "true" else */"false"),
                        Pair("VFXColor", chartFiles.colors.circleGradient[0].colorToHex())),
                    mapOf(Pair("glowColor", chartFiles.colors.glowColor.colorToHex()),
                        Pair("perfectBarColor", ""),
                        Pair("invertPerfectBar", /*if (chartFiles.colors.invertPerfectBar) "true" else */"false"),
                        Pair("VFXColor", chartFiles.colors.circleGradient[0].colorToHex())),
                    mapOf(Pair("glowColor", chartFiles.colors.glowColor.colorToHex()),
                        Pair("perfectBarColor", ""),
                        Pair("invertPerfectBar", /*if (chartFiles.colors.invertPerfectBar) "true" else */"false"),
                        Pair("VFXColor", chartFiles.colors.circleGradient[0].colorToHex())),
                    mapOf(Pair("glowColor", chartFiles.colors.glowColor.colorToHex()),
                        Pair("perfectBarColor", ""),
                        Pair("invertPerfectBar", /*if (chartFiles.colors.invertPerfectBar) "true" else */"false"),
                        Pair("VFXColor", chartFiles.colors.circleGradient[0].colorToHex())),
                )),
                Pair("TrackIntensityGlow", chartFiles.colors.circleGradient[0].colorToHex()),
                Pair("VFXColor", chartFiles.colors.circleGradient[0].colorToHex()),
                Pair("VFXAlternativeColor", chartFiles.colors.circleGradient[0].colorToHex()),
            ))
        )
        return JSONObject(configMap).toString()
    }

    //Files export utils ======================================================================================================================================================
    //Calculate maxScore (DethoRhyne <3)
    private fun calculateMaxScore(chart: Chart, difficulty: Int): Int {
        var units = 0

        //Get all notes count (swipe holds counts as 2 notes)
        val notesCount = chart.notes.size + chart.notes.filter { note -> note.offsets.size > 1 && note.swipe != 0}.size

        //Calculate (Loop because i'm stupid now)
        for (i in 0 until notesCount) {
            units += if (i in 11..25) { //x2 multiplier
                10
            } else if ((i in 26..50) || (i >= 51 && difficulty == 4)) { //x3 multiplier (Normal maximum)
                15
            } else if (i in 51..100 || (i >= 101 && difficulty == 3)) { //x4 multiplier (Hard maximum)
                20
            } else if (i >= 101) { //x5 (Only for extremes)
                25
            } else { //Less that 11
                5
            }
        }

        //Add all bonus ticks
        chart.notes.filter { note -> note.offsets.size > 1 }.forEach {
            var bonusTicks: Int = floor(it.offsets.last().position).toInt() - floor(it.offsets.first().position).toInt()
            if (it.swipe != 0) bonusTicks -= 1
            if (bonusTicks > 0) units += bonusTicks
        }

        return units * 50
    }
    //Convert mp3 to Wav
    fun convertMp3ToWav(fileInput: File, fileOutput: File): Boolean {
        FFmpeg.execute("-i ${fileInput.absolutePath} -acodec pcm_s16le -ar 44100 ${fileOutput.absolutePath}").let {
            when (it) {
                RETURN_CODE_SUCCESS -> {
                    Log.i(Config.TAG, "Command execution completed successfully.")
                    return true
                }
                RETURN_CODE_CANCEL -> {
                    Log.i(Config.TAG, "Command execution cancelled by user.");
                    return false
                }
                else -> {
                    Log.i(
                        Config.TAG,
                        String.format(
                            "Command execution failed with rc=%d and the output below.",
                            it
                        )
                    );
                    Config.printLastCommandOutput(Log.INFO);
                    return false
                }
            }
        }
    }

    //ARTWORK FILE (Texture Decoder) ===========================================================================================================================================
    fun getArtworkBitmap(bundleBytes: ByteArray): Bitmap {
        //Read width
        val widthBytes = ByteBuffer.allocate(2)
        widthBytes.order(ByteOrder.LITTLE_ENDIAN)
        widthBytes.put(bundleBytes[10865]); widthBytes.put(bundleBytes[10866])
        val w = widthBytes.getShort(0)

        //Read height
        val heightBytes = ByteBuffer.allocate(2)
        heightBytes.order(ByteOrder.LITTLE_ENDIAN)
        heightBytes.put(bundleBytes[10869]); heightBytes.put(bundleBytes[10870])
        val h = heightBytes.getShort(0)

        val tBytes = bundleBytes.copyOfRange(10937, bundleBytes.size-131084/*142008*/)
        System.loadLibrary("decoder_texture")

        val rawImage = decodeBytesC(tBytes, tBytes.size, w.toInt(), h.toInt())

        val originalBitmap = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        originalBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(rawImage))

        val matrix = Matrix()
        matrix.postScale((1).toFloat(), (-1).toFloat(), originalBitmap.width / 2f, originalBitmap.height / 2f)

        return Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
    }

    fun getArtworkEncodedBytes(image: Bitmap): MutableList<Byte> {
        //Отражаем изображение а затем просто конвертируем его в нужный нам формат
        val matrix = Matrix().apply {
            postScale(1f, -1f, image.width / 2f, image.height / 2f)
        }
        Bitmap.createBitmap(image, 0, 0, image.width, image.height, matrix, true).let {
            val pixels = IntArray(it.width * it.height)
            it.getPixels(pixels, 0, it.width, 0, 0, it.width, it.height)
            val buffer = mutableListOf<Byte>()
            for (pixel in pixels) {
                buffer.add(Color.red(pixel).toByte()) // R
                buffer.add(Color.green(pixel).toByte()) // G
                buffer.add(Color.blue(pixel).toByte()) // B
                buffer.add(255.toByte()) // A
            }
            val bytes = buffer.toByteArray()

            System.loadLibrary("decoder_texture")
            return packArtworkToBundle(encodeArtworkBytes(bytes, bytes.size).toMutableList())
        }
    }
    private fun packArtworkToBundle(bytes: MutableList<Byte>): MutableList<Byte> {
        //Header and other bundle things
        val fileData = BeatChartsUtils.FilesData.ArtworkData().artworkFile.asHex().toMutableList()

        for ((j, i) in (10937..142008).withIndex()){
            fileData[i] = bytes[j]
        }

        //Generate randomized id
        val newId = getRandomString(32)
        for (i in 122..153) {
            fileData[i] = newId[153-i].toByte()
        }

        val newAb = getRandomString(32)
        for (i in 179..210) {
            fileData[i] = newAb[210-i].toByte()
        }

        return fileData
    }

    private external fun decodeBytesC(data: ByteArray, dataSize: Int, w: Int, h: Int): ByteArray
    private external fun encodeArtworkBytes(dataInput: ByteArray, dataInputSize: Int): ByteArray

    //Extends
    fun getRandomString(length: Int) : String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }
    private fun MutableList<Byte>.write4BytesInBigEndian(data: ByteArray, offset: Int) {
        for ((j, i) in (offset .. offset+3).withIndex()) {
            this[i] = data[j]
        }
    }
    private fun MutableList<Byte>.write4BytesInLittleEndian(data: ByteArray, offset: Int) {
        for (i in offset+3 downTo offset) {
            this[i] = data[(4-(i-offset))-1]
        }
    }
    private fun Long.toUInt32(): ByteArray {
        val bytes = ByteArray(4)
        bytes[3] = (this and 0xFFFF).toByte()
        bytes[2] = ((this ushr 8) and 0xFFFF).toByte()
        bytes[1] = ((this ushr 16) and 0xFFFF).toByte()
        bytes[0] = ((this ushr 24) and 0xFFFF).toByte()
        return bytes
    }
    private fun String.asHex(): ByteArray {
        return chunked(2).map {
            it.toInt(16).toByte()
        }.toByteArray()
    }
    private fun Float.toBytes(): ByteArray {
        return ByteBuffer.allocate(4).putFloat(this).array().reversedArray()
    }
    private fun Int.toVarint(): ByteArray {
        var intBytes = ""
        val doneVarint = mutableListOf<Byte>()

        //Make string, that contains all bits
        for (i in 0..3) intBytes = String.format("%8s", Integer.toBinaryString(java.lang.Byte.toUnsignedInt((this shr (i*8)).toByte()))).replace(" ".toRegex(), "0") + intBytes

        //Check if the number is zero
        if (Integer.parseInt(intBytes, 2) == 0){
            doneVarint.add((0).toByte())
            return doneVarint.toByteArray()
        }
        //Remove all first zeros
        while (intBytes[0] == '0') intBytes = intBytes.drop(1)

        //Reverse and parse this string to actual bytes
        intBytes = intBytes.reversed()

        val intBytesList = intBytes.chunked(7)
        intBytesList.forEachIndexed { index, it ->
            var thisPart = it.reversed()
            var currentByte: Byte = 0

            //Add 1 to the end, if this byte is not last
            if ((intBytesList.size-1)-index > 0) thisPart = "1$thisPart"

            //Actually parse the bytes
            for ((i, j) in (thisPart.length-1 downTo 0).withIndex()) {
                if (thisPart[j] == '1') currentByte = currentByte or ((1 shl i).toByte())
            }
            doneVarint.add(currentByte)
        }

        return doneVarint.toByteArray()
    }
}
