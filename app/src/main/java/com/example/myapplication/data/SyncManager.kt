package com.example.myapplication.data

import android.content.Context
import com.example.myapplication.ui.QrUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

object SyncManager {

    private const val PREFS_NAME = "household_sync_prefs"
    private const val KEY_HOUSEHOLD_ID = "active_household_id"
    private const val KEY_WEBSERVICE_URL = "webservice_url"

    private val _activeHouseholdId = MutableStateFlow("default")
    val activeHouseholdId: StateFlow<String> = _activeHouseholdId

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline

    private val gson = com.google.gson.Gson()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var savedId = prefs.getString(KEY_HOUSEHOLD_ID, null)
        if (savedId.isNullOrBlank()) {
            savedId = generateHouseholdId()
            prefs.edit().putString(KEY_HOUSEHOLD_ID, savedId).apply()
        }
        _activeHouseholdId.value = savedId
    }

    fun getWebserviceUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_WEBSERVICE_URL, "http://192.168.0.223:8080") ?: "http://192.168.0.223:8080"
    }

    fun setWebserviceUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_WEBSERVICE_URL, url.trim().trimEnd('/')).apply()
    }

    fun generateHouseholdId(): String {
        val code = UUID.randomUUID().toString().replace("-", "").take(6).uppercase(Locale.ROOT)
        return "HAUS-$code"
    }

    fun joinHousehold(context: Context, newHouseholdId: String) {
        val cleanId = newHouseholdId.trim().uppercase(Locale.ROOT)
        if (cleanId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_HOUSEHOLD_ID, cleanId).apply()
        _activeHouseholdId.value = cleanId
    }

    fun getHouseholdQrCode(householdId: String, size: Int = 512) = QrUtils.generateQrCode(householdId, size)

    /**
     * Konfliktfreie Echtzeit-Synchronisation zweier Item-Listen (Offline-First, Last-Write-Wins mit Deltas).
     */
    suspend fun reconcileItems(
        dao: FridgeItemDao,
        remoteItems: List<FridgeItem>,
        householdId: String
    ) = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        try {
            val localItems = dao.getItemsByHouseholdSync(householdId)
            val localMap = localItems.associateBy { it.id }.toMutableMap()

            for (remote in remoteItems) {
                val local = localMap[remote.id]
                if (local == null) {
                    dao.insertItem(remote.copy(householdId = householdId))
                } else {
                    if (remote.lastModified > local.lastModified) {
                        dao.updateItem(remote.copy(householdId = householdId))
                    } else if (local.lastModified > remote.lastModified) {
                        // Lokaler Stand ist neuer
                    } else {
                        val mergedQty = maxOf(local.quantity, remote.quantity)
                        if (mergedQty != local.quantity) {
                            dao.updateItem(local.copy(quantity = mergedQty))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Automatischer Abgleich mit dem Webservice.
     * Ist der Webservice nicht erreichbar, bleibt die App dank Room/SQLite vollständig lokal les- und bedienbar.
     */
    suspend fun syncWithWebservice(context: Context, dao: FridgeItemDao): Boolean = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        val configuredUrl = getWebserviceUrl(context)
        val candidateUrls = listOf(
            configuredUrl,
            "http://192.168.0.223:8080",
            "http://10.0.2.2:8080",
            "http://localhost:8080"
        ).distinct()

        var syncSuccess = false

        for (baseUrl in candidateUrls) {
            try {
                val url = java.net.URL("$baseUrl/api/inventory")
                val connection = (url.openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 2500
                    readTimeout = 3000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                }

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(connection.inputStream, "UTF-8"))
                    val json = reader.use { it.readText() }
                    connection.disconnect()

                    val itemType = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
                    val rawItems: List<Map<String, Any>> = gson.fromJson(json, itemType) ?: emptyList()

                    val currentHousehold = _activeHouseholdId.value
                    val localItems = dao.getItemsByHouseholdSync(currentHousehold)
                    val localByName = localItems.associateBy { it.name.trim().lowercase() }

                    for (raw in rawItems) {
                        val name = raw["name"]?.toString()?.trim() ?: continue
                        if (name.isBlank()) continue
                        val qty = (raw["quantity"] as? Number)?.toInt() ?: 1
                        val unit = raw["unit"]?.toString() ?: "Stk."
                        val storageLoc = raw["storageLocation"]?.toString() ?: "Kühlschrank"
                        val price = (raw["price"] as? Number)?.toDouble() ?: 0.0
                        val expiryDate = (raw["expiryDate"] as? Number)?.toLong()
                        val category = raw["category"]?.toString() ?: "Sonstiges"
                        val barcode = raw["barcode"]?.toString()

                        val existing = localByName[name.lowercase()]
                        if (existing == null) {
                            dao.insertItem(
                                FridgeItem(
                                    name = name,
                                    quantity = qty,
                                    unit = unit,
                                    storageLocation = storageLoc,
                                    price = price,
                                    expiryDate = expiryDate,
                                    category = category,
                                    barcode = barcode,
                                    householdId = currentHousehold,
                                    lastModified = System.currentTimeMillis()
                                )
                            )
                        } else {
                            if (expiryDate != null && existing.expiryDate == null) {
                                dao.updateItem(existing.copy(expiryDate = expiryDate, lastModified = System.currentTimeMillis()))
                            }
                        }
                    }

                    _isOnline.value = true
                    syncSuccess = true
                    setWebserviceUrl(context, baseUrl)
                    android.util.Log.d("SyncManager", "Webservice-Sync erfolgreich mit $baseUrl (${rawItems.size} Artikel empfangen)")
                    break
                } else {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                android.util.Log.d("SyncManager", "Webservice unter $baseUrl nicht erreichbar: ${e.message}")
            }
        }

        if (!syncSuccess) {
            _isOnline.value = false
            android.util.Log.d("SyncManager", "Webservice nicht erreichbar - Offline-Modus aktiv, Daten werden lokal aus Room/SQLite gelesen.")
        }

        _isSyncing.value = false
        syncSuccess
    }
}
