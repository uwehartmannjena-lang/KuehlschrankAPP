package com.example.myapplication.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.example.myapplication.CameraHelper
import com.example.myapplication.ItemEditViewModel
import com.example.myapplication.MhdOcrHelper
import com.example.myapplication.ScannerActivity
import com.example.myapplication.data.FridgeItem
import com.example.myapplication.ui.LocationTileSelector
import com.example.myapplication.ui.QuantityStepper
import com.example.myapplication.ui.UnitSelector
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditItemScreen(
    item: FridgeItem? = null,
    ocrRawText: String? = null,
    viewModel: ItemEditViewModel = viewModel(),
    onDismiss: () -> Unit = {},
    onConfirm: (FridgeItem) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var isCroppingForMhd by remember { mutableStateOf(false) }

    LaunchedEffect(item, ocrRawText) {
        viewModel.initItem(item, ocrRawText)
    }

    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uriContent = result.uriContent
            if (uriContent != null) {
                if (isCroppingForMhd) {
                    scope.launch {
                        val parsedDate = MhdOcrHelper.parseExpiryDateFromUri(context, uriContent)
                        if (!parsedDate.isNullOrBlank()) {
                            viewModel.onExpiryDateChange(parsedDate)
                        }
                    }
                } else {
                    val localUri = CameraHelper.copyImageToLocalStorage(context, uriContent) ?: uriContent
                    viewModel.onImageUrlChange(localUri.toString())
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempCameraUri?.let { uri ->
                cropLauncher.launch(
                    CropImageContractOptions(
                        uri = uri,
                        cropImageOptions = CropImageOptions()
                    )
                )
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            cropLauncher.launch(
                CropImageContractOptions(
                    uri = uri,
                    cropImageOptions = CropImageOptions()
                )
            )
        }
    }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val barcode = result.data?.getStringExtra("SCANNED_BARCODE")
            if (!barcode.isNullOrBlank()) {
                viewModel.scanBarcode(barcode)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (item == null) "Neuer Artikel" else "Artikel bearbeiten",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Produkt-Vorschau via Coil AsyncImage mit lokalem Disk-Cache
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                val placeholderPainter = rememberVectorPainter(Icons.Default.Image)
                val errorPainter = rememberVectorPainter(Icons.Default.Restaurant)

                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(uiState.imageUrl.takeIf { it.isNotBlank() })
                        .crossfade(true)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = uiState.name.ifBlank { "Produktbild" },
                    placeholder = placeholderPainter,
                    error = errorPainter,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }

            // Buttons für Kamera- und Galerie-Auswahl inklusive Auto-Crop
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        isCroppingForMhd = false
                        val uri = CameraHelper.createTempPictureUri(context)
                        tempCameraUri = uri
                        cameraLauncher.launch(uri)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Foto")
                }

                OutlinedButton(
                    onClick = {
                        isCroppingForMhd = false
                        galleryLauncher.launch("image/*")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Galerie")
                }
            }

            // EAN / Barcode mit Scanner-Button
            OutlinedTextField(
                value = uiState.barcode,
                onValueChange = { viewModel.onBarcodeChange(it) },
                label = { Text("Barcode / EAN") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val intent = Intent(context, ScannerActivity::class.java).apply {
                                putExtra("EXTRA_MULTI_SCAN", false)
                            }
                            scannerLauncher.launch(intent)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Barcode scannen",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )

            if (uiState.isScanning) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Produktdaten werden von Open Food Facts geladen...")
                }
            }

            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Produktname
            OutlinedTextField(
                value = uiState.name,
                onValueChange = { viewModel.onNameChange(it) },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // MHD / Mindesthaltbarkeitsdatum mit OCR-Crop-Scanner
            OutlinedTextField(
                value = uiState.expiryDate,
                onValueChange = { viewModel.onExpiryDateChange(it) },
                label = { Text("MHD (z.B. 15.11.2026)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            isCroppingForMhd = true
                            val uri = CameraHelper.createTempPictureUri(context)
                            tempCameraUri = uri
                            cameraLauncher.launch(uri)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = "MHD scannen",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )

            // Bild-Pfad / Bild-URL
            OutlinedTextField(
                value = uiState.imageUrl,
                onValueChange = { viewModel.onImageUrlChange(it) },
                label = { Text("Bild-URL / Pfad") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Menge und Einheit
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuantityStepper(uiState.quantity) { viewModel.onQuantityChange(it) }
                UnitSelector(uiState.unit) { viewModel.onUnitChange(it) }
            }

            // Lagerort-Auswahl
            LocationTileSelector(uiState.storageLocation) { viewModel.onLocationChange(it) }

            // Gefrierfach-Auswahl
            if (uiState.storageLocation == "Gefrierfach") {
                Text("Gefrierfach Schublade", fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf(1 to "Fach 1", 2 to "Fach 2", 3 to "Fach 3", 4 to "Fach 4")) { (idx, title) ->
                        FilterChip(
                            selected = (uiState.freezerDrawer == idx),
                            onClick = { viewModel.onDrawerChange(idx) },
                            label = { Text(title) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Speichern-Button
            Button(
                onClick = {
                    viewModel.saveItem { savedItem ->
                        onConfirm(savedItem)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = uiState.name.isNotBlank()
            ) {
                Text("Speichern")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
