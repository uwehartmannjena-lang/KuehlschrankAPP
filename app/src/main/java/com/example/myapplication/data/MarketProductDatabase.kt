package com.example.myapplication.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MarketProductEntry::class, MarketProductFts::class],
    version = 2,
    exportSchema = false
)
abstract class MarketProductDatabase : RoomDatabase() {

    abstract fun marketProductDao(): MarketProductDao

    companion object {
        @Volatile
        private var INSTANCE: MarketProductDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE market_dictionary ADD COLUMN market_id TEXT")
                db.execSQL("ALTER TABLE market_dictionary ADD COLUMN purchase_count INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE market_dictionary ADD COLUMN synonyms TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_market_id` ON `market_dictionary` (`market_id`)")
                db.execSQL("DROP TABLE IF EXISTS market_dictionary_fts")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `market_dictionary_fts` USING FTS4(`receiptPattern` TEXT NOT NULL, `cleanName` TEXT NOT NULL, `category` TEXT NOT NULL, `synonyms` TEXT, content=`market_dictionary`)")
                db.execSQL("INSERT INTO market_dictionary_fts(market_dictionary_fts, `receiptPattern`, `cleanName`, `category`, `synonyms`) SELECT 'rebuild', `receiptPattern`, `cleanName`, `category`, `synonyms` FROM market_dictionary")
            }
        }

        fun getInstance(context: Context): MarketProductDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MarketProductDatabase::class.java,
                    "market_dict.db"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
