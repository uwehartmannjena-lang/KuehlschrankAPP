package com.example.myapplication.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "wasted_items")
data class WastedItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: Int,
    val unit: String,
    val category: String,
    val price: Double = 0.0,
    val wasteDate: Long = System.currentTimeMillis(),
    val reason: String = "Abgelaufen",
    val brand: String = "", // Feature 5: Marke im Abfall tracken
    val carbonFootprint: Double = 0.0 // NEU: CO2-Auswirkung
)

@Entity(tableName = "consumed_items") // Feature 6: Verbrauchs-Historie
data class ConsumedItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: Int,
    val unit: String,
    val category: String,
    val kcal: Int = 0, // Feature 7: Kalorienverbrauch-Tracking
    val consumeDate: Long = System.currentTimeMillis()
)
