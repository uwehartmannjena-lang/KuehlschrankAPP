package com.example.myapplication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.FridgeDao
import com.example.myapplication.data.FridgeItem
import com.example.myapplication.data.OpenFoodFactsRepository
import com.example.myapplication.data.UserCorrection
import com.example.myapplication.data.UserLearnedCorrection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ItemEditUiState(
    val name: String = "",
    val barcode: String = "",
    val imageUrl: String = "",
    val brand: String = "",
    val quantity: Int = 1,
    val unit: String = "Stk.",
    val storageLocation: String = "Kühlschrank",
    val freezerDrawer: Int = 0,
    val expiryDate: String = "",
    val isScanning: Boolean = false,
    val errorMessage: String? = null
)

class ItemEditViewModel(
    application: Application,
    private val dao: FridgeDao = AppDatabase.getDatabase(application).fridgeItemDao(),
    private val openFoodFactsRepository: OpenFoodFactsRepository = OpenFoodFactsRepository()
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ItemEditUiState())
    val uiState: StateFlow<ItemEditUiState> = _uiState.asStateFlow()

    private var initialItem: FridgeItem? = null
    private var initialOcrText: String? = null

    fun initItem(item: FridgeItem?, ocrText: String? = null) {
        initialItem = item
        initialOcrText = ocrText ?: item?.notes?.takeIf { it.startsWith("OCR:") }?.removePrefix("OCR:")?.trim() ?: item?.name
        _uiState.value = ItemEditUiState(
            name = item?.name ?: "",
            barcode = item?.barcode ?: "",
            imageUrl = item?.imageUrl ?: "",
            brand = item?.brand ?: "",
            quantity = item?.quantity ?: 1,
            unit = item?.unit ?: "Stk.",
            storageLocation = item?.storageLocation ?: "Kühlschrank",
            freezerDrawer = item?.freezerDrawer ?: 0,
            expiryDate = item?.expiryDate?.let { java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY).format(java.util.Date(it)) } ?: ""
        )
    }

    fun onExpiryDateChange(newDate: String) {
        _uiState.value = _uiState.value.copy(expiryDate = newDate)
    }

    fun onNameChange(newName: String) {
        _uiState.value = _uiState.value.copy(name = newName)
    }

    fun onBarcodeChange(newBarcode: String) {
        _uiState.value = _uiState.value.copy(barcode = newBarcode)
    }

    fun onImageUrlChange(newUrl: String) {
        _uiState.value = _uiState.value.copy(imageUrl = newUrl)
    }

    fun onQuantityChange(newQty: Int) {
        _uiState.value = _uiState.value.copy(quantity = newQty)
    }

    fun onUnitChange(newUnit: String) {
        _uiState.value = _uiState.value.copy(unit = newUnit)
    }

    fun onLocationChange(newLocation: String) {
        _uiState.value = _uiState.value.copy(storageLocation = newLocation)
    }

    fun onDrawerChange(newDrawer: Int) {
        _uiState.value = _uiState.value.copy(freezerDrawer = newDrawer)
    }

    fun scanBarcode(barcode: String) {
        val cleanBarcode = barcode.trim()
        if (cleanBarcode.isBlank()) return

        _uiState.value = _uiState.value.copy(isScanning = true, errorMessage = null)
        viewModelScope.launch {
            val result = openFoodFactsRepository.fetchScannedProduct(cleanBarcode)
            result.fold(
                onSuccess = { productDetails ->
                    if (productDetails != null) {
                        _uiState.value = _uiState.value.copy(
                            isScanning = false,
                            barcode = cleanBarcode,
                            name = productDetails.name,
                            brand = productDetails.brand ?: _uiState.value.brand,
                            imageUrl = productDetails.imageUrl ?: _uiState.value.imageUrl,
                            errorMessage = null
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isScanning = false,
                            barcode = cleanBarcode,
                            errorMessage = "Kein Produkt bei Open Food Facts gefunden."
                        )
                    }
                },
                onFailure = { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isScanning = false,
                        barcode = cleanBarcode,
                        errorMessage = "Netzwerkfehler: ${throwable.localizedMessage}"
                    )
                }
            )
        }
    }

    fun saveItem(onSaved: (FridgeItem) -> Unit) {
        val currentState = _uiState.value
        val currentItem = initialItem
        val updatedItem = (currentItem ?: FridgeItem(name = currentState.name)).copy(
            name = currentState.name.trim(),
            barcode = currentState.barcode.trim().takeIf { it.isNotBlank() },
            imageUrl = currentState.imageUrl.trim().takeIf { it.isNotBlank() },
            brand = currentState.brand.trim().takeIf { it.isNotBlank() },
            quantity = currentState.quantity,
            unit = currentState.unit,
            storageLocation = currentState.storageLocation,
            freezerDrawer = if (currentState.storageLocation == "Gefrierfach") currentState.freezerDrawer else 0,
            expiryDate = MhdOcrHelper.parseDateToTimestamp(currentState.expiryDate) ?: currentItem?.expiryDate
        )

        viewModelScope.launch(Dispatchers.IO) {
            // 1. Kühlschrank-Inventar aktualisieren
            if (currentItem != null) {
                dao.updateItem(updatedItem)
            } else {
                dao.insertItem(updatedItem)
            }

            // 2. Lern-Effekt: Auto-Korrektur in UserLearnedCorrections & UserCorrections sichern
            val rawOcr = initialOcrText?.trim()
            if (!rawOcr.isNullOrBlank() && (rawOcr != updatedItem.name || !updatedItem.imageUrl.isNullOrBlank())) {
                val learnedCorrection = UserLearnedCorrection(
                    receiptRawText = rawOcr,
                    correctedName = updatedItem.name,
                    imageUrl = updatedItem.imageUrl
                )
                dao.insertUserLearnedCorrection(learnedCorrection)

                val userCorrection = UserCorrection(
                    rawReceiptText = rawOcr,
                    correctedName = updatedItem.name,
                    imageUrl = updatedItem.imageUrl,
                    storageLocation = updatedItem.storageLocation
                )
                dao.insertUserCorrection(userCorrection)

                withContext(Dispatchers.Main) {
                    ReceiptImportSanitizer.addLearnedCorrection(rawOcr, updatedItem.name)
                    MarketDictionaryHelper.addLearnedCorrectionWithImage(rawOcr, updatedItem.name, updatedItem.imageUrl)
                }
            }

            withContext(Dispatchers.Main) {
                onSaved(updatedItem)
            }
        }
    }
}
