package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

class ScannerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isMultiScan = intent.getBooleanExtra("EXTRA_MULTI_SCAN", false)
        
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BarcodeScannerScreen(
                        isMultiScan = isMultiScan,
                        onBarcodeDetected = { barcode ->
                            if (!isMultiScan) {
                                val intent = Intent().apply {
                                    putExtra("SCAN_RESULT", barcode)
                                }
                                setResult(RESULT_OK, intent)
                                finish()
                            }
                        },
                        onMultiScanFinished = { barcodes ->
                            val intent = Intent().apply {
                                putStringArrayListExtra("SCAN_RESULTS", ArrayList(barcodes))
                            }
                            setResult(RESULT_OK, intent)
                            finish()
                        },
                        onClose = { finish() }
                    )
                }
            }
        }
    }

    @Composable
    fun BarcodeScannerScreen(
        isMultiScan: Boolean,
        onBarcodeDetected: (String) -> Unit,
        onMultiScanFinished: (List<String>) -> Unit,
        onClose: () -> Unit
    ) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        var hasPermission by remember { mutableStateOf(false) }
        var detectedProductName by remember { mutableStateOf<String?>(null) }
        
        val scannedBarcodes = remember { mutableStateListOf<String>() }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            hasPermission = isGranted
        }

        LaunchedEffect(Unit) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        if (hasPermission) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        val executor = ContextCompat.getMainExecutor(ctx)
                        val analysisExecutor = Executors.newSingleThreadExecutor()

                        val options = BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                            .build()
                        val barcodeScanner = BarcodeScanning.getClient(options)

                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder()
                                .setTargetResolution(Size(1280, 720))
                                .build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setTargetResolution(Size(1280, 720))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                processImageProxy(barcodeScanner, imageProxy) { barcode ->
                                    if (isMultiScan) {
                                        if (!scannedBarcodes.contains(barcode)) {
                                            scannedBarcodes.add(barcode)
                                            detectedProductName = "Zuletzt: $barcode (${scannedBarcodes.size} erfasst)"
                                        }
                                    } else {
                                        detectedProductName = "Erkannt: $barcode"
                                        onBarcodeDetected(barcode)
                                    }
                                }
                            }

                            try {
                                cameraProvider.unbindAll()
                                val camera = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                                
                                previewView.setOnTouchListener { view, event ->
                                    val action = FocusMeteringAction.Builder(
                                        previewView.meteringPointFactory.createPoint(event.x, event.y)
                                    ).build()
                                    camera.cameraControl.startFocusAndMetering(action)
                                    true
                                }
                            } catch (e: Exception) {
                                Log.e("ScannerActivity", "Camera binding failed", e)
                            }
                        }, executor)
                        
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (isMultiScan) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = "${scannedBarcodes.size} Artikel gescannt",
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Schließen", tint = Color.White)
                        }
                    }
                    
                    Box(contentAlignment = Alignment.Center) {
                        Card(
                            modifier = Modifier.size(260.dp).padding(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            border = BorderStroke(2.dp, Color.Green)
                        ) { }
                        
                        detectedProductName?.let { name ->
                            Surface(
                                color = Color.Black.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
                            ) {
                                Text(
                                    text = name,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isMultiScan) {
                            Button(
                                onClick = { onMultiScanFinished(scannedBarcodes) },
                                modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth(0.8f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Fertig (${scannedBarcodes.size})", color = Color.White, style = MaterialTheme.typography.titleMedium)
                            }
                        } else {
                            Text(
                                text = "Barcode in den Rahmen halten",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Green, strokeWidth = 2.dp)
                        }
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Kamera-Berechtigung erforderlich")
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(
        barcodeScanner: BarcodeScanner,
        imageProxy: ImageProxy,
        onBarcodeDetected: (String) -> Unit
    ) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        barcode.rawValue?.let { 
                            onBarcodeDetected(it)
                            return@addOnSuccessListener 
                        }
                    }
                }
                .addOnFailureListener {
                    Log.e("ScannerActivity", "ML Kit Error", it)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}