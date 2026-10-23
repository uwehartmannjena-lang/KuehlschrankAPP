package com.example.myapplication.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProductDao {

    @Query("SELECT * FROM market_products WHERE receiptPattern = :pattern LIMIT 1")
    suspend fun findByReceiptPattern(pattern: String): ProductEntity?

    @Query("""
        SELECT * FROM market_products 
        WHERE cleanName = :cleanName 
           OR cleanName LIKE '%' || :cleanName || '%' 
           OR :cleanName LIKE '%' || cleanName || '%'
           OR receiptPattern LIKE '%' || :cleanName || '%'
        ORDER BY 
           CASE 
               WHEN cleanName = :cleanName THEN 1 
               WHEN cleanName LIKE :cleanName || '%' THEN 2 
               WHEN :cleanName LIKE cleanName || '%' THEN 3
               ELSE 4 
           END, 
           purchase_count DESC 
        LIMIT 1
    """)
    suspend fun findByCleanName(cleanName: String): ProductEntity?

    @Query("SELECT * FROM market_products WHERE receiptPattern = :pattern AND market = :market LIMIT 1")
    suspend fun findByReceiptPatternAndMarket(pattern: String, market: String): ProductEntity?

    @Query("""
        SELECT * FROM market_products 
        WHERE (cleanName = :cleanName 
           OR cleanName LIKE '%' || :cleanName || '%' 
           OR :cleanName LIKE '%' || cleanName || '%'
           OR receiptPattern LIKE '%' || :cleanName || '%')
          AND market = :market
        ORDER BY 
           CASE 
               WHEN cleanName = :cleanName THEN 1 
               WHEN cleanName LIKE :cleanName || '%' THEN 2 
               ELSE 3 
           END, 
           purchase_count DESC 
        LIMIT 1
    """)
    suspend fun findByCleanNameAndMarket(cleanName: String, market: String): ProductEntity?

    @Query("SELECT * FROM market_products WHERE cleanName LIKE '%' || :query || '%' OR receiptPattern LIKE '%' || :query || '%' ORDER BY purchase_count DESC LIMIT :limit")
    suspend fun searchProductsFast(query: String, limit: Int = 50): List<ProductEntity>

    @Query("SELECT COUNT(*) FROM market_products")
    suspend fun getProductCount(): Int

    @Query("""
        SELECT * FROM market_products 
        WHERE receiptPattern = :pattern 
           OR cleanName = :pattern 
           OR :pattern LIKE '%' || receiptPattern || '%' 
           OR :pattern LIKE '%' || cleanName || '%'
        ORDER BY 
           CASE 
               WHEN receiptPattern = :pattern THEN 1 
               WHEN cleanName = :pattern THEN 2 
               ELSE 3 
           END, 
           purchase_count DESC 
        LIMIT 1
    """)
    suspend fun matchOcrText(pattern: String): ProductEntity?

    @Query("""
        SELECT * FROM market_products 
        WHERE cleanName LIKE '%' || :query || '%' 
           OR receiptPattern LIKE '%' || :query || '%' 
        ORDER BY 
           CASE 
               WHEN cleanName LIKE :query || '%' THEN 1 
               WHEN receiptPattern LIKE :query || '%' THEN 2 
               ELSE 3 
           END, 
           purchase_count DESC 
        LIMIT :limit
    """)
    suspend fun searchAutocomplete(query: String, limit: Int = 10): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)
}
