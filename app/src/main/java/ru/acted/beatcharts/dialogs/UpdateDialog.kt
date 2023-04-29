package ru.acted.beatcharts.dialogs

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.transition.AutoTransition
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import ru.acted.beatcharts.R
import ru.acted.beatcharts.databinding.FragmentUpdateDialogBinding
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.scaleDisappear
import ru.acted.beatcharts.viewModels.MainViewModel

class UpdateDialog : Fragment() {

    private var type: Int = 0
    private var isTest: Boolean = false
    private var changelog: String = "Something went wrong..."
    private var size: String = "Something went wrong..."
    private var link: String = "https://www.google.com"
    private var bttnPos: Int = 0
    private var isModal: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            type = it.getInt("type")
            isTest = it.getBoolean("isTest")
            changelog = it.getString("changelog").toString()
            size = it.getString("size").toString()
            link = it.getString("link").toString()
            bttnPos = it.getInt("bttnPos")
            isModal = it.getBoolean("isModal")
        }
    }

    private var _binding: FragmentUpdateDialogBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MainViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentUpdateDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)

        val outsideEnd = ConstraintSet()
        val outsideStart = ConstraintSet()
        val insideEnd = ConstraintSet()
        val insideStart = ConstraintSet()

        binding.apply {
            if (isTest) testBuildCard.visibility = View.VISIBLE
            when (type) {
                1 -> updTypeText.text = resources.getString(R.string.upd_type_optional)
                2 -> updTypeText.text = resources.getString(R.string.upd_type_features)
                3 -> updTypeText.text = resources.getString(R.string.upd_type_mandatory)
                4 -> updTypeText.text = resources.getString(R.string.upd_type_critical)
                5 -> updTypeText.text = resources.getString(R.string.upd_type_forced)
                else -> updTypeCard.visibility = View.GONE
            }
            updSizeText.text = resources.getString(R.string.mb, size)
            changelogText.text = changelog
            downloadBttn.setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            }
            if (type == 5) cancelBttn.visibility = View.GONE
            else cancelBttn.setOnClickListener {
                if (isModal) {
                    viewModel.changeDialogTo(0)
                    binding.updContent.scaleDisappear {}
                }
                else requireActivity().supportFragmentManager.beginTransaction().remove(this@UpdateDialog).commitNow()
            }

            clickListener.setOnClickListener {

            }

            outsideEnd.clone(frameLayout5)
            outsideStart.clone(requireContext(), R.layout.fragment_update_dialog_start)

            val absoluteY = IntArray(2)
            absolutePos.getLocationOnScreen(absoluteY)

            outsideStart.setMargin(R.id.updContent, ConstraintSet.TOP, bttnPos - absoluteY[1])
            outsideStart.applyTo(frameLayout5)

            insideEnd.clone(updContent2)
            insideStart.clone(requireContext(), R.layout.fragment_update_dialog_start_inside)
            insideStart.applyTo(updContent2)
        }

        binding.frameLayout5.post {
            val transition = AutoTransition()
            transition.duration = 300
            transition.interpolator = DecelerateInterpolator(4F)

            val transition2 = ChangeBounds()
            transition2.duration = 100

            TransitionManager.beginDelayedTransition(binding.frameLayout5, transition)
            outsideEnd.applyTo(binding.frameLayout5)

            Handler().postDelayed({
                TransitionManager.beginDelayedTransition(binding.updContent2, transition2)
                insideEnd.applyTo(binding.updContent2)
                                  }, 350)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(type: Int, isTest: Boolean, changelog: String, size: String, link: String, bttnPos: Int, isModal: Boolean) =
            UpdateDialog().apply {
                arguments = Bundle().apply {
                    putInt("type", type)
                    putBoolean("isTest", isTest)
                    putString("changelog", changelog)
                    putString("size", size)
                    putString("link", link)
                    putInt("bttnPos", bttnPos)
                    putBoolean("isModal", isModal)
                }
            }
    }
}