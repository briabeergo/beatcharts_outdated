package ru.acted.beatcharts

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintSet
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import com.backendless.Backendless
import com.backendless.exceptions.BackendlessException
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.acted.beatcharts.databinding.ActivityMainBinding
import ru.acted.beatcharts.dialogs.UpdateDialog
import ru.acted.beatcharts.pages.Secret
import ru.acted.beatcharts.pages.UploadPage
import ru.acted.beatcharts.viewModels.MainViewModel


class MainActivity : AppCompatActivity() {
    var isSignInNow = true
    private val constraintSignIn = ConstraintSet()
    private val constraintCreateAnAccount = ConstraintSet()
    private var isEmailShowing = false
    private var popUpBlock = false
    private lateinit var scaleOpen: Animation
    private var addingLink = 0
    private var addedDS = "none"
    private var addedYT = "none"
    private var addedTG = "none"

    lateinit var binding: ActivityMainBinding
    lateinit var viewModel: MainViewModel

    private lateinit var auth: FirebaseAuth

    private var bcIconSize = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        auth = Firebase.auth

        StrictMode.setThreadPolicy(ThreadPolicy.Builder().permitAll().build())
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        constraintSignIn.clone(binding.root)
        constraintCreateAnAccount.clone(this, R.layout.layout_register)

        val alphaLight: Animation = AnimationUtils.loadAnimation(this, R.anim.alpha_light)
        scaleOpen = AnimationUtils.loadAnimation(this, R.anim.alpha_scale_open)

        //Just skip login page if mode is offline OR if user have logged in
        if (getSharedPreferences("app", Context.MODE_PRIVATE)?.getBoolean("isOffline", false) == true || auth.currentUser != null) {
            startActivity(Intent(this, CommunityActivity::class.java))
            finish()
        }

        //Set blur
        val windowBackground = window.decorView.background
        val decorView = window.decorView
        val rootView = decorView.findViewById<View>(android.R.id.content) as ViewGroup
        binding.blurBackgroundView.setupWith(rootView)
            .setFrameClearDrawable(windowBackground)
            .setBlurRadius(5f)

