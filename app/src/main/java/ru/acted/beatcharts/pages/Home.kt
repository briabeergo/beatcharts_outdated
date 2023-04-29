package ru.acted.beatcharts.pages

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.acted.beatcharts.R
import ru.acted.beatcharts.adapters.SongsRecyclerViewAdapter
import ru.acted.beatcharts.databinding.FragmentHomeBinding
import ru.acted.beatcharts.types.Song
import ru.acted.beatcharts.viewModels.MainViewModel

class Home : Fragment() {

    val xy = IntArray(2)
    val xyo = IntArray(2)
    var checkZero = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    private lateinit var viewModel: MainViewModel
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var isBeatstarInstalled = false
    private var isBeatcloneInstalled = false
    private var cont = context
    private var savedState: Parcelable? = null
    private var firstOpen = true
    private var songList: List<Song>? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)

        //Get installed clients
        var clientsCount = 0
        val packages = activity?.packageManager?.getInstalledApplications(PackageManager.GET_META_DATA)
        if (packages != null) {
            for (packageInfo in packages) {
                if (packageInfo.packageName == "com.spaceapegames.beatstar"){
                    isBeatstarInstalled = true
                    binding.imageView13.setImageDrawable(packageInfo.loadIcon(requireActivity().packageManager))
                    clientsCount += 1
                }
                else if (packageInfo.packageName == "com.spaceapegames.beatclon"){
                    isBeatcloneInstalled = true
                    binding.imageView131.setImageDrawable(packageInfo.loadIcon(requireActivity().packageManager))
                    clientsCount += 1
                }
            }
        }
        cont = context

        //Load downloaded songs
        binding.songsRecyclerVIew.layoutManager = LinearLayoutManager(context)

        viewModel.songsList.observe(requireActivity(), Observer {
            initSongs()
        })

        binding.clientInstalledInfo.visibility = View.VISIBLE
        binding.clientInstalledInfo.text = clientsCount.toString()

        //Binding client info window
        binding.apply {
            if (isBeatstarInstalled){
                if (isBeatcloneInstalled){
                    beatstarInfo.setText(R.string.version_idk)
                    beatstarChecking.visibility = View.GONE
                    beatstarIcon.alpha = 0.6.toFloat()
                    beatstarIcon.setImageResource(R.drawable.question_circle)
                } else {
                    beatcloneCard.visibility = View.GONE
                    versionDivider.visibility = View.GONE
                }
            } else {
                if (isBeatcloneInstalled){
                    beatstarCard.visibility = View.GONE
                    versionDivider.visibility = View.GONE
                } else {
                    beatcloneCard.visibility = View.GONE
                    versionDivider.visibility = View.GONE
                    beatstarChecking.visibility = View.GONE
                    imageView13.visibility = View.GONE
                    beatstarInfo.visibility = View.GONE
                    beatstarIcon.visibility = View.GONE
                    beatstarTitle.visibility = View.GONE
                    noClientText.visibility = View.VISIBLE
                    cardView3.setCardBackgroundColor(resources.getColor(R.color.background_level_b))
                }
            }
            //songsRecyclerVIew.viewTreeObserver.dispatchOnDraw()
            scrollViewAbsoluteZero.post {
                scrollViewAbsoluteZero.getLocationInWindow(xyo)
                homeScrollView.viewTreeObserver.addOnScrollChangedListener {
                    songsInfoTitleRecycler.getLocationInWindow(xy)
                    if (xy[1] <= xyo[1]) songsTitleOverlay.visibility = View.VISIBLE
                    else songsTitleOverlay.visibility = View.INVISIBLE
                }
            }
        }


    }

    /*override fun onPause() {
        super.onPause()
        val sharedPref = requireContext().getSharedPreferences("state", Context.MODE_PRIVATE)
        with(sharedPref?.edit()!!) {
            putInt("updateSongList", 0)
            putInt("makeBlur", 1)
            apply()
        }
    }*/

   /* override fun onResume() {
        super.onResume()

        if (firstOpen) {
            firstOpen = false
        } else {
            val sharedPref = requireContext().getSharedPreferences("state", Context.MODE_PRIVATE)
            val songOperation = sharedPref?.getInt("updateSongList", 0)
            if (songOperation !== 0){
                val sharedPref = requireContext().getSharedPreferences("state", Context.MODE_PRIVATE)
                with(sharedPref?.edit()!!) {
                    putInt("updateSongList", 0)
                    putInt("makeBlur", 2)
                    apply()
                }

                savedState = songsRecyclerVIew.layoutManager?.onSaveInstanceState()
                initSongs()
            } else {
                val sharedPref = requireContext().getSharedPreferences("state", Context.MODE_PRIVATE)
                with(sharedPref?.edit()!!) {
                    putInt("makeBlur", 0)
                    apply()
                }
            }
        }
    }*/

    private fun initSongs() {
        lifecycleScope.launch(Dispatchers.IO) {

            songList = viewModel.songsList.value

            withContext(Dispatchers.Main) {
                binding.songsDownloadedInfo.text = songList!!.size.toString()
                binding.songsDownloadedInfo.visibility = View.VISIBLE
                binding.songsDownloadedInfo1.text = songList!!.size.toString()
                binding.songsDownloadedInfo1.visibility = View.VISIBLE

                binding.songsRecyclerVIew.adapter = SongsRecyclerViewAdapter(
                    songList!!,
                    resources,
                    cont!!,
                    requireActivity(),
                    viewModel
                )
                if (savedState !== null) binding.songsRecyclerVIew.layoutManager?.onRestoreInstanceState(
                    savedState
                )

                binding.songDataProgress.visibility = View.GONE
                binding.songsRecyclerVIew.visibility = View.VISIBLE
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    companion object {
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance() =
            Home().apply {
                arguments.let {

                }
            }

    }
}