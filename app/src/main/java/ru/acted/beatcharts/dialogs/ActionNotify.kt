package ru.acted.beatcharts.dialogs

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.os.Bundle
import android.os.Handler
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.ViewModelProvider
import ru.acted.beatcharts.R
import ru.acted.beatcharts.databinding.FragmentActionNotifyBinding
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.openFromRight
import ru.acted.beatcharts.viewModels.MainViewModel

private const val ARG_PARAM1 = "message"
class ActionNotify : Fragment() {
    private var param1: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
        }
    }

    private var _binding: FragmentActionNotifyBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MainViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActionNotifyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val animDurations = 600L
        val decelerateInterpolator = DecelerateInterpolator(4f)
        viewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)

        binding.apply {
            notifyText.text = param1
            notifyContainer.post {
                //Animate container
                notifyContainer.translationX = notifyContainer.width.toFloat()
                notifyContainer.visibility = View.VISIBLE
                notifyContainer.animate().let {
                    it.duration = animDurations
                    it.interpolator = decelerateInterpolator
                    it.translationX(0f)
                    it.start()
                }

                greenLight.translationX = notifyContainer.width.toFloat()
                greenLight.rotation = 0f
                //Animate green thing
                Handler().postDelayed({
                    greenLight.animate().let {
                        it.duration = animDurations
                        it.interpolator = decelerateInterpolator
                        it.translationX(0f)
                        it.rotation(10f)
                        it.alpha(0.4f)
                        it.start()
                    }
                }, 100)

                //Animate check icon
                Handler().postDelayed({
                    notifyIcon.openFromRight()
                }, 200)

                //Animate text
                Handler().postDelayed({
                    notifyText.openFromRight()
                }, 300)

                //Hide everything with animation
                Handler().postDelayed({
                    notifyContainer.animate().let {
                        it.duration = animDurations
                        it.interpolator = decelerateInterpolator
                        it.translationX(notifyContainer.width.toFloat())
                        it.start()
                    }
                }, 4000)
            }
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String) =
            ActionNotify().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                }
            }
    }
}