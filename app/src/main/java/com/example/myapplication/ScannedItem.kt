package com.example.myapplication

import java.io.Serializable

/**
 * Repräsentiert ein vom Kassenzettel erkanntes Produkt.
 */
data class ScannedItem(
    val name: String,
    val preis: Double,
    val menge: Int = 1
) : Serializable