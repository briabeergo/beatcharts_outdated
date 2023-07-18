package ru.acted.beatcharts.pages

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import com.google.firebase.analytics.FirebaseAnalytics
import eightbitlab.com.blurview.RenderScriptBlur
import jp.wasabeef.blurry.Blurry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import ru.acted.beatcharts.DeEncodingManager
import ru.acted.beatcharts.utils.FileZipUtils
import ru.acted.beatcharts.R
import ru.acted.beatcharts.adapters.SimpleCardRecyclerViewAdapter
import ru.acted.beatcharts.databinding.FragmentUploadPageBinding
import ru.acted.beatcharts.types.*
import ru.acted.beatcharts.utils.URIHelper
import ru.acted.beatcharts.utils.BeatChartsUtils
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.animateDisable
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.animateEnable
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.closeToLeft
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.scaleAppear
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.scaleDisappear
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.downToTopDisappear
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.highlightAnimate
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.openFromBottom
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.openFromRight
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.openFromTop
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.smoothFarAppearFromLeft
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.upToDownAppear
import ru.acted.beatcharts.utils.BeatChartsUtils.Conversions.Companion.px
import ru.acted.beatcharts.utils.BeatChartsUtils.Data.Companion.colorToHex
import ru.acted.beatcharts.utils.BeatChartsUtils.Data.Companion.formatString
import ru.acted.beatcharts.utils.BeatChartsUtils.Data.Companion.generateString
import ru.acted.beatcharts.utils.BeatChartsUtils.Sys.Companion.hideKeyboard
import ru.acted.beatcharts.viewModels.MainViewModel
import java.io.File

class ChartBaseColors() {
    var baseColor: Int = 0
    var lightColor: Int = 0
    var darkColor: Int = 0
    var superDarkColor: Int = 0
    fun setColors(color: Int) {
        baseColor = color
        lightColor = color.manipulateColor(1.3f)
        darkColor = color.manipulateColor(0.7f)
        superDarkColor = color.manipulateColor(0.3f)
    }
    private fun Int.manipulateColor(factor: Float): Int {
        val a = Color.alpha(this); val r = Math.round(Color.red(this) * factor); val g = Math.round(Color.green(this) * factor); val b = Math.round(Color.blue(this) * factor)
        return Color.argb(a, Math.min(r, 255), Math.min(g, 255), Math.min(b, 255))
    }
}

class UploadPage : Fragment() {

    private var _binding: FragmentUploadPageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentUploadPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private lateinit var viewModel: MainViewModel

