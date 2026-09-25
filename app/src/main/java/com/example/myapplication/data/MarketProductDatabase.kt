package com.example.myapplication.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MarketProductEntry::class, MarketProductFts::class],
    version = 1,
    exportSchema = false
)
abstract class MarketProductDatabase : RoomDatabase() {

    abstract fun marketProductDao(): MarketProductDao

    companion object {
        @Volatile
        private var INSTANCE: MarketProductDatabase? = null

        fun getInstance(context: Context): MarketProductDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MarketProductDatabase::class.java,
                    "market_products.db"
                )
                .createFromAsset("market_products.db")
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
