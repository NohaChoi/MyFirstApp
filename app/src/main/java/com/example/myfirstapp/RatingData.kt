package com.example.myfirstapp

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

// Add ": java.io.Serializable" to make this class transferable
data class Player(
    val rating: Double = 1500.0,
    val rd: Double = 350.0,
    val vol: Double = 0.06,
    val wins: Int = 0,
    val losses: Int = 0
) : java.io.Serializable

@Entity(tableName = "rated_image_table")
// Also add ": java.io.Serializable" here
data class RatedImage(
    @PrimaryKey
    val fileName: String,

    @Embedded
    val playerStats: Player
) : java.io.Serializable