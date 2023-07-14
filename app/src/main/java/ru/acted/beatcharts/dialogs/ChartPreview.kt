package ru.acted.beatcharts.dialogs

import android.animation.Animator
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import jp.wasabeef.blurry.Blurry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import ru.acted.beatcharts.DeEncodingManager
import ru.acted.beatcharts.R
import ru.acted.beatcharts.dataProcessors.AudioManager
import ru.acted.beatcharts.databinding.FragmentChartPreviewBinding
import ru.acted.beatcharts.types.Chart
import ru.acted.beatcharts.types.NoteOffset
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.openFromCenter
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.scaleDisappear
import ru.acted.beatcharts.viewModels.MainViewModel
import java.io.File
import java.io.FileOutputStream

class ChartPreview : Fragment() {
    private var songId: Int? = null

    private var _binding: FragmentChartPreviewBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel

    private var _libvlc: LibVLC? = null
    private var _mediaPlayer: MediaPlayer? = null
    private val libvlc get() = _libvlc!!
    private val mediaPlayer get() = _mediaPlayer!!

    private val bgTasks = mutableListOf<Job>()
    private val timers = mutableListOf<CountDownTimer>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            songId = it.getInt("id")
        }

        viewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)
    }

    private fun copyFileFromAssets(context: Context, fileName: String, directory: String) {
        val inputStream = context.assets.open(fileName)
        File(directory).mkdirs()
        File(directory, fileName).createNewFile()
        val outputStream = FileOutputStream(File(directory, fileName))

        inputStream.copyTo(outputStream)

        inputStream.close()
        outputStream.flush()
        outputStream.close()
    }

    private fun closeThisWindow() {
        bgTasks.forEach {
            it.cancel()
        }
        timers.forEach {
            it.cancel()
        }

        binding.previewRoot.scaleDisappear {}
        Handler().postDelayed({viewModel.changeDialogTo(0)}, 100)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.previewRoot.openFromCenter()
        _libvlc = LibVLC(requireContext())
        _mediaPlayer = MediaPlayer(libvlc)

        viewModel.songsList.value?.get(songId!!)?.let { song ->
            //Bind visuals
            binding.apply {
                songArtworkIcon.setImageBitmap(song.artBitmap)
                chartTitlePreview.text = song.title
                chartArtistPreview.text = song.artist
                song.charter.let {
                    chartPreviewText.text = if (it != "") resources.getString(R.string.charted_by) + " " + it else resources.getString(R.string.unknown_charter)
                }
                Blurry.with(context).radius(5).from(song.artBitmap).into(bgArt2)

                previewCloseButton.setOnClickListener {
                    closeThisWindow()
                }
            }

            //Load chart
            lifecycleScope.launch(Dispatchers.IO) {
                File(song.directoryPath, "chart.bundle").readBytes().let { chartBundleBytes ->
                    DeEncodingManager().parseChartBytes(chartBundleBytes.copyOfRange(4431, chartBundleBytes.lastIndex), null).let { conversionResult ->
                        if (conversionResult.exceptionList.size == 0) {
                            chart = conversionResult.chart

                            chart!!.notes.sortBy { note -> note.offsets[0].position }

                            withContext(Dispatchers.Main) {
                                checkForLoadingCompleted()
                            }
                        } else {
                            binding.apply {
                                previewErrorContainer.visibility = View.VISIBLE
                                loadingIndicator.visibility = View.GONE
                            }
                        }
                    }
                }
            }.let {
                bgTasks.add(it)
            }

            //Load audio
            lifecycleScope.launch(Dispatchers.IO) {
                //Create codebooks if doesn't exist
                File("/storage/emulated/0/beatstar/BCTemp/codebooks.bin").let { codebooksFile ->
                    yield()
                    if (!codebooksFile.exists()) copyFileFromAssets(requireContext(), codebooksFile.name, codebooksFile.parent!!)

                    //Copy wem from bundle to the temp folder
                    AudioManager().takeWemFromBundle(File("${song.directoryPath}/audio.bundle")).let { tempWem ->
                        yield()
                        //Check if this wem is not compressed
                        if (tempWem.readBytes().copyOfRange(0, 100).decodeToString().contains("JUNK")) {
                            yield()
                            //This is not compressed - just roll back the header to wav and convert with ffmpeg
                            AudioManager().makeMp3FromJunkWem(tempWem)?.let { mp3File ->
                                yield()
                                //Mark audio as loaded
                                mediaFile = mp3File

                                withContext(Dispatchers.Main) {
                                    checkForLoadingCompleted()
                                }
                            }
                        } else {
                            //This is compressed - create ogg from wem and mp3 from ogg then
                            AudioManager().covertWemToMp3(tempWem, codebooksFile)?.let {  mp3File ->
                                yield()
                                //Mark audio as loaded
                                mediaFile = mp3File

                                withContext(Dispatchers.Main) {
                                    checkForLoadingCompleted()
                                }
                            }
                        }
                    }
                }
            }.let{
                bgTasks.add(it)
            }
        }
    }

    private var mediaFile: File? = null
    private var chart: Chart? = null
    private fun checkForLoadingCompleted() {
        if (mediaFile != null && chart != null) {
            //Play this file
            mediaPlayer.media = Media(libvlc, Uri.fromFile(mediaFile))
            //Block playing until length init
            mediaPlayer.play()
            while (mediaPlayer.length <= (0).toLong()) {}
            val songDuration = mediaPlayer.length/2
            mediaPlayer.stop()
            mediaPlayer.play()

            //Feedback start
            binding.apply {
                loadingIndicator.visibility = View.GONE
                animateStart()
            }

            //Block until media metadata will be initialized
            binding.songPreviewProgress.max = songDuration.toInt()
            binding.songPreviewProgressHiglight.max = songDuration.toInt()

            val bpm = viewModel.songsList.value!!.get(songId!!).bpm
            //BPM timer and highlight things
            object: CountDownTimer(songDuration, (60/(bpm)*1000).toLong()) {
                override fun onFinish() {
                    closeThisWindow()
                }

                override fun onTick(millisUntilFinished: Long) {
                    binding.apply {
                        //Move progress bar
                        songPreviewProgress.progress = (songDuration-millisUntilFinished).toInt()
                        songPreviewProgressHiglight.progress = (songDuration-millisUntilFinished).toInt()

                        //Highlight things
                        songPreviewProgressHiglight.alpha = 0.5f
                        songPreviewProgressHiglight.animate().apply {
                            interpolator = LinearInterpolator()
                            duration = (60/(bpm)*1000).toLong()
                            alpha(0f)
                            start()
                        }

                        topRhytm.alpha = 0.2f
                        topRhytm.animate().apply {
                            interpolator = LinearInterpolator()
                            duration = (60/(bpm)*1000).toLong()
                            alpha(0f)
                            start()
                        }

                        bottomRhytm.alpha = 0.2f
                        bottomRhytm.animate().apply {
                            interpolator = LinearInterpolator()
                            duration = (60/(bpm)*1000).toLong()
                            alpha(0f)
                            start()
                        }
                    }
                }
            }.start().let {
                timers.add(it)
            }

            //Bind visual notes remaining indicator
            chart?.let { binding.remainigNotesCount.text = it.notes.size.toString() }

            //Preview timer
            data class RailHold (
                var offsets: MutableList<NoteOffset> = mutableListOf(),
                var originalLane: Int = 0
            )
            val railHolds = mutableListOf<RailHold>()
            object: CountDownTimer(songDuration, 10) {
                override fun onTick(millisUntilFinished: Long) {
                    val indexesForDeletion = mutableListOf<Int>()
                    val railsForDeletion = mutableListOf<Int>()
                    val currentTime = songDuration - millisUntilFinished

                    //Check buffered offsets to be triggered
                    railHolds.forEachIndexed { index, railHold ->
                        val thisOffsetTime = ((railHold.offsets.first().position*192)/bpm/32*10000).toLong()
                        val offset = railHold.offsets.first()

                        if (thisOffsetTime < currentTime) {
                            val nextOffsetTime = if (1 <= railHold.offsets.lastIndex){
                                ((railHold.offsets[1].position*192)/bpm/32*10000).toLong()
                            }
                            else {
                                railsForDeletion.add(index)
                                thisOffsetTime
                            }

                            if (nextOffsetTime != thisOffsetTime) railHold.offsets.removeAt(0)

                            val pianoButton = when (railHold.originalLane) {
                                0 -> binding.leftLane
                                1 -> binding.midLane
                                2 -> binding.rightLane
                                else -> binding.leftLane
                            }

                            val neededLanePos = when (offset.lane) {
                                0 -> binding.leftLane.x
                                1 -> binding.midLane.x
                                2 -> binding.rightLane.x
                                else -> binding.leftLane.x
                            }

                            pianoButton.animation?.cancel()
                            if (railHold.offsets.size == 0) //This was the last offset [final one]
                                pianoButton.animate().apply {
                                    interpolator = LinearInterpolator()
                                    duration = 150
                                    startDelay = 0
                                    alpha(0f)
                                    start()
                                }
                            else //Change lane
                                pianoButton.animate().apply {
                                    interpolator = LinearInterpolator()
                                    duration = (nextOffsetTime - thisOffsetTime).let { return@let if (it - 50 > 0) it - 50 else it }
                                    startDelay = 0
                                    translationX((pianoButton.x - neededLanePos)*-1)
                                    start()
                                }
                        }
                    }

                    //Check nearest notes to be triggered
                    for (i in chart!!.notes.indices) {
                        val note = chart!!.notes[i]

                        val noteTime = ((note.offsets[0].position*192)/bpm/32*10000).toLong()

                        //Determine if this is hold - set the time to remove the note
                        val lastTime = ((note.offsets.last().position*192)/bpm/32*10000).toLong()

                        if (noteTime < currentTime) {
                            //This note is counted on that tick! (Tag to remove this one from queue)
                            indexesForDeletion.add(i)

                            val pianoButton = when (note.lane) {
                                0 -> binding.leftLane
                                1 -> binding.midLane
                                2 -> binding.rightLane
                                else -> binding.leftLane
                            }

                            pianoButton.animation?.cancel()
                            pianoButton.translationX = 0f
                            pianoButton.alpha = 0.8f

                            //Disappear timings
                            pianoButton.animate().apply {
                                interpolator = LinearInterpolator()
                                duration = 150
                                startDelay = if (note.offsets.size > 1) lastTime - noteTime else 0
                                alpha(0f)
                                start()
                            }

                            //Check if this is rail holds keks
                            if (note.type == 5) railHolds.add(RailHold(note.offsets, note.lane))

                            //Now decrease notes counter
                            binding.remainigNotesCount.text = (binding.remainigNotesCount.text.toString().toInt() - 1).toString()
                            animateNotesCountChanged()
                        } else break
                    }

                    //Remove all triggered notes from queue
                    indexesForDeletion.forEachIndexed { index_offset, i ->
                        chart!!.notes.removeAt(i - index_offset)
                    }
                    railsForDeletion.forEachIndexed { index_offset, i ->
                        railHolds.removeAt(i - index_offset)
                    }
                }

                override fun onFinish() {}
            }.start().let {
                timers.add(it)
            }
        }
    }

    private fun animateNotesCountChanged() {
        val localDecelerate = DecelerateInterpolator(2f)

        binding.apply {
            notesRemainingContainer.scaleX = 1.3f
            notesRemainingContainer.scaleY = 1.3f
            notesRemainingContainer.animate().apply {
                interpolator = localDecelerate
                scaleX(1f)
                scaleY(1f)
                duration = 300
                start()
            }

            notesRemainingHighlight.alpha = 0.3f
            notesRemainingHighlight.animate().apply {
                interpolator = localDecelerate
                alpha(0.05f)
                duration = 300
                start()
            }

            remainigNotesCount.alpha = 1f
            remainigNotesCount.animate().apply {
                interpolator = localDecelerate
                alpha(0.7f)
                duration = 300
                start()
            }

            notesRemainingText.alpha = 0.7f
            notesRemainingText.animate().apply {
                interpolator = localDecelerate
                alpha(0f)
                duration = 300
                start()
            }
        }
    }

    private fun animateStart() {
        val decelerateInterpolator = DecelerateInterpolator(1.0f)
        binding.apply {

            playboard.scaleX = 1.3f
            playboard.scaleY = 1.3f
            playboard.alpha = 0f
            playboardContainer.visibility = View.VISIBLE
            playboard.animate().apply {
                interpolator = decelerateInterpolator
                duration = 1500
                alpha(1f)
                scaleX(1f)
                scaleY(1f)
                rotationX(10f)
                start()
            }

            previewRunningTop.visibility = View.VISIBLE
            previewRunningTop.translationY = previewRunningBottom.height.toFloat()
            previewRunningTop.alpha = 0f
            previewRunningTop.animate().apply {
                interpolator = decelerateInterpolator
                duration = 500
                alpha(1f)
                translationY(0f)
                start()
            }
            topHighlight.animate().apply {
                interpolator = LinearInterpolator()
                duration = 500
                translationY(topHighlight.height.toFloat()*-1-5f)
                start()
            }

            previewRunningBottom.visibility = View.VISIBLE
            previewRunningBottom.translationY = previewRunningBottom.height.toFloat()*-1
            previewRunningBottom.alpha = 0f
            previewRunningBottom.animate().apply {
                interpolator = decelerateInterpolator
                duration = 500
                alpha(1f)
                translationY(0f)
                start()
            }
            bottomHighlight.animate().apply {
                interpolator = LinearInterpolator()
                duration = 500
                translationY(bottomHighlight.height.toFloat())
                start()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        mediaPlayer.stop()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChartPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    companion object {
        @JvmStatic
        fun newInstance(chartIdInList: Int) =
            ChartPreview().apply {
                arguments = Bundle().apply {
                    putInt("id", chartIdInList)
                }
            }
    }
}