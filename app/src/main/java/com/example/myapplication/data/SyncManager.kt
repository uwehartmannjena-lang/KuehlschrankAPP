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

    private val _activeHouseholdId = MutableStateFlow("default")
    val activeHouseholdId: StateFlow<String> = _activeHouseholdId

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var savedId = prefs.getString(KEY_HOUSEHOLD_ID, null)
        if (savedId.isNullOrBlank()) {
            savedId = generateHouseholdId()
            prefs.edit().putString(KEY_HOUSEHOLD_ID, savedId).apply()
        }
        _activeHouseholdId.value = savedId
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
}
