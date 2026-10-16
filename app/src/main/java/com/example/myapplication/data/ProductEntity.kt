package com.example.myapplication.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "market_products",
    indices = [
        Index(value = ["receiptPattern"], name = "idx_market_products_receipt_pattern"),
        Index(value = ["cleanName"], name = "idx_market_products_clean_name")
    ]
)
data class ProductEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val cleanName: String,
    val receiptPattern: String,
    val category: String = "SONSTIGES",
    val defaultStorage: String = "Kühlschrank",
    val defaultShelfLifeDays: Int = 7,
    val market: String = "general",
    @ColumnInfo(name = "market_id")
    val marketId: String? = null,
    @ColumnInfo(name = "purchase_count")
    val purchaseCount: Int = 0,
    val synonyms: String? = null
)
