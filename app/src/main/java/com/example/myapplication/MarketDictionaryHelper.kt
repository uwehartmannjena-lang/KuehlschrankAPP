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

    fun getSuggestions(query: String, context: Context, limit: Int = 5): List<String> {
        val q = query.lowercase().trim()
        if (q.isBlank()) return emptyList()

        val results = mutableListOf<String>()

        marketDictionary.values.forEach { entry ->
            if (entry.clean_name.lowercase().contains(q) || entry.receipt_pattern.lowercase().contains(q)) {
                results.add(entry.clean_name)
            }
        }
        generalDictionary.values.forEach { entry ->
            if (entry.clean_name.lowercase().contains(q) || entry.receipt_pattern.lowercase().contains(q)) {
                results.add(entry.clean_name)
            }
        }

        if (results.size < limit) {
            try {
                runBlocking(Dispatchers.IO) {
                    val dbDao = MarketProductDatabase.getInstance(context).marketProductDao()
                    val dbEntries = dbDao.searchMarketProductsLike(q)
                    dbEntries.forEach { entry ->
                        results.add(entry.cleanName)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Fuzzy Fallback falls nichts gefunden
        if (results.isEmpty()) {
            val tokens = q.split(Regex("""\s+""")).filter { it.length >= 2 }
            if (tokens.isNotEmpty()) {
                val bestToken = tokens.maxByOrNull { it.length } ?: tokens.first()
                marketDictionary.values.forEach { entry ->
                    if (entry.clean_name.lowercase().contains(bestToken)) results.add(entry.clean_name)
                }
                generalDictionary.values.forEach { entry ->
                    if (entry.clean_name.lowercase().contains(bestToken)) results.add(entry.clean_name)
                }
                try {
                    runBlocking(Dispatchers.IO) {
                        val dbDao = MarketProductDatabase.getInstance(context).marketProductDao()
                        val dbEntries = dbDao.searchMarketProductsLike(bestToken)
                        dbEntries.forEach { entry -> results.add(entry.cleanName) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return results
            .filter { it.isNotBlank() }
            .distinct()
            .sortedByDescending { it.lowercase().startsWith(q) }
            .take(limit)
    }

    /**
     * 11. Lern-Vorrang -> Smart Guessing (Tokens) -> DB LIKE %:word% -> Levenshtein-Fallback -> Beautifier.
     */
    fun findBestMatch(rawLine: String, context: Context? = null): MarketDictionaryEntry? {
        // 11. Lern-Vorrang: ZUERST UserLearnedCorrections prüfen
        val learned = ReceiptImportSanitizer.getLearnedCorrection(rawLine)
        if (learned != null) {
            return MarketDictionaryEntry(
                receipt_pattern = rawLine,
                clean_name = learned,
                category = "SONSTIGES",
                default_storage = "Kühlschrank",
                default_shelf_life_days = 7
            )
        }

        // 1, 2, 3, 13. Pre-Sanitization & Abfangen von kurzen/leeren Zeilen (< 3 Zeichen)
        val cleaned = ReceiptImportSanitizer.cleanReceiptText(rawLine).lowercase().trim()
        if (cleaned.length < 3) return null

        // Exakter Match im In-Memory Speicher
        marketDictionary[cleaned]?.let { return it }
        generalDictionary[cleaned]?.let { return it }

        // 4 & 5. Smart-Guessing: Reststring zerlegen, längstes Token nehmen
        val ignoreTokens = setOf("der", "die", "das", "mit", "von", "und", "für", "dem", "den")
        val words = cleaned.split(Regex("""\s+"""))
            .map { it.trim() }
            .filter { it.length >= 2 && it !in ignoreTokens }

        val longestWord = words.maxByOrNull { it.length }

        if (longestWord != null) {
            val marketMatch = marketDictionary.values.find {
                it.receipt_pattern.lowercase().contains(longestWord) || it.clean_name.lowercase().contains(longestWord)
            }
            if (marketMatch != null) return marketMatch

            val genMatch = generalDictionary.values.find {
                it.receipt_pattern.lowercase().contains(longestWord) || it.clean_name.lowercase().contains(longestWord)
            }
            if (genMatch != null) return genMatch
        }

        // 5 & 15. Asynchrone DB Token-Suche via LIKE %:word% mit Try-Catch & Null-Sicherheit
        if (context != null) {
            try {
                var entry: MarketProductEntry? = null
                runBlocking(Dispatchers.IO) {
                    try {
                        val dao = MarketProductDatabase.getInstance(context).marketProductDao()
                        if (currentMarket != "general") {
                            entry = dao.findExactMatchByMarket(cleaned, currentMarket)
                        }
                        if (entry == null) {
                            entry = dao.findExactMatchGeneral(cleaned)
                        }
                        if (entry == null && longestWord != null) {
                            val likeResults = dao.searchMarketProductsLike(longestWord)
                            entry = likeResults.find { it.market == currentMarket } ?: likeResults.firstOrNull()
                        }
                        if (entry == null && cleaned.isNotBlank()) {
                            val ftsResults = dao.searchMarketProductsFts(cleaned)
                            entry = ftsResults.find { it.market == currentMarket } ?: ftsResults.firstOrNull()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
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

        // 6. Levenshtein-Fallback über gecachte Klar-Namen
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
