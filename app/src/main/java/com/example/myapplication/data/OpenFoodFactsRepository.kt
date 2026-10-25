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
                val bestImage = product.imageFrontUrl?.trim()?.takeIf { it.isNotBlank() }
                    ?: product.imageUrl?.trim()?.takeIf { it.isNotBlank() }
                    ?: product.imageSmallUrl?.trim()?.takeIf { it.isNotBlank() }
                val score = product.nutriScore?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
                val cal = product.nutriments?.kcal100g?.toInt()?.toString()
                    ?: product.nutriments?.kcalServing?.toInt()?.toString()
                val detectedCategory = mapOpenFoodFactsCategory(product.categories, product.categoriesTags)
                ScannedProductDetails(
                    name = product.productName.trim(),
                    brand = product.marke?.trim(),
                    imageUrl = bestImage,
                    nutriScore = score,
                    calories = cal,
                    category = detectedCategory,
                    rawCategories = product.categories,
                    categoriesTags = product.categoriesTags
                )
            } else {
                null
            }
        }
    }

    fun mapOpenFoodFactsCategory(categories: String?, categoriesTags: List<String>?): String {
        return try {
            val tokens = mutableListOf<String>()
            categoriesTags?.forEach { tag ->
                tokens.add(tag.lowercase().removePrefix("en:").removePrefix("de:").replace('-', ' '))
            }
            categories?.split(',', ';')?.forEach { cat ->
                tokens.add(cat.trim().lowercase())
            }

            if (tokens.isEmpty()) return "Sonstiges"
            val fullText = tokens.joinToString(" ")

            when {
                // Getränke (vor Obst/Gemüse wegen 'plant-based beverages')
                fullText.containsAny("beverage", "beverages", "getränk", "getraenk", "drink", "drinks", "water", "wasser", "juice", "juices", "saft", "säfte", "soda", "limo", "beer", "bier", "wine", "wein", "tea", "tee", "coffee", "kaffee", "cola") -> "Getränke"

                // Milchprodukte
                fullText.containsAny("dairy", "dairies", "milk", "milch", "cheese", "cheeses", "käse", "kaese", "joghurt", "yogurt", "yogurts", "butter", "quark", "cream", "creams", "sahne", "schmand", "fromage", "curd", "mozzarella", "milchprodukt", "milchprodukte") -> "Milchprodukte"

                // Fleisch & Fisch
                fullText.containsAny("meat", "meats", "fleisch", "poultry", "geflügel", "gefluegel", "chicken", "hähnchen", "haehnchen", "beef", "rind", "rinder", "pork", "schwein", "fish", "fishes", "fisch", "seafood", "sausage", "sausages", "wurst", "würstchen", "ham", "hams", "schinken", "charcuterie", "salmon", "lachs", "tuna", "thunfisch") -> "Fleisch"

                // Brot & Backwaren
                fullText.containsAny("bread", "breads", "brot", "bakery", "backwaren", "pastry", "pastries", "gebäck", "gebaeck", "cake", "cakes", "kuchen", "biscuit", "biscuits", "keks", "kekse", "cookie", "cookies", "croissant", "toast", "roll", "rolls", "brötchen", "broetchen", "baguette") -> "Brot & Backwaren"

                // Obst & Gemüse
                fullText.containsAny("fruit", "fruits", "obst", "vegetable", "vegetables", "gemüse", "gemuese", "plant based foods", "fresh vegetable", "fresh fruit", "salad", "salads", "salat", "apple", "apfel", "banana", "banane", "tomato", "tomate", "potato", "kartoffel") -> "Obst & Gemüse"

                // Vorrat & Konserven
                fullText.containsAny("canned", "konserve", "konserven", "pasta", "nudeln", "spaghetti", "rice", "reis", "cereal", "cereals", "müsli", "muesli", "flour", "mehl", "sugar", "zucker", "sauce", "sauces", "soße", "sosse", "spice", "spices", "gewürz", "oil", "öl", "oils", "vinegar", "essig", "jam", "marmelade", "honey", "honig", "spread", "aufstrich", "snack", "snacks", "chocolate", "schokolade") -> "Vorrat & Konserven"

                // Drogerie & Hygiene
                fullText.containsAny("hygiene", "cosmetics", "kosmetik", "soap", "seife", "shampoo", "toothpaste", "zahnpasta", "deo") -> "Drogerie & Hygiene"

                // Haushalt
                fullText.containsAny("household", "haushalt", "cleaning", "reiniger", "detergent", "paper") -> "Haushalt"

                // Haustier
                fullText.containsAny("pet food", "cat food", "dog food", "tiernahrung", "katzenfutter", "hundefutter", "pets", "pet") -> "Haustier"

                else -> "Sonstiges"
            }
        } catch (_: Exception) {
            "Sonstiges"
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it, ignoreCase = true) }
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
    val imageUrl: String? = null,
    val nutriScore: String? = null,
    val calories: String? = null,
    val category: String? = null,
    val rawCategories: String? = null,
    val categoriesTags: List<String>? = null
)

