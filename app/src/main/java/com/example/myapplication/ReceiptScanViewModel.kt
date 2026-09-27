package com.example.myapplication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.MarketProductDatabase
import com.example.myapplication.data.MarketProductEntry
import com.example.myapplication.data.Product
import com.example.myapplication.data.UserLearnedCorrection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ReceiptScanViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).fridgeItemDao()

    private val _scannedProducts = MutableStateFlow<List<Product>>(emptyList())
    val scannedProducts: StateFlow<List<Product>> = _scannedProducts

    private val _searchResults = MutableStateFlow<List<MarketProductEntry>>(emptyList())
    val searchResults: StateFlow<List<MarketProductEntry>> = _searchResults

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions

    init {
        loadUserLearnedCorrections()
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

    fun searchSuggestions(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val q = ReceiptImportSanitizer.intelligentSanitize(query)
                val localLearned = ReceiptImportSanitizer.getLearnedCorrection(query) ?: ReceiptImportSanitizer.getLearnedCorrection(q)
                
                val dbDao = MarketProductDatabase.getInstance(getApplication()).marketProductDao()
                val dbResults = if (q.isBlank()) {
                    dbDao.getGeneralProducts()
                } else {
                    var list = dbDao.searchMarketProductsLike(q)
                    if (list.isEmpty() && q.contains(" ")) {
                        val tokens = q.split(Regex("""\s+""")).filter { it.length >= 2 }
                        for (token in tokens) {
                            val tokenResults = dbDao.searchMarketProductsLike(token)
                            if (tokenResults.isNotEmpty()) {
                                list = tokenResults
                                break
                            }
                        }
                    }
                    list
                }
                val dbNames = dbResults
                    .map { it.cleanName.ifBlank { it.receiptPattern } }
                    .filter { it.isNotBlank() }

                val dictNames = MarketDictionaryHelper.getSuggestions(q, getApplication(), limit = 5)
                val combined = mutableListOf<String>()
                if (localLearned != null) combined.add(localLearned)
                combined.addAll((dbNames + dictNames).distinct())

                _suggestions.value = combined.distinct().take(5)
            } catch (e: Exception) {
                e.printStackTrace()
                val sq = ReceiptImportSanitizer.intelligentSanitize(query)
                val learned = ReceiptImportSanitizer.getLearnedCorrection(sq)
                val dictResults = MarketDictionaryHelper.getSuggestions(sq, getApplication(), limit = 5).toMutableList()
                if (learned != null && !dictResults.contains(learned)) dictResults.add(0, learned)
                _suggestions.value = dictResults.take(5)
            }
        }
    }

    fun setScannedProducts(products: List<Product>) {
        _scannedProducts.value = products
    }

    fun updateProductName(index: Int, newName: String) {
        updateProductDetails(index, newName = newName)
    }

    fun updateProductDetails(index: Int, newName: String? = null, newImageUrl: String? = null) {
        val currentList = _scannedProducts.value.toMutableList()
        if (index in currentList.indices) {
            val oldProduct = currentList[index]
            val updated = oldProduct.copy(
                name = newName?.takeIf { it.isNotBlank() } ?: oldProduct.name,
                imageUrl = newImageUrl?.takeIf { it.isNotBlank() } ?: oldProduct.imageUrl
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
