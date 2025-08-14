package com.example.myfirstapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myfirstapp.databinding.RankingItemLayoutBinding
import kotlin.math.roundToInt

class RankingsAdapter(private val imageList: List<RatedImage>) : RecyclerView.Adapter<RankingsAdapter.RankingViewHolder>() {

    /**
     * This ViewHolder holds the views for a single row in our list.
     */
    inner class RankingViewHolder(val binding: RankingItemLayoutBinding) : RecyclerView.ViewHolder(binding.root)

    /**
     * Called by the RecyclerView when it needs to create a new row.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RankingViewHolder {
        val binding = RankingItemLayoutBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RankingViewHolder(binding)
    }

    /**
     * Returns the total number of items in our list.
     */
    override fun getItemCount(): Int {
        return imageList.size
    }

    /**
     * Called by the RecyclerView to display the data at a specific position.
     * This is where we connect our data to the TextViews in the layout.
     */
    override fun onBindViewHolder(holder: RankingViewHolder, position: Int) {
        val currentImage = imageList[position]

        holder.binding.rankTextView.text = "#${position + 1}"
        holder.binding.fileNameTextView.text = currentImage.fileName
        holder.binding.ratingTextView.text = "Rating: ${currentImage.playerStats.rating.roundToInt()}"
        holder.binding.winLossTextView.text = "W: ${currentImage.playerStats.wins} / L: ${currentImage.playerStats.losses}"
    }
}