package ru.acted.beatcharts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import ru.acted.beatcharts.dataProcessors.SongManager
import ru.acted.beatcharts.databinding.ActivityCommunityBinding
import ru.acted.beatcharts.dialogs.ActionNotify
import ru.acted.beatcharts.dialogs.ChartPreview
import ru.acted.beatcharts.dialogs.DeleteDialog
import ru.acted.beatcharts.dialogs.FilesDialog
import ru.acted.beatcharts.dialogs.UpdateDialog
import ru.acted.beatcharts.pages.Store
import ru.acted.beatcharts.pages.Home
import ru.acted.beatcharts.pages.UploadPage
import ru.acted.beatcharts.types.Song
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.animateDisable
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.animateEnable
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.closeToRight
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.openFromCenter
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.openFromRightScreenSide
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.scaleAppear
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.scaleDisappear
import ru.acted.beatcharts.utils.BeatChartsUtils.Sys.Companion.hideKeyboard
import ru.acted.beatcharts.viewModels.MainViewModel
import java.io.File
import java.util.function.Predicate

class CommunityActivity : AppCompatActivity(){

    private var currentPage = 1
    lateinit var songsList: List<Song>

    private lateinit var viewModel: MainViewModel
    private lateinit var binding: ActivityCommunityBinding

    var prevDialog = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommunityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        //Get offline state
        if (getSharedPreferences("app", Context.MODE_PRIVATE)?.getBoolean("isOffline", false) == true) {
            viewModel.offlineMode.value = true
        }

        val windowBackground = window.decorView.background
        val decorView = window.decorView
        val rootView = decorView.findViewById<View>(android.R.id.content) as ViewGroup
        binding.blurBackgroundHome.setupWith(rootView)
            .setFrameClearDrawable(windowBackground)
            .setBlurRadius(5f)
        binding.blurBackgroundHome.setBlurEnabled(false)

