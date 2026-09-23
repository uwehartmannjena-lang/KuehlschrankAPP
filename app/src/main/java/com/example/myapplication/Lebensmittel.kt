package com.example.myapplication

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kuehlschrank_tabelle",
    indices = [Index(value = ["name", "dateiname"], unique = true)]
)
data class Lebensmittel(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val barcode: String,
    val ablaufdatum: String,
    val menge: Int = 1,
    val dateiname: String? = null
)
