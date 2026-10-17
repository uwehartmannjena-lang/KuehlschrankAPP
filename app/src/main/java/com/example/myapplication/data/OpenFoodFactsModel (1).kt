package com.example.myapplication.data

import com.google.gson.annotations.SerializedName

data class ProductResponse(
    val status: Int, // 1 = gefunden, 0 = nicht gefunden
    val product: ProductData?
)

data class ProductData(
    val code: String?,
    @SerializedName("product_name") val productName: String?,
    @SerializedName("brands") val marke: String?,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("image_front_small_url") val imageSmallUrl: String?,
    @SerializedName("nutriscore_grade") val nutriScore: String?,
    @SerializedName("ecoscore_grade") val ecoScore: String?,
    @SerializedName("allergens") val allergens: String?,
    @SerializedName("categories") val categories: String?,
    val nutriments: NutrimentsData?
)

data class SearchResponse(
    val count: Int,
    val products: List<ProductData>?
)

data class NutrimentsData(
    @SerializedName("energy-kcal_100g") val kcal100g: Double?,
    @SerializedName("energy-kcal_serving") val kcalServing: Double?,
    @SerializedName("proteins_100g") val proteins100g: Double?,
    @SerializedName("sugars_100g") val sugars100g: Double?,
    @SerializedName("salt_100g") val salt100g: Double?,
    @SerializedName("fat_100g") val fat100g: Double?
)