        viewModel.notifIndicator.observe(this) {
            when (it) {
                1 -> {
                    //Show notif
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.notifStuff, ActionNotify.newInstance(viewModel.notifText.value!!), "notif").commitNow()
                }
            }
        }

        binding.updateNotif.setOnClickListener {
            binding.updateNotif.visibility = View.GONE
            viewModel.changeDialogTo(5)
        }

        viewModel.currentDialog.observe(this) {
            Log.i("TEST", "Changed dialog to $it")
            when (it) {
                0 -> {
                    //Close dialogs
                    when (prevDialog) {
                        99 -> {} //Don't do anything when it's action notif
                        4 -> { //Close this upload page with animation
                            binding.tapMuter.visibility = View.VISIBLE
                            binding.dialogOverlay.closeToRight {
                                supportFragmentManager.findFragmentByTag("dialog")?.let {
                                    supportFragmentManager.beginTransaction().remove(it).commitNow()
                                }
                                binding.dialogOverlay.translationX = 0f
                                binding.dialogOverlay.alpha = 1f
                                binding.dialogOverlay.visibility = View.GONE
                                binding.mainLoadingIndicator.visibility = View.GONE
                                binding.navblur.setBlurEnabled(true)
                                binding.blurBackgroundHome.setBlurEnabled(false)
                                binding.blurBackgroundHome.visibility = View.GONE
                                binding.tapMuter.visibility = View.GONE
                            }
                        }
                        7 -> {
                            binding.blurBackgroundHome.setBlurAutoUpdate(true)
                            binding.blurBackgroundHome.animateDisable {
                                binding.blurBackgroundHome.setBlurEnabled(false)
                                binding.blurBackgroundHome.visibility = View.GONE
                                binding.dialogOverlay.visibility = View.GONE
                                supportFragmentManager.findFragmentByTag("dialog")?.let {
                                    supportFragmentManager.beginTransaction().remove(it).commitNow()
                                }
                                binding.mainLoadingIndicator.visibility = View.GONE
                                binding.navblur.setBlurEnabled(true)
                            }
                        }
                        else -> { //Close everything else
                            Handler().postDelayed({
                                binding.blurBackgroundHome.animateDisable {
                                    binding.blurBackgroundHome.setBlurEnabled(false)
                                    binding.blurBackgroundHome.visibility = View.GONE
                                    binding.dialogOverlay.visibility = View.GONE
                                    supportFragmentManager.findFragmentByTag("dialog")?.let {
                                        supportFragmentManager.beginTransaction().remove(it).commitNow()
                                    }
                                    binding.mainLoadingIndicator.visibility = View.GONE
                                    binding.navblur.setBlurEnabled(true)
                                }
                            }, 200)
                        }
                    }

                    prevDialog = 0
                }
                1 -> {
                    //Chart delete dialog
                    binding.dialogOverlay.visibility = View.VISIBLE
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.dialogOverlay, DeleteDialog.newInstance(), "dialog").commitNow()

                    binding.blurBackgroundHome.visibility = View.VISIBLE
                    binding.blurBackgroundHome.setBlurEnabled(true)
                    binding.blurBackgroundHome.animateEnable()
                    binding.navblur.setBlurEnabled(false)
                    prevDialog = 1
                }
                2 -> {
                    //Get type of action
                    when (prevDialog) {
                        0 -> {
                            //Invert is hidden value and replace song's directory in list
                            val newList = viewModel.songsList.value!!.toMutableList()
                            newList.find { it.directoryPath == viewModel.songData.value!!.directoryPath}!!.isHidden = viewModel.songData.value!!.isHidden
                            val songDirectory = File(newList.find { it.directoryPath == viewModel.songData.value!!.directoryPath}!!.directoryPath)
                            newList.find { it.directoryPath == viewModel.songData.value!!.directoryPath}!!.directoryPath = if (viewModel.songData.value!!.isHidden) "/storage/emulated/0/beatstar/hidden/${songDirectory.nameWithoutExtension}"
                                else "/storage/emulated/0/beatstar/songs/${songDirectory.nameWithoutExtension}"
                            viewModel.setSongsList(newList.toList())

                            viewModel.showNotif(if (viewModel.songData.value!!.isHidden) resources.getString(R.string.hide_success) else resources.getString(R.string.show_success))
                            prevDialog = 99
                        }
                        1 -> {
                            //Song was deleted
                            //Remove deleted song from list
                            val newList = viewModel.songsList.value!!.toMutableList()
                            val predicate = Predicate { song: Song -> song.directoryPath == viewModel.songData.value!!.directoryPath}
                            newList.removeIf(predicate)
                            viewModel.setSongsList(newList.toList())

                            viewModel.showNotif(resources.getString(R.string.deleted_successfully))
                            prevDialog = 2
                        }
                        3 -> {
                            //Files permission granted
                            viewModel.setSongsList(SongManager().parseSongsInfo(resources))

                            binding.dialogOverlay.visibility = View.GONE
                            binding.mainLoadingIndicator.visibility = View.VISIBLE

                            showPopUps() //Show pop ups after permission granted

                            //viewModel.showNotif(resources.getString(R.string.setup_complete))
                            prevDialog = 2
                        }
                    }

                    viewModel.changeDialogTo(0)
                }
                3 -> {
                    //Open files request activity
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.dialogOverlay, FilesDialog.newInstance(), "dialog").commitNow()
                    binding.dialogOverlay.visibility = View.VISIBLE
                    binding.blurBackgroundHome.visibility = View.VISIBLE
                    binding.blurBackgroundHome.setBlurEnabled(true)
                    binding.navblur.setBlurEnabled(false)
                    prevDialog = 3
                }
                4 -> {
                    //Open upload chart dialog
                    binding.blurBackgroundHome.visibility = View.VISIBLE
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.dialogOverlay, UploadPage.newInstance(), "dialog").commitNow()
                    binding.dialogOverlay.visibility = View.VISIBLE
                    //binding.dialogOverlay.openFromCenter()
                    binding.navblur.setBlurEnabled(false)
                    prevDialog = 4
                    hideKeyboard()
                }
                5 -> {
                    //Update dialog
                    binding.dialogOverlay.visibility = View.VISIBLE
                    val notifLocation = IntArray(2)
                    binding.updateNotif.getLocationOnScreen(notifLocation)
                    supportFragmentManager.beginTransaction().replace(R.id.dialogOverlay, UpdateDialog.newInstance(tags!!.getValue(ver).get("type").toString().toInt(), tags!!.getValue(ver).get("isTest") as Boolean, changelogs!!.get(ver)!!.replace("\\n", "\n"), tags!!.getValue(ver).get("size_mb").toString(), tags!!.getValue(ver).get("link").toString(), notifLocation[1], true)).commitNow()

                    binding.blurBackgroundHome.visibility = View.VISIBLE
                    binding.blurBackgroundHome.setBlurEnabled(true)
                    binding.blurBackgroundHome.animateEnable()
                    binding.navblur.setBlurEnabled(false)
                    prevDialog = 5
                }
                6 -> {
                    //Sync promo dialog
                    binding.dialogOverlay.visibility = View.VISIBLE
                    binding.scoreSyncDialog.scaleAppear()

                    binding.blurBackgroundHome.visibility = View.VISIBLE
                    binding.blurBackgroundHome.setBlurEnabled(true)
                    binding.blurBackgroundHome.animateEnable()
                    binding.navblur.setBlurEnabled(false)
                    prevDialog = 6
                }
                7 -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.dialogOverlay, ChartPreview.newInstance(viewModel.songIdForPreview.value!!), "dialog").commitNow()
                    binding.dialogOverlay.visibility = View.VISIBLE

                    binding.blurBackgroundHome.visibility = View.VISIBLE
                    binding.navblur.setBlurEnabled(false)
                    binding.blurBackgroundHome.setBlurEnabled(true)
                    binding.blurBackgroundHome.setBlurAutoUpdate(false)
                    binding.blurBackgroundHome.animateEnable()
                    prevDialog = 7
                }
            }
        }

        initNavigation()

        binding.apply {
            //Prevent unwanted clicks
            blurBackgroundHome.setOnClickListener {}
            tapMuter.setOnClickListener {  }

            //TODO remove it
            openEncodingActivity.setOnClickListener {
                startActivity(Intent(this@CommunityActivity, TestAudioActivity::class.java))
            }

            closeSyncPromo.setOnClickListener {
                binding.scoreSyncDialog.scaleDisappear {
                    binding.scoreSyncDialog.visibility = View.GONE
                }
                getSharedPreferences("app", Context.MODE_PRIVATE).edit().putBoolean("promoScore", true).apply()
                viewModel.changeDialogTo(0)
                showPopUps()
            }

            root.post { //Check storage permission
                if (!checkStoragePermission()){
                    //No permission - open dialog
                    viewModel.changeDialogTo(3)
                } else {
                    //Load all songs
                    viewModel.setSongsList(SongManager().parseSongsInfo(resources))

                    showPopUps()
                }

                checkForUpdates()
            }
        }
    }

    private fun showPopUps() {
        Handler().postDelayed({
            //Show promo if user not seen it
            if (getSharedPreferences("app", Context.MODE_PRIVATE)?.getBoolean("promoScore", false) == false) {
                viewModel.changeDialogTo(6)
            }
        }, 1000)

    }

    override fun onBackPressed() {
        if (prevDialog == 4) viewModel.changeDialogTo(0)
    }


    private var tags: Map<String, Map<String, *>>? = null
    private var changelogs: Map<String, String>? = null
    private var ver = ""
    private fun checkForUpdates() {
        val db = Firebase.firestore
        db.collection("status").document("app").get().addOnCompleteListener {
            if (it.isSuccessful && it.result.data != null) {
                ver = it.result.data!!["ver"].toString()
                if (ver != BuildConfig.VERSION_NAME) {
                    tags = it.result.data!!["tags"] as Map<String, Map<String, *>>
                    changelogs = it.result.data!!["changelogs"] as Map<String, String>

                    binding.updateNotifContainer.post {
                        if (tags!!.getValue(ver)["type"].toString().toInt() == 5) {
                            //Force open
                            viewModel.changeDialogTo(5)
                        } else {
                            binding.updateNotifContainer.openFromRightScreenSide()
                        }
                    }
                }
            }
        }
    }

    private fun initNavigation(){
        //MakeBlurs
        val windowBackground = window.decorView.background
        val decorView = window.decorView
        val rootView = decorView.findViewById<View>(android.R.id.content) as ViewGroup
        binding.navblur.setupWith(rootView)
            .setFrameClearDrawable(windowBackground)
            .setBlurRadius(20f)

        //Listen taps
        binding.apply {
            homeClickListener.setOnClickListener(){
                if (currentPage != 1 && viewModel.currentDialog.value!! <= 0) openOtherPage(1)
            }
            communityClickListener.setOnClickListener(){
                if (currentPage != 2 && viewModel.currentDialog.value!! <= 0) openOtherPage(2)
            }
            otherClickListener.setOnClickListener(){
                if (currentPage != 3 && viewModel.currentDialog.value!! <= 0) openOtherPage(3)
            }
        }

        //Open pages
        supportFragmentManager.beginTransaction().replace(R.id.homePage, Home.newInstance()).commitNow()
        supportFragmentManager.beginTransaction().replace(R.id.storePage, Store.newInstance()).commitNow()
    }

    private fun openOtherPage(ind: Int){
        //Close animation
        val params = ChangeBounds()
        params.duration = 100.toLong()
        params.interpolator = DecelerateInterpolator()
        TransitionManager.beginDelayedTransition(binding.navbar, params)

        when (currentPage){
            1 -> {
                val homeClose = ConstraintSet(); homeClose.clone(this, R.layout.item_navbar_home_closed)
                //TransitionManager.beginDelayedTransition(home_button, params)
                homeClose.applyTo(binding.homeButton)
                binding.homeIcon.alpha = 0.4.toFloat()

                binding.homePage.visibility = View.GONE

            }
            2 -> {
                val communityClose = ConstraintSet(); communityClose.clone(this, R.layout.item_navbar_community_closed)
                //TransitionManager.beginDelayedTransition(community_button, params)
                communityClose.applyTo(binding.communityButton)
                binding.communityIcon.alpha = 0.4.toFloat()

                binding.storePage.visibility = View.GONE
            }
            3 -> {
                val otherClose = ConstraintSet(); otherClose.clone(this, R.layout.item_navbar_other_closed)
                //TransitionManager.beginDelayedTransition(other_button, params)
                otherClose.applyTo(binding.otherButton)
                binding.otherIcon.alpha = 0.4.toFloat()

                binding.otherPage.visibility = View.GONE
            }
        }
        //Open animation and display page
        when (ind){
            1 -> {
                currentPage = 1
                //Anim
                val homeOpen = ConstraintSet(); homeOpen.clone(this, R.layout.item_navbar_home_opened)
                //TransitionManager.beginDelayedTransition(home_button, params)
                homeOpen.applyTo(binding.homeButton)
                binding.homeIcon.alpha = 1.toFloat()
                //Open
                binding.homePage.visibility = View.VISIBLE
                binding.homePage.openFromCenter()
            }
            2 -> {
                currentPage = 2
                //Anim
                val communityOpen = ConstraintSet(); communityOpen.clone(this, R.layout.item_navbar_community_opened)
                //TransitionManager.beginDelayedTransition(community_button, params)
                communityOpen.applyTo(binding.communityButton)
                binding.communityIcon.alpha = 1.toFloat()
                //Open
                binding.storePage.visibility = View.VISIBLE
                binding.storePage.openFromCenter()
            }
            3 -> {
                currentPage = 3
                //Anim
                val otherOpen = ConstraintSet(); otherOpen.clone(this, R.layout.item_navbar_other_opened)
                //TransitionManager.beginDelayedTransition(other_button, params)
                otherOpen.applyTo(binding.otherButton)
                binding.otherIcon.alpha = 1.toFloat()
                //Open
                binding.otherPage.visibility = View.VISIBLE
                binding.otherPage.openFromCenter()
            }
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
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                validate = false
            }
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                validate = false
            }
        }
        return validate
    }
}