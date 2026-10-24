package com.example.myapplication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.FridgeItem
import com.example.myapplication.data.UnconfirmedReceiptItem
import com.example.myapplication.data.UnconfirmedReceiptsRepository
import com.example.myapplication.data.UserLearnedCorrection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReceiptScannerViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val fridgeItemDao = db.fridgeItemDao()
    private val userLearnedDao = db.userLearnedDao()

    val itemsToConfirm: StateFlow<List<UnconfirmedReceiptItem>> =
        UnconfirmedReceiptsRepository.unconfirmedItems
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    fun confirmItem(item: UnconfirmedReceiptItem, correctedName: String) {
        val trimmed = correctedName.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val fridgeItem = FridgeItem(
                name = trimmed,
                quantity = item.quantity,
                unit = item.unit,
                category = item.category,
                storageLocation = "Kühlschrank",
                price = item.price,
                expiryDate = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000L),
                purchaseDate = System.currentTimeMillis()
            )
            fridgeItemDao.insertItem(fridgeItem)

            val correction = UserLearnedCorrection(
                receiptRawText = item.rawText,
                correctedName = trimmed,
                timestamp = System.currentTimeMillis()
            )
            userLearnedDao.insertCorrection(correction)
            ReceiptImportSanitizer.addLearnedCorrection(item.rawText, trimmed)

            UnconfirmedReceiptsRepository.removeItem(item.id)
        }
    }

    fun dismissItem(itemId: String) {
        UnconfirmedReceiptsRepository.removeItem(itemId)
    }
}
