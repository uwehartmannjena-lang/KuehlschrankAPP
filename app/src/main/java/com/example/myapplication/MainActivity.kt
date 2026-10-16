package com.example.myapplication

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
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
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.EnrichmentWorker
import com.example.myapplication.data.ExpiryWorker
import com.example.myapplication.data.SyncManager
import com.example.myapplication.ui.RefrigeratorApp
import com.example.myapplication.ui.openRetailerWebsite
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private val currentIntentState = androidx.compose.runtime.mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        currentIntentState.value = intent
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
        createNotificationChannel(this)
        scheduleExpiryCheck(this)
        scheduleWebserviceSync(this)
        scheduleEnrichmentWorker(this)
        SyncManager.init(applicationContext)
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

            LaunchedEffect(currentIntentState.value) {
                currentIntentState.value?.let { currentIntent ->
                    handleIntent(currentIntent, context, fridgeViewModel)
                }
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
        currentIntentState.value = intent
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
            val notificationManager: NotificationManager = context.getSystemService(
                NOTIFICATION_SERVICE
            ) as NotificationManager
            
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
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("ExpiryCheck", ExistingPeriodicWorkPolicy.UPDATE, workRequest)
    }

    private fun scheduleWebserviceSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workRequest = PeriodicWorkRequestBuilder<com.example.myapplication.data.WebserviceSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("WebserviceSync", ExistingPeriodicWorkPolicy.UPDATE, workRequest)
    }

    private fun scheduleEnrichmentWorker(context: Context) {
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
                val uris = mutableListOf<Uri>()

                if (Intent.ACTION_SEND_MULTIPLE == action) {
                    val streamUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    }
                    if (streamUris != null) {
                        uris.addAll(streamUris.filterNotNull())
                    }
                }

                if (uris.isEmpty()) {
                    val singleUri = intent.data ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    } ?: intent.clipData?.getItemAt(0)?.uri

                    if (singleUri != null) {
                        uris.add(singleUri)
                    }
                }

                if (uris.isEmpty() && intent.clipData != null) {
                    val clipData = intent.clipData!!
                    for (i in 0 until clipData.itemCount) {
                        clipData.getItemAt(i).uri?.let { uris.add(it) }
                    }
                }

                if (uris.isNotEmpty()) {
                    intent.putExtra("INTENT_PROCESSED", true)
                    val cachedUris = mutableListOf<Uri>()

                    for ((index, uri) in uris.withIndex()) {
                        try {
                            context.grantUriPermission(context.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (_: Exception) {}

                        val mimeType = try { context.contentResolver.getType(uri) ?: type ?: "" } catch (_: Exception) { type ?: "" }
                        val isPdf = mimeType == "application/pdf" ||
                                    uri.toString().lowercase().endsWith(".pdf") ||
                                    isPdfStream(context, uri)

                        val fileName = if (isPdf) "shared_receipt_$index.pdf" else "shared_receipt_$index.jpg"
                        val cachedFile = copyUriToCacheSync(context, uri, fileName)

                        if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
                            Log.d("ReceiptDebug", "Kassenbon erfolgreich gecacht: ${cachedFile.absolutePath} (${cachedFile.length()} Bytes)")
                            cachedUris.add(Uri.fromFile(cachedFile))
                        } else {
                            Log.e("ReceiptDebug", "Datei konnte nicht synchron in den Cache kopiert werden: $uri")
                        }
                    }

                    if (cachedUris.size == 1) {
                        val cachedUri = cachedUris.first()
                        val isPdf = isPdfStream(context, cachedUri) || cachedUri.toString().lowercase().endsWith(".pdf")
                        when {
                            isPdf -> viewModel.importFromPdf(context, cachedUri)
                            else -> viewModel.importFromImage(context, cachedUri)
                        }
                    } else if (cachedUris.size > 1) {
                        viewModel.importMultipleFiles(context, cachedUris)
                    }
                } else if (Intent.ACTION_SEND == action && type?.startsWith("text/") == true) {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                        intent.putExtra("INTENT_PROCESSED", true)
                        viewModel.searchAndAddItems(it)
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e("ReceiptDebug", "Fehler beim Verarbeiten des Intents", e)
            Toast.makeText(context, "Fehler beim Empfangen des Kassenbons.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyUriToCacheSync(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val cacheFile = File(context.cacheDir, fileName)
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
            val openStream: () -> java.io.InputStream? = {
                if (uri.scheme == "file" && uri.path != null) {
                    val directFile = File(uri.path!!)
                    if (directFile.exists() && directFile.canRead()) {
                        java.io.FileInputStream(directFile)
                    } else {
                        context.contentResolver.openInputStream(uri)
                    }
                } else {
                    context.contentResolver.openInputStream(uri)
                }
            }
            openStream()?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (cacheFile.exists() && cacheFile.length() > 0) cacheFile else null
        } catch (e: Throwable) {
            Log.e("ReceiptDebug", "Fehler beim synchronen Kopieren der URI in den Cache: $uri", e)
            null
        }
    }
}
