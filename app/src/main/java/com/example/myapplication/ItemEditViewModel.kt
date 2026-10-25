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
import com.example.myapplication.data.UserLearnedDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ItemEditUiState(
    val name: String = "",
    val barcode: String = "",
    val imageUrl: String = "",
    val brand: String = "",
    val category: String = "Sonstiges",
    val nutriScore: String = "",
    val calories: String = "",
    val quantity: Int = 1,
    val unit: String = "Stk.",
    val storageLocation: String = "Kühlschrank",
    val freezerDrawer: Int = 0,
    val expiryDate: String = "",
    val isScanning: Boolean = false,
    val errorMessage: String? = null,
    val wasteWarning: String? = null
)

class ItemEditViewModel(
    application: Application,
    private val dao: FridgeDao = AppDatabase.getDatabase(application).fridgeItemDao(),
    private val userLearnedDao: UserLearnedDao = AppDatabase.getDatabase(application).userLearnedDao(),
    private val openFoodFactsRepository: OpenFoodFactsRepository = OpenFoodFactsRepository()
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ItemEditUiState())
    val uiState: StateFlow<ItemEditUiState> = _uiState.asStateFlow()

    private var initialItem: FridgeItem? = null
    private var initialOcrText: String? = null

    fun initItem(item: FridgeItem?, ocrText: String? = null) {
        initialItem = item
        initialOcrText = ocrText ?: item?.notes?.takeIf { it.startsWith("OCR:") }?.removePrefix("OCR:")?.trim() ?: item?.name
        val caloriesStr = item?.calories?.takeIf { it.isNotBlank() }
            ?: (item?.kcal?.takeIf { it > 0 }?.toString() ?: "")
        _uiState.value = ItemEditUiState(
            name = item?.name ?: "",
            barcode = item?.barcode ?: "",
            imageUrl = item?.imageUrl ?: "",
            brand = item?.brand ?: "",
            category = item?.category?.takeIf { it.isNotBlank() } ?: "Sonstiges",
            nutriScore = item?.nutriScore ?: "",
            calories = caloriesStr,
            quantity = item?.quantity ?: 1,
            unit = item?.unit ?: "Stk.",
            storageLocation = item?.storageLocation ?: "Kühlschrank",
            freezerDrawer = item?.freezerDrawer ?: 0,
            expiryDate = item?.expiryDate?.let { java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY).format(java.util.Date(it)) } ?: ""
        )
        checkWasteHistory(item?.name ?: "")
    }

    fun checkWasteHistory(productName: String) {
        val clean = productName.trim()
        if (clean.isBlank()) {
            _uiState.value = _uiState.value.copy(wasteWarning = null)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val wastedList = dao.getAllWastedItems().first()
                val wasteCount = wastedList.count {
                    it.name.trim().equals(clean, ignoreCase = true) ||
                    (clean.length >= 4 && it.name.contains(clean, ignoreCase = true))
                }
                val warning = if (wasteCount >= 2) {
                    "Achtung: Dieser Artikel landet bei dir oft im Müll ($wasteCount-mal weggeworfen)."
                } else {
                    null
                }
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(wasteWarning = warning)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(wasteWarning = null)
                }
            }
        }
    }

    fun onExpiryDateChange(newDate: String) {
        _uiState.value = _uiState.value.copy(expiryDate = newDate)
    }

    fun onNameChange(newName: String) {
        _uiState.value = _uiState.value.copy(name = newName)
        checkWasteHistory(newName)
    }

    fun onBarcodeChange(newBarcode: String) {
        _uiState.value = _uiState.value.copy(barcode = newBarcode)
    }

    fun onImageUrlChange(newUrl: String) {
        _uiState.value = _uiState.value.copy(imageUrl = newUrl)
    }

    fun onCategoryChange(newCategory: String) {
        _uiState.value = _uiState.value.copy(category = newCategory)
    }

    fun onNutriScoreChange(newScore: String) {
        _uiState.value = _uiState.value.copy(nutriScore = newScore.uppercase())
    }

    fun onCaloriesChange(newCalories: String) {
        _uiState.value = _uiState.value.copy(calories = newCalories)
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

    fun scanBarcode(barcode: String, onResult: ((Boolean, String?) -> Unit)? = null) {
        val cleanBarcode = barcode.trim()
        if (cleanBarcode.isBlank()) return

        _uiState.value = _uiState.value.copy(isScanning = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val result = openFoodFactsRepository.fetchScannedProduct(cleanBarcode)
                result.fold(
                    onSuccess = { productDetails ->
                        if (productDetails != null && !productDetails.name.isBlank()) {
                            val productName = productDetails.name.trim()
                            val officialPhotoUrl = productDetails.imageUrl?.trim()?.takeIf { it.isNotBlank() }
                            val effectiveImageUrl = officialPhotoUrl ?: _uiState.value.imageUrl
                            val detectedCategory = productDetails.category?.takeIf { it.isNotBlank() }
                                ?: openFoodFactsRepository.mapOpenFoodFactsCategory(productDetails.rawCategories, productDetails.categoriesTags)

                            _uiState.value = _uiState.value.copy(
                                isScanning = false,
                                barcode = cleanBarcode,
                                name = productName,
                                brand = productDetails.brand ?: _uiState.value.brand,
                                imageUrl = effectiveImageUrl,
                                category = detectedCategory,
                                nutriScore = productDetails.nutriScore?.uppercase() ?: _uiState.value.nutriScore,
                                calories = productDetails.calories ?: _uiState.value.calories,
                                errorMessage = null
                            )

                            // Lern-Logik: Mapping automatisch in das UserLearnedDao eintragen
                            withContext(Dispatchers.IO) {
                                userLearnedDao.insertCorrection(
                                    UserLearnedCorrection(
                                        receiptRawText = cleanBarcode,
                                        correctedName = productName,
                                        imageUrl = effectiveImageUrl.takeIf { it.isNotBlank() }
                                    )
                                )
                                val originName = initialOcrText?.trim()?.takeIf { it.isNotBlank() }
                                    ?: initialItem?.name?.trim()?.takeIf { it.isNotBlank() }
                                if (!originName.isNullOrBlank() && originName != cleanBarcode && originName != productName) {
                                    userLearnedDao.insertCorrection(
                                        UserLearnedCorrection(
                                            receiptRawText = originName,
                                            correctedName = productName,
                                            imageUrl = effectiveImageUrl.takeIf { it.isNotBlank() }
                                        )
                                    )
                                }
                            }

                            ReceiptImportSanitizer.addLearnedCorrection(cleanBarcode, productName)
                            MarketDictionaryHelper.addLearnedCorrectionWithImage(
                                cleanBarcode,
                                productName,
                                effectiveImageUrl.takeIf { it.isNotBlank() }
                            )

                            checkWasteHistory(productName)

                            onResult?.invoke(true, null)
                        } else {
                            val notFoundMsg = "Kein Produkt bei Open Food Facts gefunden."
                            _uiState.value = _uiState.value.copy(
                                isScanning = false,
                                barcode = cleanBarcode,
                                errorMessage = notFoundMsg
                            )
                            onResult?.invoke(false, notFoundMsg)
                        }
                    },
                    onFailure = { throwable ->
                        val errorMsg = "Fehler beim Laden: ${throwable.localizedMessage ?: "Verbindungsfehler"}"
                        _uiState.value = _uiState.value.copy(
                            isScanning = false,
                            barcode = cleanBarcode,
                            errorMessage = errorMsg
                        )
                        onResult?.invoke(false, errorMsg)
                    }
                )
            } catch (e: Exception) {
                val errorMsg = "Fehler beim Scannen: ${e.localizedMessage ?: "Unbekannter Fehler"}"
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    barcode = cleanBarcode,
                    errorMessage = errorMsg
                )
                onResult?.invoke(false, errorMsg)
            }
        }
    }

    fun saveItem(onSaved: (FridgeItem) -> Unit) {
        val currentState = _uiState.value
        val currentItem = initialItem
        val kcalInt = currentState.calories.trim().toIntOrNull() ?: currentItem?.kcal ?: 0
        val updatedItem = (currentItem ?: FridgeItem(name = currentState.name)).copy(
            name = currentState.name.trim(),
            barcode = currentState.barcode.trim().takeIf { it.isNotBlank() },
            imageUrl = currentState.imageUrl.trim().takeIf { it.isNotBlank() },
            brand = currentState.brand.trim().takeIf { it.isNotBlank() },
            category = currentState.category.trim().ifBlank { "Sonstiges" },
            nutriScore = currentState.nutriScore.trim().uppercase(),
            calories = currentState.calories.trim(),
            kcal = kcalInt,
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
