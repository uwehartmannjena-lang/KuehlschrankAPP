package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.myapplication.data.Product
import com.example.myapplication.data.GeminiRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Canvas
import android.graphics.Paint

class KassenzettelScannerActivity : ComponentActivity() {

    private val geminiRepository = GeminiRepository()
    
    private val scannerOptions = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .build()
        
    private val scanner = GmsDocumentScanning.getClient(scannerOptions)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScannerScreen()
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
                                
                                val items = if (isFridgeMode) {
                                    geminiRepository.analyzeFridgePhoto(bitmaps)
                                } else {
                                    // Für Kassenbons (Receipt) nutzen wir das erste Bild oder kombinieren?
                                    // Gemini 1.5 kann auch mehrere Bilder für einen Bon analysieren
                                    geminiRepository.analyzeReceipt(bitmaps.first())
                                }
                                
                                if (items.isEmpty() && !isFridgeMode) {
                                    detectedItems = analyzeReceiptOffline(bitmaps.first())
                                    if (detectedItems.isEmpty()) {
                                        Toast.makeText(context, "Fehler bei der Analyse. Bitte Bild wiederholen.", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    detectedItems = items
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

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val isFridgeMode = intent.getBooleanExtra("IS_FRIDGE_SCAN", false)
            Text(if (isFridgeMode) "Vorrats-Scanner 🍏" else "Kassenbon Scanner (Premium)", style = MaterialTheme.typography.headlineMedium)
            Text(if (isFridgeMode) "Fotografiere mehrere Artikel gleichzeitig" else "Scanne deinen Beleg für den Auto-Import", fontSize = 12.sp)
            Spacer(modifier = Modifier.height(16.dp))

            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Bon Foto",
                    modifier = Modifier.height(250.dp).fillMaxWidth()
                )
            } else {
                Box(
                    modifier = Modifier.height(250.dp).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Nutze den Google Doc Scanner für beste Ergebnisse")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { 
                scanner.getStartScanIntent(this@KassenzettelScannerActivity)
                    .addOnSuccessListener { intentSender ->
                        scannerLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(intentSender).build())
                    }
            }, enabled = !isAnalyzing) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isFridgeMode) "Foto machen" else "Bon scannen")
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
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Übernehmen")
                }
            }
        }
    }

    private suspend fun analyzeReceiptOffline(bitmap: Bitmap): List<Product> = withContext(Dispatchers.IO) {
        try {
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

            // Mit Lerndaten parsen!
            KassenzettelParser.parseReceipt(visionText, corrections)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
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
