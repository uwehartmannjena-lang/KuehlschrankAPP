package com.example.myapplication.data

import com.example.myapplication.ImageSearchFallback
import com.example.myapplication.ReceiptImportSanitizer

class ProductRepository(
    private val dao: FridgeItemDao,
    private val openFoodFactsApi: OpenFoodFactsApi
) {

    /**
     * Multi-API Kaskade (Wasserfall-Prinzip):
     * SCHRITT A: Prüfe ZUERST die lokale Room-Tabelle `UserCorrections`.
     * SCHRITT B: Wenn A leer, frage OpenFoodFacts API.
     * SCHRITT C: Wenn B leer, frage Fallback-Routine (generische Bilder).
     */
    suspend fun fetchProductImage(ocrText: String): String {
        val cleanOcr = ReceiptImportSanitizer.cleanReceiptText(ocrText)

        // SCHRITT A: Lokale Room-Tabelle UserCorrections
        val localCorrection = dao.getUserCorrection(cleanOcr) ?: dao.getUserCorrection(ocrText)
        if (!localCorrection?.imageUrl.isNullOrBlank()) {
            return localCorrection.imageUrl
        }

        // SCHRITT B: OpenFoodFacts API
        try {
            val response = openFoodFactsApi.searchProduktByName(cleanOcr)
            val firstWithImage = response.products?.find { !it.imageUrl.isNullOrBlank() }
            if (firstWithImage?.imageUrl != null) {
                return firstWithImage.imageUrl
            }
        } catch (_: Exception) {
            // Netzfehlertoleranz
        }

        // SCHRITT C: Alternative Fallback-API
        return ImageSearchFallback.getFallbackImageUrl(cleanOcr)
    }

    /**
     * Speichert die manuelle Produktbild/ID-Korrektur des Nutzers dauerhaft in UserCorrections.
     */
    suspend fun saveUserCorrection(ocrText: String, correctProductId: String) {
        val cleanOcr = ReceiptImportSanitizer.cleanReceiptText(ocrText)
        val correction = UserCorrection(
            rawReceiptText = cleanOcr.ifBlank { ocrText },
            correctedName = cleanOcr,
            imageUrl = correctProductId
        )
        dao.insertUserCorrection(correction)
    }
}
