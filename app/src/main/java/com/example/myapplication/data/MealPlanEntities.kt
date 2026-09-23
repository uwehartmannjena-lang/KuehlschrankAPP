package com.example.myapplication.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "meal_plans")
data class MealPlan(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: Long, // Timestamp für den Tag
    val recipeTitle: String,
    val recipeUrl: String? = null,
    val mealType: String = "Abendessen" // Frühstück, Mittag, Abend
)

@Entity(tableName = "consumption_patterns")
data class ConsumptionPattern(
    @PrimaryKey val itemName: String,
    val totalConsumed: Double = 0.0,
    val averageIntervalDays: Double = 0.0,
    val lastConsumedTimestamp: Long = System.currentTimeMillis(),
    val predictedNextNeed: Long = 0L,
    val unit: String = "Stk."
)
