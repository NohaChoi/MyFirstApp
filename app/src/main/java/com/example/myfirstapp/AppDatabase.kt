package com.example.myfirstapp

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RatedImage::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ratedImageDao(): RatedImageDao

    companion object {
        // This map will hold a different database instance for each folder.
        @Volatile
        private var INSTANCES = mutableMapOf<String, AppDatabase>()

        fun getDatabase(context: Context, dbName: String): AppDatabase {
            // Return the existing instance for this folder if it exists.
            return INSTANCES[dbName] ?: synchronized(this) {
                // If not, create a new database with the folder-specific name.
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    dbName
                ).build()
                INSTANCES[dbName] = instance
                instance
            }
        }
    }
}