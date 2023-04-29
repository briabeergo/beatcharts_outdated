package ru.acted.beatcharts.dataProcessors

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintSet
import ru.acted.beatcharts.R
import ru.acted.beatcharts.databinding.ActivityScoreHandlerBinding
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.closeToTop
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.openFromRight
import ru.acted.beatcharts.utils.BeatChartsUtils.Animations.Companion.openFromTop

class ScoreHandler : Activity() {
    @SuppressLint("SetTextI18n")

    private val openedLayout = ConstraintSet()
    private val closedLayout = ConstraintSet()

    private lateinit var binding: ActivityScoreHandlerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScoreHandlerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        finishAndRemoveTask() //TODO remove it later

        binding.feedbackTextview.text =
            "ID: ${intent.getStringExtra("id")}\n" +
            "TITLE: ${intent.getStringExtra("title")}\n" +
            "ARTIST: ${intent.getStringExtra("artist")}\n" +
            "SCORE: ${intent.getStringExtra("score")}"
        /*recievedId.text = "Id: ${intent.getStringExtra("id")}"
        recievedTitle.text = "Title: ${intent.getStringExtra("title")}"
        recievedArtist.text = "Artist: ${intent.getStringExtra("artist")}"
        recievedScore.text = "Score: ${intent.getStringExtra("score")}"*/

        /*if (intent.getStringExtra("handshake") == "ASK EXTERNAL :)") { //<--- This is a secret handshake code to make reverse engineering a bit harder. Ask External if you have reached an online services implementation point
            isOpenTrusted.text = "Open trusted :)"
            isOpenTrusted.setTextColor(Color.parseColor("#FF00B509"))
        }*/
        binding.button.setOnClickListener {
            closeThisWindow()
        }

        //Open a message with animation
        openedLayout.clone(this, R.layout.layout_overlay_opened)
        closedLayout.clone(binding.rootOfOverlay)

        binding.rootOfOverlay.post {

        }

        //TODO: implement other features
        showError(resources.getString(R.string.service_unavailable))
    }

    override fun onBackPressed() {
        super.onBackPressed()
        closeThisWindow()
    }

    private fun showError(text: String) {
        //TODO move notif animations to utils
        val animDurations = 600L
        val decelerateInterpolator = DecelerateInterpolator(3f)
        binding.apply {
            imageView29.setColorFilter(resources.getColor(R.color.background_level_a))
            notifyIcon.setColorFilter(resources.getColor(R.color.foreground_active))
            errorContainer.visibility = View.VISIBLE

            notifyText.text = text
            notifyContainer.post {
                //Animate container
                notifyContainer.translationX = notifyContainer.width.toFloat()
                notifyContainer.visibility = View.VISIBLE
                notifyContainer.animate().let {
                    it.duration = animDurations
                    it.interpolator = decelerateInterpolator
                    it.translationX(0f)
                    it.start()
                }

                greenLight.translationX = notifyContainer.width.toFloat()
                greenLight.rotation = 0f
                //Animate green thing
                Handler().postDelayed({
                    greenLight.animate().let {
                        it.duration = animDurations
                        it.interpolator = decelerateInterpolator
                        it.translationX(0f)
                        it.rotation(10f)
                        it.alpha(0.4f)
                        it.start()
                    }
                }, 100)

                //Animate check icon
                Handler().postDelayed({
                    notifyIcon.openFromRight()
                }, 200)

                //Animate text
                Handler().postDelayed({
                    notifyText.openFromRight()
                }, 300)

                //Animate title
                Handler().postDelayed({
                    scoreSyncTitle.visibility = View.VISIBLE
                    scoreSyncTitle.openFromTop()
                }, 400)

                //Hide everything with animation
                Handler().postDelayed({
                    scoreSyncTitle.closeToTop {}
                    notifyContainer.animate().let {
                        it.duration = animDurations
                        it.interpolator = decelerateInterpolator
                        it.translationX(notifyContainer.width.toFloat())
                        it.setListener(object: AnimatorListener {
                            override fun onAnimationStart(p0: Animator) {}
                            override fun onAnimationEnd(p0: Animator) {closeThisWindow()} //TODO uncomment this in production
                            override fun onAnimationCancel(p0: Animator) {}
                            override fun onAnimationRepeat(p0: Animator) {}
                        })
                        it.start()
                    }
                }, 4000)
            }
        }
    }

    private fun closeThisWindow(){
        val transition = AutoTransition()
        transition.interpolator = DecelerateInterpolator(2.0f)
        transition.duration = 450

        TransitionManager.beginDelayedTransition(binding.rootOfOverlay, transition)
        closedLayout.applyTo(binding.rootOfOverlay)

        Handler().postDelayed({
            finishAndRemoveTask()
        }, 450)
    }

}