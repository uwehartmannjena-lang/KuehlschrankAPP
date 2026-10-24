package com.example.myapplication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.BarcodeScanResult
import com.example.myapplication.data.MarketProductDatabase
import com.example.myapplication.data.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BarcodeUiState(
    val isLoading: Boolean = false,
    val scannedProduct: BarcodeScanResult.Success? = null,
    val showManualInputDialog: Boolean = false,
    val pendingBarcode: String? = null,
    val errorMessage: String? = null,
    val nutriScore: String? = null,
    val caloriesKcal: Double? = null,
    val fat: Double? = null,
    val saturatedFat: Double? = null,
    val carbohydrates: Double? = null,
    val sugars: Double? = null,
    val proteins: Double? = null,
    val salt: Double? = null
)

class BarcodeScannerViewModel(
    application: Application,
    private val repository: ProductRepository = ProductRepository(
        dao = AppDatabase.getDatabase(application).fridgeItemDao(),
        marketProductDao = MarketProductDatabase.getInstance(application).marketProductDao(),
        productDao = MarketProductDatabase.getInstance(application).productDao()
    )
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BarcodeUiState())
    val uiState: StateFlow<BarcodeUiState> = _uiState.asStateFlow()

    fun onBarcodeScanned(barcode: String) {
        val cleanBarcode = barcode.trim()
        if (cleanBarcode.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                scannedProduct = null,
                nutriScore = null,
                caloriesKcal = null,
                fat = null,
                saturatedFat = null,
                carbohydrates = null,
                sugars = null,
                proteins = null,
                salt = null
            )

            when (val result = repository.lookupBarcode(cleanBarcode)) {
                is BarcodeScanResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        scannedProduct = result,
                        showManualInputDialog = false,
                        pendingBarcode = null,
                        errorMessage = null,
                        nutriScore = result.nutriScore,
                        caloriesKcal = result.caloriesKcal,
                        fat = result.fat,
                        saturatedFat = result.saturatedFat,
                        carbohydrates = result.carbohydrates,
                        sugars = result.sugars,
                        proteins = result.proteins,
                        salt = result.salt
                    )
                }
                is BarcodeScanResult.Failure.NotFound -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        scannedProduct = null,
                        showManualInputDialog = true,
                        pendingBarcode = cleanBarcode,
                        errorMessage = "Artikel nicht gelistet. Bitte manuell erfassen."
                    )
                }
                is BarcodeScanResult.Failure.NetworkError -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        scannedProduct = null,
                        showManualInputDialog = true,
                        pendingBarcode = cleanBarcode,
                        errorMessage = "Offline oder Netzwerk-Timeout. Bitte Artikel manuell eingeben."
                    )
                }
            }
        }
    }

    fun saveManualEntry(
        name: String,
        brand: String? = null,
        quantity: Int = 1,
        unit: String = "Stk.",
        category: String = "Sonstiges",
        nutriScore: String? = null,
        caloriesKcal: Double? = null,
        fat: Double? = null,
        saturatedFat: Double? = null,
        carbohydrates: Double? = null,
        sugars: Double? = null,
        proteins: Double? = null,
        salt: Double? = null
    ) {
        val barcode = _uiState.value.pendingBarcode ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.saveManualBarcodeProduct(
                barcode = barcode,
                name = name,
                brand = brand,
                quantity = quantity,
                unit = unit,
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
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                showManualInputDialog = false,
                pendingBarcode = null,
                errorMessage = null,
                nutriScore = nutriScore,
                caloriesKcal = caloriesKcal,
                fat = fat,
                saturatedFat = saturatedFat,
                carbohydrates = carbohydrates,
                sugars = sugars,
                proteins = proteins,
                salt = salt,
                scannedProduct = BarcodeScanResult.Success(
                    barcode = barcode,
                    name = name,
                    brand = brand,
                    quantity = quantity,
                    unit = unit,
                    category = category,
                    isFromOfflineDb = true,
                    nutriScore = nutriScore,
                    caloriesKcal = caloriesKcal,
                    fat = fat,
                    saturatedFat = saturatedFat,
                    carbohydrates = carbohydrates,
                    sugars = sugars,
                    proteins = proteins,
                    salt = salt
                )
            )
        }
    }

    fun dismissManualDialog() {
        _uiState.value = _uiState.value.copy(
            showManualInputDialog = false,
            pendingBarcode = null,
            errorMessage = null
        )
    }

    fun clearScannedProduct() {
        _uiState.value = _uiState.value.copy(
            scannedProduct = null,
            nutriScore = null,
            caloriesKcal = null,
            fat = null,
            saturatedFat = null,
            carbohydrates = null,
            sugars = null,
            proteins = null,
            salt = null
        )
    }
}
