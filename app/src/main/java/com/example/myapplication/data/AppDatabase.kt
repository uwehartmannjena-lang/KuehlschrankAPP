package com.example.myapplication.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [
    FridgeItem::class, WastedItem::class, ConsumedItem::class, CustomUnit::class, 
    FavoriteRecipe::class, PriceRecord::class, RecentBarcode::class, 
    LoyaltyCard::class, LearningEntry::class, ShoppingItem::class, 
    MealPlan::class, ConsumptionPattern::class,
    Household::class, HouseholdLog::class, UserProfile::class,
    BudgetConfig::class, Receipt::class, CachedProduct::class
], version = 26, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fridgeItemDao(): FridgeItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fridge_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
