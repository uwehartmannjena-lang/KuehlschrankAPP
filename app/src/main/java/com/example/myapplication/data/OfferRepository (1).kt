package com.example.myapplication.data

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URL
import android.net.Uri

@Serializable
data class StoreOffer(
    val title: String,
    val price: Double,
    val retailer: String
)

data class OnlineProductData(
    val brand: String? = null,
    val imageUrl: String? = null,
    val ecoScore: String? = null,
    val fullName: String? = null
)

class OfferRepository(private val httpClient: HttpClient) {

    suspend fun checkLocalOffers(itemNames: List<String>): List<StoreOffer> {
        val allOffers = mutableListOf<StoreOffer>()
        
        for (name in itemNames) {
            try {
                // Echte Simulation: Suche via Google Search URL (wie DealWorker)
                val encodedName = Uri.encode(name)
                val searchUrl = "https://www.google.de/search?q=angebot+$encodedName"
                
                // Wir simulieren hier, dass wir Ergebnisse von bekannten Händlern finden
                val retailers = listOf("Kaufland", "Lidl", "Rewe", "Aldi", "Edeka", "dm")
                val foundRetailer = retailers.random()
                
                // Preisberechnung basierend auf dem Namen (Heuristik)
                val basePrice = if (name.lowercase().contains("wasser")) 0.59 else 1.99
                val dealPrice = Math.round((basePrice * 0.8) * 100.0) / 100.0
                
                // Nur hinzufügen wenn "Glück" (Simulation von Treffern)
                if (System.currentTimeMillis() % 4 != 0L) {
                    allOffers.add(StoreOffer(name, dealPrice, foundRetailer))
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
        return allOffers
    }

    suspend fun calculateBestRetailer(shoppingItems: List<ShoppingItem>): Map<String, Double> {
        val retailerTotals = mutableMapOf<String, Double>()
        val retailers = listOf(
            "Kaufland", "Lidl", "Rewe", "Aldi Nord", "Aldi Süd", "Edeka", "dm",
            "Netto", "Penny", "Norma", "Globus", "Tegut", "Rossmann", "Müller"
        )
        
        for (retailer in retailers) {
            var total = 0.0
            for (item in shoppingItems) {
                // Simulation von Preisen pro Händler (Basispreis + Zufallsschwankung)
                val basePrice = 1.50 
                val factor = when(retailer) {
                    "Aldi", "Lidl" -> 0.9
                    "Rewe", "Edeka" -> 1.1
                    else -> 1.0
                }
                total += item.quantity * basePrice * factor
            }
            retailerTotals[retailer] = total
        }
        return retailerTotals.toList().sortedBy { it.second }.toMap()
    }

    suspend fun checkRecallStatus(barcode: String): String {
        return try {
            // Simulation einer Rückruf-Datenbank (z.B. lebensmittelwarnung.de)
            if (barcode == "4000123456789") "WARN: Rückruf wegen Salmonellen!"
            else "OK"
        } catch (e: Exception) { "UNKNOWN" }
    }

    suspend fun searchProductOnline(name: String): OnlineProductData {
        return executeOnlineSearch(name) ?: OnlineProductData()
    }

    private suspend fun executeOnlineSearch(query: String): OnlineProductData? {
        return try {
            // Simulation einer Produktsuche
            OnlineProductData(brand = "Hausmarke", ecoScore = "B")
        } catch (e: Exception) { null }
    }
}
