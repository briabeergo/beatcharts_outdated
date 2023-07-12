package ru.acted.beatcharts.adapters

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.recyclerview.widget.RecyclerView
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException
import com.squareup.picasso.Picasso
import ru.acted.beatcharts.R
import ru.acted.beatcharts.types.UserData
import java.io.IOException
import java.net.URL


class UserBadgesAdapter(private val badges: List<UserData.Badge>, private val userColor: Int, private val res: Resources): RecyclerView.Adapter<UserBadgesAdapter.MyViewHolder>() {

    class MyViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val badgeText: TextView = itemView.findViewById(R.id.badgeText)
        val badgeIcon: ImageView = itemView.findViewById(R.id.badgeIcon)
        val bgCard: CardView = itemView.findViewById(R.id.bgCard)
        val bgAngle: ConstraintLayout = itemView.findViewById(R.id.bgAngle)

        val angle1: LinearLayout = itemView.findViewById(R.id.linearLayout32)
        val angle2: LinearLayout = itemView.findViewById(R.id.linearLayout33)
        val angle3: LinearLayout = itemView.findViewById(R.id.linearLayout34)

        val additionalMargins: Group = itemView.findViewById(R.id.additionalMargins)
        val additionalEndOffset: Space = itemView.findViewById(R.id.badgeAdditionalEndOffset)
    }

    private fun changeAngleColor(holder: MyViewHolder, color: Int) {
        holder.apply {
            angle1.setBackgroundColor(color)
            angle2.setBackgroundColor(color)
            angle3.setBackgroundColor(color)
        }
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val badge = badges[position]

        holder.badgeText.text = badge.text

        if (badge.iconUrl == "") holder.badgeIcon.visibility = View.GONE
        else {
            val stream = URL(badge.iconUrl).openStream()
            try {
                val svg = SVG.getFromInputStream(stream)
                val picture = svg.renderToPicture()
                val bitmap = Bitmap.createBitmap(picture.width, picture.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawPicture(picture)
                holder.badgeIcon.setImageBitmap(bitmap)
            } catch (e: SVGParseException) {
                e.printStackTrace()
            } finally {
                try {
                    stream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }

        holder.apply {
            //Add additional space
            if (position + 1 <= badges.size - 1) {
                badges[position+1].backgroundValue.let {  badgeBackground ->
                    if (badgeBackground != "" && badgeBackground != "card")
                        additionalEndOffset.visibility = View.GONE
                    else
                        additionalEndOffset.visibility = View.VISIBLE
                }
            } else additionalEndOffset.visibility = View.VISIBLE

            additionalMargins.visibility = View.VISIBLE
            badgeText.alpha = 1f
            badgeIcon.alpha = 1f

            when (badge.backgroundValue) {
                "" -> {
                    bgCard.visibility = View.GONE
                    bgAngle.visibility = View.GONE
                    additionalMargins.visibility = View.GONE
                    badgeText.alpha = 0.8f
                    badgeIcon.alpha = 0.8f
                }
                "card" -> {
                    bgCard.visibility = View.VISIBLE
                    bgAngle.visibility = View.GONE
                    additionalMargins.visibility = View.GONE
                    badgeText.alpha = 0.8f
                    badgeIcon.alpha = 0.8f
                }
                "user" -> {
                    bgCard.visibility = View.GONE
                    bgAngle.visibility = View.VISIBLE
                    badgeText.setTextColor(res.getColor(R.color.white))
                    badgeIcon.setColorFilter(res.getColor(R.color.white))
                    changeAngleColor(holder, userColor)
                }
                else -> {
                    bgCard.visibility = View.GONE
                    bgAngle.visibility = View.VISIBLE
                    try {
                        val color =
                            if (badge.backgroundValue.length > 2)
                                Color.parseColor(badge.backgroundValue) else userColor

                        changeAngleColor(holder, color)
                    } catch (_: Exception) {

                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.recyclerview_badge_item, parent, false)
        return UserBadgesAdapter.MyViewHolder(itemView)
    }

    override fun getItemCount() = badges.size

}