    private val colors = ChartBaseColors()
    private var chart = Chart()
    private val chartFiles = ChartFiles()
    private var neededActions = 3
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)

        //Set dynamic margin of diffs
        binding.diffsLayout.post {
            (binding.normalButton.layoutParams as ViewGroup.MarginLayoutParams).setMargins((binding.songArtworkIcon.measuredWidth/2)+25, 0, 0, 0)
            binding.normalButton.requestLayout()
        }

        //Set dynamic titile width
        binding.apply {
            chartArtist.post {
                chartTitle.layoutParams = (chartTitle.layoutParams as ConstraintLayout.LayoutParams).apply {
                    matchConstraintMaxWidth = chartArtist.width
                }
            }
        }

        //Inits
        binding.chartFilesPage.visibility = View.VISIBLE; binding.chartSettingsPage.visibility = View.GONE; binding.uploadPage.visibility = View.GONE
        changeDiff(4)
        binding.apply {
            proceedButtonHint.text = String.format(resources.getString(R.string.add_all_files_hint), neededActions)
            dialogButton.setOnClickListener {
                closeDialogs()
            }
            dialogCropButton.setOnClickListener {
                applyImageCrop()
            }
            imageRotateButton.setOnClickListener {
                imageCropView.rotateImage(90)
            }
            dialogCropCancelButton.setOnClickListener {
                closeDialogs()
            }
            dialogValueButton.setOnClickListener {
                val timeEditables = listOf<EditText>(timeMillisecondsEdit, timeSecondsEdit, timeMinutesEdit); timeEditables.forEach { if (it.text.toString().length == 0) it.setText("0") }
                val milliseconds = timeMillisecondsEdit.text.toString().toFloat(); val seconds = timeSecondsEdit.text.toString().toFloat(); val minutes = timeMinutesEdit.text.toString().toFloat()
                val newOffset = (((minutes*60+seconds+milliseconds/10)/10)*32*chart.bpm).toString()
                when (valueDialogId) {
                    1 -> {
                        //Changed a bpm
                        valueDialogEdit.text.toString().toInt().let {
                            if (it > 0){
                                chart.bpm = it
                                initBpmBased()
                                Handler().postDelayed({closeDialogs()}, 100)
                            }
                            else {
                                valueDialogEdit.error = "0?"
                            }
                        }
                    }
                    2 -> {
                        //Changed an offset
                        if (newOffset != "" && newOffset.toFloat() >= 0) {
                            newOffset.toFloat().let {
                                if (it.toFloat()/192 == chart.sections[valueDialogContentId] || chart.sections.find { it == newOffset.toFloat()/192 } == null) {
                                    chart.sections[valueDialogContentId] = it/192
                                    refreshGameplaySettings()
                                    initBpmBased()
                                    Handler().postDelayed({closeDialogs()}, 100)
                                } else timeMillisecondsEdit.error = resources.getString(R.string.stage_already_exists)
                            }
                        } else timeMillisecondsEdit.error = resources.getString(R.string.value_out_of_bound)
                    }
                    3 -> {
                        //Added an offset
                        if (newOffset != "" && newOffset.toFloat() >= 0) {
                            newOffset.toFloat().let {
                                if (chart.sections.find { it == newOffset.toFloat()/192 } == null) {
                                    chart.sections.add(it/192)
                                    refreshGameplaySettings()
                                    initBpmBased()
                                    Handler().postDelayed({closeDialogs()}, 100)
                                } else timeMillisecondsEdit.error = resources.getString(R.string.stage_already_exists)
                            }
                        } else timeMillisecondsEdit.error = resources.getString(R.string.value_out_of_bound)
                    }
                    4 -> {
                        //Added a perfect
                        if (newOffset != "" && newOffset.toFloat() >= 0 && valueDialogEdit.text.toString() != "") {
                            newOffset.toFloat().let {
                                if (chart.perfects.find { it.offset == newOffset.toFloat()/192 } == null) {
                                    chart.perfects.add(Perfect().apply { this.offset = it/192; this.multiplier = valueDialogEdit.text.toString().toFloat() } )
                                    initBpmBased()
                                    Handler().postDelayed({closeDialogs()}, 100)
                                    applyGameplay(true)
                                } else valueDialogEdit.error = resources.getString(R.string.this_element_exists_already)
                            }
                        } else valueDialogEdit.error = resources.getString(R.string.value_out_of_bound)
                    }
                    5 -> {
                        //Changed a perfect
                        if (newOffset != "" && newOffset.toFloat() >= 0) {
                            newOffset.toFloat().let {
                                if (it.toFloat()/192 == chart.perfects[valueDialogContentId].offset || chart.perfects.find { it.offset == newOffset.toFloat()/192 } == null) {
                                    chart.perfects[valueDialogContentId].offset = it/192
                                    chart.perfects[valueDialogContentId].multiplier = valueDialogEdit.text.toString().toFloat()
                                    initBpmBased()
                                    Handler().postDelayed({closeDialogs()}, 100)
                                    applyGameplay(true)
                                } else valueDialogEdit.error = resources.getString(R.string.stage_already_exists)
                            }
                        } else valueDialogEdit.error = resources.getString(R.string.value_out_of_bound)
                    }
                    6 -> {
                        //Added a speed
                        if (newOffset != "" && newOffset.toFloat() >= 0 && valueDialogEdit.text.toString() != "") {
                            newOffset.toFloat().let {
                                if (chart.speeds.find { it.offset == newOffset.toFloat()/192 } == null) {
                                    chart.speeds.add(Speed().apply { this.offset = it/192; this.multiplier = valueDialogEdit.text.toString().toFloat() } )
                                    initBpmBased()
                                    Handler().postDelayed({closeDialogs()}, 100)
                                    applyGameplay(true)
                                } else valueDialogEdit.error = resources.getString(R.string.this_element_exists_already)
                            }
                        } else valueDialogEdit.error = resources.getString(R.string.value_out_of_bound)
                    }
                    7 -> {
                        //Changed a speed
                        if (newOffset != "" && newOffset.toFloat() >= 0) {
                            newOffset.toFloat().let {
                                if (it.toFloat()/192 == chart.speeds[valueDialogContentId].offset || chart.speeds.find { it.offset == newOffset.toFloat()/192 } == null) {
                                    chart.speeds[valueDialogContentId].offset = it/192
                                    chart.speeds[valueDialogContentId].multiplier = valueDialogEdit.text.toString().toFloat()
                                    initBpmBased()
                                    Handler().postDelayed({closeDialogs()}, 100)
                                    applyGameplay(true)
                                } else valueDialogEdit.error = resources.getString(R.string.stage_already_exists)
                            }
                        } else valueDialogEdit.error = resources.getString(R.string.value_out_of_bound)
                    }
                }
            }
            dialogValueCancelButton.setOnClickListener { closeDialogs() }
            stagesAddButton.setOnClickListener {
                valueDialogId = 3
                showValueDialog(resources.getString(R.string.enter_stage_offset), listOf("0"), 1, true)
            }
            sizeAddButton.setOnClickListener {
                valueDialogId = 4
                showValueDialog(resources.getString(R.string.enter_perfect), listOf("0", "0"), 2, true)
            }
            speedAddButton.setOnClickListener {
                valueDialogId = 6
                showValueDialog(resources.getString(R.string.enter_speed), listOf("0", "0"), 2, true)
            }
            changeBpmButton.setOnClickListener {
                valueDialogId = 1
                showValueDialog(resources.getString(R.string.enter_bpm), listOf(chart.bpm.toString()), 0, true)
            }
            closeColorPickerButton.setOnClickListener {

            }
            stagesListView.adapter = SimpleCardRecyclerViewAdapter(stagesList, viewModel, 1)
            sizesListView.adapter = SimpleCardRecyclerViewAdapter(perfectsList, viewModel, 2)
            speedsListView.adapter = SimpleCardRecyclerViewAdapter(speedsList, viewModel, 3)

            //Prevent unwanted clicks
            uploadPagesBlur.setOnClickListener{}
            proceedButtonRoot.setOnClickListener{}
            colorPickerDialog.setOnClickListener{}
            fileColorsOverrideNotify.setOnClickListener{}

            //Difficulties buttons
            normalButton.setOnClickListener {
                changeDiff(4)
            }
            hardButton.setOnClickListener {
                changeDiff(3)
            }
            extremeButton.setOnClickListener {
                changeDiff(1)
            }

            //Files buttons
            archiveWithAllFilesBttn.setOnClickListener {
                filePick("Select zip file", 101)
            }
            infoFileBttn.setOnClickListener {
                filePick("Select info file", 102)
            }
            chartFileBttn.setOnClickListener {
                filePick("Select chart file", 103)
            }
            artworkFileBttn.setOnClickListener {
                filePick("Select artwork file", 104)
            }
            audioFileBttn.setOnClickListener {
                filePick("Select audio file", 105)
            }
            configFileBttn.setOnClickListener {
                filePick("Select config file", 106)
            }

            chartTitle.addTextChangedListener(object: TextWatcher {
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                    chartFiles.info.title = chartTitle.text.toString().trim()
                    prepareSave()
                }
                override fun afterTextChanged(p0: Editable?) {}
            })
            chartArtist.addTextChangedListener(object: TextWatcher {
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                    chartFiles.info.artist = chartArtist.text.toString().trim()
                }
                override fun afterTextChanged(p0: Editable?) {}
            })
        }

        //Setup blurs
        val windowBackground = requireActivity().window.decorView.background
        val rootView2 = requireActivity().window.decorView.findViewById<View>(android.R.id.content) as ViewGroup
        binding.uploadPagesBlur.setupWith(rootView2)
            .setFrameClearDrawable(windowBackground)
            .setBlurRadius(20f)
        binding.uploadPagesBlur.setBlurEnabled(false)

        //Navigate buttons
        binding.proceedButton.setOnClickListener {
            proceedNext()
        }
        binding.exitPublicationButton.setOnClickListener {
            viewModel.changeDialogTo(0)
        }

        //Observe interactions
        viewModel.pubContentInteraction.observe(viewLifecycleOwner) {
            when (it) {
                1 -> {
                    //Change stage offset
                    viewModel.pubContentInteraction.value = 0
                    valueDialogContentId = viewModel.itemInteractionId.value!!
                    valueDialogId = 2
                    showValueDialog(resources.getString(R.string.enter_stage_offset), listOf(((chart.sections[valueDialogContentId]*192).toFloat()/chart.bpm/32*10).toString()), 1, true)
                }
                2 -> {
                    //Remove offset
                    viewModel.pubContentInteraction.value = 0
                    chart.sections.removeAt(viewModel.itemInteractionId.value!!)
                    refreshGameplaySettings()
                    initBpmBased()
                }
                3 -> {
                    //Change perfect
                    viewModel.pubContentInteraction.value = 0
                    valueDialogContentId = viewModel.itemInteractionId.value!!
                    valueDialogId = 5
                    showValueDialog(resources.getString(R.string.enter_perfect), listOf(((chart.perfects[valueDialogContentId].offset*192).toFloat()/chart.bpm/32*10).toString(), chart.perfects[valueDialogContentId].multiplier.toString()), 2, true)
                }
                4 -> {
                    //Remove perfect
                    viewModel.pubContentInteraction.value = 0
                    chart.perfects.removeAt(viewModel.itemInteractionId.value!!)
                    applyGameplay(true)
                    initBpmBased()
                }
                5 -> {
                    //Change speed
                    viewModel.pubContentInteraction.value = 0
                    valueDialogContentId = viewModel.itemInteractionId.value!!
                    valueDialogId = 7
                    showValueDialog(resources.getString(R.string.enter_speed), listOf(((chart.speeds[valueDialogContentId].offset*192).toFloat()/chart.bpm/32*10).toString(), chart.speeds[valueDialogContentId].multiplier.toString()), 2, true)
                }
                6 -> {
                    //Remove speed
                    viewModel.pubContentInteraction.value = 0
                    chart.speeds.removeAt(viewModel.itemInteractionId.value!!)
                    applyGameplay(true)
                    initBpmBased()
                }
            }
        }
        viewModel.pubProgressInteraction.observe(viewLifecycleOwner) {
            binding.pubLoading.setProgress(it, true)
        }

        //Make animations
        binding.apply {
            header.openFromTop()
            allPages.openFromRight()
            proceedButtonRoot.openFromBottom()
        }
    }

    private fun filePick(title: String, code: Int){
        val intent = Intent()
            .setType("*/*")
            .setAction(Intent.ACTION_GET_CONTENT)
        startActivityForResult(Intent.createChooser(intent, title), code)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == AppCompatActivity.RESULT_OK) {
            val uri = data?.data!!
            val selectedFile = File(URIHelper().getPath(requireActivity(), uri)!!)

            if (selectedFile.exists()) {
                when (requestCode) {
                    101 -> if (selectedFile.extension == "zip") addZipFile(selectedFile) else showIncorrectFile(requestCode, selectedFile.name)
                    102 -> if (selectedFile.extension == "json") addInfoFile(selectedFile) else showIncorrectFile(requestCode, selectedFile.name)
                    103 -> if (selectedFile.extension == "bundle" || selectedFile.extension == "chart") addChartFile(selectedFile) else showIncorrectFile(requestCode, selectedFile.name)
                    104 -> if (selectedFile.extension == "bundle" || selectedFile.extension == "png" || selectedFile.extension == "jpg") addArtworkFile(selectedFile) else showIncorrectFile(requestCode, selectedFile.name)
                    105 -> if (selectedFile.extension == "bundle" || selectedFile.extension == "mp3" || selectedFile.extension == "wem") addAudioFile(selectedFile) else showIncorrectFile(requestCode, selectedFile.name)
                    106 -> if (selectedFile.extension == "json") addConfigFile(selectedFile) else showIncorrectFile(requestCode, selectedFile.name)
                }
            } else Toast.makeText(requireContext(), "File doesn't exist", Toast.LENGTH_LONG).show()
        }
    }

    //Wrong file in custom dialog
    private fun showIncorrectFile(requestCode: Int, filename: String){
        showCustomDialog(resources.getString(R.string.wrong_file_title), String.format(resources.getString(R.string.wrong_file_text, filename, when (requestCode) {
            101 -> resources.getString(R.string.archive_with_all_files).lowercase()
            102 -> resources.getString(R.string.info_file).lowercase()
            103 -> resources.getString(R.string.chart_file).lowercase()
            104 -> resources.getString(R.string.artwork_file).lowercase()
            105 -> resources.getString(R.string.audio_file).lowercase()
            106 -> resources.getString(R.string.config_file).lowercase()
            else -> "unknown"
        })), R.drawable.file, resources.getString(R.string.ill_choose_another))
    }

    private fun cardsFeedback(filename: String, id: Int){
        when (id) {
            102 -> {
                //Info file -----------------
                binding.infoFileBttn.setCardBackgroundColor(resources.getColor(R.color.background_level_b))
                binding.infoFileState.text = filename
                binding.infoFileIcon.setImageResource(R.drawable.check)
            }
            103 -> {
                //Chart file -----------------
                if (binding.chartFileState.text == resources.getString(R.string.click_to_select_chart_file)) {
                    neededActions--
                    updateNeededStatus()
                }

                binding.chartFileBttn.setCardBackgroundColor(resources.getColor(R.color.background_level_b))
                binding.chartFileState.text = filename
                binding.chartFileIcon.setImageResource(R.drawable.check)
                binding.chartFileRedDot.visibility = View.GONE
            }
            104 -> {
                //Artwork file -----------------
                if (binding.artworkFileState.text == resources.getString(R.string.click_to_select_artwork)) {
                    neededActions--
                    updateNeededStatus()
                }

                binding.artworkFileBttn.setCardBackgroundColor(resources.getColor(R.color.background_level_b))
                binding.artworkFileState.text = filename
                binding.artworkFileIcon.setImageResource(R.drawable.check)
                binding.artworkFileRedDot.visibility = View.GONE
            }
            105 -> {
                //Audio file -----------------
                if (binding.audioFileState.text == resources.getString(R.string.click_to_select_audio_file)) {
                    neededActions--
                    updateNeededStatus()
                }

                binding.audioFileBttn.setCardBackgroundColor(resources.getColor(R.color.background_level_b))
                binding.audioFileState.text = filename
                binding.audioFileIcon.setImageResource(R.drawable.check)
                binding.audioFileRedDot.visibility = View.GONE
            }
            106 -> {
                //Config file -----------------
                binding.configFileBttn.setCardBackgroundColor(resources.getColor(R.color.background_level_b))
                binding.configFileState.text = filename
                binding.configFileIcon.setImageResource(R.drawable.check)
            }
        }
    }

    //Adding a zip file ------------------------------------------------------
    private val filesOrder = mutableListOf<File>()
    private val badFiles = mutableListOf<String>()
    private var currentFileCheckOrder = 0
    private var filesCount = 0
    private fun addZipFile(file: File) {
        showLoadingDialog(true, null, resources.getString(R.string.unzipping), "")
        val tmpFolder = File("/storage/emulated/0/beatstar/tempBC/")
        //Clear the temp folder
        if (tmpFolder.exists()) tmpFolder.deleteRecursively()
        //Unzip this archive to the temp folder
        lifecycleScope.launch(Dispatchers.IO) {
            FileZipUtils.unzip(file.absolutePath, "/storage/emulated/0/beatstar/tempBC/")

            requireActivity().runOnUiThread{
                //Add all files
                filesOrder.clear()
                tmpFolder.walkTopDown().forEach {
                    if (it.isFile){
                        filesOrder.add(it)
                    }
                }
                filesCount = filesOrder.size
                currentFileCheckOrder == 0
                processNextFile()
            }
        }
    }
    private fun processNextFile() {
        if (filesOrder.size == 0) {
            //Delete folder
            File("/storage/emulated/0/beatstar/tempBC/").deleteRecursively()

            binding.loadingState.text = resources.getString(R.string.conversions)
            currentFileCheckOrder == 0
            if (badFiles.size > 0) {
                //Some files had problems
                Handler().postDelayed({
                    var names = ""
                    badFiles.forEach { names += "$it, " }
                    closeDialogs()
                    showCustomDialog(resources.getString(R.string.files_had_problems), String.format(resources.getString(R.string.files_were_not_imported), names.dropLast(2)), R.drawable.file, "OK")
                }, 500)} else closeDialogs()
        } else filesOrder[0].let {
            when (it.extension) {
                "json" -> {
                    when (currentFileCheckOrder) {
                        0 -> { //Try as info file
                            showLoadingDialog(true, null, String.format(resources.getString(R.string.working_on_file), it.name), String.format(resources.getString(R.string.file_number), filesCount-filesOrder.size+1, filesCount))
                            addInfoFile(it)
                        }
                        1 -> { //Try as config file TODO config adding
                            badFiles.add(it.name)
                            currentFileCheckOrder == 0
                            filesOrder.removeFirst()
                            processNextFile()
                        }
                        else -> {
                            badFiles.add(it.name)
                            currentFileCheckOrder == 0
                            filesOrder.removeFirst()
                            processNextFile()
                        }
                    }
                }
                "bundle" -> {
                    when (currentFileCheckOrder) {
                        0 -> {
                            showLoadingDialog(true, null, String.format(resources.getString(R.string.working_on_file), it.name), String.format(resources.getString(R.string.file_number), filesCount-filesOrder.size+1, filesCount))
                            addArtworkFile(it)
                        }
                        1 -> {
                            showLoadingDialog(true, null, String.format(resources.getString(R.string.working_on_file), it.name), String.format(resources.getString(R.string.file_number), filesCount-filesOrder.size+1, filesCount))
                            addAudioFile(it)
                        }
                        2 -> {
                            showLoadingDialog(true, null, String.format(resources.getString(R.string.working_on_file), it.name), String.format(resources.getString(R.string.file_number), filesCount-filesOrder.size+1, filesCount))
                            addChartFile(it)
                        }
                        else -> {
                            badFiles.add(it.name)
                            currentFileCheckOrder == 0
                            filesOrder.removeFirst()
                            processNextFile()
                        }
                    }
                }
                else -> badFiles.add(it.name)
            }
        }
    }

    //Adding an info file ------------------------------------------------------
    private fun addInfoFile(file: File): Boolean {
        if (file.extension == "json") {
            //Try to import data
            val infoMap = JSONObject(file.readText()).toMap()
            if (infoMap["title"] != null) binding.chartTitle.setText(infoMap["title"].toString())
            if (infoMap["artist"] != null) binding.chartArtist.setText(infoMap["artist"].toString().split("//")[0].trimEnd())
            if (infoMap["difficulty"] != null) changeDiff(infoMap["difficulty"].toString().toInt())
            if (infoMap["bpm"] != null) chart.bpm = infoMap["bpm"].toString().toInt()
            cardsFeedback(file.name, 102)
            if (filesOrder.size > 0) {
                currentFileCheckOrder == 0
                filesOrder.removeFirst()
                processNextFile()
            }
        } else {
            //File is wrong
            if (filesOrder.size > 0) {
                currentFileCheckOrder++
                processNextFile()
            } else showIncorrectFile(102, file.name)
            return false
        }
        return true
    }

    //Adding a chart file ------------------------------------------------------
    private fun addChartFile(file: File): Boolean {
        var success = true
        if (file.extension == "chart"){
            lifecycleScope.launch(Dispatchers.IO) {
                //Parse file into chart class if this is .chart
                requireActivity().runOnUiThread {
                    showLoadingDialog(true, file.readLines().size, "", "")
                }
                val chartLines = file.readLines()
                val conversionResult: DeEncodingManager.ChartConversionResult = DeEncodingManager().importChartFromFile(chartLines, viewModel)
                if (conversionResult.exceptionList.size == 0) {
                    //Chart was successfully converted and added!
                    val bpm = chart.bpm
                    chart = conversionResult.chart
                    if (chart.bpm == 0) chart.bpm = bpm
                    chartFiles.chartBytes.clear()

                    requireActivity().runOnUiThread {
                        cardsFeedback(file.name, 103)
                        postAddingChart()
                        if (filesOrder.size > 0) {
                            currentFileCheckOrder == 0
                            filesOrder.removeFirst()
                            processNextFile()
                        } else closeDialogs()
                    }
                } else {
                    requireActivity().runOnUiThread {
                        if (filesOrder.size > 0) {
                            currentFileCheckOrder++
                            processNextFile()
                        } else showErrorsWithChart(conversionResult, file.name)
                        success = false
                    }
                }
            }
        } else {
            //Import bundle if it's correct file
            val fileBytes = file.readBytes()
            if (file.readBytes().size > 4431 && fileBytes.copyOfRange(4423, 4426).decodeToString() == "508"){
                lifecycleScope.launch(Dispatchers.IO) {
                    requireActivity().runOnUiThread {
                        showLoadingDialog(true, fileBytes.size-4431, resources.getString(R.string.processing_chart_bundle), resources.getString(R.string.conversions))
                    }
                    val conversionResult = DeEncodingManager().parseChartBytes(fileBytes.copyOfRange(4431, fileBytes.lastIndex), viewModel)
                    chartFiles.chartBytes = conversionResult.resultData
                    val bpm = chart.bpm
                    chart = conversionResult.chart
                    if (chart.bpm == 0) chart.bpm = bpm
                    requireActivity().runOnUiThread {
                        cardsFeedback(file.name, 103)
                        postAddingChart()
                        if (filesOrder.size > 0) {
                            currentFileCheckOrder == 0
                            filesOrder.removeFirst()
                            processNextFile()
                        } else closeDialogs()
                    }
                }
            } else {
                if (filesOrder.size > 0) {
                    currentFileCheckOrder++
                    processNextFile()
                } else showIncorrectFile(103, file.name)
                success = false
            }
        }


        return success
    }
    private fun postAddingChart() {
        //Post adding operations
        if (chart.notes.size > 0) {
            //Set deluxe type if there is any rail hold
            if (chart.notes.filter { it.type == 5 }.size > 0) {
                chart.isDeluxe = true
                binding.deluxeHighlight.visibility = View.VISIBLE
            } else {
                chart.isDeluxe = false
                binding.deluxeHighlight.visibility = View.GONE
            }
        }
    }
    //Show errors with chart file
    private fun showErrorsWithChart(conversionResult: DeEncodingManager.ChartConversionResult, filename: String){
        binding.loadingDialog.visibility = View.GONE
        var errors = ""
        for (i in 0 until conversionResult.exceptionList.size) {
            errors += "\n-\n"
            var currentError = ""
            when (conversionResult.exceptionList[i].id) {
                1 -> currentError = resources.getString(R.string.exception_1_unknown_blue_flag)
                2 -> currentError = resources.getString(R.string.exception_2_unknown_effect)
                3 -> currentError = resources.getString(R.string.exception_3_error_parsing)
                4 -> currentError = resources.getString(R.string.exception_4_event_note_missing)
                5 -> currentError = resources.getString(R.string.exception_5_unknown_event)
                6 -> currentError = resources.getString(R.string.exception_6_swipe_missing_note)
                7 -> currentError = resources.getString(R.string.exception_7_too_much_sections)
                8 -> currentError = resources.getString(R.string.exception_8_no_sections)
            }
            when (conversionResult.exceptionList[i].dataBundle.size) {
                1 -> currentError = String.format(currentError, conversionResult.exceptionList[i].dataBundle[0])
                2 -> currentError = String.format(currentError, conversionResult.exceptionList[i].dataBundle[0], conversionResult.exceptionList[i].dataBundle[1])
            }
            errors += currentError
        }
        showCustomDialog(resources.getString(R.string.chart_file_incorrect), String.format(resources.getString(R.string.chart_file_incorrect_text), filename, errors), R.drawable.file, resources.getString(R.string.ill_fix_it))
    }

    //Add an artwork file ------------------------------------------------------
    private var imageFileName = ""
    private var image: Bitmap? = null
    private fun addArtworkFile(file: File): Boolean {
        if (file.extension == "bundle") {
            //Check this bundle, add to artwork
            if (file.readBytes().size > 10729 && file.readBytes().copyOfRange(10725, 10729).decodeToString() == ".png"){
                image = DeEncodingManager().getArtworkBitmap(file.readBytes())
                chartFiles.artworkBytes = DeEncodingManager().generateRandomId(file.readBytes().toMutableList())

                updateHeader(image!!)
                cardsFeedback(file.name, 104)
                if (filesOrder.size > 0) {
                    currentFileCheckOrder == 0
                    filesOrder.removeFirst()
                    processNextFile()
                }
            } else {
                if (filesOrder.size > 0) {
                    currentFileCheckOrder++
                    processNextFile()
                } else showIncorrectFile(104, file.name)
                return false
            }
        } else if (filesOrder.size == 0) {
            //Crop an image and apply it
            image = BitmapFactory.decodeFile(file.absolutePath)
            imageFileName = file.name
            showImageCropDialog()
        }
        return true
    }
    //Set header feedbacks
    private fun updateHeader(image: Bitmap) {
        val color = Palette.from(image).generate().getDominantColor(resources.getColor(R.color.background_level_a))
        colors.setColors(color)
        binding.apply {
            artworkPreview.setImageBitmap(image)
            songArtworkIcon.setImageBitmap(image)
            bgColor.setBackgroundColor(color.toInt())

            //Make blurred image and apply it to background
            Blurry.with(context).radius(50).from(image).into(bgArt)
        }


        //Set header text colors
        if (isColorDark(color)) {
            //Color is dark
            binding.uploadPageHeadImage.setColorFilter(ContextCompat.getColor(requireContext(), R.color.white), android.graphics.PorterDuff.Mode.SRC_IN)
            binding.uploadPageHeadTitle.setTextColor(resources.getColor(R.color.white))
            binding.chartTitle.setTextColor(resources.getColor(R.color.white))
            binding.chartTitle.setHintTextColor(Color.parseColor("#79FFFFFF"))
            binding.chartArtist.setTextColor(resources.getColor(R.color.white))
            binding.chartArtist.setHintTextColor(Color.parseColor("#79FFFFFF"))
            binding.headerPanelTint.setBackgroundColor(resources.getColor(R.color.black))
        } else {
            //Color is light
            binding.uploadPageHeadImage.setColorFilter(ContextCompat.getColor(requireContext(), R.color.black), android.graphics.PorterDuff.Mode.SRC_IN)
            binding.uploadPageHeadTitle.setTextColor(resources.getColor(R.color.black))
            binding.chartTitle.setTextColor(resources.getColor(R.color.black))
            binding.chartTitle.setHintTextColor(Color.parseColor("#79000000"))
            binding.chartArtist.setTextColor(resources.getColor(R.color.black))
            binding.chartArtist.setHintTextColor(Color.parseColor("#79000000"))
            binding.headerPanelTint.setBackgroundColor(resources.getColor(R.color.white))
        }
    }
    //Show image crop dialog
    private fun showImageCropDialog() {
        binding.imageCropDialog.visibility = View.VISIBLE
        binding.uploadPagesBlur.animateEnable()
        binding.dialogContainer.scaleAppear()

        binding.imageCropView.setAspectRatio(1, 1)

        binding.imageCropView.setImageBitmap(image)
    }
    private fun applyImageCrop() {
        image = Bitmap.createScaledBitmap(binding.imageCropView.croppedImage, 512, 512, false)
        binding.imageCropDialog.visibility = View.GONE
        showLoadingDialog(true, null, resources.getString(R.string.processing_image), resources.getString(R.string.conversions))
        Handler().postDelayed({
            requireActivity().runOnUiThread {
                chartFiles.artworkBytes = DeEncodingManager().getArtworkEncodedBytes(image!!)
                File("/storage/emulated/0/beatstar/BCTests/texture.dat").let {
                    File("/storage/emulated/0/beatstar/BCTests/").mkdirs()
                    it.writeBytes(chartFiles.artworkBytes.toByteArray())
                }
                updateHeader(image!!)
                cardsFeedback(imageFileName, 104)
                closeDialogs()
            }
        },1000)
    }

    //Add an audio file ------------------------------------------------------
    private fun addAudioFile(file: File): Boolean {
        when (file.extension) {
            "bundle" -> {
                chartFiles.isAudioCompressed = true
                if (file.length() > 12582912) {
                    //Size is too big
                    if (filesOrder.size > 0) {
                        currentFileCheckOrder++
                        processNextFile()
                    } else showCustomDialog(resources.getString(R.string.file_too_big), String.format(resources.getString(R.string.any_file_more_than_mb), file.name, 12), R.drawable.file, "okay")
                    return false
                } else if (file.readBytes().size > 4519 && file.readBytes().copyOfRange(4515, 4519).decodeToString() == "RIFF") {
                    //Check bundle for compatibility
                    chartFiles.audioBytes = DeEncodingManager().generateRandomId(file.readBytes().toMutableList())
                    cardsFeedback(file.name, 105)

                    if (filesOrder.size > 0) {
                        currentFileCheckOrder == 0
                        filesOrder.removeFirst()
                        processNextFile()
                    }
                } else {
                    if (filesOrder.size > 0) {
                        currentFileCheckOrder++
                        processNextFile()
                    } else showIncorrectFile(105, file.name)
                    return false
                }
            }
            "wem" -> {
                chartFiles.isAudioCompressed = true
                if (file.length() > 12582912) {
                    //Size is too big
                    if (filesOrder.size > 0) {
                        currentFileCheckOrder++
                        processNextFile()
                    } else showCustomDialog(resources.getString(R.string.file_too_big), String.format(resources.getString(R.string.any_file_more_than_mb), file.name, 12), R.drawable.file, "okay")
                    return false
                } else {
                    //Pack wem to bundle
                    chartFiles.audioBytes = DeEncodingManager().packWemToBundle(file.readBytes().toMutableList())
                    //Make feedbacks
                    cardsFeedback(file.name, 105)

                    if (filesOrder.size > 0) {
                        currentFileCheckOrder == 0
                        filesOrder.removeFirst()
                        processNextFile()
                    }
                }
            }
            "mp3" -> {
                chartFiles.isAudioCompressed = false
                if (file.length() > 12582912) {
                    //Size is too big
                    if (filesOrder.size > 0) {
                        currentFileCheckOrder++
                        processNextFile()
                    } else showCustomDialog(resources.getString(R.string.file_too_big), String.format(resources.getString(R.string.any_file_more_than_mb), file.name, 12), R.drawable.file, "OK")
                    return false
                } else {
                    //Save this for future converters
                    chartFiles.audioBytes = file.readBytes().toMutableList()
                    //Make feedbacks
                    cardsFeedback(file.name, 105)

                    if (filesOrder.size > 0) {
                        currentFileCheckOrder == 0
                        filesOrder.removeFirst()
                        processNextFile()
                    }
                }
            }
        }
        return true
    }

    //Adding a config file ------------------------------------------------------
    private var isUsingFileColors = false
    private fun addConfigFile(file: File): Boolean {
        if (file.extension == "json") {
            //Get colors
            try {
                if (JSONObject(file.readText()).toMap()["SongTemplate"] != null) {
                    val colorsMap = JSONObject(file.readText()).toMap()["SongTemplate"] as Map<*, *>
                    //Reset everything
                    chartFiles.colors.circleGradient[0] = 0; chartFiles.colors.circleGradient[1] = 0
                    chartFiles.colors.menuGradient.clear()
                    chartFiles.colors.sideGradient.clear()
                    chartFiles.colors.vfxColor = 0
                    chartFiles.colors.glowColor = 0
                    chartFiles.colors.invertPerfectBar = false
                    //Assign circle colors
                    if (colorsMap["BaseColor"] != null) chartFiles.colors.circleGradient[0] = Color.parseColor("#" + colorsMap["BaseColor"] as String)
                    if (colorsMap["DarkColor"] != null) chartFiles.colors.circleGradient[1] = Color.parseColor("#" + colorsMap["DarkColor"] as String)
                    //Assign menu gradient color
                    if (colorsMap["ColorGradient"] != null) (colorsMap["ColorGradient"] as java.util.ArrayList<*>).forEach {
                        chartFiles.colors.menuGradient.add(Color.parseColor("#" + (it as Map<*, *>)["color"] as String))
                    }
                    //Assign ingame circle gradient
                    if (colorsMap["ColorGradientInGame"] != null) (colorsMap["ColorGradientInGame"] as java.util.ArrayList<*>).forEach {
                        chartFiles.colors.sideGradient.add(Color.parseColor("#" + (it as Map<*, *>)["color"] as String))
                    }
                    //Other things
                    if (colorsMap["StreakConfig"] != null) ((colorsMap["StreakConfig"] as java.util.ArrayList<*>)[0] as Map<*, *>).let {
                        chartFiles.colors.glowColor = Color.parseColor("#" + it["glowColor"] as String)
                        chartFiles.colors.vfxColor = Color.parseColor("#" + it["VFXColor"] as String)
                    }

                    chartFiles.configBytes = file.readBytes().toMutableList()
                    isUsingFileColors = true
                    cardsFeedback(file.name, 106)
                } else {
                    //File is wrong
                    showIncorrectFile(106, file.name)
                    return false
                }
            } catch (e: java.lang.Exception) {
                //File is wrong
                showIncorrectFile(106, file.name + " [Error: ${e.message}]")
                return false
            }
        } else {
            //File is wrong
            showIncorrectFile(106, file.name)
            return false
        }
        return true
    }

    //INIT SETTINGS PAGE =======================================================================================================================
    private fun initSettings() {
        pageIndex = 1
        if (chart.perfects.size > 0 || chart.speeds.size > 0) applyGameplay(true) else applyGameplay(false)
        initBpmBased()
        if (isUsingFileColors) applyColors(true) else initColors(!(chartFiles.colors.menuGradient.size > 2 && chartFiles.colors.circleGradient.size == 2 && chartFiles.colors.sideGradient.size > 3))

        //Clicks gameplay
        binding.normalButtonGameplay.setOnClickListener { applyGameplay(false) }
        binding.hardButtonGameplay.setOnClickListener { applyGameplay(false) }
        binding.extremeButtonGameplay.setOnClickListener { applyGameplay(false) }
        binding.customGameplayButton.setOnClickListener { applyGameplay(true) }

        //Clicks colors
        binding.autoColorButton.setOnClickListener { isUsingFileColors = false; applyColors(false) }
        binding.customColorButton.setOnClickListener { applyColors(true) }
        binding.mC1.setOnClickListener { modifyColor(1, 0) }
        binding.mC2.setOnClickListener { modifyColor(1, 1) }
        binding.cC1.setOnClickListener { modifyColor(2, 0) }
        binding.cC2.setOnClickListener { modifyColor(2, 1) }
        binding.sC1.setOnClickListener { modifyColor(3, 0) }
        binding.sC2.setOnClickListener { modifyColor(3, 1) }
        binding.sC3.setOnClickListener { modifyColor(3, 2) }
        binding.gC.setOnClickListener { modifyColor(4, 0) }
        binding.closeColorPickerButton.setOnClickListener {
            binding.colorPickerDialog.visibility = View.GONE
            binding.colorPickerDialog.let { it.downToTopDisappear({it.visibility = View.GONE})}
        }
        binding.undoColorPickerButton.setOnClickListener {
            binding.colorPickerDialog.visibility = View.GONE
            modifyColor(colorManipulation.colorType, colorManipulation.colorId, prevColor)
            binding.colorPickerDialog.let { it.downToTopDisappear({it.visibility = View.GONE})}
        }

        //Reading color manipulation
        binding.colorPickerView.subscribe { color, _, _ ->
            if (updateNeeded) updateColor(color, true)
        }
        binding.colorHexEdit.addTextChangedListener {
            try {
                it.toString().let {
                    val colorFormatted = if (it[0] != '#') "#" + it else it
                    updateColor(Color.parseColor(colorFormatted), false)
                    updateNeeded = false
                    binding.colorPickerView.let {
                        it.setInitialColor(Color.parseColor(colorFormatted)); it.reset()
                    }
                    updateNeeded = true
                }
            } catch (e: java.lang.Exception) {
                binding.colorHexEdit.setError(resources.getString(R.string.not_a_color))
            }
        }
    }
    private var updateNeeded = true
    private fun updateColor(color: Int, updateHexEdit: Boolean) {
        when (colorManipulation.colorType) {
            1 -> chartFiles.colors.menuGradient[colorManipulation.colorId] = color
            2 -> chartFiles.colors.circleGradient[colorManipulation.colorId] = color
            3 -> chartFiles.colors.sideGradient[colorManipulation.colorId] = color
            4 -> chartFiles.colors.glowColor = color
        }
        if (updateHexEdit) {
            binding.colorHexEdit.setText("#${color.colorToHex()}")
        }
        applyColors(true)
    }
    private val stagesList = mutableListOf<BeatChartsUtils.SimpleItem>()
    private val perfectsList = mutableListOf<BeatChartsUtils.SimpleItem>()
    private val speedsList = mutableListOf<BeatChartsUtils.SimpleItem>()
    private fun initBpmBased() {
        chart.apply {
            sections.sort()
            perfects.sortBy { it.offset }
            speeds.sortBy { it.offset }
        }
        var isEverythingOk = true

        //Check and set bpm
        if (chart.bpm == 0) {
            showValueDialog(resources.getString(R.string.enter_bpm), listOf(chart.bpm.toString()), 0, false)
            valueDialogId = 1
        } else {
            //Set information
            binding.apply {
                notesCount.text = chart.notes.size.toString()
                val chartSeconds = ((chart.notes[chart.notes.size-1].offsets[0].position*192)/chart.bpm/32*10).toFloat()
                chartDuration.text = "${if (chartSeconds.toInt()/60 < 10) "0${chartSeconds.toInt()/60}" else chartSeconds.toInt()/60}:${if (chartSeconds.toInt()%60 < 10) "0${chartSeconds.toInt()%60}" else chartSeconds.toInt()%60}.${(chartSeconds*10%10).toInt()}"
                chartBpm.text = chart.bpm.toString()
            }

            //Set stages
            stagesList.clear()
            chart.sections.forEachIndexed { index, it ->
                val stage = BeatChartsUtils.SimpleItem()
                if (index == 3) stage.title = String.format(resources.getString(R.string.stage_number), resources.getString(R.string.stage_continues_final))
                else if (index == 4) stage.title = resources.getString(R.string.song_end)
                else if (index > 4) {
                    stage.title = String.format(resources.getString(R.string.stage_number), resources.getString(R.string.stage_continues_unwanted))
                    stage.titleIcon = resources.getDrawable(R.drawable.info_circle)
                }
                else stage.title = String.format(resources.getString(R.string.stage_number), index+2)

                val rawSeconds = it*192/chart.bpm/32*10
                stage.text = "${if (rawSeconds.toInt()/60 < 10) "0${rawSeconds.toInt()/60}" else rawSeconds.toInt()/60}:${if (rawSeconds.toInt()%60 < 10) "0${rawSeconds.toInt()%60}" else rawSeconds.toInt()%60}.${(rawSeconds.toFloat()*10%10).toInt()}"

                stagesList.add(stage)
            }
            (binding.stagesListView.adapter as SimpleCardRecyclerViewAdapter).notifyDataSetChanged()

            //Feedback stages and needed action
            /*if (stagesList.size < 5) { 5 STAGES IS NOT REQUIRED
                binding.stagesRedDot.visibility = View.VISIBLE
                binding.stagesState.text = resources.getString(R.string.not_enough_sections) + " (${stagesList.size}/5)"
                isEverythingOk = false
            } else*/
            binding.apply {
                noSectionsHint.visibility = View.GONE
                sectionDeleteHint.visibility = View.VISIBLE

                if (stagesList.size == 0) {
                    noSectionsHint.visibility = View.VISIBLE
                    sectionDeleteHint.visibility = View.GONE
                } else if (stagesList.size > 5) {
                    stagesRedDot.visibility = View.VISIBLE
                    stagesState.text = resources.getString(R.string.too_much_sections) + " (${stagesList.size}/5)"
                    isEverythingOk = false
                } else {
                    stagesRedDot.visibility = View.GONE
                    stagesState.text = resources.getString(R.string.everything_ok) + " (5/5) :)"
                }
            }


            //Set perfects and feedback error
            perfectsList.clear()
            chart.perfects.forEach {
                val perfect = BeatChartsUtils.SimpleItem()

                val rawSeconds = it.offset*192/chart.bpm/32*10
                perfect.title = "${if (rawSeconds.toInt()/60 < 10) "0${rawSeconds.toInt()/60}" else rawSeconds.toInt()/60}:${if (rawSeconds.toInt()%60 < 10) "0${rawSeconds.toInt()%60}" else rawSeconds.toInt()%60}.${(rawSeconds.toFloat()*10%10).toInt()}"
                perfect.text = it.multiplier.toString()

                perfectsList.add(perfect)
            }
            (binding.sizesListView.adapter as SimpleCardRecyclerViewAdapter).notifyDataSetChanged()
            if (chart.perfects.size > 0 && chart.perfects[0].offset == 0f) binding.sizesError.visibility = View.GONE else {
                binding.sizesError.visibility = View.VISIBLE
                isEverythingOk = false
            }

            //Set speeds and feedback error
            speedsList.clear()
            chart.speeds.forEach {
                val speed = BeatChartsUtils.SimpleItem()

                val rawSeconds = it.offset*192/chart.bpm/32*10
                speed.title = "${if (rawSeconds.toInt()/60 < 10) "0${rawSeconds.toInt()/60}" else rawSeconds.toInt()/60}:${if (rawSeconds.toInt()%60 < 10) "0${rawSeconds.toInt()%60}" else rawSeconds.toInt()%60}.${(rawSeconds.toFloat()*10%10).toInt()}"
                speed.text = it.multiplier.toString()

                speedsList.add(speed)
            }
            (binding.speedsListView.adapter as SimpleCardRecyclerViewAdapter).notifyDataSetChanged()
            if (chart.speeds.size > 0 && chart.speeds[0].offset == 0f) binding.speedsError.visibility = View.GONE else {
                binding.speedsError.visibility = View.VISIBLE
                isEverythingOk = false
            }

            //Feedback needed status
            if (isEverythingOk) neededActions = 0 else neededActions = 1
            updateNeededStatus()
        }
    }
    private var isGameplayCustom = false
    private fun applyGameplay(isCustom: Boolean) {
        isGameplayCustom = isCustom
        if (isCustom) {
            binding.apply {
                normalButtonGameplay.alpha = 0.5f
                hardButtonGameplay.alpha = 0.5f
                extremeButtonGameplay.alpha = 0.5f
                customGameplayButton.setCardBackgroundColor(resources.getColor(R.color.foreground_active))
                custonGameplayText.setTextColor(resources.getColor(R.color.background_level_a))
                presetArrow.visibility = View.GONE
                customArrow.visibility = View.VISIBLE
            }
        } else {
            binding.apply {
                normalButtonGameplay.alpha = 1f
                hardButtonGameplay.alpha = 1f
                extremeButtonGameplay.alpha = 1f
                customGameplayButton.setCardBackgroundColor(resources.getColor(R.color.background_level_a))
                custonGameplayText.setTextColor(resources.getColor(R.color.foreground_active))
                presetArrow.visibility = View.VISIBLE
                customArrow.visibility = View.GONE
            }

            val currentPerfects = when (chartFiles.info.diff) {
                1 -> ChartDefaults().extremePerfects
                3 -> ChartDefaults().hardPerfects
                4 -> ChartDefaults().normalPerfects
                else -> ChartDefaults().normalPerfects
            }
            val currentSizes = when (chartFiles.info.diff) {
                1 -> ChartDefaults().extremeSizes
                3 -> ChartDefaults().hardSizes
                4 -> ChartDefaults().normalSizes
                else -> ChartDefaults().normalSizes
            }
            chart.speeds.clear()
            chart.perfects.clear()
            chart.perfects.add(Perfect().apply { this.offset = 0f; this.multiplier = currentPerfects[0] })
            chart.speeds.add(Speed().apply { this.offset = 0f; this.multiplier = currentSizes[0] })
            for (i in 1 until chart.sections.size) {
                if (i < 5) {
                    chart.perfects.add(Perfect().apply { this.offset = chart.sections[i-1]; this.multiplier = currentPerfects[i] })
                    chart.speeds.add(Speed().apply { this.offset = chart.sections[i-1]; this.multiplier = currentSizes[i] })
                } else break
            }
            initBpmBased()
        }
    }
    private fun applyColors(isCustom: Boolean) {
        if (isUsingFileColors) {
            binding.autoColorText.text = resources.getString(R.string.setup_in_beatcharts)
            binding.customColorButton.visibility = View.GONE
            binding.autoColorButton.setCardBackgroundColor(resources.getColor(R.color.background_level_a))
            binding.autoColorText.setTextColor(resources.getColor(R.color.foreground_active))
            binding.customColorArrow.visibility = View.GONE
            binding.autoColorArrow.visibility = View.GONE
            binding.colorsBgFade.visibility = View.INVISIBLE
            initColors(false)

            //Supershit hiding
            binding.cCT.visibility = View.INVISIBLE
            binding.sCT.visibility = View.INVISIBLE
            binding.gCT.visibility = View.INVISIBLE
            binding.mC1.visibility = View.GONE
            binding.mC2.visibility = View.GONE
            binding.mCV.visibility = View.GONE
            binding.cC1.visibility = View.INVISIBLE
            binding.cC2.visibility = View.INVISIBLE
            binding.sC1.visibility = View.INVISIBLE
            binding.sC2.visibility = View.INVISIBLE
            binding.sC3.visibility = View.INVISIBLE
            binding.gC.visibility = View.INVISIBLE
            binding.fileColorsOverrideNotify.visibility = View.VISIBLE
        } else {
            //Supershit hiding
            binding.colorsBgFade.visibility = View.VISIBLE
            binding.cCT.visibility = View.VISIBLE
            binding.sCT.visibility = View.VISIBLE
            binding.gCT.visibility = View.VISIBLE
            binding.mC1.visibility = View.VISIBLE
            binding.mC2.visibility = View.VISIBLE
            binding.mCV.visibility = View.VISIBLE
            binding.cC1.visibility = View.VISIBLE
            binding.cC2.visibility = View.VISIBLE
            binding.sC1.visibility = View.VISIBLE
            binding.sC2.visibility = View.VISIBLE
            binding.sC3.visibility = View.VISIBLE
            binding.gC.visibility = View.VISIBLE
            binding.fileColorsOverrideNotify.visibility = View.GONE

            binding.autoColorText.text = resources.getString(R.string.auto)
            binding.customColorArrow.visibility = if (isCustom) View.VISIBLE else View.GONE
            binding.autoColorArrow.visibility = if (isCustom) View.GONE else View.VISIBLE
            binding.customColorButton.setCardBackgroundColor(if (isCustom) resources.getColor(R.color.foreground_active) else resources.getColor(R.color.background_level_a))
            binding.customColorText.setTextColor(if (isCustom) resources.getColor(R.color.background_level_a) else resources.getColor(R.color.foreground_active))
            binding.autoColorButton.setCardBackgroundColor(if (isCustom) resources.getColor(R.color.background_level_a) else resources.getColor(R.color.foreground_active))
            binding.autoColorText.setTextColor(if (isCustom) resources.getColor(R.color.foreground_active) else resources.getColor(R.color.background_level_a))
            binding.customColorButton.visibility = View.VISIBLE
            initColors(!isCustom)
        }
    }
    private fun initColors(overrideToAuto: Boolean) {
        //Override colors
        if (overrideToAuto) {
            //Override menu
            chartFiles.colors.menuGradient.clear()
            chartFiles.colors.menuGradient.add(colors.baseColor)
            chartFiles.colors.menuGradient.add(colors.superDarkColor)
            //Override circle
            chartFiles.colors.circleGradient[0] = colors.baseColor
            chartFiles.colors.circleGradient[1] = colors.darkColor
            //Override side
            chartFiles.colors.sideGradient.clear()
            chartFiles.colors.sideGradient.add(colors.lightColor)
            chartFiles.colors.sideGradient.add(colors.baseColor)
            chartFiles.colors.sideGradient.add(colors.darkColor)
            chartFiles.colors.invertPerfectBar = isColorDark(colors.baseColor)
            //Glow color
            chartFiles.colors.glowColor = colors.baseColor
        }

        //Menu preview
        val menuGradientDrawable = GradientDrawable().apply {
            colors = intArrayOf(
                chartFiles.colors.menuGradient[0],
                chartFiles.colors.menuGradient[1]
            )
            gradientRadius = 800f
            gradientType = GradientDrawable.RADIAL_GRADIENT
            shape = GradientDrawable.RECTANGLE
        }
        binding.outgameGradient.background = menuGradientDrawable

        //Upper gradient preview
        val circleGradient = GradientDrawable().apply {
            colors = chartFiles.colors.circleGradient.toIntArray()
            orientation = GradientDrawable.Orientation.TOP_BOTTOM //BR_TL
            gradientType = GradientDrawable.LINEAR_GRADIENT
            shape = GradientDrawable.RECTANGLE
        }
        binding.upperGradient.background = circleGradient

        //Side gradient preview
        val sideGradientDrawable = GradientDrawable().apply {
            colors = chartFiles.colors.sideGradient.toIntArray()
            orientation = GradientDrawable.Orientation.TOP_BOTTOM //BR_TL
            gradientType = GradientDrawable.LINEAR_GRADIENT
            shape = GradientDrawable.RECTANGLE
        }
        binding.sideGradient.background = sideGradientDrawable

        //Glow preview
        binding.glowColor.setBackgroundColor(chartFiles.colors.glowColor)

        //Color buttons
        if (!isUsingFileColors) {
            //Menu buttons
            binding.mC1.setCardBackgroundColor(chartFiles.colors.menuGradient[0])
            binding.mC2.setCardBackgroundColor(chartFiles.colors.menuGradient[1])
            //Upper gradient buttons
            binding.cC1.setCardBackgroundColor(chartFiles.colors.circleGradient[0])
            binding.cC2.setCardBackgroundColor(chartFiles.colors.circleGradient[1])
            //Side colors
            binding.sC1.setCardBackgroundColor(chartFiles.colors.sideGradient[0])
            binding.sC2.setCardBackgroundColor(chartFiles.colors.sideGradient[1])
            binding.sC3.setCardBackgroundColor(chartFiles.colors.sideGradient[2])
            //Glow color
            binding.gC.setCardBackgroundColor(chartFiles.colors.glowColor)
        }
    }

    private fun changeDiff(newDiff: Int) {
        binding.normalButtonGameplay.visibility = View.GONE
        binding.hardButtonGameplay.visibility = View.GONE
        binding.extremeButtonGameplay.visibility = View.GONE
        binding.normalButton.alpha = 0.5f
        binding.hardButton.alpha = 0.5f
        binding.extremeButton.alpha = 0.5f
        when (newDiff) {
            1 -> {
                binding.extremeButton.alpha = 1.0f
                chartFiles.info.diff = 1
                binding.extremeButtonGameplay.visibility = View.VISIBLE
            }
            3 -> {
                binding.hardButton.alpha = 1.0f
                chartFiles.info.diff = 3
                binding.hardButtonGameplay.visibility = View.VISIBLE
            }
            4 -> {
                binding.normalButton.alpha = 1.0f
                chartFiles.info.diff = 4
                binding.normalButtonGameplay.visibility = View.VISIBLE
            }
            else -> {
                //Set normal by default
                binding.normalButton.alpha = 1.0f
                chartFiles.info.diff = 4
                binding.normalButtonGameplay.visibility = View.VISIBLE
            }
        }
        refreshGameplaySettings()
    }
    private fun refreshGameplaySettings() {
        if (!isGameplayCustom && pageIndex == 1) applyGameplay(false)
    }

    //INIT UPLOAD PAGE =======================================================================================================================
    private var allowUpload = false
    private fun initUpload() {
        pageIndex = 2

        if (viewModel.offlineMode.value == true) {
            //Application is offline TODO: Check if there is just no account
            //Make buttons faded and disable uploads
            allowUpload = false
            binding.proceedButtonText.alpha = 0.5f
            binding.proceedButtonIcon.alpha = 0.5f
            binding.apply {
                retryConnectButton.setOnClickListener {
                    //TODO сделать реконнект
                }

            }
        } else {
            //TODO сделать получение данных о доступных тегах и вообще их применять и т.д.
        }
        binding.saveLocalButton.setOnClickListener {
            var problem = false
            binding.apply {
                if (chartTitle.text.toString() == "") {
                    problem = true
                    chartTitle.setError(resources.getString(R.string.cant_be_empty))
                }

                if (chartArtist.text.toString() == "") {
                    problem = true
                    chartArtist.setError(resources.getString(R.string.cant_be_empty))
                }
            }

            if (!problem) {
                writeFiles(false)
            }
        }
    }
    private var currentFolder = ""
    private fun prepareSave() {
        currentFolder = chartFiles.info.title.formatString() + generateString(5)
        binding.localSaveHint.text = String.format(resources.getString(R.string.local_save_hint), "beatstar/songs/$currentFolder")
    }
    private fun writeFiles(makeUpload: Boolean) {
        var workStep = 1; var workSteps = 5
        val tempFolder = "/storage/emulated/0/beatstar/BCTemp/"
        File(tempFolder).let {
            if (it.exists()) it.deleteRecursively()
            it.mkdirs()
        }
        showLoadingDialog(true, workSteps, String.format(resources.getString(R.string.working_on_file_num_of), workStep.toString(), workSteps.toString()), resources.getString(R.string.exporting_chart))
        binding.pubLoading.progress = 0
        Handler().postDelayed({
            System.gc()
            var isConverterSuccessfully = true; val badFiles = mutableListOf<String>()
            lifecycleScope.launch(Dispatchers.IO) {
                //Create info
                requireActivity().runOnUiThread { workStep++; binding.pubLoading.progress = workStep; showLoadingDialog(false, workSteps, String.format(resources.getString(R.string.working_on_file_num_of), workStep.toString(), workSteps.toString()), resources.getString(R.string.exporting_chart)) }
                val infoJson = DeEncodingManager().packInfoToJson(chart, chartFiles, viewModel)
                //Create chart bundle
                requireActivity().runOnUiThread { workStep++; binding.pubLoading.progress = workStep; showLoadingDialog(false, workSteps, String.format(resources.getString(R.string.working_on_file_num_of), workStep.toString(), workSteps.toString()), resources.getString(R.string.exporting_chart)) }
                val chartBundle = /*if (chartFiles.chartBytes.size != 0) DeEncodingManager().packChartToBundle(chartFiles.chartBytes) else*/ DeEncodingManager().convertChartToBundle(chart)
                //Config file
                requireActivity().runOnUiThread { workStep++; binding.pubLoading.progress = workStep; showLoadingDialog(false, workSteps, String.format(resources.getString(R.string.working_on_file_num_of), workStep.toString(), workSteps.toString()), resources.getString(R.string.exporting_chart)) }
                val configJson = if (isUsingFileColors) chartFiles.configBytes.toByteArray().decodeToString() else DeEncodingManager().packConfigToJson(chartFiles)
                //Create audio bundle
                requireActivity().runOnUiThread { workStep++; binding.pubLoading.progress = workStep; showLoadingDialog(false, workSteps, String.format(resources.getString(R.string.working_on_file_num_of), workStep.toString(), workSteps.toString()), resources.getString(R.string.exporting_chart)) }
                val originalFile = File("${tempFolder}audio.mp3"); val convertedFile = File("${tempFolder}audio.wav")

                var audioBundle: ByteArray? = null
                if (!chartFiles.isAudioCompressed) {
                    //Write mp3 again (to allow user to delete it after adding and before converting)
                    originalFile.writeBytes(chartFiles.audioBytes.toByteArray()); chartFiles.audioBytes.clear()
                    DeEncodingManager().convertMp3ToWav(originalFile, convertedFile).let { isSuccessfull ->
                        if (isSuccessfull) {
                            return@let DeEncodingManager().exportWavToBundle(convertedFile.readBytes().toMutableList(), File("${tempFolder}audio.bundle"))
                        } else {
                            isConverterSuccessfully = false
                            badFiles.add(originalFile.name)
                            return@let chartFiles.audioBytes
                        }
                    }
                } else audioBundle = chartFiles.audioBytes.toByteArray()

                if (isConverterSuccessfully) {
                    Log.i("TEST", "Making files...")
                    //Write new files
                    //Info
                    File("${tempFolder}info.json").writeText(infoJson)
                    //Chart bundle
                    File("${tempFolder}chart.bundle").writeBytes(chartBundle.toByteArray())
                    //Audio bundle (if compressed)
                    audioBundle?.let { File("${tempFolder}audio.bundle").writeBytes(it) }
                    //Image bundle
                    File("${tempFolder}artwork.bundle").writeBytes(chartFiles.artworkBytes.toByteArray())
                    //Config file
                    File("${tempFolder}config.json").writeText(configJson)
                    //Remove audio temps
                    convertedFile.delete()
                    originalFile.delete()

                    //Copy chart temps and remove it
                    File(tempFolder).let {
                        it.copyRecursively(File("/storage/emulated/0/beatstar/songs/$currentFolder"), true)
                        it.deleteRecursively()
                    }

                    //Log this to firebase
                    val firebaseAnalytics = FirebaseAnalytics.getInstance(requireContext())
                    val bundle = Bundle()
                    bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "chart")
                    bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, chartFiles.info.title)
                    firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle)

                    //Upload chart if needed
                    requireActivity().runOnUiThread { closeDialogs(); viewModel.showNotif(resources.getString(R.string.chart_created_locally)) }
                    if (makeUpload) {
                        //TODO произвести загрузку
                    }
                } else {
                    requireActivity().runOnUiThread {
                        showCustomDialog(resources.getString(R.string.error_converting_title), String.format(resources.getString(R.string.error_converting_text), badFiles.reduce { acc, s -> acc + " $s" }), R.drawable.info_circle, "okay...")
                    }
                }
            }
        }, 1000)
    }

    //Various dialogs here =========================================================================================================================
    private fun showCustomDialog(title: String, text: String, icon: Int, buttonText: String){
        binding.apply {
            dialogTitle.text = title
            dialogText.text = text
            dialogButtonText.text = buttonText
            dialogIcon.setImageResource(icon)
            uploadCustomDialog.visibility = View.VISIBLE

            dialogContainer.scaleAppear()
            uploadPagesBlur.animateEnable()
        }
    }
    private fun showLoadingDialog(animateOpen: Boolean, maxValue: Int?, text: String, state: String) {
        if (state.length != 0) binding.loadingState.text = state else binding.loadingState.text = resources.getString(R.string.loading)
        if (text.length != 0) binding.loadingText.text = text else binding.loadingText.text = resources.getString(R.string.please_wait)
        binding.loadingDialog.visibility = View.VISIBLE
        if (maxValue == null) binding.pubLoading.isIndeterminate = true
        else {
            binding.pubLoading.isIndeterminate = false
            binding.pubLoading.max = maxValue
        }
        if (animateOpen) {
            binding.loadingState.alpha = 0f
            binding.loadingText.alpha = 0f
            binding.dialogContainer.scaleAppear()
            binding.uploadPagesBlur.animateEnable()
            binding.loadingState.smoothFarAppearFromLeft(0.5f)
            Handler().postDelayed({
                binding.loadingText.smoothFarAppearFromLeft(1f)
            }, 200)
        } else binding.loadingText.highlightAnimate()
    }
    private var valueDialogId = 0
    private var valueDialogContentId = 0
    private fun showValueDialog(valueText: String, value: List<String>, type: Int, isCancelAvailable: Boolean) {
        binding.apply {
            valueDialogText.text = valueText
            when (type) {
                0 -> {
                    //Default value change
                    valueDialogEdit.setText(value[0])
                    valueDialogEdit.visibility = View.VISIBLE
                    timeEditContainer.visibility = View.GONE
                }
                1 -> {
                    //Change time
                    timeMillisecondsEdit.setText((value[0].toFloat()*10%10).toInt().toString())
                    timeSecondsEdit.setText((value[0].toFloat().toInt()%60).toString())
                    timeMinutesEdit.setText((value[0].toFloat().toInt()/60).toString())
                    valueDialogEdit.visibility = View.GONE
                    timeEditContainer.visibility = View.VISIBLE
                }
                2 -> {
                    //Change time and default value
                    timeMillisecondsEdit.setText((value[0].toFloat()*10%10).toInt().toString())
                    timeSecondsEdit.setText((value[0].toFloat().toInt()%60).toString())
                    timeMinutesEdit.setText((value[0].toFloat().toInt()/60).toString())
                    valueDialogEdit.setText(value[1])
                    valueDialogEdit.visibility = View.VISIBLE
                    timeEditContainer.visibility = View.VISIBLE
                }
            }

            dialogValueCancelButton.let { if (isCancelAvailable) it.visibility = View.VISIBLE else it.visibility = View.GONE }
            valueDialog.visibility = View.VISIBLE
            dialogContainer.scaleAppear()
            uploadPagesBlur.animateEnable()
        }
    }
    data class ColorManipulation(var colorType: Int = 0, var colorId: Int = 0); val colorManipulation = ColorManipulation()
    var prevColor = 0
    private fun modifyColor(type: Int, id: Int) {
        colorManipulation.colorType = type; colorManipulation.colorId = id
        prevColor = when (type) {
            1 -> chartFiles.colors.menuGradient[colorManipulation.colorId]
            2 -> chartFiles.colors.circleGradient[colorManipulation.colorId]
            3 -> chartFiles.colors.sideGradient[colorManipulation.colorId]
            4 -> chartFiles.colors.glowColor
            else -> 0
        }
        showColorPickDialog()
    }
    private fun modifyColor(type: Int, id: Int, color: Int) {
        colorManipulation.colorType = type; colorManipulation.colorId = id
        when (type) {
            1 -> chartFiles.colors.menuGradient[colorManipulation.colorId] = color
            2 -> chartFiles.colors.circleGradient[colorManipulation.colorId] = color
            3 -> chartFiles.colors.sideGradient[colorManipulation.colorId] = color
            4 -> chartFiles.colors.glowColor = color
        }
        initColors(false)
    }
    private fun showColorPickDialog() {
        binding.colorPickerDialog.visibility = View.INVISIBLE
        val contentOffset = when (colorManipulation.colorType) {
            1 -> binding.mC.getYPosition() + binding.mC.height + 8.px()
            2 -> binding.cC2.getYPosition() + binding.cC2.height + 8.px()
            3 -> binding.sC3.getYPosition() + binding.sC3.height + 8.px()
            4 -> binding.gC.getYPosition() + binding.gC.height + 8.px()
            else -> 0
        } - binding.proceedButtonRoot.getYPosition()
        val labelPos = when (colorManipulation.colorType) {
            1 -> binding.mC.getYPosition() - contentOffset - 12.px()
            2 -> binding.upperCirclePreview.getYPosition() - contentOffset - 12.px()
            3 -> binding.sidePreviewImage.getYPosition() - contentOffset - 12.px()
            4 -> binding.notePreview.getYPosition() - contentOffset
            else -> 0
        }
        binding.allPages.smoothScrollBy(0, contentOffset)
        binding.colorPickerView.setInitialColor(when (colorManipulation.colorType) {
            1 -> chartFiles.colors.menuGradient[colorManipulation.colorId]
            2 -> chartFiles.colors.circleGradient[colorManipulation.colorId]
            3 -> chartFiles.colors.sideGradient[colorManipulation.colorId]
            4 -> chartFiles.colors.glowColor
            else -> 0
        }); binding.colorPickerView.reset()
        Handler().postDelayed({
            binding.colorBottomSpace.setLayoutParams(binding.colorBottomSpace.layoutParams.apply { this.height = binding.screenBottom.getYPosition()-labelPos})
            binding.colorPickerDialog.upToDownAppear()
        }, 50)
    }
    private fun View.getYPosition(): Int = IntArray(2).apply { this@getYPosition.getLocationOnScreen(this) }[1]

    private fun closeDialogs(){
        binding.uploadPagesBlur.animateDisable({
            binding.uploadPagesBlur.visibility = View.GONE
        })
        binding.dialogContainer.scaleDisappear({
            binding.uploadCustomDialog.visibility = View.GONE
            binding.loadingDialog.visibility = View.GONE
            binding.imageCropDialog.visibility = View.GONE
            binding.valueDialog.visibility = View.GONE
        })
        hideKeyboard()
    }

    private var pageIndex = 0
    private fun updateNeededStatus(){
        when (pageIndex) {
            0 -> {
                if (neededActions > 0) binding.proceedButtonHint.text = String.format(resources.getString(R.string.add_all_files_hint), neededActions)
                else {
                    binding.proceedButtonHint.visibility = View.GONE
                    binding.proceedButtonText.alpha = 1.0F
                    binding.proceedButtonIcon.alpha = 1.0F
                }
            }
            1 -> {
                if (neededActions > 0) {
                    binding.proceedButtonHint.text = resources.getString(R.string.fix_all_chart_issues)
                    binding.proceedButtonHint.visibility = View.VISIBLE
                    binding.proceedButtonIcon.alpha = 0.5f
                    binding.proceedButtonText.alpha = 0.5f
                }
                else {
                    binding.proceedButtonHint.visibility = View.GONE
                    binding.proceedButtonText.alpha = 1.0F
                    binding.proceedButtonIcon.alpha = 1.0F
                }
            }
        }
    }

    private fun proceedNext() {
        if (neededActions < 1) {
            when (pageIndex) {
                0 -> {
                    //Change page to settings and make inits
                    binding.chartFilesPage.closeToLeft({binding.chartFilesPage.visibility = View.GONE})
                    binding.chartSettingsPage.visibility = View.VISIBLE
                    binding.chartSettingsPage.openFromRight()
                    binding.allPages.smoothScrollTo(0, 0)

                    //Feedback header and button
                    binding.chartFilesButton.alpha = 0.5f
                    binding.chartFilesArrow.alpha = 0.5f
                    binding.chartSettingsButton.alpha = 1.0f
                    binding.chartSettingsArrow.alpha = 1.0f
                    binding.proceedButtonText.text = resources.getString(R.string.chart_page_settings)

                    initSettings()
                }
                1 -> {
                    //Change page to upload page and make inits
                    binding.chartSettingsPage.closeToLeft({binding.chartSettingsPage.visibility = View.GONE})
                    binding.uploadPage.visibility = View.VISIBLE
                    binding.uploadPage.openFromRight()
                    binding.allPages.smoothScrollTo(0, 0)

                    //Feedback header and button
                    binding.chartSettingsButton.alpha = 0.5f
                    binding.chartSettingsArrow.alpha = 0.5f
                    binding.chartPageButton.alpha = 1.0f
                    binding.proceedButtonText.text = resources.getString(R.string.uploadChart)
                    binding.proceedButtonIcon.setImageDrawable(resources.getDrawable(R.drawable.upload))

                    initUpload()
                }
                2 -> {
                    if (allowUpload) {
                        //TODO произвести сохранение и загрузку чарта...
                    }
                }
            }
        }
    }

    private fun isColorDark(color: Int): Boolean {
        val darkness: Double =
            1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness >= 0.5
    }

    private fun JSONObject.toMap(): Map<String, *> = keys().asSequence().associateWith {
        when (val value = this[it])
        {
            is JSONArray ->
            {
                val map = (0 until value.length()).associate { Pair(it.toString(), value[it]) }
                JSONObject(map).toMap().values.toList()
            }
            is JSONObject -> value.toMap()
            JSONObject.NULL -> null
            else            -> value
        }
    }

    companion object {
        fun newInstance() = UploadPage()
    }
}