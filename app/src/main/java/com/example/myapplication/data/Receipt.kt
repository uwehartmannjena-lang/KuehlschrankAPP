package com.example.myapplication.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "receipts")
data class Receipt(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val supermarket: String?,
    val date: Long,
    val total: Double = 0.0,
    val note: String = ""
)
