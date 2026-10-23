package com.example.myapplication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.MarketProductDatabase
import com.example.myapplication.data.MarketProductEntry
import com.example.myapplication.data.Product
import com.example.myapplication.data.ProductEntity
import com.example.myapplication.data.CategoryDetector
import com.example.myapplication.data.OpenFoodFactsRepository
import com.example.myapplication.data.SyncManager
import com.example.myapplication.data.UserLearnedCorrection
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ProductSuggestion(
    val cleanName: String,
    val category: String = "SONSTIGES",
    val unit: String = "Stk.",
    val defaultStorage: String = "Kühlschrank",
    val source: String = "market_products.db"
)

typealias ReceiptViewModel = ReceiptScanViewModel

class ReceiptScanViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).fridgeItemDao()
    private val offRepository = OpenFoodFactsRepository()

    private val _scannedProducts = MutableStateFlow<List<Product>>(emptyList())
    val scannedProducts: StateFlow<List<Product>> = _scannedProducts

    private val _searchResults = MutableStateFlow<List<MarketProductEntry>>(emptyList())
    val searchResults: StateFlow<List<MarketProductEntry>> = _searchResults

    private val _suggestions = MutableStateFlow<List<ProductSuggestion>>(emptyList())
    val suggestions: StateFlow<List<ProductSuggestion>> = _suggestions

    private val _queryFlow = MutableStateFlow("")

    init {
        loadUserLearnedCorrections()
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            _queryFlow
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { query ->
                    executeDebouncedSearch(query)
                }
        }
    }

    fun onQueryChanged(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.length >= 2) {
            viewModelScope.launch(Dispatchers.IO) {
                val localSuggestions = loadLocalSuggestions(cleanQuery)
                withContext(Dispatchers.Main) {
                    if (_queryFlow.value == query || _queryFlow.value.isEmpty()) {
                        _suggestions.value = localSuggestions.take(7)
                    }
                }
            }
        } else {
            _suggestions.value = emptyList()
        }
        _queryFlow.value = query
    }

    fun searchSuggestions(query: String) {
        onQueryChanged(query)
    }

    private suspend fun loadLocalSuggestions(cleanQuery: String): List<ProductSuggestion> {
        val results = mutableListOf<ProductSuggestion>()
        try {
            // 1. UserLearnedCorrections (Höchste Priorität)
            val learnedMap = ReceiptImportSanitizer.getLearnedCorrections()
            val qLower = cleanQuery.lowercase()
            for ((raw, corrected) in learnedMap) {
                if (raw.contains(qLower) || corrected.lowercase().contains(qLower)) {
                    results.add(
                        ProductSuggestion(
                            cleanName = corrected,
                            category = CategoryDetector.detectCategory(corrected).displayName,
                            unit = "Stk.",
                            defaultStorage = "Kühlschrank",
                            source = "user_learned"
                        )
                    )
                }
            }

            // 2. MarketDictionaryHelper Vorschläge
            val topFromHelper = MarketDictionaryHelper.getSuggestions(cleanQuery, getApplication(), limit = 7)
            for (name in topFromHelper) {
                results.add(
                    ProductSuggestion(
                        cleanName = name,
                        category = CategoryDetector.detectCategory(name).displayName,
                        unit = "Stk.",
                        defaultStorage = "Kühlschrank",
                        source = "market_products.db"
                    )
                )
            }

            // 3. Lokale DB ProductDao
            if (results.size < 7) {
                val productDao = MarketProductDatabase.getInstance(getApplication()).productDao()
                val localEntities = productDao.searchAutocomplete(cleanQuery, limit = 7)
                for (entity in localEntities) {
                    results.add(
                        ProductSuggestion(
                            cleanName = entity.cleanName,
                            category = entity.category.ifBlank { CategoryDetector.detectCategory(entity.cleanName).displayName },
                            unit = "Stk.",
                            defaultStorage = entity.defaultStorage,
                            source = "market_products.db"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results.distinctBy { it.cleanName.lowercase().trim() }
    }

    private suspend fun executeDebouncedSearch(query: String) = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank() || cleanQuery.length < 2) {
            withContext(Dispatchers.Main) {
                _suggestions.value = emptyList()
            }
            return@withContext
        }

        // 1. Lokale Vorschläge laden
        val localList = loadLocalSuggestions(cleanQuery)

        // 2. Open Food Facts API abfragen (passende deutsche Produktnamen)
        val offList = mutableListOf<ProductSuggestion>()
        try {
            val offProducts = offRepository.searchProducts(cleanQuery)
            for (item in offProducts) {
                val name = item.productName?.trim()
                if (!name.isNullOrBlank() && name.length >= 2) {
                    val category = CategoryDetector.detectCategory(name).displayName
                    offList.add(
                        ProductSuggestion(
                            cleanName = name,
                            category = category,
                            unit = "Stk.",
                            defaultStorage = "Kühlschrank",
                            source = "openfoodfacts"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Fallback: Webservice falls lokal und OFF leer
        val webList = if (localList.isEmpty() && offList.isEmpty()) {
            fetchWebserviceList(cleanQuery)
        } else emptyList()

        // 4. Beide Ergebnislisten zusammenführen (Duplikate entfernen, maximal 5 bis 7 beste Vorschläge anzeigen)
        val combined = (localList + offList + webList)
            .distinctBy { it.cleanName.lowercase().trim() }
            .take(7)

        withContext(Dispatchers.Main) {
            _suggestions.value = combined
        }
    }

    private suspend fun fetchWebserviceList(query: String): List<ProductSuggestion> = withContext(Dispatchers.IO) {
        val webSuggestions = mutableListOf<ProductSuggestion>()
        try {
            val context = getApplication<Application>()
            val configuredUrl = SyncManager.getWebserviceUrl(context)
            val candidateUrls = listOf(
                configuredUrl,
                "http://192.168.0.223:8080",
                "http://10.0.2.2:8080",
                "http://localhost:8080"
            ).distinct()

            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            for (baseUrl in candidateUrls) {
                try {
                    val url = URL("$baseUrl/api/products/search?q=$encodedQuery")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 1500
                        readTimeout = 2000
                        requestMethod = "GET"
                        setRequestProperty("Accept", "application/json")
                    }
                    if (conn.responseCode == 200) {
                        val responseText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        conn.disconnect()
                        val jsonObject = JsonParser.parseString(responseText).asJsonObject
                        val resultsArray = jsonObject.getAsJsonArray("results")
                        for (elem in resultsArray) {
                            val obj = elem.asJsonObject
                            webSuggestions.add(
                                ProductSuggestion(
                                    cleanName = obj.get("cleanName")?.asString ?: "",
                                    category = obj.get("category")?.asString ?: "SONSTIGES",
                                    unit = obj.get("unit")?.asString ?: "Stk.",
                                    source = obj.get("source")?.asString ?: "webservice"
                                )
                            )
                        }
                        if (webSuggestions.isNotEmpty()) return@withContext webSuggestions
                    }
                    conn.disconnect()
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        webSuggestions
    }

    fun setScannedProducts(products: List<Product>) {
        viewModelScope.launch(Dispatchers.IO) {
            val productDao = MarketProductDatabase.getInstance(getApplication()).productDao()
            val matched = products.map { prod ->
                val queryStr = prod.rawText ?: prod.name
                val match = productDao.matchOcrText(queryStr)
                    ?: productDao.findByReceiptPattern(queryStr)
                    ?: productDao.findByCleanName(queryStr)
                if (match != null) {
                    prod.copy(
                        name = match.cleanName,
                        category = match.category,
                        defaultStorage = match.defaultStorage
                    )
                } else {
                    prod
                }
            }
            withContext(Dispatchers.Main) {
                _scannedProducts.value = matched
            }
        }
    }

    fun loadUserLearnedCorrections() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val corrections = dao.getAllUserLearnedCorrectionsSync()
                val map = corrections.associate { it.receiptRawText to it.correctedName }
                ReceiptImportSanitizer.setLearnedCorrections(map)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun searchCatalog(query: String) {
        val q = ReceiptImportSanitizer.intelligentSanitize(query)
        if (q.isBlank() || q.length < 2) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbDao = MarketProductDatabase.getInstance(getApplication()).marketProductDao()
                val results = dbDao.searchMarketProductsLike(q)
                _searchResults.value = results
            } catch (e: Exception) {
                e.printStackTrace()
                _searchResults.value = emptyList()
            }
        }
    }

    fun updateProductName(
        index: Int,
        newName: String,
        category: String = "SONSTIGES",
        unit: String = "Stk.",
        purchaseDate: Long? = null,
        receiptUri: String? = null
    ) {
        updateProductDetails(
            index = index,
            newName = newName,
            newCategory = category,
            newUnit = unit,
            newPurchaseDate = purchaseDate,
            newReceiptUri = receiptUri
        )
    }

    fun updateProductDetails(
        index: Int,
        newName: String? = null,
        newCategory: String? = null,
        newUnit: String? = null,
        newImageUrl: String? = null,
        newPurchaseDate: Long? = null,
        newReceiptUri: String? = null
    ) {
        val currentList = _scannedProducts.value.toMutableList()
        if (index in currentList.indices) {
            val oldProduct = currentList[index]
            val updated = oldProduct.copy(
                name = newName?.takeIf { it.isNotBlank() } ?: oldProduct.name,
                category = newCategory?.takeIf { it.isNotBlank() } ?: oldProduct.category,
                unit = newUnit?.takeIf { it.isNotBlank() } ?: oldProduct.unit,
                imageUrl = newImageUrl?.takeIf { it.isNotBlank() } ?: oldProduct.imageUrl,
                purchaseDate = newPurchaseDate ?: oldProduct.purchaseDate ?: System.currentTimeMillis(),
                receiptUri = newReceiptUri ?: oldProduct.receiptUri
            )
            currentList[index] = updated
            _scannedProducts.value = currentList.toList()

            val oldRawName = oldProduct.rawText ?: oldProduct.name
            saveUserLearnedCorrection(oldRawName, updated.name)
        }
    }

    fun saveUserLearnedCorrection(rawReceiptText: String, correctedName: String) {
        if (rawReceiptText.isBlank() || correctedName.isBlank() || rawReceiptText.equals(correctedName, ignoreCase = true)) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val correction = UserLearnedCorrection(
                    receiptRawText = rawReceiptText.trim(),
                    correctedName = correctedName.trim()
                )
                dao.insertUserLearnedCorrection(correction)
                ReceiptImportSanitizer.addLearnedCorrection(rawReceiptText, correctedName)

                val file = File(getApplication<Application>().filesDir, "pending_corrections.json")
                val list = mutableListOf<Map<String, Any>>()
                if (file.exists()) {
                    try {
                        val content = file.readText()
                        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                        val existing: List<Map<String, Any>>? = Gson().fromJson(content, type)
                        if (existing != null) list.addAll(existing)
                    } catch (e: Exception) { e.printStackTrace() }
                }
                list.removeAll { it["rawName"]?.toString()?.equals(rawReceiptText, ignoreCase = true) == true }
                list.add(mapOf(
                    "rawName" to rawReceiptText.trim(),
                    "cleanName" to correctedName.trim(),
                    "unit" to "Stk.",
                    "category" to "Vorrat",
                    "storageLocation" to "Kühlschrank",
                    "timestamp" to System.currentTimeMillis()
                ))
                file.writeText(Gson().toJson(list))

                val prefs = getApplication<Application>().getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                if (prefs.getBoolean("share_corrections_enabled", true)) {
                    val synced = SyncManager.syncCorrections(getApplication(), list)
                    if (synced > 0) {
                        file.delete()
                        val curr = prefs.getInt("synced_corrections_count", 0)
                        prefs.edit().putInt("synced_corrections_count", curr + synced).apply()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
