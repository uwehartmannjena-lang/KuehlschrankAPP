package com.example.myapplication.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FridgeItemDao {

    @Query("SELECT * FROM fridge_items ORDER BY expiry_date ASC")
    fun getAllFridgeItems(): Flow<List<FridgeItem>>

    @Query("SELECT * FROM fridge_items ORDER BY expiry_date ASC")
    fun getAllItems(): Flow<List<FridgeItem>>

    @Query("SELECT * FROM fridge_items WHERE importHash = :hash")
    suspend fun getItemsByImportHash(hash: String): List<FridgeItem>

    @Query("SELECT importHash FROM fridge_items WHERE importHash IS NOT NULL")
    suspend fun getImportHashes(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: FridgeItem)

    @Update
    suspend fun updateItem(item: FridgeItem)

    @Delete
    suspend fun deleteItem(item: FridgeItem)

    @Query("DELETE FROM fridge_items")
    suspend fun deleteAll()

    @Query("SELECT * FROM fridge_items WHERE id = :id")
    suspend fun getItemById(id: String): FridgeItem?

    @Query("SELECT * FROM wasted_items ORDER BY wasteDate DESC")
    fun getAllWastedItems(): Flow<List<WastedItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWastedItem(item: WastedItem)

    @Query("SELECT * FROM consumed_items ORDER BY consumeDate DESC")
    fun getAllConsumedItems(): Flow<List<ConsumedItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsumedItem(item: ConsumedItem)

    @Query("SELECT * FROM custom_units")
    fun getAllCustomUnits(): Flow<List<CustomUnit>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCustomUnit(unit: CustomUnit)

    @Query("SELECT * FROM favorite_recipes")
    fun getAllFavoriteRecipes(): Flow<List<FavoriteRecipe>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteRecipe(recipe: FavoriteRecipe)

    @Delete
    suspend fun deleteFavoriteRecipe(recipe: FavoriteRecipe)

    @Query("SELECT * FROM price_history ORDER BY date DESC")
    fun getAllPriceHistory(): Flow<List<PriceRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchaseHistoryEntry(entry: PurchaseHistoryEntry)

    @Query("SELECT * FROM purchase_history WHERE LOWER(productName) = LOWER(:productName) ORDER BY timestamp DESC")
    fun getPurchaseHistoryForProduct(productName: String): Flow<List<PurchaseHistoryEntry>>

    @Query("SELECT * FROM purchase_history WHERE LOWER(productName) = LOWER(:productName) ORDER BY timestamp DESC")
    suspend fun getPurchaseHistoryForProductSync(productName: String): List<PurchaseHistoryEntry>

    @Query("SELECT * FROM price_history WHERE LOWER(itemName) LIKE '%' || LOWER(:name) || '%' ORDER BY date DESC")
    suspend fun getHistoryByName(name: String): List<PriceRecord>

    @Query("SELECT * FROM price_history WHERE LOWER(itemName) LIKE '%' || LOWER(:name) || '%' ORDER BY date DESC")
    fun getPriceHistory(name: String): Flow<List<PriceRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceRecord(record: PriceRecord)

    @Query("SELECT * FROM recent_barcodes ORDER BY timestamp DESC LIMIT 20")
    fun getRecentBarcodes(): Flow<List<RecentBarcode>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentBarcode(barcode: RecentBarcode)

    @Query("SELECT * FROM loyalty_cards")
    fun getAllLoyaltyCards(): Flow<List<LoyaltyCard>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoyaltyCard(card: LoyaltyCard)

    @Delete
    suspend fun deleteLoyaltyCard(card: LoyaltyCard)

    @Query("SELECT * FROM learning_data WHERE rawName = :raw")
    suspend fun getLearningEntry(raw: String): LearningEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLearningEntry(entry: LearningEntry)

    @Query("SELECT * FROM UserCorrections WHERE rawReceiptText = :raw")
    suspend fun getUserCorrection(raw: String): UserCorrection?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserCorrection(correction: UserCorrection)

    @Query("SELECT * FROM UserCorrections")
    fun getAllUserCorrections(): Flow<List<UserCorrection>>

    @Query("SELECT * FROM UserCorrections")
    suspend fun getAllUserCorrectionsSync(): List<UserCorrection>

    @Query("SELECT * FROM UserLearnedCorrections")
    suspend fun getAllUserLearnedCorrectionsSync(): List<UserLearnedCorrection>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserLearnedCorrection(correction: UserLearnedCorrection)

    @Query("SELECT * FROM learning_data")
    fun getAllLearningData(): Flow<List<LearningEntry>>

    @Query("SELECT * FROM learning_data")
    suspend fun getAllLearningDataSync(): List<LearningEntry>

    @Query("DELETE FROM learning_data WHERE rawName = :raw")
    suspend fun deleteLearningEntryByRawName(raw: String)

    @Query("SELECT * FROM shopping_list ORDER BY name ASC")
    fun getAllShoppingItems(): Flow<List<ShoppingItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShoppingItem(item: ShoppingItem)

    @Update
    suspend fun updateShoppingItem(item: ShoppingItem)

    @Delete
    suspend fun deleteShoppingItem(item: ShoppingItem)

    @Query("DELETE FROM shopping_list WHERE isChecked = 1")
    suspend fun deleteCheckedShoppingItems()

    @Query("SELECT * FROM budget_config WHERE monthYear = :monthYear")
    suspend fun getBudgetConfig(monthYear: String): BudgetConfig?

    @Query("SELECT * FROM budget_config WHERE monthYear = :monthYear")
    fun getBudget(monthYear: String): Flow<BudgetConfig?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setBudgetConfig(config: BudgetConfig)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(config: BudgetConfig)

    @Query("SELECT * FROM receipts ORDER BY date DESC")
    fun getAllReceipts(): Flow<List<Receipt>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: Receipt)

    @Delete
    suspend fun deleteReceipt(receipt: Receipt)

    @Query("SELECT * FROM cached_products WHERE barcode = :barcode")
    suspend fun getCachedProduct(barcode: String): CachedProduct?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedProduct(product: CachedProduct)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarketProducts(entries: List<MarketProductEntry>)

    @Query("SELECT * FROM market_dictionary WHERE market = :market OR market = 'general'")
    suspend fun getAllMarketProductsForMarket(market: String): List<MarketProductEntry>

    @Query("SELECT COUNT(*) FROM market_dictionary")
    suspend fun getMarketProductCount(): Int

    @Query("SELECT * FROM meal_plans ORDER BY date ASC")
    fun getAllMealPlans(): Flow<List<MealPlan>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealPlan(plan: MealPlan)

    @Query("DELETE FROM meal_plans WHERE id = :id")
    suspend fun deleteMealPlan(id: String)

    @Query("SELECT * FROM consumption_patterns")
    fun getAllConsumptionPatterns(): Flow<List<ConsumptionPattern>>

    @Query("SELECT * FROM consumption_patterns WHERE itemName = :itemName")
    suspend fun getConsumptionPattern(itemName: String): ConsumptionPattern?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsumptionPattern(pattern: ConsumptionPattern)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHousehold(household: Household)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: HouseholdLog)
}
