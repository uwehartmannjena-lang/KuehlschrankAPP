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

    // --- ECHTE NAMENSBEREINIGUNG ---
    private fun smartSanitize(rawText: String): String {
        var text = rawText.trim()

        // Mappe Frischetheken- und Waagen-Codes (PLU) auf lesbare Kategorien
        if (text.contains(Regex("""(?i)Metzgerei.*PLU.*"""))) return "Fleisch-/Wurstwaren (Theke)"
        if (text.contains(Regex("""(?i)Käse.*PLU.*"""))) return "Käse (Frischetheke)"
        if (text.contains(Regex("""(?i)Backwaren.*PLU.*"""))) return "Backwaren (Frischetheke)"

        // 1. Marken-Präfixe entfernen (ja!, KLC, Gut&Günstig, etc.)
        val brandRegex = Regex("(?i)^(ja!|klc|k-classic|g&g|gut\\s*&\\s*günstig|rewe\\s*beste\\s*wahl)\\s*")
        text = text.replace(brandRegex, "")

        // Müll und OCR-Fehler wie "0UZ" entfernen sowie Marken-Tippfehler korrigieren
        text = text.replace(Regex("""(?i)\b(0uz|0uz\.|[09][A-Z]{2,})\b"""), " ")
                   .replace(Regex("""(?i)\bfin?ess[es]*\b"""), "Finesse")
                   .replace(Regex("""(?i)\b(?<!Herta\s)Finesse\b"""), "Herta Finesse")
                   .replace(Regex("""(?i)\bHerta\s+Finesse\s+Gutfried\b"""), "Herta Finesse")
                   .replace(Regex("""(?i)\bGutfried\s+Finesse\b"""), "Herta Finesse")

        // 2. Gewichte, Mengen und Preise am Ende entfernen (500g, 1.5L, 1 Mg, Nat6x, Zero1,25)
        val weightPriceRegex = Regex("""(?i)(\s+\d+[,.]?\d*\s*(g|mg|kg|l|ml|stück|stk|x|€)\b|\s+\d+[,.]\d{2}$|(?<=[a-zäöüß])\d+([.,]\d+)?\s*(g|mg|kg|l|ml|stück|stk|st|x|€)?\b)""")
        text = text.replace(weightPriceRegex, "")

        // 3. Typische Abkürzungen übersetzen (Dictionary)
        val abbreviationMap = mapOf(
            "tom pass" to "Passierte Tomaten",
            "tom. pass." to "Passierte Tomaten",
            "hackfl" to "Hackfleisch",
            "hackfl." to "Hackfleisch",
            "h-milch 3,5%" to "H-Milch",
            "zwieb" to "Zwiebeln",
            "apfelm" to "Apfelmus",
            "weiz" to "Weizen",
            "schw.tonicw" to "Schweppes Tonic Water",
            "schw. tonicw" to "Schweppes Tonic Water",
            "sw wild berry" to "Schweppes Wild Berry",
            "hxm weissherbst" to "Hex vom Dasenstein Weissherbst",
            "dinkel joh beerrührk" to "Dinkel Johannisbeer Rührkuchen",
            "haus handkäse" to "Hausmacher Handkäse",
            "haus handkaese" to "Hausmacher Handkäse",
            "finesse pfeffer" to "Herta Finesse Pfeffer",
            "fin. pfeffer" to "Herta Finesse Pfeffer",
            "finesse hähnchenbr.k" to "Herta Finesse Hähnchenbrust",
            "finesse hähnchenbr. k" to "Herta Finesse Hähnchenbrust",
            "fin. hähnchenbrust m" to "Herta Finesse Hähnchenbrust",
            "fin. hähnchenbrust c" to "Herta Finesse Hähnchenbrust",
            "finesse hähnchenbrust" to "Herta Finesse Hähnchenbrust",
            "fin. hähnchenbrust" to "Herta Finesse Hähnchenbrust",
            "gutfried hähnchenbrust m" to "Gutfried Hähnchenbrust",
            "gutfried hähnchenbrust k" to "Gutfried Hähnchenbrust"
        )
        val lowerText = text.lowercase()
        for ((key, value) in abbreviationMap) {
            if (lowerText.contains(key)) {
                if (value.isNotBlank() && text.trim().equals(value.trim(), ignoreCase = true)) continue
                text = text.replace(Regex("(?i)" + Regex.escape(key)), value)
                break // Erste gefundene Abkürzung nutzen
            }
        }

        // Blacklist-Regex: Störende Einzelwörter am Ende ohne sinnvollen Kontext entfernen (Zero, Light, Classic, Premium)
        text = text.replace(Regex("(?i)\\s+\\b(zero|light|classic|premium)\\b\\s*$"), "")

        // 4. Sonderzeichen bereinigen und Title Case
        text = text.replace(Regex("[.,*\\-+]"), " ")
        text = text.replace(Regex("\\s+"), " ").trim()

        // Title Case (Erster Buchstabe groß)
        return text.split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }

    fun searchCatalog(query: String) {
        val q = smartSanitize(query)
        if (q.isBlank() || q.length < 2) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbDao = MarketProductDatabase.getInstance(getApplication()).marketProductDao()
                val results = dbDao.searchMarketProductsLike("%$q%")
                _searchResults.value = results
            } catch (e: Exception) {
                e.printStackTrace()
                _searchResults.value = emptyList()
            }
        }
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val a = s1.lowercase()
        val b = s2.lowercase()
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }

        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }

    fun searchSuggestions(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val q = smartSanitize(query)
                val localLearned = ReceiptImportSanitizer.getLearnedCorrection(query) ?: ReceiptImportSanitizer.getLearnedCorrection(q)

                val dbDao = MarketProductDatabase.getInstance(getApplication()).marketProductDao()
                var dbResults = if (q.isBlank()) {
                    dbDao.getGeneralProducts()
                } else {
                    // FIX: Platzhalter (%) für Teilwörter
                    var list = dbDao.searchMarketProductsLike("%$q%")
                    if (list.isEmpty() && q.contains(" ")) {
                        val tokens = q.split(Regex("""\s+""")).filter { it.length >= 2 }
                        for (token in tokens) {
                            val tokenResults = dbDao.searchMarketProductsLike("%$token%")
                            if (tokenResults.isNotEmpty()) {
                                list = tokenResults
                                break
                            }
                        }
                    }
                    list
                }

                // Fallback: Wenn LIKE-Suche weniger als 3 Ergebnisse liefert, performante Fuzzy-Suche ausführen
                if (q.isNotBlank() && dbResults.size < 3) {
                    val prefix = if (q.length >= 2) q.take(2) else q
                    val candidateProducts = dbDao.searchMarketProductsLike(prefix)
                    val fuzzyMatches = candidateProducts.filter { entry ->
                        val name = entry.cleanName.ifBlank { entry.receiptPattern }
                        if (name.isBlank()) return@filter false
                        val distFull = levenshteinDistance(q, name)
                        val distWord = name.split(Regex("""\s+""")).minOfOrNull { levenshteinDistance(q, it) } ?: Int.MAX_VALUE
                        val minDist = minOf(distFull, distWord)
                        minDist in 1..2
                    }
                    dbResults = (dbResults + fuzzyMatches).distinctBy { it.cleanName.ifBlank { it.receiptPattern } }
                }

                val dbNames = dbResults
                    .map { it.cleanName.ifBlank { it.receiptPattern } }
                    .filter { it.isNotBlank() }

                val dictNames = MarketDictionaryHelper.getSuggestions(q, getApplication(), limit = 5)
                val combined = mutableListOf<String>()
                if (localLearned != null) combined.add(localLearned)
                combined.addAll(dbNames)
                combined.addAll(dictNames)

                val sortedSuggestions = combined
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sortedWith(
                        compareBy<String> { name ->
                            val lowerName = name.lowercase()
                            val lowerQ = q.lowercase()
                            val fullDist = levenshteinDistance(lowerQ, lowerName)
                            val wordDist = lowerName.split(Regex("""\s+""")).minOfOrNull { w -> levenshteinDistance(lowerQ, w) } ?: Int.MAX_VALUE
                            minOf(fullDist, wordDist)
                        }
                        .thenBy { levenshteinDistance(q.lowercase(), it.lowercase()) }
                        .thenByDescending { it.lowercase().startsWith(q.lowercase()) }
                    )
                    .take(5)

                _suggestions.value = sortedSuggestions
            } catch (e: Exception) {
                e.printStackTrace()
                val sq = smartSanitize(query)
                val learned = ReceiptImportSanitizer.getLearnedCorrection(sq)
                val dictResults = MarketDictionaryHelper.getSuggestions(sq, getApplication(), limit = 5).toMutableList()
                if (learned != null && !dictResults.contains(learned)) dictResults.add(0, learned)
                _suggestions.value = dictResults.take(5)
            }
        }
    }

    fun setScannedProducts(products: List<Product>) {
        val sanitizedProducts = products.map { product ->
            val clean = smartSanitize(product.name)
            val match = try {
                MarketDictionaryHelper.findBestMatch(clean, getApplication())
                    ?: MarketDictionaryHelper.findBestMatch(product.name, getApplication())
            } catch (_: Exception) { null }
            val finalName = match?.clean_name ?: clean
            product.copy(name = finalName)
        }
        _scannedProducts.value = sanitizedProducts
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