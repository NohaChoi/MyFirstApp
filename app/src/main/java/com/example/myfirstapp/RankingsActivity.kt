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

        val receivedList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("imageListKey", java.util.ArrayList::class.java) as? ArrayList<RatedImage>
        } else {
            @Suppress("DEPRECATION", "UNCHECKED_CAST")
            intent.getSerializableExtra("imageListKey") as? ArrayList<RatedImage>
        }

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