package com.example.myapplication

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.myapplication.billing.BillingManager
import com.example.myapplication.data.BudgetConfig
import com.example.myapplication.data.CachedProduct
import com.example.myapplication.data.ConsumedItem
import com.example.myapplication.data.ConsumptionPattern
import com.example.myapplication.data.DealWorker
import com.example.myapplication.data.FoodCategory
import com.example.myapplication.data.FridgeItem
import com.example.myapplication.data.FridgeItemDao
import com.example.myapplication.data.GeminiRepository
import com.example.myapplication.data.Household
import com.example.myapplication.data.HouseholdLog
import com.example.myapplication.data.LearningEntry
import com.example.myapplication.data.MealPlan
import com.example.myapplication.data.OpenFoodFactsApi
import com.example.myapplication.data.PriceRecord
import com.example.myapplication.data.Product
import com.example.myapplication.data.ShoppingItem
import com.example.myapplication.data.StoreOffer
import com.example.myapplication.data.WastedItem
import com.google.android.gms.tasks.Tasks
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.round

class FridgeViewModelFactory(private val dao: FridgeItemDao, private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = FridgeViewModel(dao, context.applicationContext) as T
}

class FridgeViewModel(val dao: FridgeItemDao, private val applicationContext: Context) : ViewModel() {
    val allFridgeItems = dao.getAllFridgeItems()
    val wastedItems = dao.getAllWastedItems()
    val consumedItems = dao.getAllConsumedItems()
    val mealPlans = dao.getAllMealPlans()
    val recommendations = dao.getAllConsumptionPatterns()

    val currentHouseholdId = mutableStateOf("default")
    val currentUserRole = mutableStateOf("Erwachsener")

    fun setHousehold(id: String) { currentHouseholdId.value = id; saveSetting("current_household", id) }
    fun setUserRole(role: String) { currentUserRole.value = role; saveSetting("user_role", role) }

    fun addHousehold(name: String) {
        viewModelScope.launch {
            dao.insertHousehold(Household(name = name, color = Color.Gray.toArgb()))
        }
    }

    fun logAction(action: String, itemName: String) {
        viewModelScope.launch {
            dao.insertLog(HouseholdLog(
                householdId = currentHouseholdId.value,
                userId = "user_1",
                userName = "Ich",
                action = action,
                itemName = itemName
            ))
        }
    }

    fun processVoiceInput(text: String) {
        viewModelScope.launch {
            val normalized = text.lowercase()
            if (normalized.contains("füge") || normalized.contains("hinzu")) {
                val quantity = Regex("(\\d+)").find(normalized)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val unit = if (normalized.contains("liter")) "l" else if (normalized.contains("gramm")) "g" else "Stk."
                val words = normalized.split(" ")
                val nameIdx = words.indexOf("liter").takeIf { it != -1 } ?: words.indexOf("gramm").takeIf { it != -1 } ?: words.indexOf("füge")
                val name = if (nameIdx + 1 < words.size) words[nameIdx + 1] else "Unbekannt"

                addItem(FridgeItem(name = name.replaceFirstChar { it.uppercase() }, quantity = quantity, unit = unit, householdId = currentHouseholdId.value))
                logAction("Hinzugefügt (Sprache)", name)
            }
        }
    }

    val searchInput = mutableStateOf("")
    val themeColor = mutableStateOf(Color(0xFF4CAF50))
    val isSyncing = mutableStateOf(false)
    val expiryWarningDays = mutableIntStateOf(3)
    val filterExpiryDays = mutableIntStateOf(0)
    val itemSpacing = mutableIntStateOf(8)
    val selectedItems = mutableStateOf(setOf<String>())
    val isCompactMode = mutableStateOf(false)
    val cardCornerRadius = mutableIntStateOf(12)
    val favoriteIconName = mutableStateOf("Star")
    val favoriteIconColor = mutableStateOf(Color(0xFFFFB300))

    val showImportPreview = mutableStateOf(false)
    val importCandidates = mutableStateOf(emptyList<FridgeItem>())
    val recognizedCount = mutableIntStateOf(0)
    val receiptBitmap = mutableStateOf<Bitmap?>(null)
    val currentIconIndex = mutableIntStateOf(1)
    val warnerMessages = mutableStateListOf<String>()
    val learnedCorrections = mutableStateMapOf<String, String>()
    val learnedEntriesMap = mutableStateMapOf<String, LearningEntry>()

    init {
        viewModelScope.launch {
            dao.getAllLearningData().collect { list ->
                learnedCorrections.clear()
                learnedEntriesMap.clear()
                list.forEach {
                    learnedCorrections[it.rawName] = it.correctedName
                    learnedEntriesMap[it.rawName] = it
                }
            }
        }
    }

    val filterCategory = mutableStateOf<String?>(null)
    val filterLocation = mutableStateOf<String?>(null)
    val sortOrder = mutableStateOf("expiry") // "name", "price", "expiry", "category"
    val viewMode = mutableStateOf("list") // "list", "grid", "gallery", "kanban", "calendar", "tree", "carousel"
    val showNutritionalDetails = mutableStateOf(false)

    val billingManager = BillingManager(applicationContext, viewModelScope)

    val allShoppingItems = dao.getAllShoppingItems()
    val householdSize = mutableIntStateOf(1)

    val selectedDietProfile = mutableStateOf("Standard")
    val allergenWarnings = mutableStateListOf<String>()

    val useLocalAIFilter = mutableStateOf(false)

    val unknownBarcodeForPhoto = mutableStateOf<String?>(null)

    val nfcScannedId = mutableStateOf<String?>(null)
    val showNfcDialog = mutableStateOf(false)

    val activeRecalls = mutableStateListOf<String>()

    init {
        runAutomaticRecallCheck()
        billingManager.startConnection()
    }

    private fun runAutomaticRecallCheck() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Die BundesAPI fragt alle Warnungen ab
                val response = httpClient.get("https://lebensmittelwarnung.bundesapi.de/api/v1/warnings")
                val responseText = response.bodyAsText()

                // Da das Format schwankt, lesen wir alle Titel/Warnungen per Regex/JSON String-Suche aus
                val items = allFridgeItems.first()
                if (items.isEmpty()) return@launch

                val foundWarnings = mutableSetOf<String>()

                for (item in items) {
                    val searchTerms = item.name.lowercase().split(" ").filter { it.length > 3 }
                    if (searchTerms.isEmpty()) continue

                    // Simple textbasierte Suche im JSON Response
                    if (searchTerms.any { responseText.lowercase().contains(it) } || responseText.lowercase().contains(item.name.lowercase())) {
                        foundWarnings.add("⚠️ Rückruf-Verdacht für: ${item.name}!\nBitte prüfe die offizielle Seite (lebensmittelwarnung.de).")
                    }
                }

