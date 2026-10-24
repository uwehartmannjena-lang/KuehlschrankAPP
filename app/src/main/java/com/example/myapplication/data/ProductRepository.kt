package com.example.myapplication.data

import com.example.myapplication.ImageSearchFallback
import com.example.myapplication.ReceiptImportSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

sealed class BarcodeScanResult {
    data class Success(
        val barcode: String,
        val name: String,
        val brand: String? = null,
        val quantity: Int = 1,
        val unit: String = "Stk.",
        val category: String = "Sonstiges",
        val imageUrl: String? = null,
        val isFromOfflineDb: Boolean = false,
        val nutriScore: String? = null,
        val caloriesKcal: Double? = null,
        val fat: Double? = null,
        val saturatedFat: Double? = null,
        val carbohydrates: Double? = null,
        val sugars: Double? = null,
        val proteins: Double? = null,
        val salt: Double? = null
    ) : BarcodeScanResult()

    sealed class Failure(val barcode: String) : BarcodeScanResult() {
        class NotFound(barcode: String) : Failure(barcode)
        class NetworkError(barcode: String, val throwable: Throwable) : Failure(barcode)
    }
}

class ProductRepository(
    private val dao: FridgeItemDao,
    private val openFoodFactsApi: OpenFoodFactsApi = OpenFoodFactsApi.create(),
    private val marketProductDao: MarketProductDao? = null,
    private val productDao: ProductDao? = null,
    private val openFoodFactsRepository: OpenFoodFactsRepository = OpenFoodFactsRepository(openFoodFactsApi)
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

    /**
     * Scan-Kaskade für Barcodes / EAN:
     * 1. Lokale Room-Datenbanken abfragen (market_products.db und FridgeDatabase)
     * 2. Falls nicht vorhanden: Automatische Anfrage an Open Food Facts
     * 3. Gefundene Produktdaten parsen und sofort in die lokale DB übernehmen (für Offline-Verfügbarkeit)
     * 4. Fehlerbehandlung (Timeout, Offline, nicht gelistet)
     */
    suspend fun lookupBarcode(barcode: String): BarcodeScanResult = withContext(Dispatchers.IO) {
        val cleanBarcode = barcode.trim()
        if (cleanBarcode.isBlank()) {
            return@withContext BarcodeScanResult.Failure.NotFound(barcode)
        }

        // 1a. Lokale FridgeDatabase: cached_products
        val cached = dao.getCachedProduct(cleanBarcode)
        if (cached != null) {
            val firstCat = cached.categories.split(",").firstOrNull()?.trim() ?: "Sonstiges"
            return@withContext BarcodeScanResult.Success(
                barcode = cleanBarcode,
                name = cached.name,
                brand = cached.brand,
                quantity = 1,
                unit = "Stk.",
                category = if (firstCat.isNotBlank()) firstCat else "Sonstiges",
                imageUrl = cached.imageUrl,
                isFromOfflineDb = true
            )
        }

        // 1b. Lokale FridgeDatabase: fridge_items
        val existingItem = dao.getItemByBarcode(cleanBarcode)
        if (existingItem != null) {
            return@withContext BarcodeScanResult.Success(
                barcode = cleanBarcode,
                name = existingItem.name,
                brand = existingItem.brand,
                quantity = existingItem.quantity,
                unit = existingItem.unit,
                category = existingItem.category,
                imageUrl = existingItem.imageUrl,
                isFromOfflineDb = true
            )
        }

        // 1c. Lokale market_products.db (ProductDao / MarketProductDao)
        val productEntity = productDao?.findByReceiptPattern(cleanBarcode)
        if (productEntity != null && productEntity.cleanName.isNotBlank()) {
            return@withContext BarcodeScanResult.Success(
                barcode = cleanBarcode,
                name = productEntity.cleanName,
                quantity = 1,
                unit = "Stk.",
                category = productEntity.category,
                isFromOfflineDb = true,
                nutriScore = productEntity.nutriScore,
                caloriesKcal = productEntity.caloriesKcal,
                fat = productEntity.fat,
                saturatedFat = productEntity.saturatedFat,
                carbohydrates = productEntity.carbohydrates,
                sugars = productEntity.sugars,
                proteins = productEntity.proteins,
                salt = productEntity.salt
            )
        }

        val marketDictEntry = marketProductDao?.findExactMatchGeneral(cleanBarcode)
        if (marketDictEntry != null && marketDictEntry.cleanName.isNotBlank()) {
            return@withContext BarcodeScanResult.Success(
                barcode = cleanBarcode,
                name = marketDictEntry.cleanName,
                quantity = 1,
                unit = "Stk.",
                category = marketDictEntry.category,
                isFromOfflineDb = true
            )
        }

        // 2. Open Food Facts API (de.openfoodfacts.org)
        val apiResult = openFoodFactsRepository.getProductByBarcode(cleanBarcode)

        apiResult.fold(
            onSuccess = { productData ->
                if (productData != null && !productData.productName.isNullOrBlank()) {
                    val name = productData.productName.trim()
                    val brand = productData.marke?.trim()
                    val (qty, unit) = parseQuantityAndUnit(productData.quantity)
                    val rawCategory = productData.categories?.split(",")?.firstOrNull()?.trim()
                    val category = rawCategory
                        ?.removePrefix("de:")?.removePrefix("en:")
                        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                        ?.ifBlank { "Sonstiges" } ?: "Sonstiges"

                    val nutrition = openFoodFactsRepository.parseNutriments(productData)

                    // 3. Sofort in lokale Datenbank cachen für zukünftige Offline-Scans
                    val newCachedProduct = CachedProduct(
                        barcode = cleanBarcode,
                        name = name,
                        brand = brand,
                        imageUrl = productData.imageUrl,
                        imageSmallUrl = productData.imageSmallUrl,
                        kcal = nutrition.caloriesKcal?.toInt() ?: 0,
                        nutriScore = nutrition.nutriScore ?: "",
                        allergens = productData.allergens ?: "",
                        ecoScore = productData.ecoScore ?: "",
                        categories = category,
                        sugar = nutrition.sugars ?: 0.0,
                        salt = nutrition.salt ?: 0.0,
                        protein = nutrition.proteins ?: 0.0,
                        fat = nutrition.fat ?: 0.0,
                        timestamp = System.currentTimeMillis()
                    )
                    dao.insertCachedProduct(newCachedProduct)

                    // In market_products als ProductEntity sichern
                    val newProductEntity = ProductEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        cleanName = name,
                        receiptPattern = cleanBarcode,
                        category = category,
                        nutriScore = nutrition.nutriScore,
                        caloriesKcal = nutrition.caloriesKcal,
                        fat = nutrition.fat,
                        saturatedFat = nutrition.saturatedFat,
                        carbohydrates = nutrition.carbohydrates,
                        sugars = nutrition.sugars,
                        proteins = nutrition.proteins,
                        salt = nutrition.salt,
                        allergens = nutrition.allergens,
                        additives_tags = nutrition.additives
                    )
                    productDao?.insertProducts(listOf(newProductEntity))

                    BarcodeScanResult.Success(
                        barcode = cleanBarcode,
                        name = name,
                        brand = brand,
                        quantity = qty,
                        unit = unit,
                        category = category,
                        imageUrl = productData.imageUrl,
                        isFromOfflineDb = false,
                        nutriScore = nutrition.nutriScore,
                        caloriesKcal = nutrition.caloriesKcal,
                        fat = nutrition.fat,
                        saturatedFat = nutrition.saturatedFat,
                        carbohydrates = nutrition.carbohydrates,
                        sugars = nutrition.sugars,
                        proteins = nutrition.proteins,
                        salt = nutrition.salt
                    )
                } else {
                    BarcodeScanResult.Failure.NotFound(cleanBarcode)
                }
            },
            onFailure = { throwable ->
                // Bei Timeout oder fehlender Internetverbindung
                BarcodeScanResult.Failure.NetworkError(cleanBarcode, throwable)
            }
        )
    }

    /**
     * Speichert eine manuelle Zuweisung eines Barcodes lokal in Room ab.
     */
    suspend fun saveManualBarcodeProduct(
        barcode: String,
        name: String,
        brand: String? = null,
        quantity: Int = 1,
        unit: String = "Stk.",
        category: String = "Sonstiges",
        imageUrl: String? = null,
        nutriScore: String? = null,
        caloriesKcal: Double? = null,
        fat: Double? = null,
        saturatedFat: Double? = null,
        carbohydrates: Double? = null,
        sugars: Double? = null,
        proteins: Double? = null,
        salt: Double? = null
    ) = withContext(Dispatchers.IO) {
        val cleanBarcode = barcode.trim()
        val cleanName = name.trim()
        if (cleanBarcode.isBlank() || cleanName.isBlank()) return@withContext

        val cachedProduct = CachedProduct(
            barcode = cleanBarcode,
            name = cleanName,
            brand = brand?.trim(),
            imageUrl = imageUrl,
            imageSmallUrl = null,
            kcal = caloriesKcal?.toInt() ?: 0,
            nutriScore = nutriScore ?: "",
            allergens = "",
            ecoScore = "",
            categories = category,
            sugar = sugars ?: 0.0,
            salt = salt ?: 0.0,
            protein = proteins ?: 0.0,
            fat = fat ?: 0.0,
            timestamp = System.currentTimeMillis()
        )
        dao.insertCachedProduct(cachedProduct)

        val productEntity = ProductEntity(
            id = java.util.UUID.randomUUID().toString(),
            cleanName = cleanName,
            receiptPattern = cleanBarcode,
            category = category,
            nutriScore = nutriScore,
            caloriesKcal = caloriesKcal,
            fat = fat,
            saturatedFat = saturatedFat,
            carbohydrates = carbohydrates,
            sugars = sugars,
            proteins = proteins,
            salt = salt
        )
        productDao?.insertProducts(listOf(productEntity))
    }

    private fun parseQuantityAndUnit(raw: String?): Pair<Int, String> {
        if (raw.isNullOrBlank()) return Pair(1, "Stk.")
        val regex = Regex("""^(\d+(?:[.,]\d+)?)\s*([a-zA-ZäöüÄÖÜ%]+)?.*$""")
        val match = regex.find(raw.trim())
        return if (match != null) {
            val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull()?.toInt() ?: 1
            val unit = match.groupValues.getOrNull(2)?.ifBlank { "Stk." } ?: "Stk."
            Pair(if (amount <= 0) 1 else amount, unit)
        } else {
            Pair(1, "Stk.")
        }
    }
}
