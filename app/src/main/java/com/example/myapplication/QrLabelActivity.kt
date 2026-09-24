package com.example.myapplication

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.QrUtils

class QrLabelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = intent.getStringExtra("QR_CONTENT") ?: "FrischeRadar"
        val itemName = intent.getStringExtra("ITEM_NAME") ?: "Artikel"
        
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = "Druckbares Etikett für:", style = MaterialTheme.typography.titleLarge)
                        Text(text = itemName, style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(24.dp))
                        val qrBitmap = remember { QrUtils.generateQrCode(content) }
                        qrBitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.size(250.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { /* Drucklogik simulieren */ }) {
                            Text("Jetzt drucken")
                        }
                    }
                }
            }
        }
    }
}
