package com.example.myapplication

import android.content.Context
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.MarketProductDatabase
import com.example.myapplication.data.MarketProductEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

data class MarketDictionaryEntry(
    val receipt_pattern: String,
    val clean_name: String,
    val category: String,
    val default_storage: String,
    val default_shelf_life_days: Int
)

object MarketDictionaryHelper {
    var currentMarket: String = "general"
    private var marketDictionary: Map<String, MarketDictionaryEntry> = emptyMap()
    private var generalDictionary: Map<String, MarketDictionaryEntry> = emptyMap()

    fun detectMarket(rawText: String) {
        val lowerText = rawText.lowercase().take(1000)
        currentMarket = when {
            lowerText.contains("kaufland") -> "kaufland"
            lowerText.contains("lidl") -> "lidl"
            lowerText.contains("aldi nord") || lowerText.contains("aldi markt") -> "aldi_nord"
            lowerText.contains("aldi süd") || lowerText.contains("aldi sued") || lowerText.contains("aldi") -> "aldi_sued"
            lowerText.contains("rewe") -> "rewe"
            lowerText.contains("edeka") -> "edeka"
            lowerText.contains("globus") -> "globus"
            lowerText.contains("netto") -> "netto"
            lowerText.contains("penny") -> "penny"
            lowerText.contains("metro") -> "metro"
            lowerText.contains("dm-drogerie") || lowerText.contains(" dm ") || lowerText.startsWith("dm ") -> "dm"
            lowerText.contains("rossmann") -> "rossmann"
            else -> "general"
        }
    }

    fun loadDictionaries(context: Context) {
        try {
            runBlocking(Dispatchers.IO) {
                val dbDao = MarketProductDatabase.getInstance(context).marketProductDao()
                val count = dbDao.getMarketProductCount()
                if (count > 0) {
                    val marketEntries = if (currentMarket != "general") {
                        dbDao.getProductsByMarket(currentMarket)
                    } else emptyList()

                    val genEntries = dbDao.getGeneralProducts()

                    marketDictionary = marketEntries.associate { entry ->
                        entry.receiptPattern.lowercase() to MarketDictionaryEntry(
                            receipt_pattern = entry.receiptPattern,
                            clean_name = entry.cleanName,
                            category = entry.category,
                            default_storage = entry.defaultStorage,
                            default_shelf_life_days = entry.defaultShelfLifeDays
                        )
                    }

                    generalDictionary = genEntries.associate { entry ->
                        entry.receiptPattern.lowercase() to MarketDictionaryEntry(
                            receipt_pattern = entry.receiptPattern,
                            clean_name = entry.cleanName,
                            category = entry.category,
                            default_storage = entry.defaultStorage,
                            default_shelf_life_days = entry.defaultShelfLifeDays
                        )
                    }
                    return@runBlocking
                }

                MarketDictionaryImporter.seedDatabaseFromAssets(context)
                val dao = AppDatabase.getDatabase(context).fridgeItemDao()
                val entries = dao.getAllMarketProductsForMarket(currentMarket)
                if (entries.isNotEmpty()) {
                    val converted = entries.associate { entry ->
                        entry.receiptPattern.lowercase() to MarketDictionaryEntry(
                            receipt_pattern = entry.receiptPattern,
                            clean_name = entry.cleanName,
                            category = entry.category,
                            default_storage = entry.defaultStorage,
                            default_shelf_life_days = entry.defaultShelfLifeDays
                        )
                    }
                    marketDictionary = converted.filter { it.value.receipt_pattern.lowercase().contains(currentMarket) }
                    generalDictionary = converted.filter { !it.value.receipt_pattern.lowercase().contains(currentMarket) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadJson(context: Context, fileName: String): Map<String, MarketDictionaryEntry> {
        return try {
            val jsonString = context.assets.open("markets/$fileName").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<MarketDictionaryEntry>>() {}.type
            val entries: List<MarketDictionaryEntry> = Gson().fromJson(jsonString, type) ?: emptyList()
            entries.associateBy { it.receipt_pattern.lowercase() }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Sucht zuerst im marktspezifischen, dann im generellen Wörterbuch via Exakt, DB-FTS und Levenshtein-Distanz.
     */
    fun findBestMatch(rawLine: String, context: Context? = null): MarketDictionaryEntry? {
        val cleaned = ReceiptImportSanitizer.cleanReceiptText(rawLine).lowercase().trim()
        if (cleaned.length < 2) return null

        // 1. Exakter Match im In-Memory Speicher
        marketDictionary[cleaned]?.let { return it }
        generalDictionary[cleaned]?.let { return it }

        // 2. DB Lookup über MarketProductDatabase
        if (context != null) {
            try {
                var entry: MarketProductEntry? = null
                runBlocking(Dispatchers.IO) {
                    val dao = MarketProductDatabase.getInstance(context).marketProductDao()
                    if (currentMarket != "general") {
                        entry = dao.findExactMatchByMarket(cleaned, currentMarket)
                    }
                    if (entry == null) {
                        entry = dao.findExactMatchGeneral(cleaned)
                    }
                    if (entry == null) {
                        val ftsResults = dao.searchMarketProductsFts(cleaned)
                        entry = ftsResults.find { it.market == currentMarket } ?: ftsResults.firstOrNull()
                    }
                    if (entry == null) {
                        val likeResults = dao.searchMarketProductsLike(cleaned)
                        entry = likeResults.find { it.market == currentMarket } ?: likeResults.firstOrNull()
                    }
                }
                val found = entry
                if (found != null) {
                    return MarketDictionaryEntry(
                        receipt_pattern = found.receiptPattern,
                        clean_name = found.cleanName,
                        category = found.category,
                        default_storage = found.defaultStorage,
                        default_shelf_life_days = found.defaultShelfLifeDays
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Fuzzy Match via Levenshtein Distanz
        var bestEntry: MarketDictionaryEntry? = null
        var minDistance = Int.MAX_VALUE

        val primaryList = marketDictionary.values
        val fallbackList = generalDictionary.values

        for (entry in primaryList) {
            val candidate = entry.receipt_pattern.lowercase()
            val dist = ReceiptImportSanitizer.calculateLevenshteinDistance(cleaned, candidate)

            val maxAllowedDistance = when {
                candidate.length <= 4 -> 1
                candidate.length <= 8 -> 2
                else -> 3
            }

            if (dist <= maxAllowedDistance && dist < minDistance) {
                minDistance = dist
                bestEntry = entry
            }
        }

        if (bestEntry != null) return bestEntry

        for (entry in fallbackList) {
            val candidate = entry.receipt_pattern.lowercase()
            val dist = ReceiptImportSanitizer.calculateLevenshteinDistance(cleaned, candidate)

            val maxAllowedDistance = when {
                candidate.length <= 4 -> 1
                candidate.length <= 8 -> 2
                else -> 3
            }

            if (dist <= maxAllowedDistance && dist < minDistance) {
                minDistance = dist
                bestEntry = entry
            }
        }

        return bestEntry
    }
}
