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
import androidx.core.widget.doOnTextChanged
import eightbitlab.com.blurview.RenderScriptBlur
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import com.backendless.Backendless
import com.backendless.exceptions.BackendlessException
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.animation.OvershootInterpolator
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
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
    private var addedVK = "none"
    private var addedTG = "none"
    private val checkNicknameTimer: CountDownTimer = object: CountDownTimer(2000, 1000) {
        override fun onTick(millisUntilFinished: Long) {
        }
        override fun onFinish() {
            //TODO: id check
            if (binding.editTextNickname.text.toString() != "") binding.textView10.text = "Availability is unknown"
        }
    }.start()

    lateinit var binding: ActivityMainBinding
    lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        StrictMode.setThreadPolicy(ThreadPolicy.Builder().permitAll().build())
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        constraintSignIn.clone(binding.root)
        constraintCreateAnAccount.clone(this, R.layout.layout_register)

        val alphaLight: Animation = AnimationUtils.loadAnimation(this, R.anim.alpha_light)
        scaleOpen = AnimationUtils.loadAnimation(this, R.anim.alpha_scale_open)

        //Just skip login page if mode is offline
        if (getSharedPreferences("app", Context.MODE_PRIVATE)?.getBoolean("isOffline", false) == true) {
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
            signInButton.setOnClickListener(){
                //if (editTextTextEmailAddress.text.toString() != "" && editTextPassword.text.toString() != "") loginInAccount()
                if (editTextTextEmailAddress.text.toString() == "n3i2314155" && editTextPassword.text.toString() == "228227") continueOverlay.visibility = View.VISIBLE
                else if (editTextTextEmailAddress.text.toString() == "d" && editTextPassword.text.toString() == "1") startActivity(Intent(this@MainActivity, CommunityActivity::class.java))
                //else if (editTextTextEmailAddress.text.toString() == "d" && editTextPassword.text.toString() == "2") continueOverlay.visibility = View.VISIBLE
                else showCustomDialog(resources.getString(R.string.login_error), resources.getString(R.string.login_is_disabled))
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
                                textView13.setText(R.string.add_vk)
                                addedVK = "none"
                            }
                            else{
                                addedVK = editTextAddingLink.text.toString()
                                textView13.text = addedVK
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

            addVKButton.setOnClickListener(){
                addingLink = 2
                addLinkDialogShow()
            }

            addTGButton.setOnClickListener(){
                addingLink = 3
                addLinkDialogShow()
            }

            submitAccountCreation.setOnClickListener(){
                var problem = false

                showCustomDialog(resources.getString(R.string.registration_error), resources.getString(R.string.reg_is_desabled))

                /*if (editTextTextEmailAddressREG.text.toString() == "" || !android.util.Patterns.EMAIL_ADDRESS.matcher(editTextTextEmailAddressREG.text.toString()).matches()){
                    problem = true
                    editTextTextEmailAddressREG.error = resources.getString(R.string.invalid_email)
                }

                val nicknameRegex = Regex("[a-zA-Z0-9 ]+")
                if (editTextNickname.text.toString().length < 3 || editTextNickname.text.toString().length > 12 || !editTextNickname.text.toString().matches(nicknameRegex)){
                    problem = true
                    editTextNickname.error = resources.getString(R.string.invalid_nickname)
                }

                val passwordRegex = Regex("[a-zA-Z0-9]+")
                if (editTextPasswordREG.text.toString().length < 3 || editTextPasswordREG.text.toString().length > 20 || !editTextPasswordREG.text.toString().matches(passwordRegex)){
                    problem = true
                    editTextPasswordREG.error = resources.getString(R.string.invalid_password)
                }

                if (editTextBio2.text.toString().length > 500){
                    problem = true
                    editTextBio2.error = resources.getString(R.string.invalid_bio)
                }

                if (!checkBox.isChecked) {
                    problem = true
                    Toast.makeText(this, resources.getText(R.string.terms_needed), Toast.LENGTH_SHORT).show()
                }

                if (!problem) registerAnAccount()*/
            }

            openTOF.setOnClickListener(){

            }

            editTextNickname.doOnTextChanged { _, _, _, _ ->
                if (editTextNickname.text.toString() != "") {
                    textView10.setText(R.string.checking)
                    checkNicknameTimer.cancel()
                    checkNicknameTimer.start()
                } else {
                    checkNicknameTimer.cancel()
                    textView10.setText(R.string.nickname_id_hint)
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
            //checkForUpdates() TODO implement update check here too :)
        }
    }

    override fun onBackPressed() {
        if (!isSignInNow){
            constraintSignIn.applyTo(binding.root)
            isSignInNow = !isSignInNow
        }
        else super.onBackPressed()
    }

    private val updButtonEnd = ConstraintSet()
    private var tags: Map<String, Map<String, *>>? = null
    private var changelogs: Map<String, String>? = null
    private var ver = ""
    /*private fun checkForUpdates() {
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
    }*/

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
                    binding.linkIcon.setImageResource(R.drawable.vk)
                    binding.linkTitle.setText(R.string.add_vk)
                    binding.editTextAddingLink.setHint(R.string.vk_example)
                    binding.editTextAddingLink.setText(if (addedVK == "none") "" else addedVK)
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
        try {
            Backendless.UserService.setCurrentUser(Backendless.UserService.login(binding.editTextTextEmailAddress.text.toString(), binding.editTextPassword.text.toString(), true))

            startActivity(Intent(this, CommunityActivity::class.java))
            finish()
        } catch (e: BackendlessException) {
            if (e.code == "3003") showCustomDialog(resources.getString(R.string.login_error), resources.getString(R.string.invalid_login_data))
            else showCustomDialog(resources.getString(R.string.login_error), "ERROR CODE: ${e.code}\nHTTP STATUS CODE: ${e.httpStatusCode}\nERROR DETAILS: ${e.detail}")
        }
    }

    //TODO: Remove Backendless 2
    private fun registerAnAccount(){

       /* val newUser = BackendlessUser()
        newUser.setProperty("email", editTextTextEmailAddressREG.text.toString())
        newUser.setProperty("nickname", editTextNickname.text.toString())
        newUser.setProperty("bio", editTextBio2.text.toString())
        newUser.setProperty("links", "$addedDS/#@/$addedTG/#@/$addedVK")
        newUser.password = editTextPasswordREG.text.toString()

        try {
            Backendless.UserService.register(newUser)
            Backendless.UserService.setCurrentUser(Backendless.UserService.login(editTextTextEmailAddressREG.text.toString(), editTextPasswordREG.text.toString(), true))

            startActivity(Intent(this, CommunityActivity::class.java))
            finish()
        } catch (e: BackendlessException){
            showCustomDialog(resources.getString(R.string.registration_error), "ERROR CODE: ${e.code}\nHTTP STATUS CODE: ${e.httpStatusCode}\nERROR DETAILS: ${e.detail}")
        }*/
    }

    private fun switchLoginRegister(){
        if (isSignInNow){
            constraintCreateAnAccount.applyTo(binding.root)
        } else {
            constraintSignIn.applyTo(binding.root)
        }
        isSignInNow = !isSignInNow
    }

}