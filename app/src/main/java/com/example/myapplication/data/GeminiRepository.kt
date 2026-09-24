package com.example.myapplication.data

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.google.ai.client.generativeai.type.generationConfig

class GeminiRepository {

    private val apiKey = "AQ.Ab8RN6ICQy_IBpdBMBjk9GJJHi5_ydt3R9pMK_qbxR91R5Unqw"
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash", 
        apiKey = apiKey,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
            temperature = 0.1f
        }
    )
    private val gson = Gson()

    suspend fun analyzeReceipt(bitmap: Bitmap): List<Product> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                Du bist ein präziser Kassenbon-Analysator für eine deutsche Kühlschrank-App.
                Analysiere diesen Kassenbon.
                
                Ignoriere komplett: Summen, Steuern, Kartenzahlung, Rabatte, Pfand, Adressen und Müll.
                
                Extrahiere:
                1. Den Namen des Supermarkts (z.B. Kaufland, Lidl, Rewe, Edeka, Netto, Penny, dm, Rossmann).
                2. Das Kaufdatum (Format: DD.MM.YYYY).
                3. Alle gekauften Lebensmittel und Haushaltsartikel. Schreibe abgekürzte Artikel vollständig aus.
                4. Weise jedem Artikel eine Kategorie zu (z.B. Molkerei, Obst & Gemüse, Fleisch, Backwaren, Getränke, Haushalt, Drogerie).
                
                Gib das Ergebnis AUSSCHLIESSLICH als JSON-Objekt in folgendem Format zurück:
                {
                  "supermarket": "Name",
                  "date": "DD.MM.YYYY",
                  "items": [
                    {"quantity": 1, "name": "Müller Ayran", "price": 0.88, "category": "Molkerei"},
                    {"quantity": 2, "name": "Weihenstephan H-Milch 1,5%", "price": 2.22, "category": "Molkerei"}
                  ]
                }
            """.trimIndent()

            val inputContent = content {
                image(bitmap)
                text(prompt)
            }

            val response = generativeModel.generateContent(inputContent)
            val textResponse = response.text ?: ""
            Log.d("GeminiRepository", "Raw Gemini Receipt Response: $textResponse")
            
            val jsonStart = textResponse.indexOf("{")
            val jsonEnd = textResponse.lastIndexOf("}")
            
            if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) {
                Log.e("GeminiRepository", "Could not find valid JSON in receipt response")
                return@withContext emptyList()
            }
            
            val cleanResponse = textResponse.substring(jsonStart, jsonEnd + 1)
            Log.d("GeminiRepository", "Cleaned JSON: $cleanResponse")
            
            val result: GeminiReceipt = gson.fromJson(cleanResponse, GeminiReceipt::class.java)
            
            val purchaseDate = try {
                val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY)
                result.date?.let { sdf.parse(it)?.time }
            } catch (e: Exception) { null }

            result.items.map { 
                Product(
                    name = it.name,
                    price = it.price,
                    quantity = it.quantity,
                    rawText = it.name,
                    category = it.category ?: "Sonstiges",
                    supermarket = result.supermarket,
                    purchaseDate = purchaseDate
                )
            }
        } catch (e: Exception) {
            Log.e("GeminiRepository", "Fehler bei der Beleg-Analyse", e)
            emptyList()
        }
    }

    suspend fun analyzeFridgePhoto(bitmaps: List<Bitmap>): List<Product> = withContext(Dispatchers.IO) {
        try {
            if (bitmaps.isEmpty()) return@withContext emptyList()

            val prompt = """
                Du bist ein Vision-Experte für eine Kühlschrank-App. 
                Analysiere diese Fotos. Das Foto zeigt verschiedene Lebensmittelverpackungen, z.B. Aufschnitt, Käse, Gemüse oder Fertiggerichte.
                
                Lies ALLE Texte und Etiketten auf den Verpackungen, um die Produkte so genau wie möglich zu identifizieren (z.B. "Wiltmann Geflügel Salami XXL", "Herta Paprikahähnchen").
                Identifiziere alle erkennbaren Lebensmittel auf ALLEN Bildern und schätze deren Anzahl/Menge.
                Vermeide Duplikate, falls derselbe Artikel auf mehreren Bildern zu sehen ist.
                
                Extrahiere:
                1. Den exakten Namen des Artikels inklusive Marke (falls lesbar, z.B. "Herta Paprikahähnchen"). Wenn keine Marke lesbar ist, dann was es allgemein ist.
                2. Die Menge (Anzahl der Packungen).
                3. Eine passende Kategorie (z.B. Fleisch, Molkerei, Obst & Gemüse, Backwaren, Getränke).
                
                Antworte AUSSCHLIESSLICH im folgenden JSON-Format und schreibe KEINEN weiteren Text davor oder danach:
                {
                  "items": [
                    {"quantity": 1, "name": "Gurke", "category": "Obst & Gemüse"},
                    {"quantity": 2, "name": "Paprika", "category": "Obst & Gemüse"}
                  ]
                }
            """.trimIndent()

            Log.d("GeminiRepository", "Sending ${bitmaps.size} images to Gemini")
            val inputContent = content {
                bitmaps.forEach { image(it) }
                text(prompt)
            }

            val response = try {
                Log.d("GeminiRepository", "Sending request to Gemini 2.5 flash...")
                generativeModel.generateContent(inputContent)
            } catch (e: Exception) {
                Log.e("GeminiRepository", "Primary model failed, trying fallback", e)
                val fallbackModel = GenerativeModel(
                    modelName = "gemini-2.5-flash", // Fallback auf das selbe (wir haben keine pro-credits)
                    apiKey = apiKey,
                    generationConfig = generationConfig {
                        responseMimeType = "application/json"
                    }
                )
                fallbackModel.generateContent(inputContent)
            }

            val textResponse = response.text ?: ""
            Log.d("GeminiRepository", "Raw Gemini Response: $textResponse")
            
            // Sichere JSON-Extraktion für den Fall, dass das Modell noch zusätzlichen Text sendet
            val jsonStart = textResponse.indexOf("{")
            val jsonEnd = textResponse.lastIndexOf("}")
            
            if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) {
                Log.e("GeminiRepository", "Could not find valid JSON in response")
                return@withContext emptyList()
            }
            
            val cleanResponse = textResponse.substring(jsonStart, jsonEnd + 1)
            Log.d("GeminiRepository", "Cleaned JSON FridgeScan: $cleanResponse")
            
            val result: GeminiFridgeScan = gson.fromJson(cleanResponse, GeminiFridgeScan::class.java)

            Log.d("GeminiRepository", "Parsed ${result.items.size} items from JSON")

            result.items.map { 
                Product(
                    name = it.name,
                    price = 0.0,
                    quantity = it.quantity,
                    rawText = it.name,
                    category = it.category ?: "Sonstiges",
                    purchaseDate = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            Log.e("GeminiRepository", "Fehler bei der Foto-Analyse", e)
            emptyList()
        }
    }

    suspend fun getRecipeSuggestions(inventory: List<FridgeItem>, dietProfile: String): List<String> = withContext(Dispatchers.IO) {
        try {
            if (inventory.isEmpty()) return@withContext emptyList()

            val inventoryText = inventory.joinToString("\n") { 
                "- ${it.name} (${it.quantity} ${it.unit}), läuft ab in ${getDaysRemaining(it.expiryDate)} Tagen"
            }

            val prompt = """
                Du bist ein kreativer Koch-Assistent für die App 'FrischeRadar'.
                Hier ist mein aktueller Bestand im Kühlschrank:
                $inventoryText
                
                Mein Ernährungsprofil ist: $dietProfile
                
                Bitte schlage mir 3 leckere Gerichte vor, die ich heute kochen kann. 
                Priorisiere dabei unbedingt die Zutaten, die am baldsten ablaufen.
                
                Antworte AUSSCHLIESSLICH als JSON-Liste von Strings mit den Namen der Gerichte:
                ["Gericht 1", "Gericht 2", "Gericht 3"]
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            val textResponse = response.text ?: ""
            
            val listType = object : TypeToken<List<String>>() {}.type
            gson.fromJson(textResponse, listType)
        } catch (e: Exception) {
            Log.e("GeminiRepository", "Fehler bei Rezept-Vorschlägen", e)
            emptyList()
        }
    }

    private fun getDaysRemaining(expiryDate: Long?): Long {
        if (expiryDate == null) return 999
        val diff = expiryDate - System.currentTimeMillis()
        return diff / (1000 * 60 * 60 * 24)
    }

    private data class GeminiFridgeScan(
        val items: List<GeminiProduct> = emptyList()
    )

    private data class GeminiReceipt(
        val supermarket: String? = null,
        val date: String? = null,
        val items: List<GeminiProduct> = emptyList()
    )

    private data class GeminiProduct(
        val quantity: Int,
        val name: String,
        val price: Double = 0.0,
        val category: String? = null
    )
}
