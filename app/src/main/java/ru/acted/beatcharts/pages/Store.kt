package ru.acted.beatcharts.pages

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import ru.acted.beatcharts.databinding.FragmentStoreBinding
import ru.acted.beatcharts.viewModels.MainViewModel

class Store : Fragment() {

    private var _binding: FragmentStoreBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentStoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    private lateinit var viewModel: MainViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)

        binding.apply {
            //Undersearchbar buttons
            uploadButton.setOnClickListener {
                viewModel.changeDialogTo(4)
            }
            starredButton.setOnClickListener {  }
            requestsButton.setOnClickListener {  }

            //Load data
            if (viewModel.offlineMode.value == true) {
                offlineMode.visibility = View.VISIBLE

                retryConnectButton.setOnClickListener {
                    //TODO try to reconnect
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun newInstance() = Store()
    }
}