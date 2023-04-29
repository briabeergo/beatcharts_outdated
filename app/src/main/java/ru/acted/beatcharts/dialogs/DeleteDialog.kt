package ru.acted.beatcharts.dialogs

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import ru.acted.beatcharts.R
import ru.acted.beatcharts.dataProcessors.SongManager
import ru.acted.beatcharts.databinding.FragmentDeleteDialogBinding
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.scaleDisappear
import ru.acted.beatcharts.viewModels.MainViewModel


class DeleteDialog : Fragment() {

    companion object {
        fun newInstance() = DeleteDialog()
    }

    private lateinit var viewModel: MainViewModel
    private var _binding: FragmentDeleteDialogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentDeleteDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

    }

    val constraintStart = ConstraintSet()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)

        val songSelected = viewModel.songData.value

        binding.apply {
            if (songSelected != null) {
                //Proceed binding view
                if (songSelected.directoryPath != ""){
                    charter.text = if (songSelected.charter != "") "${resources.getString(R.string.charted_by)} ${songSelected.charter} · ID ${songSelected.id}" else "${resources.getString(R.string.unknown_charter)} · ID ${songSelected.id}"
                    songTitle.text = songSelected.title
                    songArtist.text = songSelected.artist
                    when (songSelected.diff){
                        "1" -> {
                            //Extreme
                            difficultyHolder.visibility = View.VISIBLE
                            difficultyIcon.setImageResource(R.drawable.extreme_difficulty_logo)
                            difficultyHolder.setBackgroundDrawable(resources.getDrawable(R.drawable.card_colored_extreme))
                        }
                        "3" -> {
                            //Hard
                            difficultyHolder.visibility = View.VISIBLE
                            difficultyIcon.setImageResource(R.drawable.hard_difficulty_logo)
                            difficultyHolder.setBackgroundDrawable(resources.getDrawable(R.drawable.card_colored_hard))
                        }
                        "4" -> {
                            //Normal
                            difficultyHolder.visibility = View.GONE
                        }
                    }

                    //Bind art and colors
                    if (songSelected.baseColor != ""){
                        songCardColor.setBackgroundColor(songSelected.baseColor.toInt())

                        if (songSelected.isColorDark){
                            charter.setTextColor(resources.getColor(R.color.white))
                            songTitle.setTextColor(resources.getColor(R.color.white))
                            songArtist.setTextColor(resources.getColor(R.color.white))
                        } else {
                            charter.setTextColor(resources.getColor(R.color.black))
                            songTitle.setTextColor(resources.getColor(R.color.black))
                            songArtist.setTextColor(resources.getColor(R.color.black))
                        }


                        songArtwork.setImageBitmap(songSelected.artBitmap)


                        //Bind gradient
                        val gradientDrawable = GradientDrawable().apply {
                            colors = intArrayOf(
                                songSelected.baseColor.toInt(),
                                Color.parseColor("#00FFFFFF")
                            )
                            orientation = GradientDrawable.Orientation.RIGHT_LEFT //BR_TL
                            gradientType = GradientDrawable.LINEAR_GRADIENT
                            shape = GradientDrawable.RECTANGLE
                        }
                        songArtGradient.setBackgroundDrawable(gradientDrawable)
                    }

                    //Bind buttons
                    if (songSelected.isHidden){
                        textViewH.setText(R.string.show_in_game)
                        imageViewH.setImageResource(R.drawable.eye)
                    }
                }
            }

            deleteSubmitButton.setOnClickListener(){
                val transition = AutoTransition()
                transition.duration = 200
                transition.interpolator = DecelerateInterpolator()

                deleteDialogRoot.scaleDisappear {
                    SongManager().deleteChart(songSelected!!.directoryPath)
                    viewModel.notifText.value = resources.getString(R.string.deleted_successfully)
                    Handler().postDelayed({
                        viewModel.changeDialogTo(2)
                    }, 300)
                }
            }
            deleteCancelButton.setOnClickListener(){
                val transition = AutoTransition()
                transition.duration = 200
                transition.interpolator = DecelerateInterpolator(3F)

                TransitionManager.beginDelayedTransition(deleteDialogRoot, transition)
                constraintStart.applyTo(deleteDialogRoot)

                viewModel.changeDialogTo(0)
            }

            //Begin animation
            val location = IntArray(2)
            absolutePosView.getLocationOnScreen(location)

            constraintStart.clone(deleteDialogRoot)
            constraintStart.setMargin(R.id.deleteSongCard, ConstraintSet.TOP, songSelected!!.cardYCoordinate - location[1])
            constraintStart.applyTo(deleteDialogRoot)

            deleteDialogRoot.post {
                val constraintEnd = ConstraintSet()
                constraintEnd.clone(requireActivity(), R.layout.fragment_delete_dialog_end)

                val transition = AutoTransition()
                transition.duration = 200
                transition.interpolator = OvershootInterpolator(0.8F)

                TransitionManager.beginDelayedTransition(deleteDialogRoot, transition)
                constraintEnd.applyTo(deleteDialogRoot)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}