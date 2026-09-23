package com.example.myapplication

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Lebensmittel::class], version = 1, exportSchema = false)
abstract class KuehlschrankDatabase : RoomDatabase() {

    abstract fun lebensmittelDao(): LebensmittelDao

    companion object {
        @Volatile
        private var INSTANCE: KuehlschrankDatabase? = null

        fun getDatabase(context: Context): KuehlschrankDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KuehlschrankDatabase::class.java,
                    "kuehlschrank_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
