package com.example.myapplication.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "custom_units")
data class CustomUnit(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val unitName: String
)

@Entity(tableName = "favorite_recipes")
data class FavoriteRecipe(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val ingredients: String,
    val instructions: String
)

@Entity(tableName = "price_history")
data class PriceRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val itemId: String,
    val itemName: String,
    val price: Double,
    val quantity: Int = 1,
    val unit: String = "Stk.",
    val barcode: String? = null,
    val date: Long = System.currentTimeMillis()
)

@Entity(tableName = "recent_barcodes")
data class RecentBarcode(
    @PrimaryKey val barcode: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "loyalty_cards")
data class LoyaltyCard(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val storeName: String,
    val cardNumber: String
)

@Entity(tableName = "learning_data")
data class LearningEntry(
    @PrimaryKey val rawName: String, 
    val correctedName: String,       
    val defaultStorageLocation: String? = null,
    val defaultCategory: String? = null,
    val defaultImageUrl: String? = null,
    val defaultBarcode: String? = null,
    val defaultUnit: String? = null,
    val usageCount: Int = 1
)

@Entity(tableName = "shopping_list")
data class ShoppingItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: Int = 1,
    val unit: String = "Stk.",
    val isChecked: Boolean = false,
    val priceEstimate: Double = 0.0,
    val store: String? = null
)

@Entity(tableName = "budget_config")
data class BudgetConfig(
    @PrimaryKey val monthYear: String, // z.B. "2023-10"
    val limit: Double
)
