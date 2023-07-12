package ru.acted.beatcharts.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.cardview.widget.CardView
import ru.acted.beatcharts.R

class AvatarView(context: Context, attrs: AttributeSet): FrameLayout(context, attrs) {
    private val userIcon: ImageView
    private val userImage: ImageView
    private val card: CardView

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.view_avatar, this, true)

        userImage = view.findViewById(R.id.userImage)
        userIcon = view.findViewById(R.id.userIcon)
        card = view.findViewById(R.id.avatarCard)

        //Set avatar color from attribute
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AvatarView)
        card.setCardBackgroundColor(typedArray.getColor(R.styleable.AvatarView_avatarBackgroundColor, resources.getColor(R.color.background_level_a)))
        typedArray.recycle()
    }

    fun setImage(bitmap: Bitmap) {
        userIcon.visibility = View.GONE
        userImage.visibility = View.VISIBLE
        userImage.setImageBitmap(bitmap)
    }
}