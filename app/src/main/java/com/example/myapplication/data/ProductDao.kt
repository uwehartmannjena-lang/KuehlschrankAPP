package com.example.myapplication.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProductDao {

    @Query("SELECT * FROM market_products WHERE receiptPattern = :pattern LIMIT 1")
    suspend fun findByReceiptPattern(pattern: String): ProductEntity?

    @Query("SELECT * FROM market_products WHERE cleanName = :cleanName LIMIT 1")
    suspend fun findByCleanName(cleanName: String): ProductEntity?

    @Query("SELECT * FROM market_products WHERE receiptPattern = :pattern AND market = :market LIMIT 1")
    suspend fun findByReceiptPatternAndMarket(pattern: String, market: String): ProductEntity?

    @Query("SELECT * FROM market_products WHERE cleanName = :cleanName AND market = :market LIMIT 1")
    suspend fun findByCleanNameAndMarket(cleanName: String, market: String): ProductEntity?

    @Query("SELECT * FROM market_products WHERE cleanName LIKE '%' || :query || '%' OR receiptPattern LIKE '%' || :query || '%' ORDER BY purchase_count DESC LIMIT :limit")
    suspend fun searchProductsFast(query: String, limit: Int = 50): List<ProductEntity>

    @Query("SELECT COUNT(*) FROM market_products")
    suspend fun getProductCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)
}
