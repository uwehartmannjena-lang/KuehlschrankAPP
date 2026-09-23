package com.example.myapplication.data

import java.io.Serializable

data class Product(
    val name: String,
    val price: Double = 0.0,
    val quantity: Int = 1,
    val unit: String = "Stk.",
    val barcode: String? = null,
    val imageUrl: String? = null,
    val allergens: String = "",
    val kcal: Int = 0,
    val rawText: String? = null,
    val boundingBox: String? = null,
    val category: String = "SONSTIGES",
    val supermarket: String? = null,
    val purchaseDate: Long? = null
) : Serializable
