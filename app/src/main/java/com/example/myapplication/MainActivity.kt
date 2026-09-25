package com.example.myapplication

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.*
import com.example.myapplication.data.*
import com.example.myapplication.ui.RefrigeratorApp
import com.example.myapplication.ui.openRetailerWebsite
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.util.*
import java.util.concurrent.TimeUnit
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.app.PendingIntent
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
        createNotificationChannel(this)
        scheduleExpiryCheck(this)
        scheduleEnrichmentWorker(this)
        KassenzettelParser.loadProfiles(this)
        PDFBoxResourceLoader.init(applicationContext)

        setContent {
            val context = LocalContext.current
            val database = remember { AppDatabase.getDatabase(context) }
            val factory = remember { FridgeViewModelFactory(database.fridgeItemDao(), context) }
            val fridgeViewModel: FridgeViewModel = viewModel(factory = factory)

            LaunchedEffect(Unit) {
                fridgeViewModel.loadProductLexicon()
            }

            MaterialTheme {
                RefrigeratorApp(fridgeViewModel)
            }

            LaunchedEffect(intent) {
                handleIntent(intent, context, fridgeViewModel)
            }
            
            LaunchedEffect(intent) {
                val retailer = intent?.getStringExtra("OPEN_OFFER_RETAILER")
                val product = intent?.getStringExtra("OPEN_OFFER_PRODUCT")
                if (retailer != null) {
                    openRetailerWebsite(context, retailer, product)
                    intent.removeExtra("OPEN_OFFER_RETAILER")
                    intent.removeExtra("OPEN_OFFER_PRODUCT")
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action || NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }
            tag?.let { processNfcTag(it) }
        }
    }

    private fun processNfcTag(tag: Tag) {
        val tagId = tag.id.joinToString("") { "%02X".format(it) }
        Log.d("NFC", "Tag ID: $tagId")
        val vm = ViewModelProvider(this)[FridgeViewModel::class.java]
        vm.handleNfcDiscovery(tagId)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val channel = NotificationChannel("DEAL_ALERTS", "Angebots Alarm", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Benachrichtigungen für reduzierte Lieblingsartikel"
            }
            
            val expiryChannel = NotificationChannel("EXPIRY_ALERTS", "MHD-Warner", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Mahnung für ablaufende Lebensmittel"
            }

            notificationManager.createNotificationChannel(channel)
            notificationManager.createNotificationChannel(expiryChannel)
        }
    }

    private fun scheduleExpiryCheck(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<ExpiryWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(calculateDelayToNineAM(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("ExpiryCheck", ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }

    private fun scheduleEnrichmentWorker(context: Context) {
        // Läuft alle 12 Stunden im Hintergrund, wenn das Gerät Netzwerk hat
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val workRequest = PeriodicWorkRequestBuilder<EnrichmentWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("EnrichmentCheck", ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }

    private fun calculateDelayToNineAM(): Long {
        val now = Calendar.getInstance()
        val nineAM = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        if (now.after(nineAM)) nineAM.add(Calendar.DAY_OF_MONTH, 1)
        return nineAM.timeInMillis - now.timeInMillis
    }

    private fun isPdfStream(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(4)
                val read = stream.read(header)
                read == 4 && header[0] == '%'.code.toByte() && header[1] == 'P'.code.toByte() &&
                        header[2] == 'D'.code.toByte() && header[3] == 'F'.code.toByte()
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun handleIntent(intent: Intent?, context: Context, viewModel: FridgeViewModel) {
        if (intent == null || intent.action == null) return
        if (intent.getBooleanExtra("INTENT_PROCESSED", false)) return

        val action = intent.action
        val type = intent.type

        try {
            if (Intent.ACTION_SEND == action || Intent.ACTION_SEND_MULTIPLE == action || Intent.ACTION_VIEW == action) {
                val uri = intent.data ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                } ?: intent.clipData?.getItemAt(0)?.uri
                
                if (uri != null) {
                    intent.putExtra("INTENT_PROCESSED", true)
                    
                    try {
                        context.grantUriPermission(context.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: Exception) {}

                    val mimeType = context.contentResolver.getType(uri) ?: type ?: ""
                    val isPdf = mimeType == "application/pdf" || 
                                uri.toString().lowercase().endsWith(".pdf") || 
                                isPdfStream(context, uri)

                    val cachedFile = copyUriToCache(context, uri, if (isPdf) "shared_receipt.pdf" else "shared_receipt.jpg")
                    val cachedUri = if (cachedFile != null && cachedFile.length() > 0) Uri.fromFile(cachedFile) else uri

                    when {
                        isPdf -> viewModel.importFromPdf(context, cachedUri)
                        else -> viewModel.importFromImage(context, cachedUri)
                    }
                } else if (Intent.ACTION_SEND == action && type?.startsWith("text/") == true) {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let { 
                        intent.putExtra("INTENT_PROCESSED", true)
                        viewModel.searchAndAddItems(it) 
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Fehler beim Verarbeiten", e)
            Toast.makeText(context, "Inhalt konnte nicht verarbeitet werden", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyUriToCache(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val cacheFile = File(context.cacheDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (cacheFile.exists() && cacheFile.length() > 0) cacheFile else null
        } catch (e: Exception) {
            Log.e("MainActivity", "Fehler beim Kopieren der URI in den Cache: $uri", e)
            null
        }
    }
}
