package ru.acted.beatcharts.adapters

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import ru.acted.beatcharts.R
import ru.acted.beatcharts.dataProcessors.SongManager
import ru.acted.beatcharts.types.Song
import ru.acted.beatcharts.viewModels.MainViewModel

class SongsRecyclerViewAdapter(private val song: List<Song>, private val reso: Resources, private val context: Context, private val lifecycleOwner: LifecycleOwner, private val viewModel: MainViewModel): RecyclerView.Adapter<SongsRecyclerViewAdapter.MyViewHolder>() {

    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val songRoot: CardView = itemView.findViewById(R.id.songRoot)
        val charter: TextView = itemView.findViewById(R.id.charter)
        val songTitle: TextView = itemView.findViewById(R.id.songTitle)
        val songArtist: TextView = itemView.findViewById(R.id.songArtist)
        val difficultyHolder: LinearLayout = itemView.findViewById(R.id.difficultyHolder)
        val songCardColor: LinearLayout = itemView.findViewById(R.id.songCardColor)
        val difficultyIcon: ImageView = itemView.findViewById(R.id.difficultyIcon)
        val songArtwork: ImageView = itemView.findViewById(R.id.songArtwork)
        val songArtGradient: LinearLayout = itemView.findViewById(R.id.songArtGradient)

        //Buttons
        val textViewH: TextView = itemView.findViewById(R.id.textViewH)
        val imageViewH: ImageView = itemView.findViewById(R.id.imageViewH)

        //Button holders
        val inCommunityButton: CardView = itemView.findViewById(R.id.inCommunityButton)
        val hideInGameButton: CardView = itemView.findViewById(R.id.hideInGameButton)
        val deleteButton: CardView = itemView.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.recyclerview_home_song_item, parent, false)
        return MyViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val currentSong = song[position]

        //Bind song info
        holder.charter.text = if (currentSong.charter !== "") "${reso.getString(R.string.charted_by)} ${currentSong.charter} · ID ${currentSong.id}" else "${reso.getString(
            R.string.unknown_charter
        )} · ID ${currentSong.id}"
        holder.songTitle.text = currentSong.title
        holder.songArtist.text = currentSong.artist
        when (currentSong.diff){
            "1" -> {
                //Extreme
                holder.difficultyHolder.visibility = View.VISIBLE
                holder.difficultyIcon.setImageResource(R.drawable.extreme_difficulty_logo)
                holder.difficultyHolder.setBackgroundDrawable(reso.getDrawable(R.drawable.card_colored_extreme))
            }
            "3" -> {
                //Hard
                holder.difficultyHolder.visibility = View.VISIBLE
                holder.difficultyIcon.setImageResource(R.drawable.hard_difficulty_logo)
                holder.difficultyHolder.setBackgroundDrawable(reso.getDrawable(R.drawable.card_colored_hard))
            }
            "4" -> {
                //Normal
                holder.difficultyHolder.visibility = View.GONE
            }
        }

        //Bind art and colors
        if (currentSong.baseColor !== ""){
            holder.songCardColor.setBackgroundColor(currentSong.baseColor.toInt())

            if (currentSong.isColorDark){
                holder.charter.setTextColor(reso.getColor(R.color.white))
                holder.songTitle.setTextColor(reso.getColor(R.color.white))
                holder.songArtist.setTextColor(reso.getColor(R.color.white))
            } else {
                holder.charter.setTextColor(reso.getColor(R.color.black))
                holder.songTitle.setTextColor(reso.getColor(R.color.black))
                holder.songArtist.setTextColor(reso.getColor(R.color.black))
            }

            holder.songArtwork.setImageBitmap(currentSong.artBitmap )

            //Bind gradient
            val gradientDrawable = GradientDrawable().apply {
                colors = intArrayOf(
                    currentSong.baseColor.toInt(),
                    Color.parseColor("#00FFFFFF")
                )
                orientation = GradientDrawable.Orientation.RIGHT_LEFT //BR_TL
                gradientType = GradientDrawable.LINEAR_GRADIENT
                shape = GradientDrawable.RECTANGLE
            }
            holder.songArtGradient.setBackgroundDrawable(gradientDrawable)
        }

        //Bind buttons
        if (currentSong.isHidden){
            holder.textViewH.setText(R.string.show_in_game)
            holder.imageViewH.setImageResource(R.drawable.eye)
        }

        //Bind listeners
        if (viewModel.offlineMode.value!!) {
            holder.inCommunityButton.visibility = View.GONE
        } else holder.inCommunityButton.setOnClickListener {
        }
        holder.hideInGameButton.setOnClickListener(){
            SongManager().hideShowSong(context, currentSong, viewModel)
        }
        holder.deleteButton.setOnClickListener(){
            val location = IntArray(2)
            holder.songRoot.getLocationOnScreen(location)
            currentSong.cardYCoordinate = location[1]
            viewModel.setSong(currentSong)
            viewModel.changeDialogTo(1)
            holder.songRoot.visibility = View.INVISIBLE
        }
    }

    override fun getItemCount() = song.size

    override fun onViewAttachedToWindow(holder: MyViewHolder) {
        super.onViewAttachedToWindow(holder)
        viewModel.currentDialog.observe(lifecycleOwner) {
            if (it == 0) {
                Handler().postDelayed({
                    holder.songRoot.visibility = View.VISIBLE
                }, 300)
            }
        }
    }

}