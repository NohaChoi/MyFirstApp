package com.example.myfirstapp

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface RatedImageDao {
    @Upsert
    suspend fun upsertImage(image: RatedImage)

    @Query("SELECT * FROM rated_image_table WHERE fileName = :fileName")
    suspend fun getImage(fileName: String): RatedImage?
}