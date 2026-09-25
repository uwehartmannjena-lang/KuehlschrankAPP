package com.example.myapplication

import android.content.Context
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.MarketProductEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Importer-Klasse zum schnellen Einlesen strukturierter Sortimente (Open Food Facts JSON/CSV, Markt-JSONs).
 * Speichert bis zu 20.000 Artikel vorindiziert in der SQLite/Room-Struktur mit FTS.
 */
object MarketDictionaryImporter {

    /**
     * Liest JSON-Dateien aus Assets oder externen Quellen ein und speichert sie in Chunks in Room.
     */
    suspend fun importFromJsonString(context: Context, jsonString: String, marketName: String = "general") = withContext(Dispatchers.IO) {
        try {
            val type = object : TypeToken<List<MarketDictionaryEntry>>() {}.type
            val rawEntries: List<MarketDictionaryEntry> = Gson().fromJson(jsonString, type) ?: emptyList()

            val roomEntries = rawEntries.map { entry ->
                MarketProductEntry(
                    market = marketName,
                    receiptPattern = entry.receipt_pattern,
                    cleanName = entry.clean_name,
                    category = entry.category,
                    defaultStorage = entry.default_storage,
                    defaultShelfLifeDays = entry.default_shelf_life_days
                )
            }

            val dao = AppDatabase.getDatabase(context).fridgeItemDao()
            roomEntries.chunked(500).forEach { chunk ->
                dao.insertMarketProducts(chunk)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Liest Open Food Facts CSV-Exporte (barcode;name;category;brand;...) im Hintergrund ein.
     */
    suspend fun importFromOpenFoodFactsCsv(context: Context, inputStream: InputStream, marketName: String = "general") = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val entries = mutableListOf<MarketProductEntry>()
            reader.readLine() // Header überspringen
            var line: String? = reader.readLine()
            while (line != null) {
                val tokens = line.split(";")
                if (tokens.size >= 2) {
                    val rawName = tokens[1].trim()
                    if (rawName.length >= 3) {
                        val category = if (tokens.size >= 3 && tokens[2].isNotBlank()) tokens[2].trim().uppercase() else "SONSTIGES"
                        val cleanName = ReceiptImportSanitizer.cleanReceiptText(rawName)
                        entries.add(
                            MarketProductEntry(
                                market = marketName,
                                receiptPattern = rawName,
                                cleanName = if (cleanName.isNotBlank()) cleanName else rawName,
                                category = category
                            )
                        )
                    }
                }
                line = reader.readLine()
            }

            val dao = AppDatabase.getDatabase(context).fridgeItemDao()
            entries.chunked(500).forEach { chunk ->
                dao.insertMarketProducts(chunk)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Befüllt die Room-Datenbank initial aus den vorhandenen Assets (kaufland.json, general.json, etc.).
     */
    suspend fun seedDatabaseFromAssets(context: Context) = withContext(Dispatchers.IO) {
        val dao = AppDatabase.getDatabase(context).fridgeItemDao()
        if (dao.getMarketProductCount() > 0) return@withContext // Bereits befüllt

        val markets = listOf("kaufland", "lidl", "aldi", "rewe", "edeka", "netto", "general")
        for (m in markets) {
            try {
                val json = context.assets.open("markets/$m.json").bufferedReader().use { it.readText() }
                importFromJsonString(context, json, m)
            } catch (e: Exception) {
                // Asset eventuell nicht vorhanden, ignorieren
            }
        }
    }
}
