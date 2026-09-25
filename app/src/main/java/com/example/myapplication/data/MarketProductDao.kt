package com.example.myapplication.data

import androidx.room.*

@Dao
interface MarketProductDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarketProducts(entries: List<MarketProductEntry>)

    @Query("SELECT COUNT(*) FROM market_dictionary")
    suspend fun getMarketProductCount(): Int

    @Query("""
        SELECT m.* FROM market_dictionary m
        JOIN market_dictionary_fts fts ON m.rowid = fts.docid
        WHERE market_dictionary_fts MATCH :query
        LIMIT 50
    """)
    suspend fun searchMarketProductsFts(query: String): List<MarketProductEntry>

    @Query("SELECT * FROM market_dictionary WHERE receiptPattern LIKE '%' || :query || '%' OR cleanName LIKE '%' || :query || '%' LIMIT 100")
    suspend fun searchMarketProductsLike(query: String): List<MarketProductEntry>

    @Query("SELECT * FROM market_dictionary WHERE market = :market OR market = 'general' LIMIT 20000")
    suspend fun getAllMarketProductsForMarket(market: String): List<MarketProductEntry>

    @Query("SELECT * FROM market_dictionary WHERE market = :market LIMIT 20000")
    suspend fun getProductsByMarket(market: String): List<MarketProductEntry>

    @Query("SELECT * FROM market_dictionary WHERE market = 'general' LIMIT 20000")
    suspend fun getGeneralProducts(): List<MarketProductEntry>

    @Query("SELECT * FROM market_dictionary LIMIT 20000")
    suspend fun getAllMarketProducts(): List<MarketProductEntry>

    @Query("SELECT * FROM market_dictionary WHERE LOWER(receiptPattern) = LOWER(:pattern) AND market = :market LIMIT 1")
    suspend fun findExactMatchByMarket(pattern: String, market: String): MarketProductEntry?

    @Query("SELECT * FROM market_dictionary WHERE LOWER(receiptPattern) = LOWER(:pattern) LIMIT 1")
    suspend fun findExactMatchGeneral(pattern: String): MarketProductEntry?
}
