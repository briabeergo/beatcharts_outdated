package ru.acted.beatcharts.pages

import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.marginLeft
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.acted.beatcharts.R
import ru.acted.beatcharts.adapters.UserBadgesAdapter
import ru.acted.beatcharts.databinding.FragmentOtherBinding
import ru.acted.beatcharts.databinding.FragmentUserProfileBinding
import ru.acted.beatcharts.types.UserData
import ru.acted.beatcharts.utils.BeatChartsUtils
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.closeToLeft
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.closeToRight
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.openFromRight
import ru.acted.beatcharts.utils.BeatChartsUtils.Data.Companion.manipulateColor
import ru.acted.beatcharts.viewModels.MainViewModel

class UserProfile : Fragment() {
    private var _binding: FragmentUserProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MainViewModel

    private var auth = Firebase.auth
    private var db = Firebase.firestore
    private var uid = "none"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)
        arguments?.let {
            uid = it.getString("uid").toString()
        }
    }

    private var userColor = 0

    private fun closeThisPage() {
        binding.userPageRoot.closeToRight {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()


    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            userPageRoot.openFromRight()
            userPageRoot.setOnClickListener {  }

            //Close buttons
            profileBackButton.setOnClickListener {
                profileBackButton.isEnabled = false
                closeThisPage()
            }

            closeErrorButton.setOnClickListener {
                closeErrorButton.isEnabled = false
                closeThisPage()
            }

            //Apply dynamic badge margin
            avatarSpace.post {
                badgesList.addItemDecoration(BeatChartsUtils.Other.Companion.FirstItemSpacingDecoration(avatarSpace.width, 0, 0, 0))
            }

            //Apply divider appear
            userContentScrollView.setOnScrollChangeListener { _, _, _, _, _ ->
                if (userContentScrollView.canScrollVertically(-1))
                    userHeaderDivider.visibility = View.VISIBLE
                else
                    userHeaderDivider.visibility = View.GONE
            }
        }
        userColor = resources.getColor(R.color.background_level_a)

        db.collection("users").document(uid).get().addOnCompleteListener {
            if (it.isSuccessful && it.result.get("name") != null) {
                binding.apply {
                    //Load username
                    username.setText(it.result.get("name") as String)

                    //Load bio
                    (it.result.get("about") as String?).let { about ->
                        if (!about.isNullOrEmpty())
                            userBio.text = about
                        else {
                            userBio.text = String.format(resources.getString(R.string.user_empty_bio), username.text.toString())
                            userBio.setTypeface(null, Typeface.ITALIC)
                        }
                    }

                    //Load avatar
                    (it.result.get("pic") as String).let { url ->
                        if (url != "") {
                            Picasso.get().load(url).into(object : com.squareup.picasso.Target {
                                override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
                                override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {}
                                override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                                    if (bitmap != null){
                                        userAvatar.setImage(bitmap)
                                        userColor = Palette.from(bitmap).generate().getDominantColor(resources.getColor(R.color.background_level_a)).manipulateColor(0.7f)
                                        userBg.setBackgroundColor(userColor)
                                        username.setTextColor(resources.getColor(R.color.white))
                                        userPageBackButtonText.setTextColor(resources.getColor(R.color.white))
                                        userPageBackButtonIcon.setColorFilter(resources.getColor(R.color.white))
                                    }
                                }
                            })
                        }
                    }

                    //Load badges
                    if (it.result.get("badges") != null) {
                        (it.result.get("badges") as ArrayList<*>).let { badges ->
                            val badgesLoaded = mutableListOf<UserData.Badge>()
                            badges.forEachIndexed { index, badgeIndex ->
                                db.collection("badges").document(badgeIndex.toString()).get().addOnCompleteListener { task ->
                                    if (task.isSuccessful && task.result.get("text") != null) {
                                        badgesLoaded.add(UserData.Badge().apply {
                                            text = task.result.get("text") as String
                                            iconUrl = task.result.get("icon") as String
                                            backgroundValue = task.result.get("bg") as String
                                        })
                                    }
                                    if (index == badges.size-1) {
                                        if (badgesLoaded.size > 0) {
                                            badgesList.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
                                            badgesList.adapter = UserBadgesAdapter(badgesLoaded, userColor, resources)
                                            loadingBadges.visibility = View.GONE
                                            badgesList.visibility = View.VISIBLE
                                        } else {
                                            noBadgesText.setText(R.string.cant_load_badges)
                                            loadingBadges.visibility = View.GONE
                                            noBadgesText.visibility = View.VISIBLE
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        loadingBadges.visibility = View.GONE
                        noBadgesText.visibility = View.VISIBLE
                    }
                }
            } else {
                binding.errorMessage.visibility = View.VISIBLE
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentUserProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    companion object {
        @JvmStatic
        fun newInstance(userId: String) = UserProfile().apply {
            arguments = Bundle().apply {
                putString("uid", userId)
            }
        }
    }
}