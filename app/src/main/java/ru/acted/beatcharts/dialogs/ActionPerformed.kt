package ru.acted.beatcharts.dialogs

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import ru.acted.beatcharts.databinding.FragmentSongDeletedBinding

private const val ARG_PARAM1 = "songName"

class ActionPerformed : Fragment() {
    private var songName: String? = null

    private var _binding: FragmentSongDeletedBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            songName = it.getString(ARG_PARAM1)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentSongDeletedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.actionSuccessText.text = songName
    }

    companion object {
        @JvmStatic
        fun newInstance(songName: String) =
            ActionPerformed().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, songName)
                    putString("tag", "dialog")
                }
            }
    }
}