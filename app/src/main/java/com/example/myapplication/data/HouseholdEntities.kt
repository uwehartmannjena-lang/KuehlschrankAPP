package com.example.myapplication.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "households")
data class Household(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: Int,
    val isCloudSynced: Boolean = false,
    val shareToken: String? = null
)

@Entity(tableName = "household_logs")
data class HouseholdLog(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val householdId: String,
    val userId: String,
    val userName: String,
    val action: String, // "Hinzugefügt", "Verbraucht", "Müll"
    val itemName: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_users")
data class UserProfile(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: String = "Erwachsener" // "Erwachsener", "Kind"
)
