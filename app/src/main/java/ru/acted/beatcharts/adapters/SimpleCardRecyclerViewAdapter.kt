package ru.acted.beatcharts.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import ru.acted.beatcharts.R
import ru.acted.beatcharts.utils.BeatChartsUtils
import ru.acted.beatcharts.viewModels.MainViewModel

class SimpleCardRecyclerViewAdapter(private val elementsList: MutableList<BeatChartsUtils.SimpleItem>, private val viewModel: MainViewModel?, private val cardContentId: Int): RecyclerView.Adapter<SimpleCardRecyclerViewAdapter.MyViewHolder>() {

    class MyViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        //Card itself
        val cardRoot: CardView = itemView.findViewById(R.id.simpleCard)

        //Title
        val titleIcon: ImageView = itemView.findViewById(R.id.simpleTitleIcon)
        val cardTitle: TextView = itemView.findViewById(R.id.simpleCardTitle)

        //Text
        val textIcon: ImageView = itemView.findViewById(R.id.simpleTextIcon)
        val cardText: TextView = itemView.findViewById(R.id.simpleCardText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.recyclerview_simplecard_item, parent, false)
        return MyViewHolder(itemView)
    }

    override fun getItemCount() = elementsList.size

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        elementsList[position].let {
            //Icons
            if (it.titleIcon != null) {
                holder.titleIcon.visibility = View.VISIBLE
                holder.titleIcon.setImageDrawable(it.titleIcon)
            } else holder.titleIcon.visibility = View.GONE
            if (it.textIcon != null) {
                holder.textIcon.visibility = View.VISIBLE
                holder.textIcon.setImageDrawable(it.textIcon)
            } else holder.textIcon.visibility = View.GONE

            //Texts
            holder.cardTitle.text = it.title
            holder.cardText.text = it.text

            //Card tap listener
            if (viewModel != null){
                when (cardContentId) {
                    1 -> { //This is stages list: change current value or remove it
                        holder.cardRoot.setOnClickListener {
                            viewModel.itemInteractionId.value = position
                            viewModel.pubContentInteraction.value = 1 //Edit section
                        }
                        holder.cardRoot.setOnLongClickListener {
                            viewModel.itemInteractionId.value = position
                            viewModel.pubContentInteraction.value = 2 //Delete section
                            true }
                    }
                    2 -> { //This is perfects list: edit on click or remove on hold
                        holder.cardRoot.setOnClickListener {
                            viewModel.itemInteractionId.value = position
                            viewModel.pubContentInteraction.value = 3 //Edit perfect
                        }
                        holder.cardRoot.setOnLongClickListener {
                            viewModel.itemInteractionId.value = position
                            viewModel.pubContentInteraction.value = 4 //Delete perfect
                            true
                        }
                    }
                    3 -> { //This is speeds list: edit on click or remove on hold
                        holder.cardRoot.setOnClickListener {
                            viewModel.itemInteractionId.value = position
                            viewModel.pubContentInteraction.value = 5 //Edit speed
                        }
                        holder.cardRoot.setOnLongClickListener {
                            viewModel.itemInteractionId.value = position
                            viewModel.pubContentInteraction.value = 6 //Delete speed
                            true
                        }
                    }
                }
            }
        }
    }
}