package ru.acted.beatcharts.dialogs

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.Settings
import android.transition.AutoTransition
import android.transition.TransitionManager
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.PermissionChecker
import androidx.core.content.PermissionChecker.checkSelfPermission
import androidx.lifecycle.ViewModelProvider
import ru.acted.beatcharts.R
import ru.acted.beatcharts.databinding.FragmentFilesDialogBinding
import ru.acted.beatcharts.viewModels.MainViewModel

class FilesDialog : Fragment() {

    companion object {
        fun newInstance() = FilesDialog()
    }

    private var _binding: FragmentFilesDialogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentFilesDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    private lateinit var viewModel: MainViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)

        binding.apply {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R){
                textView27.setText(R.string.requestPermissions)
                requestText.setText(R.string.permissionOldFilesText)
                requestAdditional.visibility = View.GONE
            }

            toSettingsButtons.setOnClickListener(){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    val uri = Uri.fromParts("package", requireActivity().packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } else {
                    requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
                }
            }

            val startConstraintSet = ConstraintSet()
            val endConstraintSet = ConstraintSet()
            startConstraintSet.clone(requireContext(), R.layout.fragment_files_dialog_start)
            endConstraintSet.clone(requestDialog)

            startConstraintSet.applyTo(requestDialog)
            //Start animation
            requestDialog.post {
                Handler().postDelayed({val transition = AutoTransition()
                    transition.duration = 200
                    transition.interpolator = OvershootInterpolator()

                    TransitionManager.beginDelayedTransition(requestDialog, transition)
                    endConstraintSet.applyTo(requestDialog)}, 1000)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        //This is file check permission
        if (checkStoragePermission()) {
            viewModel.changeDialogTo(2)
        }
    }

    private fun checkStoragePermission(): Boolean {
        var validate = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //New Android system
            if (!Environment.isExternalStorageManager()) {
                validate = false
            }
        } else {
            //Old Android system
            if (checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PermissionChecker.PERMISSION_GRANTED){
                validate = false
            }
            if (checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PermissionChecker.PERMISSION_GRANTED){
                validate = false
            }
        }

        return validate
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