                withContext(Dispatchers.Main) {
                    if (foundWarnings.isNotEmpty()) {
                        activeRecalls.addAll(foundWarnings)
                    }
                }
            } catch (e: Exception) {
                // Fallback: Wenn die BundesAPI down ist, scrapen wir die offizielle Seite
                try {
                    val document = Jsoup.connect("https://www.lebensmittelwarnung.de/").get()
                    val text = document.text().lowercase()
                    val items = allFridgeItems.first()

                    val foundWarnings = mutableSetOf<String>()
                    for (item in items) {
                        if (item.name.length > 3 && text.contains(item.name.lowercase())) {
                            foundWarnings.add("⚠️ Rückruf-Verdacht für: ${item.name}!\nAuf lebensmittelwarnung.de gelistet.")
                        }
                    }
                    withContext(Dispatchers.Main) {
                        activeRecalls.addAll(foundWarnings)
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }
    }

    fun handleNfcDiscovery(tagId: String) {
        viewModelScope.launch {
            nfcScannedId.value = tagId
            val items = allFridgeItems.first()
            val existingItem = items.find { it.nfcTagId == tagId }
            if (existingItem != null) {
                if (existingItem.storageLocation == "Gefrierfach") {
                    consumeItem(existingItem)
                    Toast.makeText(applicationContext, "${existingItem.name} ausgebucht!", Toast.LENGTH_SHORT).show()
                } else {
                    dealAlertItem.value = Pair("Inhalt: ${existingItem.name}", "Gefrierdose")
                }
            } else {
                showNfcDialog.value = true
            }
        }
    }

    fun linkNfcTagToItem(item: FridgeItem) {
        val tagId = nfcScannedId.value ?: return
        viewModelScope.launch {
            dao.updateItem(item.copy(nfcTagId = tagId))
            showNfcDialog.value = false
            nfcScannedId.value = null
        }
    }

    val predictiveShoppingList: Flow<List<ShoppingItem>> = combine(
        allFridgeItems,
        recommendations
    ) { inventory, patterns ->
        patterns.filter { pattern ->
            val inInventory = inventory.any { it.name.contains(pattern.itemName, true) }
            val predictionDue = pattern.predictedNextNeed < System.currentTimeMillis() + TimeUnit.DAYS.toMillis(4)
            !inInventory || predictionDue
        }.map { ShoppingItem(name = it.itemName, quantity = 1, unit = it.unit) }
    }

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { kotlinx.serialization.json.Json { ignoreUnknownKeys = true } }
    }
    private val offerRepository = com.example.myapplication.data.OfferRepository(httpClient)
    val bringApi = com.example.myapplication.data.BringApi(httpClient)
    private val geminiRepository = GeminiRepository()

    val dealAlertStores = mutableStateOf(setOf("Kaufland", "Lidl", "Rewe", "Aldi", "Edeka", "dm"))
    val dealAlertItem = mutableStateOf<Pair<String, String>?>(null)
    val dealSearchMethod = mutableIntStateOf(0)

    val currentOffers = mutableStateListOf<StoreOffer>()
    val showOfferAlert = mutableStateOf(false)
    val watchlist = mutableStateOf(setOf<String>())

    fun toggleWatchlist(name: String) {
        val current = watchlist.value.toMutableSet()
        if (current.contains(name)) current.remove(name) else current.add(name)
        watchlist.value = current
        saveSetting("global_watchlist", current)
        checkOffersForFavorites()
    }

    fun checkOffersForFavorites() {
        viewModelScope.launch {
            isSyncing.value = true
            val favoriteNames = allFridgeItems.first().filter { it.isFavorite }.map { it.name }
            val shoppingNames = dao.getAllShoppingItems().first().map { it.name }
            val allSearchItems = (favoriteNames + shoppingNames + watchlist.value).distinct()

            val offers = offerRepository.checkLocalOffers(allSearchItems)
            currentOffers.clear()
            currentOffers.addAll(offers)
            if (offers.isNotEmpty()) {
                showOfferAlert.value = true
            }
            isSyncing.value = false
        }
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val api = OpenFoodFactsApi.create()

    val recipeSuggestions = mutableStateListOf<String>()
    val isRecipeLoading = mutableStateOf(false)

    fun getRecipeSuggestions() {
        viewModelScope.launch {
            isRecipeLoading.value = true
            val inventory = allFridgeItems.first()
            val soonExpiring = inventory.filter {
                it.expiryDate != null && (it.expiryDate!! - System.currentTimeMillis()) < TimeUnit.DAYS.toMillis(4)
            }

            val suggestions = mutableListOf<String>()
            if (soonExpiring.isNotEmpty()) {
                val names = soonExpiring.map { it.name }.distinct()
                suggestions.add("Express-Pfanne mit ${names.take(2).joinToString(" & ")}")
                suggestions.add("Kühlschrank-Resteauflauf")
                suggestions.add("Bunter Mix-Salat mit ${names.last()}")
            } else {
                suggestions.add("Basis-Rezept: Pasta Aglio e Olio")
                suggestions.add("Gemüse-Quiche")
            }

            recipeSuggestions.clear()
            recipeSuggestions.addAll(suggestions)
            isRecipeLoading.value = false
        }
    }

    val swipeLeftAction = mutableStateOf("Müll")
    val swipeRightAction = mutableStateOf("Verbraucht")
    val swipeUpAction = mutableStateOf("Favorit")
    val swipeDownAction = mutableStateOf("Geöffnet")

    fun syncSettings() {
        val prefs = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        themeColor.value = Color(prefs.getInt("theme_color", Color(0xFF4CAF50).toArgb()))
        expiryWarningDays.intValue = prefs.getInt("expiry_warning", 3)
        itemSpacing.intValue = prefs.getInt("item_spacing", 8)
        isCompactMode.value = prefs.getBoolean("is_compact", false)
        cardCornerRadius.intValue = prefs.getInt("corner_radius", 12)
        householdSize.intValue = prefs.getInt("household_size", 1)
        useLocalAIFilter.value = prefs.getBoolean("use_ai_filter", false)
        currentIconIndex.intValue = prefs.getInt("app_icon_index", 4)
        favoriteIconName.value = prefs.getString("favorite_icon_name", "Star") ?: "Star"
        selectedDietProfile.value = prefs.getString("diet_profile", "Standard") ?: "Standard"
        currentHouseholdId.value = prefs.getString("current_household", "default") ?: "default"
        currentUserRole.value = prefs.getString("user_role", "Erwachsener") ?: "Erwachsener"
        bringUuid.value = prefs.getString("bring_uuid", "") ?: ""
        bringToken.value = prefs.getString("bring_token", "") ?: ""
        bringEmail.value = prefs.getString("bring_email", "") ?: ""
        bringPassword.value = prefs.getString("bring_password", "") ?: ""
        favoriteIconColor.value = Color(prefs.getInt("favorite_icon_color", Color(0xFFFFB300).toArgb()))

        val savedWatchlist = prefs.getStringSet("global_watchlist", emptySet()) ?: emptySet()
        watchlist.value = savedWatchlist

        swipeLeftAction.value = prefs.getString("swipe_left_action", "Müll") ?: "Müll"
        swipeRightAction.value = prefs.getString("swipe_right_action", "Verbraucht") ?: "Verbraucht"
        swipeUpAction.value = prefs.getString("swipe_up_action", "Favorit") ?: "Favorit"
        swipeDownAction.value = prefs.getString("swipe_down_action", "Geöffnet") ?: "Geöffnet"

        val allergenList = prefs.getStringSet("allergen_warnings", emptySet()) ?: emptySet()
        allergenWarnings.clear()
        allergenWarnings.addAll(allergenList)
    }

    private fun saveSetting(key: String, value: Any) {
        val prefs = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
        when (value) {
            is Int -> prefs.putInt(key, value)
            is String -> prefs.putString(key, value)
            is Boolean -> prefs.putBoolean(key, value)
            is Set<*> -> @Suppress("UNCHECKED_CAST") prefs.putStringSet(key, value as Set<String>)
        }
        prefs.apply()
    }

    fun setThemeColor(color: Color) { themeColor.value = color; saveSetting("theme_color", color.toArgb()) }
    fun setFavoriteIconColor(color: Color) { favoriteIconColor.value = color; saveSetting("favorite_icon_color", color.toArgb()) }
    fun setBringUuid(uuid: String) { bringUuid.value = uuid; saveSetting("bring_uuid", uuid) }
    fun setBringEmail(email: String) { bringEmail.value = email; saveSetting("bring_email", email) }
    fun setBringPassword(pass: String) { bringPassword.value = pass; saveSetting("bring_password", pass) }
    fun setCompactMode(enabled: Boolean) { isCompactMode.value = enabled; saveSetting("is_compact", enabled) }
    fun setCardCornerRadius(radius: Int) { cardCornerRadius.intValue = radius; saveSetting("corner_radius", radius) }
    fun setExpiryWarningDays(days: Int) { expiryWarningDays.intValue = days; saveSetting("expiry_warning", days) }
    fun setHouseholdSize(size: Int) { householdSize.intValue = size; saveSetting("household_size", size) }
    fun setFavoriteIcon(name: String) { favoriteIconName.value = name; saveSetting("favorite_icon_name", name) }
    fun setDietProfile(profile: String) { selectedDietProfile.value = profile; saveSetting("diet_profile", profile) }
    fun setSwipeLeftAction(action: String) { swipeLeftAction.value = action; saveSetting("swipe_left_action", action) }
    fun setSwipeRightAction(action: String) { swipeRightAction.value = action; saveSetting("swipe_right_action", action) }
    fun setSwipeUpAction(action: String) { swipeUpAction.value = action; saveSetting("swipe_up_action", action) }
    fun setSwipeDownAction(action: String) { swipeDownAction.value = action; saveSetting("swipe_down_action", action) }
    fun toggleAllergenWarning(allergen: String) {
        if (allergenWarnings.contains(allergen)) allergenWarnings.remove(allergen) else allergenWarnings.add(allergen)
        saveSetting("allergen_warnings", allergenWarnings.toSet())
    }

    fun syncData() {
        viewModelScope.launch {
            isSyncing.value = true
            try {
                val items = dao.getAllFridgeItems().first()
                val semaphore = Semaphore(3)

                coroutineScope {
                    items.map { item ->
                        launch(Dispatchers.IO) {
                            semaphore.withPermit {
                                var updatedItem = enrichItemWithSmartLogic(item)

                                if (updatedItem.imageUrl == null || updatedItem.brand == null) {
                                    updatedItem = enrichProductDetails(updatedItem)
                                }

                                if (updatedItem != item) {
                                    dao.updateItem(updatedItem)
                                }
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Daten aktualisiert & Bilder geladen!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("FridgeViewModel", "Fehler beim Sync", e)
            } finally {
                isSyncing.value = false
            }
        }
    }

    fun toggleDealAlertStore(store: String) {
        val current = dealAlertStores.value.toMutableSet()
        if (current.contains(store)) current.remove(store) else current.add(store)
        dealAlertStores.value = current
    }

    fun checkFavoritesForDeals() {
        viewModelScope.launch {
            val favs = allFridgeItems.first().filter { it.isFavorite }
            if (favs.isNotEmpty()) {
                val offers = offerRepository.checkLocalOffers(favs.map { it.name })
                if (offers.isNotEmpty()) dealAlertItem.value = Pair(offers[0].title, offers[0].retailer)
            }
        }
    }

    fun setDealSearchMethod(method: Int) {
        dealSearchMethod.intValue = method
        val workManager = WorkManager.getInstance(applicationContext)
        if (method == 0) workManager.cancelUniqueWork("DealSearchWorker") else {
            val data = workDataOf("METHOD" to method)
            val workRequest = PeriodicWorkRequestBuilder<DealWorker>(12, TimeUnit.HOURS).setInputData(data).build()
            workManager.enqueueUniquePeriodicWork("DealSearchWorker", ExistingPeriodicWorkPolicy.UPDATE, workRequest)
        }
    }

    fun toggleFavorite(item: FridgeItem) { 
        viewModelScope.launch { 
            val newFavStatus = !item.isFavorite
            dao.updateItem(item.copy(isFavorite = newFavStatus))
            if (newFavStatus) {
                val current = watchlist.value.toMutableSet()
                current.add(item.name)
                watchlist.value = current
                saveSetting("global_watchlist", current)
            }
        } 
    }
    fun toggleSelection(id: String) { val current = selectedItems.value; selectedItems.value = if (current.contains(id)) current - id else current + id }

    fun discardImportCandidate(item: FridgeItem) {
        importCandidates.value -= item
    }

    fun analyzePhotoForItems(bitmap: Bitmap) {
        viewModelScope.launch {
            val products = geminiRepository.analyzeFridgePhoto(listOf(bitmap))
            handleScannedProducts(products, "PHOTO")
        }
    }

    fun toggleShoppingItem(item: ShoppingItem) {
        viewModelScope.launch {
            dao.insertShoppingItem(item.copy(isChecked = !item.isChecked))
        }
    }

    fun getBudget(monthYear: String) = dao.getBudget(monthYear)

    fun extractRawName(item: FridgeItem): String {
        return if (item.notes.contains("RAW:")) {
            item.notes.substringAfter("RAW:").substringBefore(" @").trim()
        } else {
            item.name.trim()
        }
    }

    fun ignoreCandidate(item: FridgeItem) {
        val rawName = extractRawName(item)
        if (rawName.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                val updated = LearningEntry(rawName = rawName, correctedName = "___IGNORE___")
                dao.insertLearningEntry(updated)
                withContext(Dispatchers.Main) {
                    learnedCorrections[rawName] = "___IGNORE___"
                    learnedEntriesMap[rawName] = updated
                }
            }
        }
        importCandidates.value -= item
    }

    fun updateCandidateName(candidateId: String, newName: String) {
        importCandidates.value = importCandidates.value.map {
            if (it.id == candidateId) it.copy(name = newName) else it
        }
    }

    fun teachItemCorrection(oldItem: FridgeItem, newName: String? = null, newLocation: String? = null, newCategory: String? = null, newUnit: String? = null) {
        val rawName = extractRawName(oldItem)
        val finalNewName = newName?.trim() ?: oldItem.name.trim()

        if (rawName.isNotBlank() && (finalNewName != rawName || newLocation != null || newCategory != null)) {
            viewModelScope.launch(Dispatchers.IO) {
                val existing = dao.getLearningEntry(rawName)
                val updated = LearningEntry(
                    rawName = rawName,
                    correctedName = finalNewName,
                    defaultStorageLocation = newLocation ?: existing?.defaultStorageLocation ?: oldItem.storageLocation,
                    defaultCategory = newCategory ?: existing?.defaultCategory ?: oldItem.category,
                    defaultUnit = newUnit ?: existing?.defaultUnit ?: oldItem.unit,
                    usageCount = (existing?.usageCount ?: 0) + 1
                )
                dao.insertLearningEntry(updated)

                withContext(Dispatchers.Main) {
                    learnedCorrections[rawName] = updated.correctedName
                    learnedEntriesMap[rawName] = updated
                }
            }
        }
    }

    fun teachNewName(oldItem: FridgeItem, newName: String) {
        teachItemCorrection(oldItem, newName = newName)
    }

    fun toggleDonation(item: FridgeItem) {
        viewModelScope.launch {
            dao.updateItem(item.copy(isDonationCandidate = !item.isDonationCandidate))
        }
    }

    fun getPriceHistory(itemId: String) = dao.getPriceHistory(itemId)

    fun markAsOpened(item: FridgeItem) {
        viewModelScope.launch {
            val consumptionDays = when {
                item.name.lowercase().contains("milch") -> 4
                item.name.lowercase().contains("saft") -> 3
                item.name.lowercase().contains("schmand") || item.name.lowercase().contains("quark") -> 5
                item.name.lowercase().contains("wurst") || item.name.lowercase().contains("käse") -> 7
                else -> 5
            }
            val newExpiry = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(consumptionDays.toLong())
            dao.updateItem(item.copy(
                openedDate = System.currentTimeMillis(),
                expiryDate = minOf(item.expiryDate ?: Long.MAX_VALUE, newExpiry),
                consumptionAfterOpeningDays = consumptionDays
            ))
            logAction("Geöffnet", item.name)
        }
    }
    fun clearInventory() { viewModelScope.launch { dao.deleteAll() } }

    fun loadProductLexicon() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = applicationContext.assets.open("product_lexicon.json").bufferedReader().use { it.readText() }
                val type = object : com.google.gson.reflect.TypeToken<List<LexiconEntry>>() {}.type
                val entries: List<LexiconEntry> = com.google.gson.Gson().fromJson(json, type)
                entries.forEach { entry ->
                    dao.insertLearningEntry(LearningEntry(rawName = entry.raw, correctedName = entry.corrected))
                }
            } catch (e: Exception) {
                Log.e("FridgeViewModel", "Lexicon loading failed", e)
            }
        }
    }

    data class LexiconEntry(val raw: String, val corrected: String)

    fun exportBackup(context: Context) {
        viewModelScope.launch {
            val items = dao.getAllFridgeItems().first()
            val wasted = dao.getAllWastedItems().first()
            val backupData = mapOf("items" to items, "wasted" to wasted)
            val json = Gson().toJson(backupData)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, json)
                val date = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date())
                putExtra(Intent.EXTRA_SUBJECT, "Kühlschrank Profi Backup $date")
            }
            context.startActivity(Intent.createChooser(intent, "Backup speichern"))
        }
    }
    fun restoreBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = inputStream?.bufferedReader()
                val json = reader?.readText()
                inputStream?.close()

                if (json != null) {
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    val data: Map<String, Any> = Gson().fromJson(json, type)

                    val itemsJson = Gson().toJson(data["items"])
                    val itemsType = object : TypeToken<List<FridgeItem>>() {}.type
                    val items: List<FridgeItem> = Gson().fromJson(itemsJson, itemsType)

                    items.forEach { dao.insertItem(it) }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "${items.size} Artikel importiert!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("FridgeViewModel", "Backup Fehler", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Fehler beim Laden des Backups", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun addItem(item: FridgeItem) {
        viewModelScope.launch {
            if (currentUserRole.value == "Kind") {
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Kinder dürfen nur Produkte hinzufügen (Eingeschränkt)", Toast.LENGTH_SHORT).show()
                }
            }
            val hasAllergen = allergenWarnings.any { item.allergens.contains(it, true) }
            if (hasAllergen) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Achtung: Artikel enthält überwachte Allergene!", Toast.LENGTH_LONG).show()
                }
            }
            var recallStatus = "OK"
            item.barcode?.let {
                recallStatus = offerRepository.checkRecallStatus(it)
                if (recallStatus.startsWith("WARN")) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "ACHTUNG: $recallStatus", Toast.LENGTH_LONG).show()
                    }
                }
            }

            if (item.price > 0) {
                checkProductWarner(item.name, item.price, item.quantity)
                dao.insertPriceRecord(PriceRecord(itemId = item.id, itemName = item.name, price = item.price, quantity = item.quantity, barcode = item.barcode))
            }

            dao.insertItem(item.copy(
                expiryDate = item.expiryDate ?: (System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000),
                householdId = currentHouseholdId.value,
                lastRecallStatus = recallStatus
            ))

            val shoppingList = allShoppingItems.first()
            shoppingList.find { it.name.contains(item.name, true) || item.name.contains(it.name, true) }?.let {
                removeFromShoppingList(it)
            }

            logAction("Hinzugefügt", item.name)
        }
    }

    fun updateItem(item: FridgeItem) {
        if (currentUserRole.value == "Kind") return
        viewModelScope.launch {
            try {
                if (item.id.isBlank()) {
                    Log.e("FridgeViewModel", "Update abgebrochen: ID leer!")
                    return@launch
                }

                teachItemCorrection(item, newName = item.name)

                dao.updateItem(item)
                logAction("Bearbeitet", item.name)
            } catch (e: Exception) {
                Log.e("FridgeViewModel", "Fehler beim Update", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Fehler beim Speichern: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun removeItem(item: FridgeItem) {
        if (currentUserRole.value == "Kind") return
        viewModelScope.launch { dao.deleteItem(item); logAction("Gelöscht", item.name) }
    }

    fun consumeItem(item: FridgeItem) {
        viewModelScope.launch {
            val pattern = dao.getConsumptionPattern(item.name) ?: ConsumptionPattern(itemName = item.name, unit = item.unit)
            val now = System.currentTimeMillis()
            val interval = if (pattern.lastConsumedTimestamp > 0) (now - pattern.lastConsumedTimestamp) else 0L
            val newAvg = if (interval > 0) {
                if (pattern.averageIntervalDays > 0) (pattern.averageIntervalDays + (interval / 86400000.0)) / 2.0
                else interval / 86400000.0
            } else pattern.averageIntervalDays

            dao.insertConsumptionPattern(pattern.copy(
                totalConsumed = pattern.totalConsumed + 1,
                lastConsumedTimestamp = now,
                averageIntervalDays = newAvg,
                predictedNextNeed = now + (newAvg * 86400000.0).toLong()
            ))

            dao.insertConsumedItem(ConsumedItem(name = item.name, quantity = 1, unit = item.unit, category = item.category, kcal = item.kcal))
            if (item.quantity > 1) {
                dao.updateItem(item.copy(quantity = item.quantity - 1))
            } else {
                dao.deleteItem(item)
                addToShoppingList(ShoppingItem(name = item.name, quantity = 1, unit = item.unit))
            }
            logAction("Verbraucht", item.name)
        }
    }

    fun wasteItem(item: FridgeItem) {
        viewModelScope.launch {
            val lowerCat = item.category.lowercase()
            val co2Factor = when {
                lowerCat.contains("fleisch") || lowerCat.contains("fisch") || lowerCat.contains("wurst") -> 15.0
                lowerCat.contains("milch") || lowerCat.contains("käse") || lowerCat.contains("quark") -> 5.0
                lowerCat.contains("obst") || lowerCat.contains("gemüse") -> 1.0
                else -> 2.5
            }
            dao.insertWastedItem(WastedItem(
                name = item.name,
                price = item.price,
                quantity = item.quantity,
                unit = item.unit,
                category = item.category,
                brand = item.brand ?: "",
                carbonFootprint = item.carbonFootprint.takeIf { it > 0 } ?: (co2Factor * item.quantity)
            ))
            dao.deleteItem(item)
            logAction("Müll", item.name)
        }
    }

    fun setBudgetLimit(monthYear: String, limit: Double) {
        viewModelScope.launch {
            dao.insertBudget(BudgetConfig(monthYear, limit))
        }
    }

    fun shareShoppingList(context: Context, shoppingItems: List<ShoppingItem>) {
        if (shoppingItems.isEmpty()) return

        // Ansatz 1: Android Share-Intent (ACTION_SEND)
        val exportText = shoppingItems.joinToString("\n") { item ->
            if (item.quantity > 1) "${item.name}, ${item.quantity} ${item.unit}" else item.name
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Einkaufsliste")
            putExtra(Intent.EXTRA_TEXT, exportText)
        }
        context.startActivity(Intent.createChooser(intent, "Einkaufsliste senden an..."))
    }

    // Ansatz 4: Zwischenablage (Clipboard-Export)
    fun copyShoppingListToClipboard(context: Context, items: List<ShoppingItem>) {
        if (items.isEmpty()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = items.joinToString("\n") { "• ${it.name} (${it.quantity} ${it.unit})" }
        val clip = ClipData.newPlainText("Einkaufsliste", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Einkaufsliste in Zwischenablage kopiert", Toast.LENGTH_SHORT).show()
    }

    fun addToShoppingList(item: ShoppingItem) { viewModelScope.launch { dao.insertShoppingItem(item) } }
    fun removeFromShoppingList(item: ShoppingItem) { viewModelScope.launch { dao.deleteShoppingItem(item) } }

    val bringUuid = mutableStateOf("")
    val bringToken = mutableStateOf("")
    val bringEmail = mutableStateOf("")
    val bringPassword = mutableStateOf("")
    val bringLists = mutableStateListOf<com.example.myapplication.data.BringApi.BringList>()

    fun loginToBring() {
        viewModelScope.launch {
            isSyncing.value = true
            val success = bringApi.login(bringEmail.value, bringPassword.value)
            if (success) {
                val lists = bringApi.getLists()
                bringLists.clear()
                bringLists.addAll(lists)
                Toast.makeText(applicationContext, "Erfolgreich bei Bring! angemeldet", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(applicationContext, "Login bei Bring! fehlgeschlagen", Toast.LENGTH_LONG).show()
            }
            isSyncing.value = false
        }
    }

    fun addMealPlan(plan: MealPlan) { viewModelScope.launch { dao.insertMealPlan(plan) } }
    fun deleteMealPlan(id: String) { viewModelScope.launch { dao.deleteMealPlan(id) } }



    fun importFromImage(context: Context, uri: Uri, isFridgePhoto: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isSyncing.value = true
                showImportPreview.value = true
                receiptBitmap.value = null
            }
            try {
                val fileName = getFileName(context, uri) ?: "unknown_image"

                // Temp-Datei im Cache erstellen, damit unseekable Streams aus Drittanbieter-Apps stabil dekodiert werden
                val tempFile = File(context.cacheDir, "temp_shared_image.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val bitmap = if (tempFile.exists() && tempFile.length() > 0) {
                    BitmapFactory.decodeFile(tempFile.absolutePath)
                } else null

                if (bitmap == null) {
                    withContext(Dispatchers.Main) {
                        isSyncing.value = false
                        Toast.makeText(context, "Bild konnte nicht geladen werden.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    receiptBitmap.value = bitmap
                }

                if (isFridgePhoto) {
                    val products = geminiRepository.analyzeFridgePhoto(listOf(bitmap))
                    withContext(Dispatchers.Main) {
                        handleScannedProducts(products, "FRIDGE_SCAN_" + System.currentTimeMillis())
                    }
                } else {
                    val image = InputImage.fromBitmap(bitmap, 0)
                    val corrections = dao.getAllLearningDataSync().associate { it.rawName to it.correctedName }

                    val visionText = Tasks.await(recognizer.process(image))
                    var products = KassenzettelParser.parseReceipt(visionText, corrections)

                    if (products.isEmpty() || products.sumOf { it.price } <= 0.0) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(applicationContext, "OCR unvollständig. KI-Fallback wird gestartet...", Toast.LENGTH_SHORT).show()
                        }
                        products = geminiRepository.analyzeReceipt(bitmap)
                    }

                    withContext(Dispatchers.Main) {
                        handleScannedProducts(products, fileName)
                    }
                }
            } catch (e: Exception) {
                Log.e("FridgeViewModel", "Fehler beim Bild-Import", e)
            } finally {
                withContext(Dispatchers.Main) { isSyncing.value = false }
            }
        }
    }

    fun importFromPdf(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isSyncing.value = true
                showImportPreview.value = true
                receiptBitmap.value = null
            }

            try {
                val fileName = getFileName(context, uri) ?: "unknown_pdf"

                // Temp-Datei im Cache erstellen, damit sowohl PDFBox als auch PdfRenderer stabil darauf zugreifen können
                val tempFile = File(context.cacheDir, "temp_shared_receipt.pdf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                if (!tempFile.exists() || tempFile.length() == 0L) {
                    withContext(Dispatchers.Main) {
                        isSyncing.value = false
                        Toast.makeText(context, "PDF konnte nicht geöffnet werden.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Render 1. Seite als Vorschau-Bild
                try {
                    val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        val bitmap = Bitmap.createBitmap((page.width * 1.5f).toInt(), (page.height * 1.5f).toInt(), Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        val preview = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                        withContext(Dispatchers.Main) { receiptBitmap.value = preview }
                        bitmap.recycle()
                        page.close()
                    }
                    renderer.close()
                    pfd.close()
                } catch (e: Exception) {
                    Log.e("FridgeViewModel", "Fehler bei PDF Vorschau Rendering", e)
                }

                // Text per PDFBox extrahieren
                val document = PDDocument.load(tempFile)
                val allProducts = mutableListOf<Product>()

                for (pageIndex in 0 until document.numberOfPages) {
                    val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                    stripper.startPage = pageIndex + 1
                    stripper.endPage = pageIndex + 1
                    val text = stripper.getText(document)
                    if (text.isNotBlank()) {
                        allProducts.addAll(KassenzettelParser.parseReceiptText(text))
                    }
                }
                document.close()

                if (allProducts.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        handleScannedProducts(allProducts, fileName)
                    }
                    tempFile.delete()
                    return@launch
                }

                // Fallback auf OCR
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Kein digitaler Text gefunden, nutze OCR...", Toast.LENGTH_SHORT).show()
                }

                val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val allProductsOcr = mutableListOf<Product>()
                val corrections = dao.getAllLearningDataSync().associate { it.rawName to it.correctedName }

                var firstPageBitmap: Bitmap? = null

                for (pageIndex in 0 until renderer.pageCount) {
                    val page = renderer.openPage(pageIndex)
                    val scale = 2.0f
                    val bitmap = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    
                    if (firstPageBitmap == null) firstPageBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)

                    val image = InputImage.fromBitmap(bitmap, 0)
                    val visionText = Tasks.await(recognizer.process(image))
                    if (visionText.text.isNotBlank()) {
                        val products = KassenzettelParser.parseReceipt(visionText, corrections)
                        allProductsOcr.addAll(products)
                    }
                    bitmap.recycle()
                    page.close()
                }
                renderer.close()
                pfd.close()
                tempFile.delete()

                if (allProductsOcr.isEmpty() || allProductsOcr.sumOf { it.price } <= 0.0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "OCR unvollständig. KI-Fallback wird gestartet...", Toast.LENGTH_SHORT).show()
                    }
                    if (firstPageBitmap != null) {
                        val aiProducts = geminiRepository.analyzeReceipt(firstPageBitmap)
                        allProductsOcr.clear()
                        allProductsOcr.addAll(aiProducts)
                    }
                }
                
                firstPageBitmap?.recycle()

                withContext(Dispatchers.Main) {
                    handleScannedProducts(allProductsOcr, fileName)
                }
            } catch (e: Exception) {
                Log.e("FridgeViewModel", "PDF Fehler", e)
            } finally {
                withContext(Dispatchers.Main) { isSyncing.value = false }
            }
        }
    }

    fun searchAndAddItems(text: String) {
        viewModelScope.launch {
            try {
                isSyncing.value = true
                showImportPreview.value = true

                val newItems = mutableListOf<FridgeItem>()
                val sourceId = "TEXT_IMPORT_" + text.hashCode().toString()

                val parsedProducts = KassenzettelParser.parseReceiptText(text)
                val corrections = learnedCorrections.toMap()

                parsedProducts.forEach { p ->
                    val itemNotes = p.rawText?.let { "RAW:$it" } ?: ""
                    val item = FridgeItem(
                        name = p.name,
                        price = p.price,
                        quantity = p.quantity,
                        unit = if (p.rawText?.contains("kg", true) == true) "kg" else "Stk.",
                        notes = itemNotes,
                        importHash = generateImportHash(sourceId, p.name)
                    )
                    val enriched = enrichItemWithSmartLogic(item, corrections)
                    if (enriched.name != "___IGNORE___") {
                        newItems.add(enriched)
                    }
                }

                if (newItems.isNotEmpty()) {
                    importCandidates.value = newItems
                    recognizedCount.intValue = newItems.size
                    selectedItems.value = newItems.map { it.id }.toSet()
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Keine Artikel im Text erkannt.", Toast.LENGTH_SHORT).show()
                        showImportPreview.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e("FridgeViewModel", "Fehler beim Text-Import", e)
            } finally {
                isSyncing.value = false
            }
        }
    }

    fun cleanSearchTerm(rawName: String): String {
        return ReceiptImportSanitizer.cleanSearchTerm(rawName)
    }

    fun fetchProductDataFromOpenFoodFacts(item: FridgeItem, customQuery: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val query = customQuery?.ifBlank { null } ?: cleanSearchTerm(item.name)
            try {
                // 1. Suche per Barcode (falls vorhanden), sonst per bereinigtem Namen
                val productData = if (!item.barcode.isNullOrBlank()) {
                    val res = api.getProduktByBarcode(item.barcode!!)
                    if (res.status == 1) res.product else null
                } else {
                    val searchRes = api.searchProduktByName(query)
                    searchRes.products?.firstOrNull { !it.imageUrl.isNullOrBlank() }
                        ?: searchRes.products?.firstOrNull()
                }

                if (productData != null) {
                    val updatedItem = item.copy(
                        imageUrl = productData.imageUrl ?: item.imageUrl,
                        brand = productData.marke ?: item.brand,
                        nutriScore = productData.nutriScore?.uppercase() ?: item.nutriScore,
                        ecoScore = productData.ecoScore?.uppercase() ?: item.ecoScore,
                        allergens = productData.allergens ?: item.allergens,
                        kcal = productData.nutriments?.kcal100g?.toInt() ?: item.kcal,
                        sugar = productData.nutriments?.sugars100g ?: item.sugar,
                        salt = productData.nutriments?.salt100g ?: item.salt,
                        protein = productData.nutriments?.proteins100g ?: item.protein,
                        fat = productData.nutriments?.fat100g ?: item.fat,
                        barcode = if (item.barcode.isNullOrBlank()) productData.code ?: item.barcode else item.barcode
                    )
                    dao.updateItem(updatedItem)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Produktdaten für '${item.name}' aktualisiert", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Keine Online-Daten für '$query' gefunden", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: HttpException) {
                Log.e("FridgeViewModel", "OpenFoodFacts HTTP Fehler: ${e.code()}", e)
                val userMsg = if (e.code() == 503 || e.code() == 502 || e.code() == 504) {
                    "OpenFoodFacts Server vorübergehend überlastet (HTTP ${e.code()}). Bitte später erneut versuchen."
                } else {
                    "OpenFoodFacts Serverfehler (HTTP ${e.code()})"
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, userMsg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("FridgeViewModel", "Fehler beim Laden von OpenFoodFacts", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Verbindungsfehler beim Laden von OpenFoodFacts", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun reloadImageFromOpenFoodFacts(item: FridgeItem) {
        fetchProductDataFromOpenFoodFacts(item)
    }

    private fun cleanAndFormatProductName(rawName: String): String {
        var name = rawName.trim()

        // 1. OCR Doppelbuchstaben-Fehler am Anfang korrigieren (z.B. Cchia -> Chia, Kakarotten -> Karotten, Pgourmet -> Gourmet)
        if (name.startsWith("Cchia", ignoreCase = true)) name = name.substring(1)
        if (name.startsWith("Kakarotten", ignoreCase = true)) name = name.substring(2)
        if (name.startsWith("Pgourmet", ignoreCase = true)) name = name.substring(1)

        // 2. Abkürzungen & Tippfehler mit Regex ersetzen
        val replacements = listOf(
            Regex("""\b(Pres\.la\s*Briqü|Pres\.la\s*Brique|PRES\.?\s*La\s*Brique)\b""", RegexOption.IGNORE_CASE) to "Président La Brique",
            Regex("""\b(PRES\.?|Pres\.)\b""", RegexOption.IGNORE_CASE) to "Président",
            Regex("""\b(TH\.WQ\.?|Thüringer\s*Waldqüll|Waldqüll)\b""", RegexOption.IGNORE_CASE) to "Thüringer Waldquell",
            Regex("""\b(Ma\.fix|Mag\.fix|M\.fix)\b""", RegexOption.IGNORE_CASE) to "Maggi Fix",
            Regex("""\b(n\.Fix|N\.fix)\b""", RegexOption.IGNORE_CASE) to "Knorr Fix",
            Regex("""\bChili\s+C\.?car\b""", RegexOption.IGNORE_CASE) to "Chili con Carne",
            Regex("""\bCurry\s+Gesch\b""", RegexOption.IGNORE_CASE) to "Curry Geschnetzeltes",
            Regex("""\b(KLC\.?|K-classic\.?)\b""", RegexOption.IGNORE_CASE) to "K-Classic ",
            Regex("""\bCash\.?cranb\.?mix\b""", RegexOption.IGNORE_CASE) to "Cashew-Cranberry-Mix",
            Regex("""\bErdn\.?gerös/?ges\b""", RegexOption.IGNORE_CASE) to "Erdnüsse geröstet & gesalzen",
            Regex("""\bKidneybohnen\b""", RegexOption.IGNORE_CASE) to "Kidney-Bohnen",
            Regex("""\bGemüsemais\b""", RegexOption.IGNORE_CASE) to "Gemüsemais",
            Regex("""\bWalnusskerne\b""", RegexOption.IGNORE_CASE) to "Walnusskerne",
            Regex("""\bCantuccini\b""", RegexOption.IGNORE_CASE) to "Cantuccini",
            Regex("""\bK\.schww\.schinken\b""", RegexOption.IGNORE_CASE) to "K-Classic Schwarzwälder Schinken",
            Regex("""\bSchwarzwälder\s+Schink\b""", RegexOption.IGNORE_CASE) to "K-Classic Schwarzwälder Schinken",
            Regex("""\bPutenlachsschinken\b""", RegexOption.IGNORE_CASE) to "Gutfried Puten-Lachsschinken",
            Regex("""\bHähnchensalami\b""", RegexOption.IGNORE_CASE) to "Gutfried Hähnchen-Salami",
            Regex("""\bGeflügelfleischwurst\b""", RegexOption.IGNORE_CASE) to "Gutfried Geflügel-Fleischwurst",
            Regex("""\bHähnchenbrust\b""", RegexOption.IGNORE_CASE) to "Gutfried Hähnchenbrust",
            Regex("""\bCorned\s+Turke(y)?\b""", RegexOption.IGNORE_CASE) to "Gutfried Corned Turkey",
            Regex("""\bKpur\.h\.brustfilet\b""", RegexOption.IGNORE_CASE) to "Purland Hähnchenbrustfilet",
            Regex("""\bRinderhackfleisch\b""", RegexOption.IGNORE_CASE) to "Purland Rinderhackfleisch",
            Regex("""\bSteinofen\s+Pizz(a)?\b""", RegexOption.IGNORE_CASE) to "Original Wagner Steinofen Pizza",
            Regex("""\bBadejunge\s+Camembert\s+Ca\b""", RegexOption.IGNORE_CASE) to "Rügener Badejunge Camembert",
            Regex("""\bRügener\s+Badejunge\b""", RegexOption.IGNORE_CASE) to "Rügener Badejunge Camembert",
            Regex("""\bMeg\.feinesüssrahm\b""", RegexOption.IGNORE_CASE) to "Meggler Feine Süßrahmbutter",
            Regex("""\bCocktailrisp\.?\b""", RegexOption.IGNORE_CASE) to "Cocktail-Rispen-Tomaten",
            Regex("""\bGurken\s+St\b""", RegexOption.IGNORE_CASE) to "Gurken",
            Regex("""\bHerzhaft\s+Scheiben\b""", RegexOption.IGNORE_CASE) to "Bergader Heumilch Käse Herzhaft",
            Regex("""\bBergbauern\s+Tilsit\.?\b""", RegexOption.IGNORE_CASE) to "Bergader Bergbauern Käse Milder Tilsiter",
            Regex("""\bBergb___au___rn?\b""", RegexOption.IGNORE_CASE) to "Bergader Bergbauern Käse Milder Tilsiter",
            Regex("""\bProtein\s+Käse\s+mild\b""", RegexOption.IGNORE_CASE) to "Quäse Protein Käse Mild",
            Regex("""\bens\s+Expressreis\b""", RegexOption.IGNORE_CASE) to "Ben's Original Expressreis",
            Regex("""\bhia-Sky-Brötchen\b""", RegexOption.IGNORE_CASE) to "Chia-Skyr-Brötchen",
            Regex("""\bchia-skyr-brötchen\b""", RegexOption.IGNORE_CASE) to "Chia-Skyr-Brötchen",
            Regex("""\brottenkrüstchen\b""", RegexOption.IGNORE_CASE) to "Karottenkrüstchen",
            Regex("""\bGewürzs?\s*600g?\b""", RegexOption.IGNORE_CASE) to "Gewürz-Spekulatius",
            Regex("""\bGewürz-spekulatius\b""", RegexOption.IGNORE_CASE) to "Gewürz-Spekulatius",
            Regex("""\bGourmet\s+Perle\s+Katzenfutter\b""", RegexOption.IGNORE_CASE) to "Gourmet Perle Katzenfutter",
            Regex("""\berle\s+Lachs\s+Gelee\b""", RegexOption.IGNORE_CASE) to "Gourmet Perle Katzenfutter Lachs Gelee",
            Regex("""\bPerle\s+Lachs\s+Gelee\b""", RegexOption.IGNORE_CASE) to "Gourmet Perle Katzenfutter Lachs Gelee",
            Regex("""\bBree\s+Fruity\s+Weiss\b""", RegexOption.IGNORE_CASE) to "Bree Fruity Weißwein"
        )

        for (rule in replacements) {
            name = name.replace(rule.first, rule.second)
        }

        // 3. Doppelte Markennamen entfernen (z.B. "Bergader Bergader", "Gutfried Gutfried")
        val brandList = listOf("Bergader", "Gutfried", "K-Classic", "Purland", "Maggi Fix", "Knorr Fix", "Président", "Bree", "Rügener")
        for (b in brandList) {
            name = name.replace(Regex("""\b($b)\s+\1\b""", RegexOption.IGNORE_CASE), b)
        }

        // 4. Richtige Groß-/Kleinschreibung (Title Case für Bindestrich-Wörter & Marken)
        name = name
            .replace("K-classic", "K-Classic", ignoreCase = true)
            .replace("K-bio eier", "K-Bio Eier", ignoreCase = true)
            .replace("kidneybohnen", "Kidneybohnen", ignoreCase = true)
            .replace("kidney-bohnen", "Kidneybohnen", ignoreCase = true)
            .replace("cashew-cranberry-mix", "Cashew-Cranberry-Mix", ignoreCase = true)
            .replace("Puten-lachsschinken", "Puten-Lachsschinken", ignoreCase = true)
            .replace("Geflügel-fleischwurst", "Geflügel-Fleischwurst", ignoreCase = true)
            .replace("Hähnchen-salami", "Hähnchen-Salami", ignoreCase = true)
            .replace("Chia-skyr-brötchen", "Chia-Skyr-Brötchen", ignoreCase = true)
            .replace("chia sky brötchen", "Chia-Skyr-Brötchen", ignoreCase = true)
            .replace("Gewürz-spekulatius", "Gewürz-Spekulatius", ignoreCase = true)
            .replace("spekulatius", "Spekulatius", ignoreCase = true)
            .replace("Cocktail-rispen-tomaten", "Cocktail-Rispen-Tomaten", ignoreCase = true)
            .replace("salami", "Salami", ignoreCase = true)
            .replace("schinken", "Schinken", ignoreCase = true)
            .replace("lachsschinken", "Lachsschinken", ignoreCase = true)
            .replace("fleischwurst", "Fleischwurst", ignoreCase = true)
            .replace("wagner steinofen pizza", "Wagner Steinofen Pizza", ignoreCase = true)
            .replace("steinofen pizza", "Steinofen Pizza", ignoreCase = true)
            .replace(Regex("""\s+"""), " ")
            .trim()

        return name
    }

    private fun enrichItemWithSmartLogic(item: FridgeItem, providedCorrections: Map<String, String>? = null): FridgeItem {
        val rawName = extractRawName(item)

        val effectiveCorrections = providedCorrections ?: learnedCorrections
        val learnedEntry = if (rawName.isNotBlank()) learnedEntriesMap[rawName] else learnedEntriesMap[item.name]

        val isUserTaught = learnedEntry != null && learnedEntry.correctedName.isNotBlank()

        var name = when {
            isUserTaught -> learnedEntry!!.correctedName
            rawName.isNotBlank() && effectiveCorrections.containsKey(rawName) -> effectiveCorrections[rawName]!!
            else -> item.name.trim()
        }

        if (!isUserTaught && !(rawName.isNotBlank() && effectiveCorrections.containsKey(rawName))) {
            name = cleanAndFormatProductName(name)
        }

        val lower = name.lowercase()
        val detectedCat = com.example.myapplication.data.CategoryDetector.detectCategory(name)
        val catName = learnedEntry?.defaultCategory ?: detectedCat.displayName

        var location = learnedEntry?.defaultStorageLocation ?: when {
            lower.contains("tiefkühl") || lower.contains("tk ") || lower.contains("eis ") || lower.contains("spinat") ||
                    (lower.contains("pizza") && !lower.contains("brötchen")) || lower.contains("pommes") || lower.contains("gefroren") -> "Gefrierfach"

            detectedCat == FoodCategory.MILCHPRODUKTE ||
                    detectedCat == FoodCategory.FLEISCH_FISCH ||
                    lower.contains("sahne") || lower.contains("schlagsahne") || lower.contains("saure sahne") ||
                    lower.contains("schmand") || lower.contains("sauerrahm") || lower.contains("creme fraiche") ||
                    lower.contains("crème fraîche") || lower.contains("mascarpone") || lower.contains("ricotta") ||
                    lower.contains("frischkäse") || lower.contains("hüttenkäse") || lower.contains("käse") ||
                    lower.contains("brique") || lower.contains("la brique") || lower.contains("président") ||
                    lower.contains("president") || lower.contains("butter") || lower.contains("milch") ||
                    lower.contains("joghurt") || lower.contains("quark") || lower.contains("skyr") ||
                    lower.contains("miree") || lower.contains("aufstrich") || lower.contains("wurst") ||
                    lower.contains("fleisch") || lower.contains("schinken") || lower.contains("salami") ||
                    lower.contains("lachs") || lower.contains("feinkost") || lower.contains("senf") ||
                    lower.contains("kefir") || lower.contains("nackenst") || lower.contains("gurke") ||
                    lower.contains("kohlrabi") || lower.contains("salat") || lower.contains("aufschnitt") ||
                    lower.contains("wiener") || lower.contains("hefe") || lower.contains("mozzarella") || 
                    lower.contains("parmesan") || lower.contains("dressing") || lower.contains("pesto") -> "Kühlschrank"

            lower.contains("kuschelweich") || lower.contains("weichspüler") || lower.contains("reiniger") ||
                    lower.contains("shampoo") || lower.contains("seife") || lower.contains("katzenfutter") ||
                    lower.contains("tasche") || lower.contains("batterie") || lower.contains("folie") ||
                    lower.contains("spülmittel") || lower.contains("esmara") || lower.contains("bluse") ||
                    lower.contains("hundefutter") || lower.contains("hygiene") || lower.contains("toilettenpapier") ||
                    lower.contains("küchenrolle") || detectedCat == FoodCategory.HAUSHALT || 
                    detectedCat == FoodCategory.DROGERIE_HYGIENE -> "Haushalt"

            else -> "Vorratskammer"
        }

        if ((lower.contains("wurst") || lower.contains("käse") || lower.contains("sahne") || lower.contains("schmand") || lower.contains("brique") || lower.contains("miree")) && location == "Vorratskammer") {
            location = "Kühlschrank"
        }

        val expiryDays = when {
            lower.contains("cornedbeef") || lower.contains("corned beef") || lower.contains("konserve") -> 730L
            lower.contains("essig") || lower.contains("öl") || lower.contains("salz") || lower.contains("zucker") || lower.contains("reis") || lower.contains("nudeln") -> 365L
            location == "Gefrierfach" -> 180L
            lower.contains("brötchen") || lower.contains("brot") || lower.contains("baguette") || lower.contains("croissant") || detectedCat == FoodCategory.BROT_BACKWAREN -> 3L
            lower.contains("lachs") || lower.contains("hackfleisch") || lower.contains("fisch") -> 2L
            lower.contains("steak") || lower.contains("geflügel") || lower.contains("hähnchen") || lower.contains("beeren") -> 3L
            lower.contains("obst") || lower.contains("gemüse") || lower.contains("salat") || lower.contains("gurke") || lower.contains("tomate") || lower.contains("kohlrabi") || detectedCat == FoodCategory.OBST_GEMUESE -> 7L
            lower.contains("sahne") || lower.contains("schlagsahne") || lower.contains("saure sahne") ||
                    lower.contains("schmand") || lower.contains("sauerrahm") || lower.contains("creme fraiche") ||
                    lower.contains("crème fraîche") || lower.contains("milch") || lower.contains("joghurt") ||
                    lower.contains("quark") || lower.contains("skyr") -> 10L
            lower.contains("käse") || lower.contains("frischkäse") || lower.contains("brique") || lower.contains("miree") || lower.contains("wurst") || lower.contains("salami") || lower.contains("schinken") -> 14L
            detectedCat == FoodCategory.MILCHPRODUKTE -> 10L
            detectedCat == FoodCategory.FLEISCH_FISCH -> 3L
            detectedCat == FoodCategory.GETRAENKE || lower.contains("bier") || lower.contains("mönchshof") || lower.contains("sekt") || lower.contains("wasser") || lower.contains("cola") || lower.contains("saft") -> 180L
            location == "Kühlschrank" -> 7L
            location == "Haushalt" || detectedCat == FoodCategory.HAUSHALT || detectedCat == FoodCategory.DROGERIE_HYGIENE -> 730L
            location == "Vorratskammer" -> 180L
            else -> 14L
        }
        val expiryDate = item.purchaseDate + (expiryDays * 24 * 60 * 60 * 1000)

        var unit = learnedEntry?.defaultUnit ?: if (item.unit == "Stk." || item.unit.isBlank()) {
            KassenzettelParser.determineSmartUnit(name, item.price, item.quantity, item.notes)
        } else {
            item.unit
        }
        var quantity = item.quantity

        // TWQ / Kisten Logik: Wenn Gesamtpreis z.B. 9.98 € ist und 1 Kiste ca. 4.99 € kostet -> 2 Kisten à 4,99 €!
        if (unit == "Kiste" || lower.contains("wasser") || lower.contains("waldquell")) {
            if (item.price >= 7.00 && quantity == 1) {
                quantity = round(item.price / 4.99).toInt().coerceAtLeast(2)
                unit = "Kiste"
            }
        }

        val isVegan = lower.contains("vegan") || lower.contains("soja") || lower.contains("hafer")
        val protein = if (detectedCat == com.example.myapplication.data.FoodCategory.FLEISCH_FISCH) 20.0 else item.protein
        val sugar = if (lower.contains("cola") || lower.contains("limo")) 10.0 else item.sugar

        var formattedName = if (isUserTaught) {
            name
        } else {
            name.split(" ")
                .filter { it.isNotBlank() }
                .joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
                .take(30)
        }

        if (!isUserTaught && ((formattedName.startsWith("2 St") || formattedName.startsWith("2 Stk") || formattedName == "Tiefkühlpizza") && item.price == 0.59)) {
            formattedName = "Brötchen"
        }

        return item.copy(
            name = formattedName, category = catName, storageLocation = location,
            expiryDate = item.expiryDate ?: expiryDate, unit = unit, quantity = quantity,
            isVegan = isVegan || item.isVegan, protein = protein, sugar = sugar
        )
    }

    fun handleScannedProducts(rawProducts: List<Product>, sourceId: String = "SCAN") {
        viewModelScope.launch {
            val products = ReceiptImportSanitizer.prepareForImport(rawProducts)
            if (products.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Keine gültigen Artikel auf dem Bon gefunden.", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            val existingHashes = dao.getImportHashes().first().toSet()
            val correctionsCopy = learnedCorrections.toMap()

            val newItems = products.mapNotNull { p ->
                val hash = generateImportHash(sourceId, p.name)
                val duplicate = existingHashes.contains(hash)

                val itemNotes = p.rawText?.let { "RAW:$it" + (if (p.supermarket != null) " @${p.supermarket}" else "") } ?: ""

                val item = FridgeItem(
                    name = p.name, price = p.price, quantity = p.quantity, barcode = p.barcode,
                    imageUrl = p.imageUrl, allergens = p.allergens, kcal = p.kcal,
                    notes = itemNotes,
                    boundingBox = p.boundingBox, purchaseDate = p.purchaseDate ?: System.currentTimeMillis(),
                    importHash = hash
                ).apply { this.isDuplicate = duplicate }

                val enriched = enrichItemWithSmartLogic(item, correctionsCopy)

                if (enriched.name == "___IGNORE___") {
                    null
                } else {
                    if (p.price > 0 && !duplicate) {
                        checkProductWarner(enriched.name, p.price, p.quantity)
                        dao.insertPriceRecord(PriceRecord(itemId = enriched.id, itemName = enriched.name, price = p.price, quantity = p.quantity, barcode = p.barcode))
                    }
                    enriched
                }
            }

            withContext(Dispatchers.Main) {
                importCandidates.value = newItems
                recognizedCount.intValue = newItems.size
                // By default, select items that are NOT household and NOT duplicate
                selectedItems.value = newItems.filter { it.storageLocation != "Haushalt" && !it.isDuplicate }.map { it.id }.toSet()
                showImportPreview.value = true

                if (newItems.all { it.isDuplicate }) {
                    Toast.makeText(applicationContext, "Bereits importiert. Du kannst den Beleg zur Korrektur überschreiben.", Toast.LENGTH_LONG).show()
                }
            }

            val enrichedCandidates = newItems.map { item ->
                viewModelScope.async(Dispatchers.IO) {
                    if (item.imageUrl == null) enrichProductDetails(item) else item
                }
            }.awaitAll()

            withContext(Dispatchers.Main) {
                // WICHTIG: Wir dürfen hier nicht einfach die Liste überschreiben,
                // da der Nutzer in den 2-3 Sekunden Ladezeit evtl. schon Namen korrigiert hat!
                // Stattdessen mergen wir nur die neu gefundenen Bilder und Nährwerte rein.
                importCandidates.value = importCandidates.value.map { current ->
                    val enriched = enrichedCandidates.find { it.id == current.id }
                    if (enriched != null) {
                        current.copy(
                            imageUrl = enriched.imageUrl ?: current.imageUrl,
                            brand = enriched.brand ?: current.brand,
                            kcal = if (enriched.kcal > 0) enriched.kcal else current.kcal,
                            nutriScore = if (enriched.nutriScore.isNotBlank()) enriched.nutriScore else current.nutriScore
                        )
                    } else current
                }
            }
        }
    }

    private fun generateImportHash(source: String, itemName: String): String = "${source}_${itemName}".hashCode().toString()

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path?.substringAfterLast('/')
        }
        return result
    }

    private suspend fun checkProductWarner(name: String, currentPrice: Double, currentQty: Int) {
        val history = dao.getHistoryByName(name)
        if (history.isNotEmpty()) {
            val last = history.first()
            val currentUnit = if (currentQty > 0) currentPrice / currentQty else currentPrice
            val lastUnit = if (last.quantity > 0) last.price / last.quantity else last.price
            
            val priceChanged = Math.abs(currentUnit - lastUnit) > 0.02 && currentPrice > 0
            val qtyChanged = currentQty != last.quantity && currentQty > 0

            if (priceChanged || qtyChanged) {
                val msg = StringBuilder("Produktwarner für '$name':\n")
                if (priceChanged) {
                    val trend = if (currentUnit > lastUnit) "Teurer!" else "Günstiger"
                    msg.append("• Einzelpreis: ${String.format(Locale.GERMANY, "%.2f", lastUnit)}€ ➔ ${String.format(Locale.GERMANY, "%.2f", currentUnit)}€ ($trend)\n")
                }
                if (qtyChanged) {
                    val trend = if (currentQty < last.quantity) "Weniger Inhalt! (Shrinkflation?)" else "Mehr Inhalt"
                    msg.append("• Menge: ${last.quantity}${last.unit} ➔ $currentQty${last.unit} ($trend)")
                }
                withContext(Dispatchers.Main) {
                    if (!warnerMessages.contains(msg.toString())) warnerMessages.add(msg.toString())
                }
            }
        }
    }
    fun fetchProductDetails(barcode: String, nameQuery: String? = null, onResult: (FridgeItem?) -> Unit) {
        viewModelScope.launch {
            try {
                val cached = dao.getCachedProduct(barcode)
                if (cached != null) {
                    onResult(FridgeItem(
                        name = cached.name, brand = cached.brand, imageUrl = cached.imageUrl,
                        nutriScore = cached.nutriScore, ecoScore = cached.ecoScore, allergens = cached.allergens,
                        kcal = cached.kcal, barcode = barcode, category = cached.categories.split(",").firstOrNull() ?: "Sonstiges",
                        sugar = cached.sugar, salt = cached.salt, protein = cached.protein, fat = cached.fat
                    ))
                    return@launch
                }

                val response = if (barcode.isNotBlank()) api.getProduktByBarcode(barcode) else null
                if (response?.status == 1 && response.product != null) {
                    val p = response.product
                    val name = p.productName ?: "Unbekannter Artikel"
                    val item = FridgeItem(
                        name = name, brand = p.marke, imageUrl = p.imageUrl, nutriScore = p.nutriScore ?: "",
                        ecoScore = p.ecoScore ?: "", allergens = p.allergens ?: "", kcal = p.nutriments?.kcal100g?.toInt() ?: 0,
                        barcode = barcode, category = p.categories?.split(",")?.firstOrNull() ?: "Sonstiges",
                        sugar = p.nutriments?.sugars100g ?: 0.0, salt = p.nutriments?.salt100g ?: 0.0,
                        protein = p.nutriments?.proteins100g ?: 0.0, fat = p.nutriments?.fat100g ?: 0.0
                    )

                    dao.insertCachedProduct(CachedProduct(
                        barcode = barcode, name = name, brand = p.marke, imageUrl = p.imageUrl,
                        imageSmallUrl = p.imageSmallUrl, kcal = item.kcal, nutriScore = item.nutriScore,
                        allergens = item.allergens, ecoScore = item.ecoScore, categories = p.categories ?: "",
                        sugar = item.sugar, salt = item.salt, protein = item.protein, fat = item.fat,
                        timestamp = System.currentTimeMillis()
                    ))
                    onResult(item)
                } else if (!nameQuery.isNullOrBlank()) {
                    val searchResponse = api.searchProduktByName(nameQuery)
                    if (searchResponse.products?.isNotEmpty() == true) {
                        val p = searchResponse.products.first()
                        val item = FridgeItem(
                            name = p.productName ?: nameQuery,
                            brand = p.marke,
                            imageUrl = p.imageUrl,
                            barcode = p.code ?: "",
                            kcal = p.nutriments?.kcal100g?.toInt() ?: 0,
                            nutriScore = p.nutriScore ?: "",
                            sugar = p.nutriments?.sugars100g ?: 0.0,
                            salt = p.nutriments?.salt100g ?: 0.0,
                            protein = p.nutriments?.proteins100g ?: 0.0,
                            fat = p.nutriments?.fat100g ?: 0.0
                        )
                        // Cachen des Suchergebnisses mit künstlichem Barcode falls keiner vorhanden
                        val cacheCode = p.code ?: "SEARCH_${nameQuery.hashCode()}"
                        dao.insertCachedProduct(CachedProduct(
                            barcode = cacheCode, name = item.name, brand = item.brand, imageUrl = item.imageUrl,
                            imageSmallUrl = p.imageSmallUrl, kcal = item.kcal, nutriScore = item.nutriScore,
                            allergens = item.allergens, ecoScore = item.ecoScore, categories = p.categories ?: "",
                            sugar = item.sugar, salt = item.salt, protein = item.protein, fat = item.fat,
                            timestamp = System.currentTimeMillis()
                        ))
                        onResult(item)
                    } else onResult(null)
                } else {
                    if (barcode.isNotBlank()) unknownBarcodeForPhoto.value = barcode
                    onResult(null)
                }
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }
    private suspend fun enrichProductDetails(item: FridgeItem): FridgeItem {
        return try {
            val bcode = item.barcode
            if (!bcode.isNullOrBlank()) {
                val response = api.getProduktByBarcode(bcode)
                if (response.status == 1 && response.product != null) {
                    val p = response.product
                    return item.copy(
                        brand = p.marke ?: item.brand,
                        imageUrl = p.imageUrl ?: p.imageSmallUrl ?: item.imageUrl,
                        nutriScore = p.nutriScore ?: item.nutriScore,
                        kcal = p.nutriments?.kcal100g?.toInt() ?: item.kcal
                    )
                }
            }

            // 1. Gesäuberter Name
            val cleanedQuery = ReceiptImportSanitizer.cleanSearchTerm(item.name)
            var searchResponse = api.searchProduktByName(cleanedQuery)

            // 2. Fallback: Suche mit Roh-Namen
            if (searchResponse.products.isNullOrEmpty() && cleanedQuery != item.name) {
                searchResponse = api.searchProduktByName(item.name)
            }

            // 3. Fallback: Suche mit den zwei wichtigsten Hauptwörtern
            if (searchResponse.products.isNullOrEmpty()) {
                val words = cleanedQuery.split(" ").filter { it.length >= 3 && !it.equals("bio", true) && !it.equals("gut", true) }
                for (word in words.take(2)) {
                    val res = api.searchProduktByName(word)
                    if (!res.products.isNullOrEmpty()) {
                        searchResponse = res
                        break
                    }
                }
            }

            val foundProduct = searchResponse.products?.firstOrNull { !it.imageUrl.isNullOrBlank() || !it.imageSmallUrl.isNullOrBlank() }
                ?: searchResponse.products?.firstOrNull()

            if (foundProduct != null) {
                Log.d("FridgeViewModel", "OpenFoodFacts Treffer für '${item.name}': Bild=${foundProduct.imageUrl}")
                return item.copy(
                    brand = foundProduct.marke ?: item.brand,
                    imageUrl = foundProduct.imageUrl ?: foundProduct.imageSmallUrl ?: item.imageUrl,
                    nutriScore = foundProduct.nutriScore ?: item.nutriScore,
                    kcal = foundProduct.nutriments?.kcal100g?.toInt() ?: item.kcal
                )
            }
            Log.d("FridgeViewModel", "OpenFoodFacts kein Treffer für '${item.name}' (Query: '$cleanedQuery')")
            item
        } catch (e: Exception) {
            Log.e("FridgeViewModel", "Fehler bei OpenFoodFacts enrichment für '${item.name}'", e)
            item
        }
    }

    fun confirmAndImport() {
        viewModelScope.launch {
            val toImport = importCandidates.value.filter { selectedItems.value.contains(it.id) }
            toImport.forEach { item ->
                // Finale Korrektur abspeichern, falls der Name manuell geändert wurde
                teachItemCorrection(item, newName = item.name)

                if (item.isDuplicate && item.importHash != null) {
                    val existingList = dao.getItemsByImportHash(item.importHash!!)
                    if (existingList.isNotEmpty()) {
                        existingList.forEach { existing ->
                            existing.name = item.name
                            existing.category = item.category
                            existing.price = item.price
                            existing.isVegan = item.isVegan
                            existing.storageLocation = item.storageLocation
                            dao.updateItem(existing)
                        }
                        return@forEach
                    }
                }
                addItem(item)
            }
            showImportPreview.value = false
            importCandidates.value = emptyList()
            selectedItems.value = emptySet()
            receiptBitmap.value = null
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, "${toImport.size} Artikel gespeichert!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun enrichItemManually(item: FridgeItem) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, "Suche in OpenFoodFacts...", Toast.LENGTH_SHORT).show()
            }
            val enriched = enrichProductDetails(item)
            if (enriched != item) {
                updateItem(enriched)
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Daten & Bild erfolgreich aktualisiert!", Toast.LENGTH_SHORT).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Keine neuen Daten gefunden.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    suspend fun compareRetailerPrices(items: List<ShoppingItem>): Map<String, Double> = offerRepository.calculateBestRetailer(items)
}