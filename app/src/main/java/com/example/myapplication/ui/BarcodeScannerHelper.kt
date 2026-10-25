package com.example.myapplication.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.myapplication.MarketDictionaryHelper
import com.example.myapplication.ReceiptImportSanitizer
import com.example.myapplication.ScannerActivity
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.OpenFoodFactsRepository
import com.example.myapplication.data.UserLearnedCorrection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScannedBarcodeItem(
    val barcode: String,
    val productName: String,
    val imageUrl: String? = null,
    val brand: String? = null,
    val nutriScore: String? = null,
    val calories: String? = null,
    val category: String? = null
)

class BarcodeScannerController(
    val context: Context,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val onProductScanned: (ScannedBarcodeItem) -> Unit,
    private val launchScanner: () -> Unit,
    private val requestPermission: () -> Unit
) {
    var isScanning by mutableStateOf(false)
    var lastScannedBarcode by mutableStateOf<String?>(null)

    fun startScan() {
        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            launchScanner()
        } else {
            requestPermission()
        }
    }

    fun handleBarcodeResult(barcode: String) {
        val cleanBarcode = barcode.trim()
        if (cleanBarcode.isBlank()) return

        lastScannedBarcode = cleanBarcode
        isScanning = true

        scope.launch {
            try {
                // 1. Offline-First: Lokale UserLearned-DB abfragen
                val db = AppDatabase.getDatabase(context)
                val localCorrection = withContext(Dispatchers.IO) {
                    db.userLearnedDao().getCorrection(cleanBarcode)
                }

                if (localCorrection != null && localCorrection.correctedName.isNotBlank()) {
                    val result = ScannedBarcodeItem(
                        barcode = cleanBarcode,
                        productName = localCorrection.correctedName,
                        imageUrl = localCorrection.imageUrl
                    )
                    isScanning = false
                    onProductScanned(result)
                    Toast.makeText(context, "Artikel lokal gefunden: ${result.productName}", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // 2. OpenFoodFacts Online API Abfrage
                val offRepo = OpenFoodFactsRepository()
                val offResult = withContext(Dispatchers.IO) {
                    offRepo.fetchScannedProduct(cleanBarcode)
                }

                offResult.fold(
                    onSuccess = { productDetails ->
                        if (productDetails != null && productDetails.name.isNotBlank()) {
                            val result = ScannedBarcodeItem(
                                barcode = cleanBarcode,
                                productName = productDetails.name.trim(),
                                imageUrl = productDetails.imageUrl?.trim()?.takeIf { it.isNotBlank() },
                                brand = productDetails.brand?.trim(),
                                nutriScore = productDetails.nutriScore?.trim()?.takeIf { it.isNotBlank() },
                                calories = productDetails.calories?.trim()?.takeIf { it.isNotBlank() },
                                category = productDetails.category ?: "Sonstiges"
                            )
                            isScanning = false
                            onProductScanned(result)

                            // Automatisches Speichern im UserLearnedDao (Lern-Logik)
                            saveLearnedMapping(cleanBarcode, result.productName, result.imageUrl)

                            Toast.makeText(context, "Produktdaten erfolgreich übernommen", Toast.LENGTH_SHORT).show()
                        } else {
                            isScanning = false
                            Toast.makeText(context, "Kein Produkt für Barcode $cleanBarcode gefunden", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFailure = { throwable ->
                        isScanning = false
                        Toast.makeText(context, "Fehler beim Laden: ${throwable.localizedMessage ?: "Offline"}", Toast.LENGTH_SHORT).show()
                    }
                )
            } catch (e: Exception) {
                isScanning = false
                Toast.makeText(context, "Fehler beim Scannen: ${e.localizedMessage ?: "Unbekannt"}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun saveLearnedMapping(
        barcode: String,
        finalName: String,
        imageUrl: String? = null,
        originalName: String? = null
    ) {
        val cleanBarcode = barcode.trim()
        val cleanFinalName = finalName.trim()
        if (cleanBarcode.isBlank() || cleanFinalName.isBlank()) return

        scope.launch(Dispatchers.IO) {
            try {
                val dao = AppDatabase.getDatabase(context).userLearnedDao()
                val effectiveImage = imageUrl?.trim()?.takeIf { it.isNotBlank() }

                dao.insertCorrection(
                    UserLearnedCorrection(
                        receiptRawText = cleanBarcode,
                        correctedName = cleanFinalName,
                        imageUrl = effectiveImage
                    )
                )

                val cleanOriginal = originalName?.trim()?.takeIf { it.isNotBlank() }
                if (!cleanOriginal.isNullOrBlank() && cleanOriginal != cleanBarcode && cleanOriginal != cleanFinalName) {
                    dao.insertCorrection(
                        UserLearnedCorrection(
                            receiptRawText = cleanOriginal,
                            correctedName = cleanFinalName,
                            imageUrl = effectiveImage
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    ReceiptImportSanitizer.addLearnedCorrection(cleanBarcode, cleanFinalName)
                    MarketDictionaryHelper.addLearnedCorrectionWithImage(cleanBarcode, cleanFinalName, effectiveImage)
                    if (!cleanOriginal.isNullOrBlank()) {
                        ReceiptImportSanitizer.addLearnedCorrection(cleanOriginal, cleanFinalName)
                        MarketDictionaryHelper.addLearnedCorrectionWithImage(cleanOriginal, cleanFinalName, effectiveImage)
                    }
                }
            } catch (_: Exception) {
                // Crash-Resistenz
            }
        }
    }
}

@Composable
fun rememberBarcodeScanner(
    onProductScanned: (ScannedBarcodeItem) -> Unit
): BarcodeScannerController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var controllerRef: BarcodeScannerController? by remember { mutableStateOf(null) }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val barcode = result.data?.getStringExtra("SCANNED_BARCODE")
            if (!barcode.isNullOrBlank()) {
                controllerRef?.handleBarcodeResult(barcode)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val intent = Intent(context, ScannerActivity::class.java).apply {
                putExtra("EXTRA_MULTI_SCAN", false)
            }
            scannerLauncher.launch(intent)
        } else {
            Toast.makeText(context, "Kamera-Berechtigung wird für den Barcode-Scanner benötigt", Toast.LENGTH_SHORT).show()
        }
    }

    val controller = remember {
        BarcodeScannerController(
            context = context,
            scope = scope,
            onProductScanned = onProductScanned,
            launchScanner = {
                val intent = Intent(context, ScannerActivity::class.java).apply {
                    putExtra("EXTRA_MULTI_SCAN", false)
                }
                scannerLauncher.launch(intent)
            },
            requestPermission = {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        )
    }

    controllerRef = controller
    return controller
}

@Composable
fun BarcodeScannerIconButton(
    controller: BarcodeScannerController,
    modifier: Modifier = Modifier,
    contentDescription: String = "Barcode scannen"
) {
    if (controller.isScanning) {
        CircularProgressIndicator(
            modifier = modifier.size(24.dp).padding(2.dp),
            strokeWidth = 2.dp
        )
    } else {
        IconButton(
            onClick = { controller.startScan() },
            modifier = modifier
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
