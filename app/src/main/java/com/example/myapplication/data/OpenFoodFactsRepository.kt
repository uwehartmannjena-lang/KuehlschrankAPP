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
}
