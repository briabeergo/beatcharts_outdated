package ru.acted.beatcharts.pages

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.transaction
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.auth.User
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.acted.beatcharts.MainActivity
import ru.acted.beatcharts.R
import ru.acted.beatcharts.databinding.FragmentOtherBinding
import ru.acted.beatcharts.databinding.FragmentStoreBinding
import ru.acted.beatcharts.viewModels.MainViewModel

class Other : Fragment() {

    private var _binding: FragmentOtherBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MainViewModel

    private var auth = Firebase.auth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            if (auth.currentUser != null) {
                profileFeaturesHint.visibility = View.GONE
                userName.setText(R.string.loading)
                userAction.setText(R.string.please_wait)

                val db = Firebase.firestore
                db.collection("users").document(auth.uid!!).get().addOnCompleteListener {
                    if (it.isSuccessful) {
                        (it.result.get("name").toString()).let { name ->
                            userName.text = name
                            viewModel.username.value = name
                        }

                        userAction.setText(R.string.open_profile)

                        (it.result.get("pic").toString()).let { url ->
                            Log.i("TEST", "url is: $url")
                            //Load picture if exists
                            if (url != "") {
                                Handler().postDelayed({Picasso.get().load(url).into(object : com.squareup.picasso.Target {
                                    override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
                                    override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {}
                                    override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                                        if (bitmap != null) avatarView.setImage(bitmap)
                                    }
                                })}, 2000)
                            }
                        }

                        //Open user page
                        profileHeader.setOnClickListener {
                            requireActivity().supportFragmentManager.beginTransaction().apply {
                                add(R.id.applicationPages, UserProfile.newInstance(auth.uid!!), "User")
                                addToBackStack("pages")
                                commit()
                            }
                        }
                    } else {
                        userName.setText(R.string.error_has_occurred)
                        userAction.setText(R.string.cant_load_user)
                        profileHeader.isEnabled = false
                    }
                }
            } else {
                profileHeader.setOnClickListener {
                    requireActivity().getSharedPreferences("app", Context.MODE_PRIVATE).edit().putBoolean("isOffline", false).apply()
                    startActivity(Intent(requireContext(), MainActivity::class.java))
                    requireActivity().finish()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentOtherBinding.inflate(inflater, container, false)
        return binding.root
    }

    companion object {
        @JvmStatic
        fun newInstance() = Other()
    }
}