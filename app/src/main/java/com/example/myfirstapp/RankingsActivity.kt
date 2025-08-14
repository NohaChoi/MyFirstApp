package com.example.myfirstapp

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myfirstapp.databinding.ActivityRankingsBinding

class RankingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRankingsBinding
    private var imageList = mutableListOf<RatedImage>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRankingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // FIXED: This block is updated to correctly cast the incoming data to the right type.
        val receivedList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The modern, type-safe way for new Android versions.
            intent.getSerializableExtra("imageListKey", ArrayList::class.java) as? ArrayList<RatedImage>
        } else {
            // The older, deprecated way. We suppress the warning and perform a safe cast.
            @Suppress("DEPRECATION", "UNCHECKED_CAST")
            intent.getSerializableExtra("imageListKey") as? ArrayList<RatedImage>
        }

        // Sort the list by rating (highest first) and set up the RecyclerView
        if (receivedList != null) {
            imageList = receivedList.sortedByDescending { it.playerStats.rating }.toMutableList()
            setupRecyclerView()
        }
    }

    private fun setupRecyclerView() {
        val rankingsAdapter = RankingsAdapter(imageList)
        binding.rankingsRecyclerView.apply {
            adapter = rankingsAdapter
            layoutManager = LinearLayoutManager(this@RankingsActivity)
        }
    }
}