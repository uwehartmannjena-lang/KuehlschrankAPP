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
    BudgetConfig::class, Receipt::class, CachedProduct::class, UserCorrection::class, UserLearnedCorrection::class,
    MarketProductEntry::class, MarketProductFts::class,
    PurchaseHistoryEntry::class
], version = 35, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fridgeItemDao(): FridgeItemDao
    abstract fun userLearnedDao(): UserLearnedDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_33_34 = object : androidx.room.migration.Migration(33, 34) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shopping_list ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fridge_database"
                )
                .addMigrations(MIGRATION_33_34)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