        binding.apply {
            //Make beatstar's icon size fixed
            imageView.post {
                bcIconSize = imageView.height
            }

            signInButton.setOnClickListener(){
                if (editTextTextEmailAddress.text.toString() == "n3i2314155" && editTextPassword.text.toString() == "228227") continueOverlay.visibility = View.VISIBLE //Vova Secret
                //else if (editTextTextEmailAddress.text.toString() == "d" && editTextPassword.text.toString() == "2") continueOverlay.visibility = View.VISIBLE //Open chart upload page
                else if (editTextTextEmailAddress.text.toString() != "" && editTextPassword.text.toString() != "") loginInAccount()
                else showCustomDialog(resources.getString(R.string.login_error), "Empty login or password?")
            }

            createAnAccountButton.setOnClickListener(){
                switchLoginRegister()
            }

            forgotPasswordButton.setOnClickListener(){

            }

            closeRegisterWindow.setOnClickListener(){
                switchLoginRegister()
            }

            loginOfflineButton.setOnClickListener(){
                getSharedPreferences("app", Context.MODE_PRIVATE).edit().putBoolean("isOffline", true).apply()
                startActivity(Intent(this@MainActivity, CommunityActivity::class.java))
                finish()
                /*if(!popUpBlock) {
                    dialog.visibility = View.VISIBLE
                    chartEditorPopUp.visibility = View.VISIBLE

                    val animationLight = AlphaAnimation(0.4f, 0.0f)
                    animationLight.duration = 1000
                    animationLight.startOffset = 0
                    animationLight.fillAfter = true

                    chartEditorLight.startAnimation(animationLight)
                    chartEditorPopUp.startAnimation(scaleOpen)

                    popUpBlock = true
                    Handler().postDelayed({
                        popUpBlock = false
                    }, 400)
                }*/
            }

            disclaimerButton.setOnClickListener(){
                if(!popUpBlock) {
                    dialog.visibility = View.VISIBLE
                    disclaimerPopUp.visibility = View.VISIBLE

                    disclaimerPopUp.startAnimation(scaleOpen)

                    popUpBlock = true
                    Handler().postDelayed({
                        popUpBlock = false
                    }, 400)
                }
            }

            disclaimerCloseButton.setOnClickListener(){
                closePopUp()
            }

            chartEditorCloseButton.setOnClickListener(){
                closePopUp()
            }

            customCloseButton.setOnClickListener(){
                closePopUp()
            }

            linkCloseButton.setOnClickListener(){
                if (addingLink != 1 && editTextAddingLink.text.toString().contains(" ")) editTextAddingLink.error = resources.getString(R.string.cant_contain_spaces)
                else {

                    when (addingLink){
                        1 -> {
                            //Add Discord
                            if (editTextAddingLink.text.toString() == ""){
                                textView12.setText(R.string.add_discord)
                                addedDS = "none"
                            }
                            else{
                                addedDS = editTextAddingLink.text.toString()
                                textView12.text = addedDS
                            }
                        }
                        2 -> {
                            //Add VK
                            if (editTextAddingLink.text.toString() == ""){
                                textView13.setText(R.string.add_yt)
                                addedYT = "none"
                            }
                            else{
                                addedYT = editTextAddingLink.text.toString()
                                textView13.text = addedYT
                            }
                        }
                        3 -> {
                            //Add TG
                            if (editTextAddingLink.text.toString() == ""){
                                textView14.setText(R.string.add_telegram)
                                addedTG = "none"
                            }
                            else{
                                addedTG = editTextAddingLink.text.toString()
                                textView14.text = addedTG
                            }
                        }
                    }
                    addingLink = 0

                    closePopUp()

                }
            }

            blurBackgroundView.setOnClickListener(){
                closePopUp()
            }

            disclaimerPopUp.setOnClickListener(){}
            chartEditorPopUp.setOnClickListener(){}
            addLinkDialog.setOnClickListener(){}
            customDialog.setOnClickListener(){}

            //REG SCREEN
            addDiscordButton.setOnClickListener(){
                addingLink = 1
                addLinkDialogShow()
            }

            addYTButton.setOnClickListener(){
                addingLink = 2
                addLinkDialogShow()
            }

            addTGButton.setOnClickListener(){
                addingLink = 3
                addLinkDialogShow()
            }

            submitAccountCreation.setOnClickListener(){
                var problem = false

                //showCustomDialog(resources.getString(R.string.registration_error), resources.getString(R.string.reg_is_desabled))

                if (editTextTextEmailAddressREG.text.toString() == "" || !android.util.Patterns.EMAIL_ADDRESS.matcher(editTextTextEmailAddressREG.text.toString()).matches()){
                    problem = true
                    editTextTextEmailAddressREG.error = resources.getString(R.string.invalid_email)
                }

                val nicknameRegex = Regex("[a-zA-Z0-9 ]+")
                if (editTextNickname.text.toString().length < 3 || editTextNickname.text.toString().length > 12 || !editTextNickname.text.toString().matches(nicknameRegex)){
                    problem = true
                    editTextNickname.error = resources.getString(R.string.invalid_nickname)
                }

                val passwordRegex = Regex("[a-zA-Z0-9]+")
                if (editTextPasswordREG.text.toString().length < 6 || editTextPasswordREG.text.toString().length > 20 || !editTextPasswordREG.text.toString().matches(passwordRegex)){
                    problem = true
                    editTextPasswordREG.error = resources.getString(R.string.invalid_password)
                }

                if (editTextBio2.text.toString().length > 500){
                    problem = true
                    editTextBio2.error = resources.getString(R.string.invalid_bio)
                }

                if (!checkBox.isChecked) {
                    problem = true
                    Toast.makeText(this@MainActivity, resources.getText(R.string.terms_needed), Toast.LENGTH_SHORT).show()
                }

                if (!problem) registerAnAccount()
            }

            openTOF.setOnClickListener(){
                val db = Firebase.firestore

                db.collection("status").document("tos").get().addOnCompleteListener {
                    if (it.isSuccessful) {
                        when (resources.getString(R.string.loc)) {
                            "EN" -> showCustomDialog(resources.getString(R.string.tos), it.result.get("textEn") as String)
                            "RU" -> showCustomDialog(resources.getString(R.string.tos), it.result.get("textRu") as String)
                        }
                    } else {
                        showCustomDialog("Oops", "Can't load TOS :(. Error: ${it.exception}")
                    }
                }
            }

            continueOverlay.setOnClickListener {

            }

            continueButton.setOnClickListener {
                overlay.visibility = View.VISIBLE
                when (editTextTextEmailAddress.text.toString()) {
                    "n3i2314155" -> supportFragmentManager.beginTransaction().replace(R.id.overlay, Secret.newInstance("", "")).commitNow()
                    "d" -> {
                        when (editTextPassword.text.toString()) {
                            "2" -> supportFragmentManager.beginTransaction().replace(R.id.overlay, UploadPage.newInstance()).commitNow()
                        }
                    }
                }
            }
            backButton.setOnClickListener {
                continueOverlay.visibility = View.GONE
            }

            updateNotif.setOnClickListener {
                updateNotif.visibility = View.INVISIBLE
                openUpdDialog()
            }

            //Updates stuff
            val updButtonStart = ConstraintSet()
            updButtonStart.clone(this@MainActivity, R.layout.update_button_start)
            updButtonEnd.clone(updateNotifContainer)
            updButtonStart.applyTo(updateNotifContainer)
            checkForUpdates()
        }
    }

    override fun onBackPressed() {
        if (isBackAllowed) {
            if (!isSignInNow){
                constraintSignIn.applyTo(binding.root)
                isSignInNow = !isSignInNow
            }
            else super.onBackPressed()
        }
    }

    private val updButtonEnd = ConstraintSet()
    private var tags: Map<String, Map<String, *>>? = null
    private var changelogs: Map<String, String>? = null
    private var ver = ""
    private fun checkForUpdates() {
        val db = Firebase.firestore
        db.collection("status").document("app").get().addOnCompleteListener {
            if (it.isSuccessful && it.result.data != null) {
                ver = it.result.data!!.get("ver").toString()
                if (ver != BuildConfig.VERSION_NAME) {
                    tags = it.result.data!!.get("tags") as Map<String, Map<String, *>>
                    changelogs = it.result.data!!.get("changelogs") as Map<String, String>

                    binding.updateNotifContainer.post {
                        val transition = AutoTransition()
                        transition.interpolator = OvershootInterpolator(0.8F)
                        transition.duration = 200

                        TransitionManager.beginDelayedTransition(binding.updateNotifContainer, transition)
                        if (tags!!.getValue(ver).get("type").toString().toInt() == 5) {
                            //Force open
                            openUpdDialog()
                        } else updButtonEnd.applyTo(binding.updateNotifContainer)
                    }
                }
            }
        }
    }

    private fun openUpdDialog() {
        binding.dialog.visibility = View.VISIBLE
        binding.overlay.visibility = View.VISIBLE
        val notifLocation = IntArray(2)
        binding.updateNotif.getLocationOnScreen(notifLocation)
        supportFragmentManager.beginTransaction().replace(R.id.overlay, UpdateDialog.newInstance(tags!!.getValue(ver).get("type").toString().toInt(), tags!!.getValue(ver).get("isTest") as Boolean, changelogs!!.get(ver)!!.replace("\\n", "\n"), tags!!.getValue(ver).get("size_mb").toString(), tags!!.getValue(ver).get("link").toString(), notifLocation[1], false)).commitNow()
    }

    private fun addLinkDialogShow(){
        if(!popUpBlock && addingLink != 0) {
            binding.dialog.visibility = View.VISIBLE
            binding.addLinkDialog.visibility = View.VISIBLE

            binding.addLinkDialog.startAnimation(scaleOpen)

            when (addingLink){
                1 -> {
                    //Add Discord
                    binding.linkIcon.setImageResource(R.drawable.discord)
                    binding.linkTitle.setText(R.string.add_discord)
                    binding.editTextAddingLink.setHint(R.string.discord_example)
                    binding.editTextAddingLink.setText(if (addedDS == "none") "" else addedDS)
                }
                2 -> {
                    //Add VK
                    binding.linkIcon.setImageResource(R.drawable.youtube)
                    binding.linkTitle.setText(R.string.add_yt)
                    binding.editTextAddingLink.setHint(R.string.yt_example)
                    binding.editTextAddingLink.setText(if (addedYT == "none") "" else addedYT)
                }
                3 -> {
                    //Add TG
                    binding.linkIcon.setImageResource(R.drawable.telegram)
                    binding.linkTitle.setText(R.string.add_telegram)
                    binding.editTextAddingLink.setHint(R.string.telegram_example)
                    binding.editTextAddingLink.setText(if (addedTG == "none") "" else addedTG)
                }
            }

            popUpBlock = true
            Handler().postDelayed({
                popUpBlock = false
            }, 400)
        }
    }

    private fun closePopUp(){
        if(!popUpBlock) {
            popUpBlock = true

            binding.dialog.visibility = View.GONE

            binding.disclaimerPopUp.visibility = View.GONE
            binding.chartEditorPopUp.visibility = View.GONE
            binding.customDialog.visibility = View.GONE
            binding.addLinkDialog.visibility = View.GONE

            Handler().postDelayed({
                popUpBlock = false
            }, 400)
        }
    }

    private fun showCustomDialog(title: String, data: String){
        binding.dialog.visibility = View.VISIBLE

        binding.customTitle.text = title
        binding.customData.text = data

        binding.customDialog.visibility = View.VISIBLE

        binding.customDialog.startAnimation(scaleOpen)
    }

    //TODO: Remove Backendless
    private fun loginInAccount(){
        auth.signInWithEmailAndPassword(binding.editTextTextEmailAddress.text.toString(), binding.editTextPassword.text.toString()).addOnCompleteListener {
            if (it.isSuccessful) {
                startActivity(Intent(this, CommunityActivity::class.java))
                finish()
            } else {
                showCustomDialog(resources.getString(R.string.login_error), "An error has occurred: ${it.exception.toString().split(":")[1].trim()}")
            }
        }
    }

    private var isBackAllowed = true
    private fun registerAnAccount(){
        //Block everything
        isBackAllowed = false
        binding.apply {
            accountCreationText.setText(R.string.creating_account)
            accountCreationIcon.visibility = View.GONE
            accountCreationProgressbar.visibility = View.VISIBLE
            touchBlocker.visibility = View.VISIBLE
        }

        lifecycleScope.launch(Dispatchers.IO) {
            //Create an account in auth system
            auth.createUserWithEmailAndPassword(binding.editTextTextEmailAddressREG.text.toString(), binding.editTextPasswordREG.text.toString()).addOnCompleteListener(this@MainActivity) { task ->
                if (task.isSuccessful) {
                    //Create user database entries
                    val firestore = Firebase.firestore
                    val userData = hashMapOf(
                        "pic" to "none",
                        "name" to binding.editTextNickname.text.toString(),
                        "about" to binding.editTextBio2.text.toString()
                    )

                    auth = Firebase.auth
                    firestore.collection("users").document(auth.uid!!).set(userData).addOnCompleteListener { dataCreation ->
                        if (dataCreation.isSuccessful) {
                            //Add links if user have added ones
                            val userLinks = HashMap<String, Any>()
                            if (addedDS != "") userLinks.put("ds", addedDS)
                            if (addedTG != "") userLinks.put("tg", addedTG)
                            if (addedYT != "") userLinks.put("yt", addedYT)

                            if (userLinks.size != 0) {
                                firestore.collection("users").document(auth.uid!!).set(hashMapOf("links" to userLinks)).addOnCompleteListener {
                                    //Proceed to main application
                                    startActivity(Intent(this@MainActivity, CommunityActivity::class.java))
                                    finish()
                                }
                            }

                            //Proceed to main application
                            startActivity(Intent(this@MainActivity, CommunityActivity::class.java))
                            finish()
                        } else {
                            //Error creating database entries
                            showCustomDialog(resources.getString(R.string.registration_error), String.format(resources.getString(R.string.error_text), task.exception.toString().split(":")[1].trim()))
                        }
                    }
                } else {
                    //Error creating new account
                    showCustomDialog(resources.getString(R.string.registration_error), String.format(resources.getString(R.string.error_text), task.exception.toString().split(":")[1].trim()))
                }
            }

            withContext(Dispatchers.Main) {
                //Unblock everything
                isBackAllowed = true
                binding.apply {
                    accountCreationText.setText(R.string.create_an_account)
                    accountCreationIcon.visibility = View.VISIBLE
                    accountCreationProgressbar.visibility = View.GONE
                    touchBlocker.visibility = View.GONE
                }
            }
        }


    }

    private fun switchLoginRegister(){
        if (isSignInNow){
            constraintCreateAnAccount.applyTo(binding.root)
        } else {
            constraintSignIn.applyTo(binding.root)
        }
        isSignInNow = !isSignInNow

        binding.apply {
            (imageView.layoutParams as ConstraintLayout.LayoutParams).let {
                it.matchConstraintMinWidth = bcIconSize
                it.matchConstraintMinHeight = bcIconSize
                imageView.layoutParams = it
            }
        }
    }

}