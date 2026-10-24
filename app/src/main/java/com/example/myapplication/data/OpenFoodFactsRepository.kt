package com.example.myapplication.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenFoodFactsRepository(
    private val api: OpenFoodFactsApi = OpenFoodFactsApi.create()
) {
    suspend fun searchProducts(query: String): List<ProductData> = withContext(Dispatchers.IO) {
        try {
            val cleanQuery = query.trim()
            if (cleanQuery.length < 2) return@withContext emptyList()
            val response = api.searchProduktByName(cleanQuery)
            response.products?.filter { !it.productName.isNullOrBlank() } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getProductByBarcode(barcode: String): Result<ProductData?> = withContext(Dispatchers.IO) {
        val cleanBarcode = barcode.trim()
        if (cleanBarcode.isBlank()) return@withContext Result.success(null)
        try {
            val response = api.getProduktByBarcode(cleanBarcode)
            if (response.status == 1 && response.product != null) {
                Result.success(response.product)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchScannedProduct(barcode: String): Result<ScannedProductDetails?> = withContext(Dispatchers.IO) {
        val cleanBarcode = barcode.trim()
        if (cleanBarcode.isBlank()) return@withContext Result.success(null)
        getProductByBarcode(cleanBarcode).map { product ->
            if (product != null && !product.productName.isNullOrBlank()) {
                val bestImage = product.imageUrl?.takeIf { it.isNotBlank() }
                    ?: product.imageFrontUrl?.takeIf { it.isNotBlank() }
                    ?: product.imageSmallUrl?.takeIf { it.isNotBlank() }
                ScannedProductDetails(
                    name = product.productName.trim(),
                    brand = product.marke?.trim(),
                    imageUrl = bestImage
                )
            } else {
                null
            }
        }
    }

    fun parseNutriments(productData: ProductData?): ParsedNutrition {
        val nutriments = productData?.nutriments
        return ParsedNutrition(
            nutriScore = productData?.nutriScore?.trim()?.uppercase()?.takeIf { it.isNotBlank() },
            caloriesKcal = nutriments?.kcal100g ?: nutriments?.kcalServing,
            fat = nutriments?.fat100g,
            saturatedFat = nutriments?.saturatedFat100g,
            carbohydrates = nutriments?.carbohydrates100g,
            sugars = nutriments?.sugars100g,
            proteins = nutriments?.proteins100g,
            salt = nutriments?.salt100g,
            allergens = parseAllergens(productData),
            additives = parseAdditives(productData)
        )
    }

    fun parseAllergens(productData: ProductData?): String? {
        val tags = productData?.allergensTags
        if (!tags.isNullOrEmpty()) {
            return tags.joinToString(", ") { tag ->
                tag.removePrefix("en:").removePrefix("de:").replace('-', ' ')
            }.trim().takeIf { it.isNotBlank() }
        }
        return productData?.allergens?.trim()?.takeIf { it.isNotBlank() }
    }

    fun parseAdditives(productData: ProductData?): String? {
        val tags = productData?.additivesTags
        if (!tags.isNullOrEmpty()) {
            return tags.joinToString(", ") { tag ->
                tag.removePrefix("en:").removePrefix("de:").uppercase()
            }.trim().takeIf { it.isNotBlank() }
        }
        return null
    }
}

data class ParsedNutrition(
    val nutriScore: String? = null,
    val caloriesKcal: Double? = null,
    val fat: Double? = null,
    val saturatedFat: Double? = null,
    val carbohydrates: Double? = null,
    val sugars: Double? = null,
    val proteins: Double? = null,
    val salt: Double? = null,
    val allergens: String? = null,
    val additives: String? = null
)

data class ScannedProductDetails(
    val name: String,
    val brand: String? = null,
    val imageUrl: String? = null
)

