package com.example.myapplication.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FridgeItemDao {
    // Haushalte
    @Query("SELECT * FROM households")
    fun getAllHouseholds(): Flow<List<Household>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHousehold(household: Household)
    @Query("SELECT * FROM household_logs WHERE householdId = :householdId ORDER BY timestamp DESC LIMIT 50")
    fun getLogsForHousehold(householdId: String): Flow<List<HouseholdLog>>
    @Insert
    suspend fun insertLog(log: HouseholdLog)

    // Artikel (gefiltert nach Haushalt)
    @Query("SELECT * FROM fridge_items WHERE householdId = :householdId ORDER BY item_name ASC")
    fun getFridgeItemsByHousehold(householdId: String): Flow<List<FridgeItem>>

    @Query("SELECT * FROM fridge_items ORDER BY item_name ASC")
    fun getAllFridgeItems(): Flow<List<FridgeItem>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: FridgeItem)

    @Update
    suspend fun updateItem(item: FridgeItem)

    @Delete
    suspend fun deleteItem(item: FridgeItem)

    @Query("SELECT * FROM wasted_items ORDER BY wasteDate DESC")
    fun getAllWastedItems(): Flow<List<WastedItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWastedItem(item: WastedItem)

    @Query("SELECT * FROM consumed_items ORDER BY consumeDate DESC")
    fun getAllConsumedItems(): Flow<List<ConsumedItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsumedItem(item: ConsumedItem)
    
    @Query("DELETE FROM fridge_items")
    suspend fun deleteAll()

    @Query("SELECT * FROM custom_units")
    fun getAllCustomUnits(): Flow<List<CustomUnit>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnit(unit: CustomUnit)
    @Delete
    suspend fun deleteUnit(unit: CustomUnit)

    @Query("SELECT * FROM favorite_recipes")
    fun getAllRecipes(): Flow<List<FavoriteRecipe>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: FavoriteRecipe)

    @Query("SELECT * FROM price_history WHERE itemId = :itemId ORDER BY date DESC")
    fun getPriceHistory(itemId: String): Flow<List<PriceRecord>>

    @Query("SELECT * FROM price_history WHERE itemName = :name ORDER BY date DESC")
    suspend fun getHistoryByName(name: String): List<PriceRecord>

    @Insert
    suspend fun insertPriceRecord(record: PriceRecord)

    @Query("SELECT * FROM recent_barcodes ORDER BY timestamp DESC LIMIT 10")
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
    @Delete
    suspend fun deleteShoppingItem(item: ShoppingItem)
    @Query("DELETE FROM shopping_list")
    suspend fun clearShoppingList()

    @Query("SELECT * FROM meal_plans ORDER BY date ASC")
    fun getAllMealPlans(): Flow<List<MealPlan>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealPlan(plan: MealPlan)
    @Query("DELETE FROM meal_plans WHERE id = :id")
    suspend fun deleteMealPlan(id: String)

    @Query("SELECT * FROM consumption_patterns")
    fun getAllConsumptionPatterns(): Flow<List<ConsumptionPattern>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsumptionPattern(pattern: ConsumptionPattern)
    @Query("SELECT * FROM consumption_patterns WHERE itemName = :name")
    suspend fun getConsumptionPattern(name: String): ConsumptionPattern?

    @Query("SELECT * FROM budget_config WHERE monthYear = :monthYear")
    fun getBudget(monthYear: String): Flow<BudgetConfig?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetConfig)

    @Query("SELECT importHash FROM fridge_items WHERE importHash IS NOT NULL")
    fun getImportHashes(): Flow<List<String>>

    @Query("SELECT * FROM fridge_items WHERE importHash = :hash")
    suspend fun getItemsByImportHash(hash: String): List<FridgeItem>

    @Query("SELECT imageUrl FROM fridge_items WHERE item_name = :name AND imageUrl IS NOT NULL LIMIT 1")
    suspend fun findImageUrlByName(name: String): String?

    // Beleg-Archiv
    @Query("SELECT * FROM receipts ORDER BY date DESC")
    fun getAllReceipts(): Flow<List<Receipt>>

    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun getReceiptById(id: String): Receipt?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: Receipt)

    @Query("SELECT * FROM fridge_items WHERE receiptId = :receiptId")
    suspend fun getItemsForReceipt(receiptId: String): List<FridgeItem>

    @Query("SELECT * FROM receipts WHERE supermarket LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%'")
    fun searchReceipts(query: String): Flow<List<Receipt>>

    // Caching für Open Food Facts
    @Query("SELECT * FROM cached_products WHERE barcode = :barcode")
    suspend fun getCachedProduct(barcode: String): CachedProduct?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedProduct(product: CachedProduct)
}
