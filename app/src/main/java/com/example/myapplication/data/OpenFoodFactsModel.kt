package com.example.myapplication.data

import com.google.gson.annotations.SerializedName

data class ProductResponse(
    val status: Int? = 0, // 1 = gefunden, 0 = nicht gefunden
    val product: ProductData? = null
)

data class ProductData(
    val code: String? = null,
    @SerializedName("product_name") val productName: String? = null,
    @SerializedName("brands") val marke: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("image_front_small_url") val imageSmallUrl: String? = null,
    @SerializedName("nutriscore_grade") val nutriScore: String? = null,
    @SerializedName("ecoscore_grade") val ecoScore: String? = null,
    @SerializedName("allergens") val allergens: String? = null,
    @SerializedName("categories") val categories: String? = null,
    val nutriments: NutrimentsData? = null
)

data class SearchResponse(
    val count: Int? = 0,
    val products: List<ProductData>? = emptyList()
)

data class NutrimentsData(
    @SerializedName("energy-kcal_100g") val kcal100g: Double? = null,
    @SerializedName("energy-kcal_serving") val kcalServing: Double? = null,
    @SerializedName("proteins_100g") val proteins100g: Double? = null,
    @SerializedName("sugars_100g") val sugars100g: Double? = null,
    @SerializedName("salt_100g") val salt100g: Double? = null,
    @SerializedName("fat_100g") val fat100g: Double? = null
)
