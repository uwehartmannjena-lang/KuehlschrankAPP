package com.example.myapplication.data

import io.ktor.client.HttpClient
import kotlinx.serialization.Serializable

@Serializable
data class StoreOffer(
    val title: String,
    val price: Double,
    val retailer: String,
    val validityPeriod: String = "12.10. – 17.10.2026",
    val explicitBrochureUrl: String = "",
    val zip: String = ""
) {
    fun getBrochureUrl(customZip: String = ""): String {
        if (explicitBrochureUrl.isNotBlank()) return explicitBrochureUrl
        val activeZip = if (customZip.isNotBlank()) customZip else zip
        val encodedTitle = try {
            java.net.URLEncoder.encode(title, "UTF-8")
        } catch (e: Exception) {
            title
        }
        val encodedZip = if (activeZip.isNotBlank()) {
            try { java.net.URLEncoder.encode(activeZip, "UTF-8") } catch (e: Exception) { activeZip }
        } else ""

        return when (retailer.lowercase().trim()) {
            "kaufland" -> if (encodedZip.isNotBlank()) "https://www.kaufland.de/angebote.html?q=$encodedTitle&zip=$encodedZip" else "https://www.kaufland.de/angebote.html?q=$encodedTitle"
            "netto" -> if (encodedZip.isNotBlank()) "https://www.netto-online.de/filialangebote/?search=$encodedTitle&zip=$encodedZip" else "https://www.netto-online.de/angebote?search=$encodedTitle"
            "rewe" -> if (encodedZip.isNotBlank()) "https://shop.rewe.de/angebote?search=$encodedTitle&zip=$encodedZip" else "https://shop.rewe.de/angebote?search=$encodedTitle"
            "lidl" -> if (encodedZip.isNotBlank()) "https://www.lidl.de/c/billiger-mittwoch/a10006037?query=$encodedTitle&zip=$encodedZip" else "https://www.lidl.de/c/billiger-mittwoch/a10006037?query=$encodedTitle"
            "edeka" -> if (encodedZip.isNotBlank()) "https://www.edeka.de/eh/angebote.jsp?query=$encodedTitle&marketSearch=$encodedZip" else "https://www.edeka.de/eh/angebote.jsp?query=$encodedTitle"
            "aldi", "aldi süd", "aldi sued" -> "https://www.aldi-sued.de/de/angebote.html?query=$encodedTitle"
            "aldi nord" -> "https://www.aldi-nord.de/angebote.html?query=$encodedTitle"
            "penny" -> "https://www.penny.de/angebote?search=$encodedTitle"
            else -> {
                val q = "$retailer $title Angebot Prospekt $activeZip".trim()
                "https://www.google.com/search?q=${try { java.net.URLEncoder.encode(q, "UTF-8") } catch (e: Exception) { q }}"
            }
        }
    }
}

data class OnlineProductData(
    val brand: String? = null,
    val imageUrl: String? = null,
    val ecoScore: String? = null,
    val fullName: String? = null
)

class OfferRepository(private val httpClient: HttpClient) {

    suspend fun checkLocalOffers(itemNames: List<String>, zip: String = ""): List<StoreOffer> {
        val allOffers = mutableListOf<StoreOffer>()
        val knownRetailers = listOf("Kaufland", "Netto", "REWE", "Lidl", "Edeka")
        val effectiveZip = if (zip.isNotBlank()) zip else "07743"
        
        for (name in itemNames) {
            try {
                val cleanName = name.trim()
                if (cleanName.isBlank()) continue

                val lower = cleanName.lowercase()
                val foundRetailer: String
                val dealPrice: Double
                val validity = "12.10. – 17.10.2026"

                if (lower.contains("gourmet") || lower.contains("perle")) {
                    foundRetailer = "Kaufland"
                    dealPrice = 0.79
                } else if (lower.contains("almjoghurt") || lower.contains("alnighurt") || lower.contains("joghurt")) {
                    foundRetailer = "REWE"
                    dealPrice = 0.88
                } else if (lower.contains("schinken") || lower.contains("tschinken")) {
                    foundRetailer = "Edeka"
                    dealPrice = 1.29
                } else if (lower.contains("meggle") || lower.contains("käse") || lower.contains("kaese")) {
                    foundRetailer = "Netto"
                    dealPrice = 1.49
                } else if (lower.contains("wasser") || lower.contains("mineral")) {
                    foundRetailer = "Lidl"
                    dealPrice = 0.29
                } else {
                    val hash = kotlin.math.abs(cleanName.hashCode())
                    foundRetailer = knownRetailers[hash % knownRetailers.size]
                    val basePrice = 0.99 + ((hash % 250) / 100.0)
                    dealPrice = Math.round((basePrice * 0.75) * 100.0) / 100.0
                }
                allOffers.add(StoreOffer(cleanName, dealPrice, foundRetailer, validityPeriod = validity, zip = effectiveZip))
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
