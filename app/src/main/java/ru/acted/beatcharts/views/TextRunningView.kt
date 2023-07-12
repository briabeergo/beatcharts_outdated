package ru.acted.beatcharts.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.CountDownTimer
import android.util.AttributeSet
import android.view.View
import com.google.firestore.v1.StructuredAggregationQuery.Aggregation.Count
import ru.acted.beatcharts.R

class TextRunningView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var text = ""
    private var speed = 1
    private var direction = DIRECTION_LEFT

    private var paint = Paint().apply {
        isAntiAlias = true
        typeface =  Typeface.createFromAsset(context.assets, "googlesans_regular.ttf")
        this.textSize = textSize
        color = resources.getColor(R.color.foreground_active)
    }

    private var startX = 0f
    private var startY = 15f
    private var offset = 0f
    private var textSize = 10f

    init {
        setWillNotDraw(false)
        val a = context.obtainStyledAttributes(attrs, R.styleable.TextRunningView)
        text = a.getString(R.styleable.TextRunningView_text) ?: ""
        speed = a.getInt(R.styleable.TextRunningView_speed, 1)
        direction = a.getInt(R.styleable.TextRunningView_direction, DIRECTION_LEFT)
        textSize = a.getDimensionPixelSize(R.styleable.TextRunningView_textSize, 10).toFloat()
        paint.textSize = textSize

        a.recycle()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        if (text.isNotBlank() && canvas != null) {
            val textWidth = paint.measureText(text)
            var x = if (direction == DIRECTION_RIGHT) width - startX - offset else startX + offset

            when (direction) {
                DIRECTION_LEFT -> {
                    while (x <= width) {
                        canvas.drawText(text, x, textSize, paint)
                        x += textWidth
                    }

                    x -= textWidth
                }
                DIRECTION_RIGHT -> {
                    while (x >= 0) {
                        canvas.drawText(text, x, textSize, paint)
                        x -= textWidth
                    }
                    canvas.drawText(text, x, textSize, paint)
                    x -= textWidth
                }
            }


            if (direction == DIRECTION_LEFT && startX + textWidth < 0f) {
                startX = x
                offset = 0f
            } else if (direction == DIRECTION_RIGHT && startX > width) {
                startX = 0 - textWidth
                offset = 0f
            } else {
                invalidate()
            }
        }
    }

    private val timerMove: CountDownTimer = object: CountDownTimer(5000, 10) {
        override fun onTick(millisUntilEnd: Long) {
            offset -= speed
        }

        override fun onFinish() {
            restartMoveTimer()
        }
    }.start()

    private fun restartMoveTimer() {
        timerMove.start()
    }

    fun setText(text: String) {
        this.text = text
        val textWidth = paint.measureText(text)
        startX = if (direction == DIRECTION_LEFT) width.toFloat() else -textWidth
        startY = 0f
        offset = 0f
        invalidate()
    }

    fun setSpeed(speed: Int) {
        this.speed = speed
    }

    fun setDirection(direction: Int) {
        this.direction = direction
    }

    companion object {
        const val DIRECTION_LEFT = 0
        const val DIRECTION_RIGHT = 1
    }
}