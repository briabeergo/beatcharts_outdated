package ru.acted.beatcharts.dataProcessors

import android.util.Log
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.FFmpeg
import java.io.File
import java.io.RandomAccessFile
import ru.acted.beatcharts.utils.BeatChartsUtils.Conversions.Companion.asHex

class AudioManager {

    fun makeMp3FromJunkWem(junkWem: File): File? {
        junkWem.readBytes().toMutableList().let {
            val file = RandomAccessFile(junkWem.absolutePath, "rw")
            val offset = 8
            val length = 48

            val buffer = ByteArray(file.length().toInt() - offset - length)
            file.seek((offset + length).toLong())
            file.read(buffer)

            file.seek(offset.toLong())
            file.setLength(offset.toLong())
            file.write("57415645666D7420100000000100020044AC000010B10200040010004C4953541A000000494E464F495346540D0000004C61766636302E352E3130300000".asHex())

            file.write(buffer)
            file.close()
        }

        junkWem.renameTo(File("/storage/emulated/0/beatstar/BCTemp/preview/audio.wav"))

        FFmpeg.execute("-y -i /storage/emulated/0/beatstar/BCTemp/preview/audio.wav /storage/emulated/0/beatstar/BCTemp/preview/converted.mp3").let {
            when (it) {
                Config.RETURN_CODE_SUCCESS -> {
                    return File("${junkWem.parent}/converted.mp3")
                }
                else -> {
                    return null
                }
            }
        }
    }

    fun takeWemFromBundle(bundle: File): File {
        File("/storage/emulated/0/beatstar/BCTemp/preview/audio.wem").let {
            File(it.parent!!).mkdirs()
            it.createNewFile()
            it.writeBytes(bundle.readBytes().drop(4515).toByteArray())

            return it
        }
    }

    fun covertWemToMp3(targetFile: File, codebookFile: File): File? {
        System.loadLibrary("ww2ogg")

        try {
            if (createOggFromWem("this.exe ${targetFile.absolutePath} --pcb ${codebookFile.absolutePath}")){
                val oggMid = File("${targetFile.absolutePath.dropLast(4)}.ogg")
                FFmpeg.execute("-y -i ${oggMid.absolutePath} ${targetFile.parent}/converted.mp3").let {
                    when (it) {
                        Config.RETURN_CODE_SUCCESS -> {
                            Log.i(Config.TAG, "Command execution completed successfully.")
                            oggMid.delete()
                            return File("${targetFile.parent}/converted.mp3")
                        }
                        Config.RETURN_CODE_CANCEL -> {
                            Log.i(Config.TAG, "Command execution cancelled by user.");
                            return null
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
                            return null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ww2ogg", e.message.toString())
            return null
        }
        return null
    }

    private external fun createOggFromWem(command: String): Boolean
}