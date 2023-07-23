package ru.acted.beatcharts.views
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.util.AttributeSet
import android.view.View
import ru.acted.beatcharts.R
import java.util.Random
import kotlin.math.roundToInt

class MovingSquaresView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val squaresList: MutableList<Square> = mutableListOf()
    private val random = Random()

    //User-defined attributes
    private var speed: Float = 0f
    private var frequency: Long = 0
    private var squareSize: Int = 0
    private var moveRight: Boolean = true
    private var squareColor: Int = Color.RED

    private val paint: Paint = Paint().apply {
        color = Color.BLUE
        isAntiAlias = true
    }

    private val handler = Handler()

    private val runnable = object : Runnable {
        override fun run() {
            updateObjects()
            invalidate()

            // Планируем следующий запуск обновления через заданный интервал времени
            handler.postDelayed(this, 16) // 16 миллисекунд - для 60 кадров в секунду
        }
    }

    private fun updateObjects() {
        //Remove squares that have moved off the screen
        squaresList.removeAll { square ->
            if (moveRight) square.x > width + squareSize * square.xScale
            else square.x + squareSize * square.xScale < 0
        }

        //Create new squares periodically
        if (System.currentTimeMillis() % frequency == 0L) {
            val x = if (moveRight) -(squareSize * 4) else width
            val y = random.nextInt(height)
            val xScale = 2.0f + random.nextFloat() * 2.0f
            val yScale = 1.0f + random.nextFloat() * 1.5f
            val relativeSpeed = 1.0f * yScale

            squaresList.add(Square(x.toFloat(), y.toFloat(), xScale, yScale, relativeSpeed))
        }
    }

    init {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.MovingSquaresView,
            defStyleAttr,
            0
        ).apply {
            try {
                speed = getFloat(R.styleable.MovingSquaresView_sqrSpeed, 0f)
                frequency = getInt(R.styleable.MovingSquaresView_frequency, 0).toLong()
                squareSize = getDimensionPixelSize(R.styleable.MovingSquaresView_squareSize, 0)
                moveRight = getBoolean(R.styleable.MovingSquaresView_moveRight, true)
                squareColor = getColor(R.styleable.MovingSquaresView_squareColor, Color.RED)
            } finally {
                recycle()
            }
        }

        handler.post(runnable)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        //Set transparent background
        canvas.drawColor(Color.TRANSPARENT)

        for (square in squaresList) {
            //Update square position
            if (moveRight) square.x += speed * square.relativeSpeed
            else square.x -= speed * square.relativeSpeed

            //Calculate alpha based on the scale
            val alpha = 1.0f - (square.yScale - 1.0f)

            //Draw square with the specified color and transparency
            paint.color = squareColor
            paint.alpha = (alpha * 255).roundToInt()
            canvas.drawRect(
                square.x, square.y,
                square.x + squareSize * square.xScale,
                square.y + squareSize * square.yScale,
                paint
            )
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(runnable)
    }

    private data class Square(
        var x: Float,
        var y: Float,
        val xScale: Float,
        val yScale: Float,
        val relativeSpeed: Float
    )
}