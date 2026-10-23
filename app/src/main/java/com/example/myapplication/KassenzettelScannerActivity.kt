package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.GeminiRepository
import com.example.myapplication.data.Product
import com.google.gson.Gson
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class KassenzettelScannerActivity : ComponentActivity() {

    private val geminiRepository = GeminiRepository()
    
    private val scannerOptions = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .build()
        
    private val scanner = GmsDocumentScanning.getClient(scannerOptions)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val isFridgeMode = intent.getBooleanExtra("IS_FRIDGE_SCAN", false)
        setContent {
            MaterialTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(if (isFridgeMode) "Vorrats-Scanner" else "Kassenbon Scanner") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Zurück"
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        ScannerScreen()
                    }
                }
            }
        }
    }

    @Composable
    fun ScannerScreen() {
        val context = LocalContext.current
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        var isAnalyzing by remember { mutableStateOf(false) }
        var detectedItems by remember { mutableStateOf<List<Product>>(emptyList()) }
        var unrecognizedBoxes by remember { mutableStateOf<List<android.graphics.Rect>>(emptyList()) }
        val scope = rememberCoroutineScope()
        
        val scannerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val scanResult = com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                val pages = scanResult?.pages ?: emptyList()
                
                if (pages.isNotEmpty()) {
                    isAnalyzing = true
                    scope.launch {
                        try {
                            val bitmaps = pages.mapNotNull { page ->
                                processAndScaleImage(context, page.imageUri, isFridgeMode = intent.getBooleanExtra("IS_FRIDGE_SCAN", false))
                            }
                            
                            if (bitmaps.isNotEmpty()) {
                                // Erstes Bild als Vorschau setzen
                                bitmap = bitmaps.first()
                                
                                val isFridgeMode = intent.getBooleanExtra("IS_FRIDGE_SCAN", false)
                                val (offlineItems, boxes) = analyzeReceiptOffline(bitmaps.first())
                                unrecognizedBoxes = boxes
                                
                                val items = if (isFridgeMode) {
                                    geminiRepository.analyzeFridgePhoto(bitmaps)
                                } else {
                                    geminiRepository.analyzeReceipt(bitmaps.first())
                                }
                                
                                if (items.isEmpty() && !isFridgeMode) {
                                    detectedItems = offlineItems
                                    if (detectedItems.isEmpty()) {
                                        Toast.makeText(context, "Fehler bei der Analyse. Bitte Bild wiederholen.", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    detectedItems = if (items.isNotEmpty()) {
                                        items.map { gItem ->
                                            val matchedBox = offlineItems.find { it.name.equals(gItem.name, true) || it.rawText?.contains(gItem.name, true) == true }?.boundingBox
                                            if (matchedBox != null) gItem.copy(boundingBox = matchedBox) else gItem
                                        }
                                    } else {
                                        offlineItems
                                    }
                                    if (detectedItems.isEmpty()) {
                                        Toast.makeText(context, "KI konnte keine Artikel erkennen.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        } finally {
                            isAnalyzing = false
                        }
                    }
                }
            }
        }

        val galleryLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                isAnalyzing = true
                scope.launch {
                    try {
                        val isFridgeMode = intent.getBooleanExtra("IS_FRIDGE_SCAN", false)
                        val scaledBmp = processAndScaleImage(context, uri, isFridgeMode = isFridgeMode)
                        if (scaledBmp != null) {
                            bitmap = scaledBmp
                            val (offlineItems, boxes) = analyzeReceiptOffline(scaledBmp)
                            unrecognizedBoxes = boxes
                            val items = if (isFridgeMode) {
                                geminiRepository.analyzeFridgePhoto(listOf(scaledBmp))
                            } else {
                                geminiRepository.analyzeReceipt(scaledBmp)
                            }
                            detectedItems = if (items.isNotEmpty()) {
                                items.map { gItem ->
                                    val matchedBox = offlineItems.find { it.name.equals(gItem.name, true) || it.rawText?.contains(gItem.name, true) == true }?.boundingBox
                                    if (matchedBox != null) gItem.copy(boundingBox = matchedBox) else gItem
                                }
                            } else {
                                offlineItems
                            }
                            if (detectedItems.isEmpty()) {
                                Toast.makeText(context, "Keine Artikel erkannt.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ScannerScreen", "Fehler bei Galerie-Import", e)
                        Toast.makeText(context, "Fehler beim Laden des Bildes.", Toast.LENGTH_SHORT).show()
                    } finally {
                        isAnalyzing = false
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val isFridgeMode = intent.getBooleanExtra("IS_FRIDGE_SCAN", false)
            Text(
                text = if (isFridgeMode) "Fotografiere mehrere Artikel gleichzeitig" else "Scanne deinen Beleg für den Auto-Import",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (bitmap != null) {
                Box(
                    modifier = Modifier
                        .height(280.dp)
                        .fillMaxWidth()
                ) {
                    val bmp = bitmap!!
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Bon Foto",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val scale = minOf(size.width / bmp.width, size.height / bmp.height)
                        val offsetX = (size.width - bmp.width * scale) / 2f
                        val offsetY = (size.height - bmp.height * scale) / 2f

                        // 1. Unerkannte Zeilen ROT umranden
                        unrecognizedBoxes.forEach { rect ->
                            drawRect(
                                color = androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.85f),
                                topLeft = androidx.compose.ui.geometry.Offset(offsetX + rect.left * scale, offsetY + rect.top * scale),
                                size = androidx.compose.ui.geometry.Size(rect.width() * scale, rect.height() * scale),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                            )
                        }

                        // 2. Erkannte Artikel GRÜN umranden
                        detectedItems.forEach { product ->
                            product.boundingBox?.let { boxStr ->
                                val parts = boxStr.split(",").mapNotNull { it.toIntOrNull() }
                                if (parts.size == 4) {
                                    val (l, t, r, b) = parts
                                    drawRect(
                                        color = androidx.compose.ui.graphics.Color(0xFF00C853),
                                        topLeft = androidx.compose.ui.geometry.Offset(offsetX + l * scale, offsetY + t * scale),
                                        size = androidx.compose.ui.geometry.Size((r - l) * scale, (b - t) * scale),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.height(250.dp).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Nutze den Google Doc Scanner für beste Ergebnisse")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { 
                        scanner.getStartScanIntent(this@KassenzettelScannerActivity)
                            .addOnSuccessListener { intentSender ->
                                scannerLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(intentSender).build())
                            }
                    },
                    enabled = !isAnalyzing,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isFridgeMode) "Foto" else "Kamera")
                }

                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    enabled = !isAnalyzing,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Galerie")
                }
            }

            if (isAnalyzing) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text("Analysiere mit Gemini...")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (detectedItems.isNotEmpty()) {
                Text("Erkannte Artikel:", style = MaterialTheme.typography.titleLarge)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(detectedItems) { item ->
                        ListItem(
                            headlineContent = { Text(item.name) },
                            supportingContent = { Text("${item.quantity}x - ${item.price}€") }
                        )
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val shareText = buildString {
                                appendLine("🧾 Erkannte Artikel (${java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY).format(java.util.Date())}):")
                                appendLine("─────────────────")
                                detectedItems.forEach {
                                    appendLine("• ${it.quantity}x ${it.name} (${String.format(java.util.Locale.GERMANY, "%.2f €", it.price)})")
                                }
                                appendLine("─────────────────")
                                val sum = detectedItems.sumOf { it.price * it.quantity }
                                if (sum > 0) appendLine("Gesamt: ${String.format(java.util.Locale.GERMANY, "%.2f €", sum)}")
                            }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            startActivity(Intent.createChooser(shareIntent, "Einkauf teilen"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Teilen")
                    }

                    Button(
                        onClick = {
                            val resultIntent = Intent().apply {
                                putExtra("SCAN_RESULTS", Gson().toJson(detectedItems))
                                // Bildpfad übergeben (temporäre Datei)
                                bitmap?.let { bmp ->
                                    try {
                                        val tempFile = File(cacheDir, "temp_receipt_scan.jpg")
                                        val out = FileOutputStream(tempFile)
                                        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
                                        out.flush()
                                        out.close()
                                        putExtra("SCAN_BITMAP_PATH", tempFile.absolutePath)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                            setResult(RESULT_OK, resultIntent)
                            finish()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Übernehmen")
                    }
                }
            }
        }
    }

    private suspend fun analyzeReceiptOffline(bitmap: Bitmap): Pair<List<Product>, List<android.graphics.Rect>> = withContext(Dispatchers.IO) {
        try {
            ReceiptImportSanitizer.loadDictionaryFromAssets(applicationContext)
            val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
            val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
            )
            val visionText = com.google.android.gms.tasks.Tasks.await(recognizer.process(image))

            // Gelernte Korrekturen aus der lokalen Room-Datenbank laden
            val db = com.example.myapplication.data.AppDatabase.getDatabase(applicationContext)
            val corrections = db.fridgeItemDao().getAllLearningDataSync().associate { 
                it.rawName to it.correctedName 
            }

            // Mit Lerndaten und Bounding-Boxes parsen!
            val parseResult = KassenzettelParser.parseReceiptWithBoxes(visionText, corrections)
            val rawProducts = parseResult.products

            // Automatischer OCR-Abgleich gegen Room-Datenbank (market_products.db)
            val productDao = com.example.myapplication.data.MarketProductDatabase.getInstance(applicationContext).productDao()
            val matchedProducts = rawProducts.map { prod ->
                val queryStr = prod.rawText ?: prod.name
                val match = productDao.matchOcrText(queryStr)
                    ?: productDao.findByReceiptPattern(queryStr)
                    ?: productDao.findByCleanName(queryStr)
                if (match != null) {
                    prod.copy(
                        name = match.cleanName,
                        category = match.category,
                        defaultStorage = match.defaultStorage
                    )
                } else {
                    prod
                }
            }
            Pair(matchedProducts, parseResult.unrecognizedBoxes)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(emptyList(), emptyList())
        }
    }

    private suspend fun processAndScaleImage(context: Context, uri: Uri, isFridgeMode: Boolean): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return@withContext null

            // 1. Skalierung (für Kühlschrank-Fotos etwas kleiner, um das Netzwerk nicht zu belasten)
            val maxDim = if (isFridgeMode) 1200 else 2000
            val scaledBitmap = if (originalBitmap.width > maxDim || originalBitmap.height > maxDim) {
                val scale = maxDim.toFloat() / Math.max(originalBitmap.width, originalBitmap.height)
                Bitmap.createScaledBitmap(
                    originalBitmap,
                    (originalBitmap.width * scale).toInt(),
                    (originalBitmap.height * scale).toInt(),
                    true
                )
            } else {
                originalBitmap
            }

            // 2. Kontrastverstärkung NUR für Kassenbons
            val finalBitmap = if (isFridgeMode) {
                scaledBitmap
            } else {
                val enhancedBitmap = enhanceReceiptContrast(scaledBitmap)
                if (scaledBitmap != originalBitmap) scaledBitmap.recycle()
                enhancedBitmap
            }
            
            if (originalBitmap != scaledBitmap && originalBitmap != finalBitmap) originalBitmap.recycle() 

            return@withContext finalBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Erhöht Kontrast und Schärfe für blassen Thermodruck.
     */
    private fun enhanceReceiptContrast(src: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint()

        // Graustufen + starker Kontrast
        val cm = ColorMatrix().apply {
            setSaturation(0f) // Sättigung raus -> Schwarz/Weiß
            val scale = 1.6f  // Kontrast um 60 % anheben
            val translate = -60f // Dunkle Stellen schwärzen, helle Stellen weißer machen
            postConcat(ColorMatrix(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }
}
