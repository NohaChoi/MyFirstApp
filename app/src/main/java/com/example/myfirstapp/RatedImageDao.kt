package com.example.myfirstapp

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/**
 * The DAO (Data Access Object). This interface defines all the functions
 * that we can use to interact with our database table.
 */
@Dao
interface RatedImageDao {

    // "Upsert" means "UPDATE or INSERT". It will insert new images or update existing ones.
    @Upsert
    suspend fun upsertImage(image: RatedImage)

    // This command gets all images from the table.
    @Query("SELECT * FROM rated_image_table")
    suspend fun getAllImages(): List<RatedImage>

    // This command gets a single image by its file name.
    @Query("SELECT * FROM rated_image_table WHERE fileName = :fileName")
    suspend fun getImage(fileName: String): RatedImage?
}