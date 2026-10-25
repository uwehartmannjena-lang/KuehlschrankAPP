package com.example.myapplication.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.CameraHelper
import com.example.myapplication.DocumentHelper
import com.example.myapplication.FolderObserverWorker
import com.example.myapplication.KassenzettelParser
import com.example.myapplication.PdfReceiptHelper
import com.example.myapplication.ReceiptImportSanitizer
import com.example.myapplication.ReceiptScannerViewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.FridgeItem
import com.example.myapplication.ui.BarcodeScannerIconButton
import com.example.myapplication.ui.rememberBarcodeScanner
import com.example.myapplication.data.Product
import com.example.myapplication.data.UnconfirmedReceiptItem
import com.example.myapplication.data.UnconfirmedReceiptsRepository
import com.example.myapplication.data.UserLearnedCorrection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScannerScreen(
    viewModel: ReceiptScannerViewModel = viewModel(),
    onScanFinished: (List<Product>) -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    var detectedProducts by remember { mutableStateOf<List<Product>>(emptyList()) }
    val itemsToConfirm by viewModel.itemsToConfirm.collectAsState()
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            val uri = tempCameraUri
            if (uri != null) {
                isProcessing = true
                scope.launch {
                    val products = withContext(Dispatchers.IO) {
                        try {
                            val image = InputImage.fromFilePath(context, uri)
                            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                            val visionText = recognizer.process(image).await()
                            KassenzettelParser.parseReceiptText(visionText.text)
                        } catch (e: Exception) {
                            Log.e("ReceiptScannerScreen", "Fehler beim Lesen des Kamerabildes", e)
                            emptyList<Product>()
                        }
                    }
                    if (products.isNotEmpty()) {
                        detectedProducts = products
                        onScanFinished(products)
                        Toast.makeText(context, "${products.size} Artikel erkannt", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Keine Produkte im Bild erkannt", Toast.LENGTH_SHORT).show()
                    }
                    isProcessing = false
                }
            }
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                Log.e("ReceiptScannerScreen", "Fehler bei takePersistableUriPermission", e)
            }
            val prefs = context.getSharedPreferences("receipt_folder_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("receipt_folder_uri", uri.toString()).apply()
            FolderObserverWorker.schedule(context, uri.toString())
            FolderObserverWorker.runOnce(context, uri.toString())
            Toast.makeText(context, "Kassenbon-Ordner verknüpft & gespeichert", Toast.LENGTH_SHORT).show()
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            scope.launch {
                val tempFile = DocumentHelper.copyUriToTempFile(context, uri)
                if (tempFile != null) {
                    val (products, _) = withContext(Dispatchers.IO) {
                        PdfReceiptHelper.processPdf(context, tempFile)
                    }
                    if (products.isNotEmpty()) {
                        DocumentHelper.archivePdfDocument(context, uri)
                        detectedProducts = products
                        onScanFinished(products)
                        Toast.makeText(context, "${products.size} Artikel eingelesen & archiviert", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Keine Produkte im PDF erkannt", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "PDF konnte nicht geöffnet werden", Toast.LENGTH_SHORT).show()
                }
                isProcessing = false
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            scope.launch {
                val products = withContext(Dispatchers.IO) {
                    try {
                        val image = InputImage.fromFilePath(context, uri)
                        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        val visionText = recognizer.process(image).await()
                        KassenzettelParser.parseReceiptText(visionText.text)
                    } catch (e: Exception) {
                        Log.e("ReceiptScannerScreen", "Fehler beim Lesen des Bildes", e)
                        emptyList<Product>()
                    }
                }
                if (products.isNotEmpty()) {
                    detectedProducts = products
                    onScanFinished(products)
                    Toast.makeText(context, "${products.size} Artikel erkannt", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Keine Produkte im Bild erkannt", Toast.LENGTH_SHORT).show()
                }
                isProcessing = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val uri = CameraHelper.createTempPictureUri(context)
                    tempCameraUri = uri
                    cameraLauncher.launch(uri)
                },
                enabled = !isProcessing,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("ReceiptScannerButton_Camera")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("Kamera", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }

            OutlinedButton(
                onClick = { galleryLauncher.launch("image/*") },
                enabled = !isProcessing,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("ReceiptScannerButton_Gallery")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("Galerie", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }

            Button(
                onClick = { pdfLauncher.launch(arrayOf("application/pdf")) },
                enabled = !isProcessing,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("ReceiptScannerButton_Pdf")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("PDF", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        FilledTonalButton(
            onClick = { folderLauncher.launch(null) },
            enabled = !isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ReceiptScannerButton_Folder")
        ) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Kassenbon-Ordner wählen")
        }

        if (isProcessing) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
            Text("Verarbeite Kassenbon...")
        }

        if (itemsToConfirm.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Zu bestätigen (${itemsToConfirm.size}):",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(onClick = { UnconfirmedReceiptsRepository.clear() }) {
                            Text("Alle verwerfen")
                        }
                    }
                    Text(
                        text = "Tippe auf den Namen, um ihn zu korrigieren und dauerhaft zu lernen:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(itemsToConfirm, key = { it.id }) { item ->
                            UnconfirmedItemCard(
                                item = item,
                                onConfirm = { correctedName ->
                                    viewModel.confirmItem(item, correctedName)
                                    Toast.makeText(context, "Gelernt: $correctedName", Toast.LENGTH_SHORT).show()
                                },
                                onDiscard = {
                                    viewModel.dismissItem(item.id)
                                }
                            )
                        }
                    }
                }
            }
        } else if (detectedProducts.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Erkannte Produkte (${detectedProducts.size}):", style = MaterialTheme.typography.titleMedium)
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(detectedProducts) { product ->
                    ListItem(
                        headlineContent = { Text(product.name) },
                        supportingContent = { Text("${product.price} € • ${product.category}") }
                    )
                }
            }
        }
    }
}

@Composable
fun UnconfirmedItemCard(
    item: UnconfirmedReceiptItem,
    onConfirm: (String) -> Unit,
    onDiscard: () -> Unit
) {
    var editedName by remember(item.id) { mutableStateOf(item.suggestedName) }
    var scannedImageUrl by remember(item.id) { mutableStateOf<String?>(null) }

    val barcodeScanner = rememberBarcodeScanner { scanned ->
        editedName = scanned.productName
        scannedImageUrl = scanned.imageUrl
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Gescannter Text: \"${item.rawText}\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = editedName,
                onValueChange = { editedName = it },
                label = { Text("Produktname zuweisen") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    BarcodeScannerIconButton(controller = barcodeScanner)
                }
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDiscard) {
                    Text("Verwerfen", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val finalName = editedName.trim()
                    val currentBarcode = barcodeScanner.lastScannedBarcode
                    if (!currentBarcode.isNullOrBlank() && finalName.isNotBlank()) {
                        barcodeScanner.saveLearnedMapping(
                            barcode = currentBarcode,
                            finalName = finalName,
                            imageUrl = scannedImageUrl,
                            originalName = item.rawText
                        )
                    }
                    onConfirm(finalName)
                }) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Bestätigen")
                }
            }
        }
    }
}
