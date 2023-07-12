package ru.acted.beatcharts.dataProcessors

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.palette.graphics.Palette
import org.json.JSONArray
import org.json.JSONObject
import ru.acted.beatcharts.DeEncodingManager
import ru.acted.beatcharts.R
import ru.acted.beatcharts.types.Song
import ru.acted.beatcharts.viewModels.MainViewModel
import java.io.File

class SongManager {

    fun parseSongsInfo(reso: Resources): List<Song>{

        val songs = mutableListOf<Song>()

        File("/storage/emulated/0/beatstar/songs").walkTopDown().forEach {
            if (it.isDirectory && it.absolutePath != "/storage/emulated/0/beatstar/songs") {
                //Add all not hidden
                songs.add(parseSongData(it, reso, false))
            }
        }
        File("/storage/emulated/0/beatstar/hidden").walkTopDown().forEach{
            if (it.isDirectory && it.absolutePath != "/storage/emulated/0/beatstar/hidden"){
                //Add all not hidden
                songs.add(parseSongData(it, reso, true))
            }
        }

        return songs
    }

    private fun parseSongData(songsDirectory: File, reso: Resources, isHiddenDirectory: Boolean): Song {
        val song = Song()

        //Get song directory
        song.directoryPath = songsDirectory.absolutePath

        //Parse info.json file
        if (File("${song.directoryPath}/info.json").exists()){
            //Get map from file
            val jsonMap = JSONObject(File("${song.directoryPath}/info.json").inputStream().readBytes().toString(Charsets.UTF_8)).toMap()

            song.id = jsonMap.get("id").toString()
            song.title = jsonMap.get("title").toString()
            song.artist = jsonMap.get("artist").toString()
            song.diff = jsonMap.get("difficulty").toString()
            song.bpm = jsonMap.get("bpm").toString().toFloat()
            jsonMap.get("type")?.let {
                try {
                    song.isDeluxe = (it as String) == "Promode"
                } catch (_: Exception) {

                }
            }

            if (song.artist.contains("//@")){
                val workers = song.artist.split("//@")
                if (workers.size == 2){
                    song.artist = workers[0]
                    song.charter = workers[1]
                }
            }
        } else {
            //File info.json doesn't exist, add a problem
            song.problem.add(1)
        }

        //Get artwork and color FROM BUNDLE
        if (File("${song.directoryPath}/artwork.bundle").exists()){
            song.artworkPath = "${song.directoryPath}/artwork.bundle"

            try {
                song.artBitmap = Bitmap.createScaledBitmap(DeEncodingManager().getArtworkBitmap(File(song.artworkPath).readBytes()), 150, 150, false)
                song.baseColor = Palette.from(song.artBitmap!!).generate().getDominantColor(reso.getColor(
                    R.color.background_level_a
                )).toString()
                song.isColorDark = isColorDark(song.baseColor.toInt())
            } catch (e: Exception) {
                song.artworkPath = "none"
            }
        } else {
            song.artworkPath = "none"
        }
        //Get hidden status
        song.isHidden = isHiddenDirectory

        return song
    }

    fun deleteChart(path: String){
        val chart = File(path)
        chart.deleteRecursively()
    }

    fun updateList(songs: List<Song>, param: String, action: Int): List<Song>{
        when (action) {
            1 -> {
                //Hide song
                songs.forEach {
                    if (it.directoryPath == param){
                        it.isHidden = true

                        Log.i("test", "Now song name is: ${it.title}, and its hidden: ${it.isHidden}. Logo: ${it.artworkPath}")
                    }
                }
                return songs
            }
            2 -> {
                //Show song
                songs.forEach {
                    if (it.directoryPath == param){
                        it.isHidden = false

                        Log.i("test", "Now song name is: ${it.title}, and its hidden: ${it.isHidden}. Logo: ${it.artworkPath}")
                    }
                }
                return songs
            }
        }

        return songs
    }

    fun hideShowSong(context: Context, songData: Song, viewModel: MainViewModel){
        val song = File(songData.directoryPath)
        var copyPath = File("")

        copyPath = when (songData.isHidden){
            false -> File("/storage/emulated/0/beatstar/hidden/${song.nameWithoutExtension}")
            true -> File("/storage/emulated/0/beatstar/songs/${song.nameWithoutExtension}")
        }

        if (!copyPath.exists()) {
            copyPath.mkdirs()
        }

        //Make copy
        var noProblems = true
        if (copyPath.listFiles().isEmpty()) {
            song.copyRecursively(copyPath, true) { _, error ->
                Toast.makeText(context, "Error copying: $error", Toast.LENGTH_LONG).show()
                noProblems = false
                OnErrorAction.valueOf("whatISupposedToWriteHere")
            }

            if (noProblems) song.deleteRecursively()
        }

        //Show dialog and invert element's hidden parameter in list
        Handler().postDelayed({
            songData.isHidden = !songData.isHidden
            viewModel.setSong(songData)
            viewModel.changeDialogTo(2)
        }, 100)
    }

    //Thanks to https://stackoverflow.com/questions/44870961/how-to-map-a-json-string-to-kotlin-map
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

    private fun isColorDark(color: Int): Boolean {
        val darkness: Double =
            1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness >= 0.5
    }
}