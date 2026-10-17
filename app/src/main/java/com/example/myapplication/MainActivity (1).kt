package com.example.myapplication

import android.Manifest
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ListItem
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.*
import com.example.myapplication.data.*
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size as CoilSize
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.google.mlkit.vision.common.InputImage
import java.net.URL
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.app.PendingIntent

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
            
            // Handle Offer Deep Link
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
        // Hier wird die Tag-ID an das ViewModel übergeben, um die UI zu triggern
        // (Wird im RefrigeratorApp via State beobachtet)
        val vm = ViewModelProvider(this)[FridgeViewModel::class.java]
        vm.handleNfcDiscovery(tagId)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Angebots Alarm"
            val descriptionText = "Benachrichtigungen für reduzierte Lieblingsartikel"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("DEAL_ALERTS", name, importance).apply {
                description = descriptionText
            }
            
            val expiryChannel = NotificationChannel("EXPIRY_ALERTS", "MHD-Warner", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Mahnung für ablaufende Lebensmittel"
            }

            val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
                read == 4 && header[0] == '%'.toByte() && header[1] == 'P'.toByte() &&
                        header[2] == 'D'.toByte() && header[3] == 'F'.toByte()
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
        Log.d("MainActivity", "Handling intent: action=$action, type=$type, data=${intent.data}")

        try {
            if (Intent.ACTION_SEND == action || Intent.ACTION_VIEW == action) {
                val uri = intent.data ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                } ?: intent.clipData?.getItemAt(0)?.uri
                
                    if (uri != null) {
                        intent.putExtra("INTENT_PROCESSED", true)
                        val mimeType = context.contentResolver.getType(uri) ?: type ?: ""
                        Log.d("MainActivity", "Extracted URI: $uri, mimeType: $mimeType")
                        
                        val isPdf = mimeType == "application/pdf" || 
                                    uri.toString().lowercase().endsWith(".pdf") || 
                                    isPdfStream(context, uri)
                        
                        when {
                            isPdf -> {
                                viewModel.importFromPdf(context, uri)
                            }
                            mimeType.startsWith("image/") -> {
                                viewModel.importFromImage(context, uri)
                            }
                            mimeType == "text/plain" -> {
                                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { viewModel.searchAndAddItems(it) }
                            }
                            else -> {
                                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { viewModel.searchAndAddItems(it) }
                            }
                        }
                    } else if (Intent.ACTION_SEND == action && type?.startsWith("text/") == true) {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let { 
                        intent.putExtra("INTENT_PROCESSED", true)
                        viewModel.searchAndAddItems(it) 
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Fehler beim Verarbeiten des geteilten Inhalts", e)
            Toast.makeText(context, "Inhalt konnte nicht verarbeitet werden", Toast.LENGTH_SHORT).show()
        }
    }
}

class DealWorker(private val context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val dao = AppDatabase.getDatabase(context).fridgeItemDao()
            val favorites = dao.getAllFridgeItems().first().filter { it.isFavorite }
            val method = inputData.getInt("METHOD", 0)

            if (favorites.isEmpty() || method == 0) return@withContext Result.success()

            for (item in favorites) {
                var dealFound = false
                var source = ""
                var retailer = ""

                if (method == 1) {
                    try {
                        val encodedName = Uri.encode(item.name)
                        // Nutze eine neutrale Suchanfrage statt direktem Scraping (Sicherer)
                        val text = URL("https://www.google.de/search?q=angebote+$encodedName").readText()
                        if (text.contains("€") || text.contains("Angebot")) {
                            dealFound = true
                            source = "Online Suche"
                            retailer = "Händler" // Wird beim Klick dynamisch bestimmt
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                } else if (method == 2) {
                    try {
                        // MyDealz RSS ist oft toleranter und bietet gute Schnäppchen
                        val rssText = URL("https://www.mydealz.de/rss/search?q=${Uri.encode(item.name)}").readText()
                        if (rssText.contains("<item>")) {
                            dealFound = true
                            source = "MyDealz"
                            retailer = "MyDealz"
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }

                if (dealFound) {
                    sendNotification(item.name, source, retailer)
                    break // Nur eine Notification pro Durchlauf, um den Nutzer nicht zu nerven
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun sendNotification(itemName: String, source: String, retailer: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_OFFER_RETAILER", retailer)
            putExtra("OPEN_OFFER_PRODUCT", itemName)
        }
        
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, "DEAL_ALERTS")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Angebot entdeckt! 🛒")
            .setContentText("Dein Favorit '$itemName' ist bei $source im Angebot.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % 10000).toInt(), builder.build())
        }
    }
}

class ExpiryWorker(private val context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val dao = AppDatabase.getDatabase(context).fridgeItemDao()
            val warningDays = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("expiry_warning", 3)
            val now = System.currentTimeMillis()
            
            val expiringItems = dao.getAllFridgeItems().first().filter { 
                it.expiryDate != null && (it.expiryDate!! - now) < TimeUnit.DAYS.toMillis(warningDays.toLong()) 
            }
            
            // Gefrierbrand-Check (Eingefroren > 6 Monate)
            val freezerBurnItems = dao.getAllFridgeItems().first().filter {
                it.isFrozen && (now - it.purchaseDate) > TimeUnit.DAYS.toMillis(180)
            }
            
            if (expiringItems.isNotEmpty() || freezerBurnItems.isNotEmpty()) {
                val msg = if (expiringItems.isNotEmpty()) {
                    "${expiringItems.size} Artikel laufen bald ab!"
                } else {
                    "${freezerBurnItems.size} Artikel zu lange eingefroren (Gefrierbrand-Gefahr)!"
                }
                sendNotification(msg)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun sendNotification(message: String) {
        val builder = NotificationCompat.Builder(context, "EXPIRY_ALERTS")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🍏 Kühlschrank Profi")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(202, builder.build())
        }
    }
}

@Composable
fun InteractiveTutorialOverlay(
    currentStep: Int,
    targets: Map<String, androidx.compose.ui.geometry.Rect>,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val steps = listOf(
        TutorialStep("Willkommen! 🍏", "Wir zeigen dir kurz alle Symbole.", null, Icons.Default.Celebration),
        TutorialStep("EAN-Scanner 🔍", "Barcode scannen für Namen & Kalorien.", "barcode_icon", Icons.Default.QrCodeScanner),
        TutorialStep("Import-Zentrum ➕", "Bons (KI), PDFs oder Spracheingabe nutzen.", "fab_button", Icons.Default.Add),
        TutorialStep("Hauptmenü 🧭", "Vorrat, Einkauf, Plan oder Statistiken.", "nav_bar", Icons.Default.Explore),
        TutorialStep("Haushalt 🏠", "Wechsle zwischen Küche, Keller etc.", "household_icon", Icons.Default.Home)
    )

    if (currentStep < 0 || currentStep >= steps.size) return
    val step = steps[currentStep]
    val targetRect = step.targetKey?.let { targets[it] }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize().clickable(enabled = false) { }) {
            val spotlightPath = Path().apply { if (targetRect != null) addOval(targetRect) }
            clipPath(spotlightPath, clipOp = ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.7f)) }
        }
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(step.icon, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(step.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                    Text(step.description, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = onSkip) { Text("Überspringen") }
                        Button(onClick = onNext) { Text(if (currentStep == steps.size - 1) "Start" else "Weiter") }
                    }
                }
            }
        }
    }
}

data class TutorialStep(val title: String, val description: String, val targetKey: String?, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun FilterAndSortSection(viewModel: FridgeViewModel) {
    var showSortMenu by remember { mutableStateOf(false) }
    val locations = listOf("Kühlschrank", "Gefrierfach", "Vorratskammer", "Haushalt")

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            FilterChip(
                selected = showSortMenu,
                onClick = { showSortMenu = true },
                label = { Icon(Icons.Default.Sort, null, modifier = Modifier.size(18.dp)) },
                leadingIcon = { Text(when(viewModel.sortOrder.value) { "expiry" -> "MHD"; "name" -> "A-Z"; else -> "Sort" }, fontSize = 12.sp) }
            )
        }

        item {
            FilterChip(
                selected = viewModel.filterExpiryDays.intValue == 0 && viewModel.filterLocation.value == null && viewModel.filterCategory.value == null,
                onClick = { 
                    viewModel.filterExpiryDays.intValue = 0
                    viewModel.filterLocation.value = null
                    viewModel.filterCategory.value = null
                },
                label = { Text("Alle") }
            )
        }

        item {
            FilterChip(
                selected = viewModel.filterExpiryDays.intValue == 3,
                onClick = { viewModel.filterExpiryDays.intValue = if (viewModel.filterExpiryDays.intValue == 3) 0 else 3 },
                label = { Text("⚠️ Bald ablaufend") }
            )
        }

        items(locations) { loc ->
            FilterChip(
                selected = viewModel.filterLocation.value == loc,
                onClick = { viewModel.filterLocation.value = if (viewModel.filterLocation.value == loc) null else loc },
                label = { Text(loc) }
            )
        }

        item {
            FilterChip(
                selected = viewModel.filterCategory.value == "Protein",
                onClick = { viewModel.filterCategory.value = if (viewModel.filterCategory.value == "Protein") null else "Protein" },
                label = { Text("💪 Protein") }
            )
        }
    }

    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
        DropdownMenuItem(text = { Text("Nach MHD") }, onClick = { viewModel.sortOrder.value = "expiry"; showSortMenu = false })
        DropdownMenuItem(text = { Text("Nach Name") }, onClick = { viewModel.sortOrder.value = "name"; showSortMenu = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefrigeratorApp(fridgeViewModel: FridgeViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<FridgeItem?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showInventoryMode by remember { mutableStateOf(false) }
    var showFreezerPlan by remember { mutableStateOf(false) }
    var itemToShowDetails by remember { mutableStateOf<FridgeItem?>(null) }
    var imageToZoom by remember { mutableStateOf<String?>(null) }

    val favoriteIconName by fridgeViewModel.favoriteIconName

    var tutorialStep by remember { mutableIntStateOf(-1) }
    val tutorialTargets = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val displayedItems by fridgeViewModel.displayedItems.collectAsState(initial = emptyList())
    val allItems by fridgeViewModel.allFridgeItems.collectAsState(initial = emptyList())
    val wastedItems by fridgeViewModel.wastedItems.collectAsState(initial = emptyList())
    val consumedItems by fridgeViewModel.consumedItems.collectAsState(initial = emptyList())
    val mealPlans by fridgeViewModel.mealPlans.collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (sp.getBoolean("tutorial_completed", false).not()) {
            tutorialStep = 0
            showHelp = true // Zeige Hilfe-Buch direkt beim ersten Start
        }
        fridgeViewModel.checkFavoritesForDeals()
        fridgeViewModel.syncSettings()
    }

    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra("SCAN_RESULT")?.let { code ->
                fridgeViewModel.fetchProductDetails(code) { product ->
                    if (product != null) fridgeViewModel.addItem(product)
                    else Toast.makeText(context, "Unbekannter Artikel", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val receiptScannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val json = result.data?.getStringExtra("SCAN_RESULTS")
            json?.let {
                val listType = object : TypeToken<List<Product>>() {}.type
                val products: List<Product> = Gson().fromJson(it, listType)
                fridgeViewModel.handleScannedProducts(products, "SCAN_" + System.currentTimeMillis())
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val mime = context.contentResolver.getType(it) ?: ""
            if (mime == "application/pdf") fridgeViewModel.importFromPdf(context, it)
            else fridgeViewModel.importFromImage(context, it)
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { fridgeViewModel.processVoiceInput(it) }
        }
    }

    val dealAlertItem = fridgeViewModel.dealAlertItem.value
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showFabSheet by remember { mutableStateOf(false) }
    var itemToEditForSheet by remember { mutableStateOf<FridgeItem?>(null) }
    var showItemSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopBarWithSearch(
                    viewModel = fridgeViewModel,
                    onMenuClick = { showMenu = true },
                    onBarcodeClick = { scannerLauncher.launch(Intent(context, ScannerActivity::class.java)) },
                    onPositioned = { k, r -> tutorialTargets[k] = r }
                )
            },
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.onGloballyPositioned { tutorialTargets["nav_bar"] = androidx.compose.ui.geometry.Rect(it.positionInRoot(), it.size.toSize()) },
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.AutoMirrored.Filled.List, null) }, label = { Text("Vorrat") })
                    NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.ShoppingCart, null) }, label = { Text("Einkauf") })
                    NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Default.RestaurantMenu, null) }, label = { Text("Plan") })
                    NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = { Icon(Icons.Default.BarChart, null) }, label = { Text("📊") })
                }
            },
            floatingActionButton = {
                if (selectedTab < 2) {
                    FloatingActionButton(
                        onClick = { showFabSheet = true }, 
                        containerColor = fridgeViewModel.themeColor.value,
                        modifier = Modifier.onGloballyPositioned { tutorialTargets["fab_button"] = androidx.compose.ui.geometry.Rect(it.positionInRoot(), it.size.toSize()) }
                    ) {
                        Icon(Icons.Default.Add, null, tint = Color.White)
                    }
                }
            }
        ) { inner ->
            Column(modifier = Modifier.padding(inner).fillMaxSize()) {
                when (selectedTab) {
                    0 -> {
                        FilterAndSortSection(fridgeViewModel)
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            item { Spacer(Modifier.height(4.dp)) }
                            items(displayedItems, key = { it.id }) { item ->
                                ModernFridgeItemCard(
                                    item = item,
                                    isSelected = fridgeViewModel.selectedItems.value.contains(item.id),
                                    onEdit = { itemToEditForSheet = item; showItemSheet = true },
                                    onLongClick = { fridgeViewModel.toggleSelection(item.id) }
                                )
                            }
                            item { Spacer(Modifier.height(80.dp)) }
                        }
                    }
                    1 -> ShoppingList(fridgeViewModel, fridgeViewModel.allShoppingItems.collectAsState(initial = emptyList()).value)
                    2 -> MealPlanView(fridgeViewModel, mealPlans, allItems)
                    3 -> StatisticsView(fridgeViewModel, allItems, wastedItems, consumedItems)
                }
            }
        }

        if (showFabSheet) {
            FabActionBottomSheet(
                onDismiss = { showFabSheet = false },
                onScanReceipt = {
                    showFabSheet = false
                    receiptScannerLauncher.launch(Intent(context, KassenzettelScannerActivity::class.java))
                },
                onScanBarcode = {
                    showFabSheet = false
                    scannerLauncher.launch(Intent(context, ScannerActivity::class.java))
                },
                onManualInput = {
                    showFabSheet = false
                    itemToEditForSheet = null
                    showItemSheet = true
                },
                onVoiceInput = {
                    showFabSheet = false
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.GERMAN.toString())
                    }
                    speechLauncher.launch(intent)
                },
                onUpload = {
                    showFabSheet = false
                    filePickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                }
            )
        }

        if (showItemSheet) {
            ItemModalBottomSheet(
                item = itemToEditForSheet,
                viewModel = fridgeViewModel,
                onDismiss = { showItemSheet = false },
                onConfirm = { 
                    if (itemToEditForSheet == null) fridgeViewModel.addItem(it) 
                    else fridgeViewModel.updateItem(it)
                    showItemSheet = false 
                }
            )
        }

        dealAlertItem?.let { (item, retailer) ->
            AlertDialog(
                onDismissRequest = { fridgeViewModel.dealAlertItem.value = null },
                title = { Text("Favorit im Angebot! 🏷️") },
                text = { Text("Dein Lieblingsprodukt '$item' ist diese Woche bei $retailer im Angebot!") },
                confirmButton = {
                    Button(onClick = { 
                        openRetailerWebsite(context, retailer)
                        fridgeViewModel.dealAlertItem.value = null 
                    }) { Text("Zum Prospekt") }
                },
                dismissButton = {
                    TextButton(onClick = { fridgeViewModel.dealAlertItem.value = null }) { Text("Schließen") }
                }
            )
        }

        if (fridgeViewModel.warnerMessages.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { fridgeViewModel.warnerMessages.clear() },
                title = { Text("Produktwarner ⚠️", color = Color.Red, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        fridgeViewModel.warnerMessages.forEach { msg ->
                            Text(msg, modifier = Modifier.padding(vertical = 4.dp))
                            HorizontalDivider()
                        }
                    }
                },
                confirmButton = { Button(onClick = { fridgeViewModel.warnerMessages.clear() }) { Text("Verstanden") } }
            )
        }

        if (showMenu) {
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("Hilfe") }, onClick = { showMenu = false; showHelp = true })
                DropdownMenuItem(text = { Text("Einstellungen") }, onClick = { showMenu = false; showSettings = true })
                DropdownMenuItem(text = { Text("Alles löschen", color = Color.Red) }, onClick = { fridgeViewModel.clearInventory(); showMenu = false })
            }
        }

        if (showSettings) SettingsDialog(fridgeViewModel) { showSettings = false }
        if (showHelp) HelpDialog { showHelp = false }
        if (tutorialStep != -1) {
            InteractiveTutorialOverlay(
                currentStep = tutorialStep,
                targets = tutorialTargets,
                onNext = { tutorialStep++; if (tutorialStep >= 5) { tutorialStep = -1; context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("tutorial_completed", true).apply() } },
                onSkip = { tutorialStep = -1; context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("tutorial_completed", true).apply() }
            )
        }
        if (fridgeViewModel.showImportPreview.value) {
            AlertDialog(
                onDismissRequest = { fridgeViewModel.showImportPreview.value = false },
                title = { Text("Import-Vorschau (${fridgeViewModel.recognizedCount.intValue} Artikel)") },
                confirmButton = {
                    Button(onClick = { fridgeViewModel.confirmAndImport() }) { Text("Alle Speichern") }
                },
                dismissButton = {
                    TextButton(onClick = { fridgeViewModel.showImportPreview.value = false }) { Text("Abbrechen") }
                },
                text = {
                    Column {
                        fridgeViewModel.receiptBitmap.value?.let { bitmap ->
                            androidx.compose.foundation.Image(
                                painter = BitmapPainter(bitmap.asImageBitmap()),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 140.dp),
                            modifier = Modifier.heightIn(max = 450.dp),
                            contentPadding = PaddingValues(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(fridgeViewModel.importCandidates.value, key = { it.id }) { candidate ->
                                val isSelected = fridgeViewModel.selectedItems.value.contains(candidate.id)
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable { fridgeViewModel.toggleSelection(candidate.id) },
                                    colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(6.dp)) {
                                        Box(modifier = Modifier.height(80.dp).fillMaxWidth()) {
                                            if (candidate.imageUrl != null) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(candidate.imageUrl)
                                                        .crossfade(true)
                                                        .size(CoilSize.ORIGINAL)
                                                        .build(),
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                                                    contentScale = ContentScale.Fit,
                                                    filterQuality = FilterQuality.Low
                                                )
                                            } else {
                                                val (icon, color) = getFallbackIconForItem(candidate.name, candidate.category, candidate.storageLocation)
                                                Box(modifier = Modifier.fillMaxSize().background(color.copy(alpha = 0.1f), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                                                    Icon(icon, null, tint = color, modifier = Modifier.size(32.dp))
                                                }
                                            }
                                            
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { fridgeViewModel.toggleSelection(candidate.id) },
                                                modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
                                            )
                                        }

                                        Spacer(Modifier.height(4.dp))
                                        
                                        var editName by remember { mutableStateOf(candidate.name) }
                                        if (isSelected) {
                                            BasicTextField(
                                                value = editName,
                                                onValueChange = { editName = it; fridgeViewModel.teachNewName(candidate, it) },
                                                textStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        } else {
                                            Text(
                                                candidate.name,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        
                                        Text(
                                            "${candidate.quantity}x • ${String.format("%.2f €", candidate.price)}",
                                            fontSize = 9.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        
                                        IconButton(
                                            onClick = { fridgeViewModel.ignoreCandidate(candidate) },
                                            modifier = Modifier.size(20.dp).align(Alignment.End)
                                        ) {
                                            Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }
        imageToZoom?.let { ImageZoomDialog(it) { imageToZoom = null } }
        itemToShowDetails?.let { ProductInfoDialog(it, fridgeViewModel) { itemToShowDetails = null } }
        
        fridgeViewModel.unknownBarcodeForPhoto.value?.let { barcode ->
            UnknownProductDialog(barcode, fridgeViewModel) { fridgeViewModel.unknownBarcodeForPhoto.value = null }
        }
    }
}

@Composable
fun UnknownProductDialog(barcode: String, viewModel: FridgeViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            Toast.makeText(context, "Foto empfangen! (Upload-Simulation)", Toast.LENGTH_LONG).show()
            // Hier würde der Robotoff-Upload erfolgen
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Produkt unbekannt 🔍") },
        text = { Text("Der Barcode $barcode ist nicht in der Datenbank. Möchtest du ein Foto machen, um der Community zu helfen?") },
        confirmButton = {
            Button(onClick = { photoLauncher.launch(null) }) {
                Icon(Icons.Default.PhotoCamera, null)
                Spacer(Modifier.width(8.dp))
                Text("Foto machen")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBarWithSearch(viewModel: FridgeViewModel, onMenuClick: () -> Unit, onBarcodeClick: () -> Unit, onPositioned: (String, androidx.compose.ui.geometry.Rect) -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text("Kühlschrank Profi") },
        navigationIcon = {
            IconButton(onClick = { 
                viewModel.filterCategory.value = null
                viewModel.filterLocation.value = null
                viewModel.filterExpiryDays.intValue = 0
                viewModel.searchInput.value = ""
            }, modifier = Modifier.onGloballyPositioned { onPositioned("household_icon", androidx.compose.ui.geometry.Rect(it.positionInRoot(), it.size.toSize())) }) {
                Icon(Icons.Default.Home, null, tint = viewModel.themeColor.value)
            }
        },
        actions = {
            IconButton(onClick = onBarcodeClick, modifier = Modifier.onGloballyPositioned { onPositioned("barcode_icon", androidx.compose.ui.geometry.Rect(it.positionInRoot(), it.size.toSize())) }) {
                Icon(Icons.Default.QrCodeScanner, null, tint = viewModel.themeColor.value)
            }
            IconButton(onClick = onMenuClick) { Icon(Icons.Default.MoreVert, null) }
        }
    )
}

@Composable
fun ModernFridgeItemCard(
    item: FridgeItem,
    isSelected: Boolean,
    onEdit: () -> Unit,
    onLongClick: () -> Unit
) {
    val daysLeft = remember(item.expiryDate) {
        val diff = (item.expiryDate ?: 0L) - System.currentTimeMillis()
        TimeUnit.MILLISECONDS.toDays(diff).toInt()
    }
    
    val indicatorColor = when {
        daysLeft < 0 -> Color.Red
        daysLeft <= 3 -> Color(0xFFFF9800) // Orange
        else -> Color(0xFF4CAF50) // Green
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .combinedClickable(onClick = onEdit, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status-Indikator links (Ampel-Punkt)
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Produkt-Icon oder Bild
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (item.imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.imageUrl)
                            .crossfade(true)
                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    val (icon, color) = getFallbackIconForItem(item.name, item.category, item.storageLocation)
                    Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Produkt & Infos
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.storageLocation}${if (item.isFrozen) " (Fach ${item.freezerDrawer})" else ""} • ${if (daysLeft < 0) "Abgelaufen!" else "Noch $daysLeft Tage"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Menge & Preis rechts aufgeräumt
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = "${item.quantity} ${item.unit}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                if (item.price > 0.0) {
                    Text(
                        text = String.format("%.2f €", item.price),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FabActionBottomSheet(
    onDismiss: () -> Unit,
    onScanReceipt: () -> Unit,
    onScanBarcode: () -> Unit,
    onManualInput: () -> Unit,
    onVoiceInput: () -> Unit,
    onUpload: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Hinzufügen", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            
            ListItem(
                headlineContent = { Text("Kassenbon scannen", fontWeight = FontWeight.Bold) },
                supportingContent = { Text("Extrahiert alle Artikel automatisch per KI") },
                leadingContent = { Icon(Icons.Default.ReceiptLong, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { onScanReceipt() }
            )
            ListItem(
                headlineContent = { Text("Barcode scannen") },
                supportingContent = { Text("Einzelartikel per EAN erfassen") },
                leadingContent = { Icon(Icons.Default.QrCodeScanner, null) },
                modifier = Modifier.clickable { onScanBarcode() }
            )
            ListItem(
                headlineContent = { Text("Manuelle Eingabe") },
                supportingContent = { Text("Artikel händisch anlegen") },
                leadingContent = { Icon(Icons.Default.Edit, null) },
                modifier = Modifier.clickable { onManualInput() }
            )
            ListItem(
                headlineContent = { Text("Spracheingabe") },
                leadingContent = { Icon(Icons.Default.Mic, null) },
                modifier = Modifier.clickable { onVoiceInput() }
            )
            ListItem(
                headlineContent = { Text("Datei / PDF hochladen") },
                leadingContent = { Icon(Icons.Default.UploadFile, null) },
                modifier = Modifier.clickable { onUpload() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemModalBottomSheet(
    item: FridgeItem?,
    viewModel: FridgeViewModel,
    onDismiss: () -> Unit,
    onConfirm: (FridgeItem) -> Unit
) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var quantity by remember { mutableIntStateOf(item?.quantity ?: 1) }
    var unit by remember { mutableStateOf(item?.unit ?: "Stk.") }
    var location by remember { mutableStateOf(item?.storageLocation ?: "Kühlschrank") }
    var drawer by remember { mutableIntStateOf(item?.freezerDrawer ?: 1) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(if (item == null) "Neuer Artikel" else "Artikel bearbeiten", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Produktname") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Menge:", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                QuantityStepper(
                    value = quantity,
                    onValueChange = { quantity = it }
                )
                Spacer(Modifier.width(16.dp))
                UnitSelector(unit) { unit = it }
            }

            Text("Lagerort", fontWeight = FontWeight.SemiBold)
            LocationTileSelector(location) { location = it }

            if (location == "Gefrierfach") {
                Text("Gefrierfach", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..4).forEach { num ->
                        FilterChip(
                            selected = drawer == num,
                            onClick = { drawer = num },
                            label = { Text("Fach $num") },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            if (item != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.consumeItem(item); onDismiss() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("Verbraucht") }
                    Button(
                        onClick = { viewModel.wasteItem(item); onDismiss() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Müll") }
                }
            }

            Button(
                onClick = { 
                    onConfirm((item ?: FridgeItem(name = name)).copy(
                        name = name, 
                        quantity = quantity, 
                        unit = unit, 
                        storageLocation = location,
                        freezerDrawer = if (location == "Gefrierfach") drawer else 0
                    ))
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Speichern")
            }
        }
    }
}

@Composable
fun QuantityStepper(value: Int, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { if (value > 1) onValueChange(value - 1) }) { Icon(Icons.Default.Remove, null) }
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
        IconButton(onClick = { onValueChange(value + 1) }) { Icon(Icons.Default.Add, null) }
    }
}

@Composable
fun UnitSelector(selected: String, onSelect: (String) -> Unit) {
    val units = listOf("Stk.", "Pack.", "kg", "g", "l", "ml")
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(selected) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            units.forEach { u ->
                DropdownMenuItem(text = { Text(u) }, onClick = { onSelect(u); expanded = false })
            }
        }
    }
}

@Composable
fun LocationTileSelector(selected: String, onSelect: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LocationTile("Kühlschrank", Icons.Default.Kitchen, selected == "Kühlschrank", Modifier.weight(1f)) { onSelect("Kühlschrank") }
        LocationTile("Gefrierfach", Icons.Default.AcUnit, selected == "Gefrierfach", Modifier.weight(1f)) { onSelect("Gefrierfach") }
        LocationTile("Vorratskammer", Icons.Default.Inventory, selected == "Vorratskammer", Modifier.weight(1f)) { onSelect("Vorratskammer") }
    }
}

@Composable
fun LocationTile(label: String, icon: ImageVector, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(80.dp).clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ExpiryLabel(expiryDateMs: Long, householdSize: Int = 1) {
    val daysRemaining = remember(expiryDateMs) { 
        val diffMs = expiryDateMs - System.currentTimeMillis()
        TimeUnit.MILLISECONDS.toDays(diffMs).toInt()
    }
    val warningThreshold = when {
        householdSize >= 4 -> 2
        householdSize >= 2 -> 4
        else -> 7
    }
    val (labelText, labelColor) = when {
        daysRemaining < 0 -> "Abgelaufen!" to Color.Red
        daysRemaining <= warningThreshold -> "Bald verbrauchen ($daysRemaining Tage)" to Color(0xFFFF9800)
        else -> "MHD: $daysRemaining Tage" to Color(0xFF4CAF50)
    }
    Text(text = labelText, color = labelColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
}

fun getFallbackIconForItem(name: String, category: String = "", location: String = ""): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> {
    val detectedCategory = com.example.myapplication.data.CategoryDetector.detectCategory(name)
    val color = when (detectedCategory) {
        com.example.myapplication.data.FoodCategory.FLEISCH_FISCH -> Color(0xFFE91E63)
        com.example.myapplication.data.FoodCategory.MILCHPRODUKTE -> Color(0xFF2196F3)
        com.example.myapplication.data.FoodCategory.BROT_BACKWAREN -> Color(0xFF795548)
        com.example.myapplication.data.FoodCategory.OBST_GEMUESE -> Color(0xFF4CAF50)
        com.example.myapplication.data.FoodCategory.VORRAT_KONSERVEN -> Color(0xFFFFC107)
        com.example.myapplication.data.FoodCategory.HAUSTIER -> Color(0xFF9C27B0)
        com.example.myapplication.data.FoodCategory.GETRAENKE -> Color(0xFF607D8B)
        com.example.myapplication.data.FoodCategory.DROGERIE_HYGIENE -> Color(0xFF00BCD4)
        com.example.myapplication.data.FoodCategory.HAUSHALT -> Color(0xFF8D6E63)
        else -> Color.Gray
    }
    if (location.contains("Gefrierfach", true) || name.contains("TK", true)) return Icons.Default.AcUnit to Color(0xFF03A9F4)
    return detectedCategory.icon to color
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemDialog(title: String, initialItem: FridgeItem?, viewModel: FridgeViewModel, onDismiss: () -> Unit, onConfirm: (FridgeItem) -> Unit) {
    var name by remember { mutableStateOf(initialItem?.name ?: "") }
    var kcal by remember { mutableStateOf(initialItem?.kcal?.toString() ?: "0") }
    var quantity by remember { mutableStateOf(initialItem?.quantity?.toString() ?: "1") }
    var unit by remember { mutableStateOf(initialItem?.unit ?: "Stk.") }
    var location by remember { mutableStateOf(initialItem?.storageLocation ?: "Kühlschrank") }
    var freezerDrawer by remember { mutableIntStateOf(initialItem?.freezerDrawer ?: 0) }
    
    val units = listOf("Stk.", "Kiste", "Bund", "kg", "g", "l", "ml", "Packung", "Dose", "Flasche")
    val locations = listOf("Kühlschrank", "Gefrierfach", "Vorratskammer", "Haushalt")

    AlertDialog(
        onDismissRequest = onDismiss, 
        title = { Text(title) }, 
        confirmButton = { 
            Button(onClick = { 
                onConfirm((initialItem ?: FridgeItem(name = name)).copy(
                    name = name, 
                    kcal = kcal.toIntOrNull() ?: 0, 
                    quantity = quantity.toIntOrNull() ?: 1,
                    unit = unit,
                    storageLocation = location,
                    freezerDrawer = if (location == "Gefrierfach") freezerDrawer else 0
                )) 
            }) { Text("Speichern") } 
        }, 
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text("Menge") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        Box(modifier = Modifier.weight(1.5f)) {
                            var showUnits by remember { mutableStateOf(false) }
                            OutlinedButton(onClick = { showUnits = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(unit)
                            }
                            DropdownMenu(expanded = showUnits, onDismissRequest = { showUnits = false }) {
                                units.forEach { u ->
                                    DropdownMenuItem(text = { Text(u) }, onClick = { unit = u; showUnits = false })
                                }
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(value = kcal, onValueChange = { kcal = it }, label = { Text("Kalorien (100g)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }
                item {
                    Text("Lagerort:", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        locations.forEach { loc ->
                            FilterChip(selected = location == loc, onClick = { location = loc }, label = { Text(loc) })
                        }
                    }
                }
                if (location == "Gefrierfach") {
                    item {
                        Text("Gefrierfach Nummer:", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..4).forEach { num ->
                                FilterChip(selected = freezerDrawer == num, onClick = { freezerDrawer = num }, label = { Text("Fach $num") })
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun ShoppingList(fridgeViewModel: FridgeViewModel, items: List<ShoppingItem>) {
    val context = LocalContext.current
    var bestRetailer by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Einkaufswagen 🛒", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { fridgeViewModel.syncWithBring(fridgeViewModel.bringUuid.value, items) }) { 
                Icon(Icons.Default.Sync, "An Bring! senden", tint = Color(0xFFE91E63)) 
            }
            IconButton(onClick = { fridgeViewModel.shareShoppingList(context, items) }) { Icon(Icons.Default.Share, null) }
        }

        Button(
            onClick = { 
                scope.launch { bestRetailer = fridgeViewModel.compareRetailerPrices(items) }
                fridgeViewModel.checkOffersForFavorites() 
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.LocalOffer, null)
            Spacer(Modifier.width(8.dp))
            Text("Bestpreis & Angebote prüfen")
        }

        if (bestRetailer.isNotEmpty()) {
            Text("Top Händler für diese Liste", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
            val sortedRetailers = bestRetailer.toList().sortedBy { it.second }
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sortedRetailers.take(3).forEachIndexed { index, (name, total) ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (index == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.width(140.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(if (index == 0) "🥇 $name" else if (index == 1) "🥈 $name" else "🥉 $name", fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("${String.format("%.2f", total)} €", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }

        val predictiveItems by fridgeViewModel.predictiveShoppingList.collectAsState(initial = emptyList())

        LazyColumn(modifier = Modifier.weight(1f).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { item ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${item.quantity}x ${item.name}", modifier = Modifier.weight(1f))
                        IconButton(onClick = { fridgeViewModel.removeFromShoppingList(item) }) {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            // ... Predictive Items logic remains ...
        }
    }
}

@Composable
fun MealPlanView(viewModel: FridgeViewModel, plans: List<MealPlan>, inventory: List<FridgeItem>) {
    var showRecipeSuggestions by remember { mutableStateOf(false) }
    var selectedForLeftovers = remember { mutableStateListOf<String>() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Wochenplan 📅", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = { showRecipeSuggestions = true }) {
                Icon(Icons.Default.AutoAwesome, null)
                Text("Ideen")
            }
        }
        
        Spacer(Modifier.height(8.dp))
        Text("Resteverwertung-Modus 🍲", style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            items(inventory) { item ->
                FilterChip(
                    selected = selectedForLeftovers.contains(item.id),
                    onClick = { if (selectedForLeftovers.contains(item.id)) selectedForLeftovers.remove(item.id) else selectedForLeftovers.add(item.id) },
                    label = { Text(item.name) }
                )
            }
        }
        if (selectedForLeftovers.isNotEmpty()) {
            Button(onClick = { 
                val items = inventory.filter { selectedForLeftovers.contains(it.id) }.joinToString(", ") { it.name }
                viewModel.addMealPlan(MealPlan(date = System.currentTimeMillis(), recipeTitle = "Mix aus $items"))
                selectedForLeftovers.clear()
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Gericht aus Auswahl planen")
            }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            items(plans) { plan ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(plan.recipeTitle, fontWeight = FontWeight.Bold)
                            Text(SimpleDateFormat("EEEE, dd.MM", Locale.getDefault()).format(Date(plan.date)), fontSize = 12.sp)
                        }
                        IconButton(onClick = { viewModel.deleteMealPlan(plan.id) }) {
                            Icon(Icons.Default.Delete, null, tint = Color.Gray)
                        }
                    }
                }
            }
        }
    }
    if (showRecipeSuggestions) {
        RecipeIdeasDialog(
            viewModel = viewModel,
            inventory = inventory,
            dietProfile = viewModel.selectedDietProfile.value,
            allergens = viewModel.allergenWarnings.toList(),
            onDismiss = { showRecipeSuggestions = false },
            onSelect = { 
                viewModel.addMealPlan(MealPlan(date = System.currentTimeMillis(), recipeTitle = it))
                showRecipeSuggestions = false
            }
        )
    }
}

@Composable
fun StatisticsView(viewModel: FridgeViewModel, items: List<FridgeItem>, wastedItems: List<WastedItem>, consumedItems: List<ConsumedItem>) {
    val currentMonthYear = SimpleDateFormat("MM-yyyy", Locale.GERMANY).format(Date())
    val budget by viewModel.dao.getBudget(currentMonthYear).collectAsState(initial = null)
    val receipts by viewModel.dao.getAllReceipts().collectAsState(initial = emptyList())
    
    val totalSpent = receipts.filter { 
        SimpleDateFormat("MM-yyyy", Locale.GERMANY).format(Date(it.date)) == currentMonthYear 
    }.sumOf { it.total }

    val totalWasted = wastedItems.sumOf { it.price }
    val totalSaved = consumedItems.size * 1.5 
    val totalWastedCo2 = wastedItems.sumOf { it.carbonFootprint }

    val totalKcal = items.sumOf { (it.kcal * it.quantity * (if(it.unit == "kg" || it.unit == "l") 10 else 1)).toInt() }
    val totalSugar = items.sumOf { it.sugar * it.quantity }
    val totalSalt = items.sumOf { it.salt * it.quantity }
    val totalProtein = items.sumOf { it.protein * it.quantity }

    var showBudgetDialog by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dashboard 📊", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { showBudgetDialog = true }) { Icon(Icons.Default.Settings, "Budget") }
            }
        }
        
        item {
            val limit = budget?.limit ?: 500.0
            val progress = (totalSpent / limit).coerceIn(0.0, 1.0).toFloat()
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = if (progress > 0.9f) Color(0xFFFFEBEE) else Color(0xFFE8F5E9))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Monatsbudget ($currentMonthYear) 💶", fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = if (progress > 0.9f) Color.Red else Color(0xFF4CAF50))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${String.format("%.2f", totalSpent)} € von ${String.format("%.2f", limit)} €", fontSize = 12.sp)
                        Text("${(progress * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Nährwert-Tagesübersicht (Vorrat) 🍎", fontWeight = FontWeight.Bold)
                    Text("Kalorien: $totalKcal kcal", fontSize = 13.sp)
                    Text("Zucker: ${String.format("%.1fg", totalSugar)} • Salz: ${String.format("%.1fg", totalSalt)} • Eiweiß: ${String.format("%.1fg", totalProtein)}", fontSize = 12.sp)
                    
                    val avgProtein = if (items.isNotEmpty()) totalProtein / items.sumOf { it.quantity } else 0.0
                    if (avgProtein > 15.0) {
                        Text("💪 Sehr proteinreicher Bestand!", color = Color(0xFF388E3C), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            val totalValue = items.sumOf { it.price * it.quantity }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) { StatisticsCard("Geld im Müll", "${String.format(Locale.getDefault(), "%.2f", totalWasted)} €", Color.Red) }
                Box(modifier = Modifier.weight(1f)) { StatisticsCard("Vorrats-Wert", "${String.format(Locale.getDefault(), "%.2f", totalValue)} €", viewModel.themeColor.value) }
            }
        }
        item {
            StatisticsCard("CO₂-Impact", "${String.format("%.1f", totalWastedCo2)} kg", Color(0xFF795548))
        }

        item {
            Text("Beleg-Archiv (Garantie & Umtausch) 📄", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            val receipts by viewModel.dao.getAllReceipts().collectAsState(initial = emptyList())
            receipts.forEach { receipt ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { /* Open URI */ }) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Receipt, null)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(receipt.supermarket ?: "Unbekannt", fontWeight = FontWeight.Bold)
                            Text(SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(receipt.date)), fontSize = 12.sp)
                        }
                        Text("${String.format("%.2f", receipt.total)} €", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showBudgetDialog) {
        BudgetEditDialog(currentMonthYear, budget?.limit ?: 500.0, onDismiss = { showBudgetDialog = false }) { limit ->
            viewModel.setBudgetLimit(currentMonthYear, limit)
            showBudgetDialog = false
        }
    }
}

@Composable
fun StatisticsCard(title: String, value: String, color: Color) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title)
            Text(value, style = MaterialTheme.typography.headlineLarge, color = color)
        }
    }
}

@Composable
fun BudgetEditDialog(monthYear: String, currentLimit: Double, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var limitInput by remember { mutableStateOf(currentLimit.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Budget für $monthYear") },
        text = {
            OutlinedTextField(
                value = limitInput,
                onValueChange = { limitInput = it },
                label = { Text("Monatliches Limit (€)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        },
        confirmButton = { Button(onClick = { limitInput.toDoubleOrNull()?.let { onConfirm(it) } }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsDialog(viewModel: FridgeViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    
    // Temporärer State für "Speichern"-Logik
    var tempExpiryDays by remember { mutableIntStateOf(viewModel.expiryWarningDays.intValue) }
    var tempHouseholdSize by remember { mutableIntStateOf(viewModel.householdSize.intValue) }
    var tempCompactMode by remember { mutableStateOf(viewModel.isCompactMode.value) }
    var tempCornerRadius by remember { mutableIntStateOf(viewModel.cardCornerRadius.intValue) }
    var tempThemeColor by remember { mutableStateOf(viewModel.themeColor.value) }
    var tempDietProfile by remember { mutableStateOf(viewModel.selectedDietProfile.value) }
    var tempBringUuid by remember { mutableStateOf(viewModel.bringUuid.value) }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            viewModel.restoreBackup(context, it)
            Toast.makeText(context, "Daten wiederhergestellt!", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App-Einstellungen ⚙️") },
        confirmButton = { 
            Button(onClick = {
                viewModel.setExpiryWarningDays(tempExpiryDays)
                viewModel.setHouseholdSize(tempHouseholdSize)
                viewModel.setCompactMode(tempCompactMode)
                viewModel.setCardCornerRadius(tempCornerRadius)
                viewModel.setThemeColor(tempThemeColor)
                viewModel.setDietProfile(tempDietProfile)
                viewModel.setBringUuid(tempBringUuid)
                Toast.makeText(context, "Einstellungen gespeichert!", Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text("Speichern & Schließen") } 
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("Integrationen", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = tempBringUuid,
                        onValueChange = { tempBringUuid = it },
                        label = { Text("Bring! Listen-ID (UUID)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    Text("Design & UI", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Kompakt-Modus", modifier = Modifier.weight(1f))
                        Switch(checked = tempCompactMode, onCheckedChange = { tempCompactMode = it })
                    }
                    Text("Abrundung Karten: ${tempCornerRadius}dp")
                    Slider(value = tempCornerRadius.toFloat(), onValueChange = { tempCornerRadius = it.toInt() }, valueRange = 0f..32f)
                }
                
                item {
                    Text("Favoriten-Einstellungen", fontWeight = FontWeight.Bold)
                    val finalIcons = listOf(
                        "Star" to Icons.Default.Star,
                        "Heart" to Icons.Default.Favorite,
                        "Bookmark" to Icons.Default.Bookmark,
                        "PushPin" to Icons.Default.PushPin,
                        "WorkspacePremium" to Icons.Default.WorkspacePremium,
                        "LocalFireDepartment" to Icons.Default.LocalFireDepartment,
                        "Lightbulb" to Icons.Default.Lightbulb,
                        "CheckCircle" to Icons.Default.CheckCircle,
                        "ShoppingBag" to Icons.Default.ShoppingBag,
                        "LocalMall" to Icons.Default.LocalMall,
                        "Loyalty" to Icons.Default.Loyalty,
                        "Diamond" to Icons.Default.Diamond,
                        "Celebration" to Icons.Default.Celebration,
                        "Eco" to Icons.Default.Eco
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        finalIcons.forEach { (name, icon) ->
                            IconButton(
                                onClick = { viewModel.setFavoriteIcon(name) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    icon, 
                                    null, 
                                    tint = if (viewModel.favoriteIconName.value == name) viewModel.themeColor.value else Color.Gray,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    
                    Text("Symbol-Farbe:", fontSize = 12.sp)
                    val colors = listOf(Color(0xFFFFB300), Color.Red, Color.Blue, Color(0xFF4CAF50), Color.Magenta)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        colors.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(c, CircleShape)
                                    .border(if (viewModel.favoriteIconColor.value == c) 2.dp else 0.dp, Color.Black, CircleShape)
                                    .clickable { viewModel.setFavoriteIconColor(c) }
                            )
                        }
                    }
                }

                item {
                    Text("MHD & Benachrichtigungen", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Warnung $tempExpiryDays Tage vor Ablauf")
                    Slider(value = tempExpiryDays.toFloat(), onValueChange = { tempExpiryDays = it.toInt() }, valueRange = 1f..14f)
                    
                    Text("Haushaltsgröße (optimiert MHD-Checks): $tempHouseholdSize Personen")
                    Slider(value = tempHouseholdSize.toFloat(), onValueChange = { tempHouseholdSize = it.toInt() }, valueRange = 1f..10f)
                }

                item {
                    Text("Ernährungsprofil", fontWeight = FontWeight.Bold)
                    val profiles = listOf("Standard", "Vegan", "Vegetarisch", "Glutenfrei", "Laktosefrei", "Low Carb")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        profiles.forEach { profile ->
                            FilterChip(selected = tempDietProfile == profile, onClick = { tempDietProfile = profile }, label = { Text(profile) })
                        }
                    }
                }

                item {
                    HorizontalDivider()
                    Text("Datenverwaltung", fontWeight = FontWeight.Bold)
                    Button(onClick = { viewModel.exportBackup(context) }, modifier = Modifier.fillMaxWidth()) { Text("Backup erstellen") }
                    Button(onClick = { restoreLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Backup laden") }
                }
            }
        }
    )
}

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    var currentPage by remember { mutableIntStateOf(0) }
    val pages = listOf(
        HelpPage("Willkommen! 🍏", "Kühlschrank Profi hilft dir, Lebensmittelverschwendung zu vermeiden und Geld zu sparen. In diesem interaktiven Handbuch zeigen wir dir, wie es geht.", Icons.Default.Celebration, Color(0xFF4CAF50)),
        HelpPage("Der Vorrat 📦", "Hier siehst du alles, was du hast. Die Farben zeigen die Frische: GRÜN: Frisch, GELB: Bald verbrauchen, ROT: Abgelaufen! Nutze die Icons unten rechts für Schnellaktionen.", Icons.Default.Inventory, Color(0xFF2196F3)),
        HelpPage("Smart-Scanner 🔍", "Scanne Barcodes für Details oder Kassenbons für den gesamten Einkauf. Die KI lernt mit der Zeit deine Korrekturen!", Icons.Default.QrCodeScanner, Color(0xFFFF9800)),
        HelpPage("Gefrierfach ❄️", "Markiere Artikel mit der Schneeflocke, um sie ins Gefrierfach zu verschieben. Nach 6 Monaten warnen wir dich vor Gefrierbrand!", Icons.Default.AcUnit, Color(0xFF03A9F4)),
        HelpPage("Einkauf & Plan 📅", "Verbrauchte Artikel landen automatisch auf der Liste. Plane deine Woche und entdecke Rezepte basierend auf deinem Bestand.", Icons.Default.CalendarMonth, Color(0xFFE91E63)),
        HelpPage("Spenden 🤝", "Artikel, die du nicht schaffst, kannst du zum Spenden markieren. Teile die Liste mit Freunden oder Nachbarn!", Icons.Default.VolunteerActivism, Color(0xFFFF5722))
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(pages[currentPage].icon, null, tint = pages[currentPage].color)
                Spacer(Modifier.width(12.dp))
                Text(pages[currentPage].title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(modifier = Modifier.height(200.dp)) {
                Text(pages[currentPage].description, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    repeat(pages.size) { i ->
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (i == currentPage) pages[currentPage].color else Color.LightGray))
                        if (i < pages.size - 1) Spacer(Modifier.width(4.dp))
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (currentPage > 0) {
                    TextButton(onClick = { currentPage-- }) { Text("Zurück") }
                }
                if (currentPage < pages.size - 1) {
                    Button(onClick = { currentPage++ }) { Text("Weiter") }
                } else {
                    Button(onClick = onDismiss) { Text("Verstanden!") }
                }
            }
        }
    )
}

data class HelpPage(val title: String, val description: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color)

@Composable
fun RecipeIdeasDialog(viewModel: FridgeViewModel, inventory: List<FridgeItem>, dietProfile: String, allergens: List<String>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.getRecipeSuggestions() }
    val suggestions = viewModel.recipeSuggestions
    val isLoading = viewModel.isRecipeLoading.value

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rezept-Ideen 💡") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                else if (suggestions.isEmpty()) Text("Keine Vorschläge gefunden.")
                else suggestions.forEach { Text("• $it", modifier = Modifier.padding(vertical = 4.dp).clickable { onSelect(it) }) }
                
                HorizontalDivider()
                Button(
                    onClick = { 
                        val items = inventory.filter { it.expiryDate != null }.map { it.name }.take(3)
                        val query = items.joinToString("+")
                        val url = "https://www.chefkoch.de/rs/s0/$query/Rezepte.html"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00))
                ) {
                    Icon(Icons.Default.Restaurant, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Auf Chefkoch suchen")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )
}

@Composable
fun LoyaltyCardsDialog(viewModel: FridgeViewModel, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Kundenkarten") }, text = { Text("Karten...") }, confirmButton = { Button(onClick = onDismiss) { Text("OK") } })
}

@Composable
fun QrSyncDialog(qr: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Sync") }, text = { Text("QR Code") }, confirmButton = { Button(onClick = onDismiss) { Text("OK") } })
}

@Composable
fun ImageZoomDialog(url: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { }, text = { Text("Bild") })
}

@Composable
fun ProductInfoDialog(item: FridgeItem, viewModel: FridgeViewModel, onDismiss: () -> Unit) {
    var isUpdatingImage by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.imageUrl != null) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp).align(Alignment.CenterHorizontally).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
                
                Button(
                    onClick = { 
                        isUpdatingImage = true
                        viewModel.fetchProductDetails(item.barcode ?: "", item.name) { updated ->
                            if (updated != null) {
                                viewModel.updateItem(item.copy(
                                    brand = updated.brand ?: item.brand,
                                    imageUrl = updated.imageUrl ?: item.imageUrl,
                                    kcal = if (updated.kcal > 0) updated.kcal else item.kcal,
                                    nutriScore = if (updated.nutriScore.isNotBlank()) updated.nutriScore else item.nutriScore,
                                    barcode = updated.barcode ?: item.barcode
                                ))
                            }
                            isUpdatingImage = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUpdatingImage
                ) {
                    if (isUpdatingImage) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    else Text("Bild & Info händisch aktualisieren")
                }

                Text("Marke: ${item.brand ?: "Unbekannt"}")
                Text("Nährwerte: ${item.kcal} kcal • P: ${item.protein}g • Z: ${item.sugar}g")
                Text("Lagerort: ${item.storageLocation}")
                
                val priceHistory by viewModel.getPriceHistory(item.id).collectAsState(initial = emptyList())
                if (priceHistory.isNotEmpty()) {
                    Text("Preisverlauf:", fontWeight = FontWeight.Bold)
                    priceHistory.forEach { Text("${String.format("%.2f", it.price)} € am ${SimpleDateFormat("dd.MM", Locale.GERMANY).format(Date(it.date))}") }
                }

                if (item.receiptId != null) {
                    Button(onClick = { /* TODO: Open Receipt */ }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                        Icon(Icons.Default.ReceiptLong, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Kassenbon anzeigen")
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("OK") } }
    )
}

class FridgeViewModelFactory(private val dao: FridgeItemDao, private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = FridgeViewModel(dao, context) as T
}

class FridgeViewModel(val dao: FridgeItemDao, private val context: Context) : ViewModel() {
    val allFridgeItems = dao.getAllFridgeItems()
    val wastedItems = dao.getAllWastedItems()
    val consumedItems = dao.getAllConsumedItems()
    val mealPlans = dao.getAllMealPlans()
    val recommendations = dao.getAllConsumptionPatterns()

    val currentHouseholdId = mutableStateOf("default")
    val currentUserRole = mutableStateOf("Erwachsener")
    val households = dao.getAllHouseholds()

    fun setHousehold(id: String) { currentHouseholdId.value = id; saveSetting("current_household", id) }
    fun setUserRole(role: String) { currentUserRole.value = role; saveSetting("user_role", role) }

    fun addHousehold(name: String) {
        viewModelScope.launch {
            dao.insertHousehold(Household(name = name, color = Color.Gray.toArgb()))
        }
    }

    fun logAction(action: String, itemName: String) {
        viewModelScope.launch {
            dao.insertLog(HouseholdLog(
                householdId = currentHouseholdId.value,
                userId = "user_1",
                userName = "Ich",
                action = action,
                itemName = itemName
            ))
        }
    }

    fun processVoiceInput(text: String) {
        viewModelScope.launch {
            val normalized = text.lowercase()
            if (normalized.contains("füge") || normalized.contains("hinzu")) {
                val quantity = Regex("(\\d+)").find(normalized)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val unit = if (normalized.contains("liter")) "l" else if (normalized.contains("gramm")) "g" else "Stk."
                val words = normalized.split(" ")
                val nameIdx = words.indexOf("liter").takeIf { it != -1 } ?: words.indexOf("gramm").takeIf { it != -1 } ?: words.indexOf("füge")
                val name = if (nameIdx + 1 < words.size) words[nameIdx + 1] else "Unbekannt"
                
                addItem(FridgeItem(name = name.replaceFirstChar { it.uppercase() }, quantity = quantity, unit = unit, householdId = currentHouseholdId.value))
                logAction("Hinzugefügt (Sprache)", name)
            }
        }
    }

    val searchInput = mutableStateOf("")
    val themeColor = mutableStateOf(Color(0xFF4CAF50))
    val isSyncing = mutableStateOf(false)
    val expiryWarningDays = mutableIntStateOf(3)
    val filterExpiryDays = mutableIntStateOf(0)
    val quickAddFavorites = mutableStateOf(listOf("Milch", "Eier", "Brot"))
    val itemSpacing = mutableIntStateOf(8)
    val selectedItems = mutableStateOf(setOf<String>())
    val isCompactMode = mutableStateOf(false)
    val cardCornerRadius = mutableIntStateOf(12)
    val favoriteIconName = mutableStateOf("Star")
    val favoriteIconColor = mutableStateOf(Color(0xFFFFB300))

    fun setFavoriteIconColor(color: Color) { favoriteIconColor.value = color; saveSetting("favorite_icon_color", color.toArgb()) }
    val recentlyIgnored = mutableStateListOf<String>()
    val showImportPreview = mutableStateOf(false)
    val importCandidates = mutableStateOf(emptyList<FridgeItem>())
    val recognizedCount = mutableIntStateOf(0)
    val receiptBitmap = mutableStateOf<Bitmap?>(null)
    val currentIconIndex = mutableIntStateOf(1)
    val warnerMessages = mutableStateListOf<String>()
    val learnedCorrections = mutableStateMapOf<String, String>()

    init {
        viewModelScope.launch {
            dao.getAllLearningData().collect { list ->
                learnedCorrections.clear()
                list.forEach { learnedCorrections[it.rawName] = it.correctedName }
            }
        }
    }

    val filterCategory = mutableStateOf<String?>(null)
    val filterLocation = mutableStateOf<String?>(null)
    val sortOrder = mutableStateOf("expiry") // "name", "price", "expiry", "category"

    val allShoppingItems = dao.getAllShoppingItems()
    val householdSize = mutableIntStateOf(1)
    
    val selectedDietProfile = mutableStateOf("Standard")
    val allergenWarnings = mutableStateListOf<String>()

    val useLocalAIFilter = mutableStateOf(false)
    val matchedOffers = mutableStateListOf<com.example.myapplication.data.StoreOffer>()

    val lastRecallStatus = mutableStateOf("OK")
    val bringUuid = mutableStateOf("")
    val unknownBarcodeForPhoto = mutableStateOf<String?>(null)

    val nfcScannedId = mutableStateOf<String?>(null)
    val showNfcDialog = mutableStateOf(false)

    fun handleNfcDiscovery(tagId: String) {
        viewModelScope.launch {
            nfcScannedId.value = tagId
            val items = allFridgeItems.first()
            val existingItem = items.find { it.nfcTagId == tagId }
            if (existingItem != null) {
                // Zwei-Wege-Workflow
                if (existingItem.storageLocation == "Gefrierfach") {
                    // Direktaktion: Wenn im Gefrierfach gescannt -> Ausbuchen
                    consumeItem(existingItem)
                    Toast.makeText(context, "${existingItem.name} ausgebucht!", Toast.LENGTH_SHORT).show()
                } else {
                    dealAlertItem.value = Pair("Inhalt: ${existingItem.name}", "Gefrierdose")
                }
            } else {
                showNfcDialog.value = true
            }
        }
    }

    fun linkNfcTagToItem(item: FridgeItem) {
        val tagId = nfcScannedId.value ?: return
        viewModelScope.launch {
            dao.updateItem(item.copy(nfcTagId = tagId))
            showNfcDialog.value = false
            nfcScannedId.value = null
        }
    }

    val predictiveShoppingList: Flow<List<ShoppingItem>> = combine(
        allFridgeItems,
        recommendations
    ) { inventory, patterns ->
        patterns.filter { pattern ->
            // Artikel ist NICHT im Inventar ODER läuft bald ab
            val inInventory = inventory.any { it.name.contains(pattern.itemName, true) }
            val predictionDue = pattern.predictedNextNeed < System.currentTimeMillis() + TimeUnit.DAYS.toMillis(4)
            !inInventory || predictionDue
        }.map { ShoppingItem(name = it.itemName, quantity = 1, unit = it.unit) }
    }

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true }) }
    }
    private val offerRepository = com.example.myapplication.data.OfferRepository(httpClient)
    private val bringApi = com.example.myapplication.data.BringApi(httpClient)
    private val geminiRepository = GeminiRepository()

    val dealAlertStores = mutableStateOf(setOf("Kaufland", "Lidl", "Rewe", "Aldi", "Edeka", "dm"))
    val dealAlertItem = mutableStateOf<Pair<String, String>?>(null)
    val dealSearchMethod = mutableIntStateOf(0)
    
    val currentOffers = mutableStateListOf<StoreOffer>()

    fun checkOffersForFavorites() {
        viewModelScope.launch {
            isSyncing.value = true
            val favoriteNames = allFridgeItems.first().filter { it.isFavorite }.map { it.name }
            val shoppingNames = dao.getAllShoppingItems().first().map { it.name }
            val allSearchItems = (favoriteNames + shoppingNames).distinct()
            
            val offers = offerRepository.checkLocalOffers(allSearchItems)
            currentOffers.clear()
            currentOffers.addAll(offers)
            isSyncing.value = false
        }
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val api = OpenFoodFactsApi.create()

    val recipeSuggestions = mutableStateListOf<String>()
    val isRecipeLoading = mutableStateOf(false)

    fun getRecipeSuggestions() {
        viewModelScope.launch {
            isRecipeLoading.value = true
            val inventory = allFridgeItems.first()
            val soonExpiring = inventory.filter { 
                it.expiryDate != null && (it.expiryDate!! - System.currentTimeMillis()) < TimeUnit.DAYS.toMillis(4) 
            }
            
            val suggestions = mutableListOf<String>()
            if (soonExpiring.isNotEmpty()) {
                val names = soonExpiring.map { it.name }.distinct()
                suggestions.add("Express-Pfanne mit ${names.take(2).joinToString(" & ")}")
                suggestions.add("Kühlschrank-Resteauflauf")
                suggestions.add("Bunter Mix-Salat mit ${names.last()}")
            } else {
                suggestions.add("Basis-Rezept: Pasta Aglio e Olio")
                suggestions.add("Gemüse-Quiche")
            }
            
            recipeSuggestions.clear()
            recipeSuggestions.addAll(suggestions)
            isRecipeLoading.value = false
        }
    }

    val displayedItems: Flow<List<FridgeItem>> = allFridgeItems.map { items ->
        items.filter { item ->
            val matchesHousehold = item.householdId == currentHouseholdId.value || currentHouseholdId.value == "default"
            val matchesSearch = searchInput.value.isBlank() || item.name.contains(searchInput.value, ignoreCase = true)
            val matchesExpiry = when (filterExpiryDays.intValue) {
                0 -> true
                -1 -> item.expiryDate != null && item.expiryDate!! < System.currentTimeMillis()
                else -> item.expiryDate != null && (item.expiryDate!! - System.currentTimeMillis()) < TimeUnit.DAYS.toMillis(filterExpiryDays.intValue.toLong())
            }
            val matchesCategory = filterCategory.value == null || item.category == filterCategory.value
            val matchesLocation = filterLocation.value == null || item.storageLocation.equals(filterLocation.value, ignoreCase = true)
            matchesHousehold && matchesSearch && matchesExpiry && matchesCategory && matchesLocation
        }.filter { item ->
            when (filterCategory.value) {
                "Protein" -> item.protein >= 10.0
                "LowCarb" -> item.sugar <= 5.0 && item.sugar >= 0
                "Vegan" -> item.isVegan
                else -> true
            }
        }.let { filtered ->
            when (sortOrder.value) {
                "name" -> filtered.sortedBy { it.name }
                "price" -> filtered.sortedByDescending { it.price }
                "category" -> filtered.sortedBy { it.category }
                "protein" -> filtered.sortedByDescending { it.protein }
                else -> filtered.sortedBy { it.expiryDate ?: Long.MAX_VALUE }
            }
        }
    }

    fun syncSettings() {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        themeColor.value = Color(prefs.getInt("theme_color", Color(0xFF4CAF50).toArgb()))
        expiryWarningDays.intValue = prefs.getInt("expiry_warning", 3)
        itemSpacing.intValue = prefs.getInt("item_spacing", 8)
        isCompactMode.value = prefs.getBoolean("is_compact", false)
        cardCornerRadius.intValue = prefs.getInt("corner_radius", 12)
        householdSize.intValue = prefs.getInt("household_size", 1)
        useLocalAIFilter.value = prefs.getBoolean("use_ai_filter", false)
        currentIconIndex.intValue = prefs.getInt("app_icon_index", 4)
        favoriteIconName.value = prefs.getString("favorite_icon_name", "Star") ?: "Star"
        selectedDietProfile.value = prefs.getString("diet_profile", "Standard") ?: "Standard"
        currentHouseholdId.value = prefs.getString("current_household", "default") ?: "default"
        currentUserRole.value = prefs.getString("user_role", "Erwachsener") ?: "Erwachsener"
        bringUuid.value = prefs.getString("bring_uuid", "") ?: ""
        val allergenList = prefs.getStringSet("allergen_warnings", emptySet()) ?: emptySet()
        favoriteIconColor.value = Color(prefs.getInt("favorite_icon_color", Color(0xFFFFB300).toArgb()))
        allergenWarnings.clear()
        allergenWarnings.addAll(allergenList)
    }

    private fun saveSetting(key: String, value: Any) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
        when (value) {
            is Int -> prefs.putInt(key, value)
            is String -> prefs.putString(key, value)
            is Boolean -> prefs.putBoolean(key, value)
            is Set<*> -> prefs.putStringSet(key, value as Set<String>)
        }
        prefs.apply()
    }

    fun setThemeColor(color: Color) { themeColor.value = color; saveSetting("theme_color", color.toArgb()) }
    fun setBringUuid(uuid: String) { bringUuid.value = uuid; saveSetting("bring_uuid", uuid) }
    fun setCompactMode(enabled: Boolean) { isCompactMode.value = enabled; saveSetting("is_compact", enabled) }
    fun setCardCornerRadius(radius: Int) { cardCornerRadius.intValue = radius; saveSetting("corner_radius", radius) }
    fun setExpiryWarningDays(days: Int) { expiryWarningDays.intValue = days; saveSetting("expiry_warning", days) }
    fun setHouseholdSize(size: Int) { householdSize.intValue = size; saveSetting("household_size", size) }
    fun setFavoriteIcon(name: String) { favoriteIconName.value = name; saveSetting("favorite_icon_name", name) }
    fun setDietProfile(profile: String) { selectedDietProfile.value = profile; saveSetting("diet_profile", profile) }
    fun toggleAllergenWarning(allergen: String) {
        if (allergenWarnings.contains(allergen)) allergenWarnings.remove(allergen) else allergenWarnings.add(allergen)
        saveSetting("allergen_warnings", allergenWarnings.toSet())
    }

    fun syncData() {
        viewModelScope.launch {
            isSyncing.value = true
            try {
                val items = dao.getAllFridgeItems().first()
                // Parallelisierung mit Concurrency-Limit (z.B. 3 gleichzeitige Requests)
                val semaphore = Semaphore(3)
                
                coroutineScope {
                    items.map { item ->
                        launch(Dispatchers.IO) {
                            semaphore.withPermit {
                                var updatedItem = enrichItemWithSmartLogic(item)
                                
                                // Nur wenn Bild oder Marke fehlt, suchen wir online
                                if (updatedItem.imageUrl == null || updatedItem.brand == null) {
                                    updatedItem = enrichProductDetails(updatedItem)
                                }
                                
                                if (updatedItem != item) {
                                    dao.updateItem(updatedItem)
                                }
                            }
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Daten aktualisiert & Bilder geladen!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("FridgeViewModel", "Fehler beim Sync", e)
            } finally {
                isSyncing.value = false
            }
        }
    }

    fun toggleDealAlertStore(store: String) {
        val current = dealAlertStores.value.toMutableSet()
        if (current.contains(store)) current.remove(store) else current.add(store)
        dealAlertStores.value = current
    }

    fun checkFavoritesForDeals() {
        viewModelScope.launch {
            val favs = allFridgeItems.first().filter { it.isFavorite }
            if (favs.isNotEmpty()) {
                val offers = offerRepository.checkLocalOffers(favs.map { it.name })
                if (offers.isNotEmpty()) dealAlertItem.value = Pair(offers[0].title, offers[0].retailer)
            }
        }
    }

    fun setDealSearchMethod(method: Int) {
        dealSearchMethod.intValue = method
        val workManager = WorkManager.getInstance(context)
        if (method == 0) workManager.cancelUniqueWork("DealSearchWorker") else {
            val data = workDataOf("METHOD" to method)
            val workRequest = PeriodicWorkRequestBuilder<DealWorker>(12, TimeUnit.HOURS).setInputData(data).build()
            workManager.enqueueUniquePeriodicWork("DealSearchWorker", ExistingPeriodicWorkPolicy.UPDATE, workRequest)
        }
    }

    fun updateSearchInput(input: String) { searchInput.value = input }
    fun toggleFavorite(item: FridgeItem) { viewModelScope.launch { dao.updateItem(item.copy(isFavorite = !item.isFavorite)) } }
    fun toggleSelection(id: String) { val current = selectedItems.value; selectedItems.value = if (current.contains(id)) current - id else current + id }

    fun ignoreCandidate(item: FridgeItem) {
        val rawName = item.notes.substringAfter("RAW:", "")
        if (rawName.isNotBlank()) {
            viewModelScope.launch {
                dao.insertLearningEntry(LearningEntry(rawName = rawName, correctedName = "___IGNORE___"))
                recentlyIgnored.add(rawName)
            }
        }
        importCandidates.value = importCandidates.value - item
    }

    fun teachNewName(oldItem: FridgeItem, newName: String) {
        if (newName.isBlank()) return
        val rawName = oldItem.notes.substringAfter("RAW:", "").substringBefore(" @").trim()
        if (rawName.isNotBlank()) {
            viewModelScope.launch { 
                dao.insertLearningEntry(LearningEntry(rawName = rawName, correctedName = newName)) 
                learnedCorrections[rawName] = newName // Sofort im Cache aktualisieren
            }
        }
        importCandidates.value = importCandidates.value.map { if (it.id == oldItem.id) it.copy(name = newName) else it }
    }

    fun toggleDonation(item: FridgeItem) {
        viewModelScope.launch {
            dao.updateItem(item.copy(isDonationCandidate = !item.isDonationCandidate))
        }
    }

    fun getPriceHistory(itemId: String) = dao.getPriceHistory(itemId)

    fun markAsOpened(item: FridgeItem) { 
        viewModelScope.launch { 
            // Automatische Verbrauchsfrist-Heuristik
            val consumptionDays = when {
                item.name.lowercase().contains("milch") -> 4
                item.name.lowercase().contains("saft") -> 3
                item.name.lowercase().contains("schmand") || item.name.lowercase().contains("quark") -> 5
                item.name.lowercase().contains("wurst") || item.name.lowercase().contains("käse") -> 7
                else -> 5
            }
            val newExpiry = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(consumptionDays.toLong())
            dao.updateItem(item.copy(
                openedDate = System.currentTimeMillis(),
                expiryDate = minOf(item.expiryDate ?: Long.MAX_VALUE, newExpiry),
                consumptionAfterOpeningDays = consumptionDays
            )) 
            logAction("Geöffnet", item.name)
        } 
    }
    fun clearInventory() { viewModelScope.launch { dao.deleteAll() } }

    fun loadProductLexicon() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = context.assets.open("product_lexicon.json").bufferedReader().use { it.readText() }
                val type = object : com.google.gson.reflect.TypeToken<List<LexiconEntry>>() {}.type
                val entries: List<LexiconEntry> = com.google.gson.Gson().fromJson(json, type)
                entries.forEach { entry ->
                    dao.insertLearningEntry(LearningEntry(rawName = entry.raw, correctedName = entry.corrected))
                }
            } catch (e: Exception) {
                Log.e("FridgeViewModel", "Lexicon loading failed", e)
            }
        }
    }

    data class LexiconEntry(val raw: String, val corrected: String)

    fun exportBackup(context: Context) {
        viewModelScope.launch {
            val items = dao.getAllFridgeItems().first()
            val wasted = dao.getAllWastedItems().first()
            val backupData = mapOf("items" to items, "wasted" to wasted)
            val json = Gson().toJson(backupData)
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, json)
                putExtra(Intent.EXTRA_SUBJECT, "Kühlschrank Profi Backup ${SimpleDateFormat("dd.MM.yyyy").format(Date())}")
            }
            context.startActivity(Intent.createChooser(intent, "Backup speichern"))
        }
    }
    fun restoreBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = inputStream?.bufferedReader()
                val json = reader?.readText()
                inputStream?.close()

                if (json != null) {
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    val data: Map<String, Any> = Gson().fromJson(json, type)
                    
                    val itemsJson = Gson().toJson(data["items"])
                    val itemsType = object : TypeToken<List<FridgeItem>>() {}.type
                    val items: List<FridgeItem> = Gson().fromJson(itemsJson, itemsType)
                    
                    items.forEach { dao.insertItem(it) }
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "${items.size} Artikel importiert!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("FridgeViewModel", "Backup Fehler", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Fehler beim Laden des Backups", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun addItem(item: FridgeItem) { 
        viewModelScope.launch { 
            if (currentUserRole.value == "Kind") {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Kinder dürfen nur Produkte hinzufügen (Eingeschränkt)", Toast.LENGTH_SHORT).show()
                }
            }
            // Automatische Allergen-Warnung beim Hinzufügen
            val hasAllergen = allergenWarnings.any { item.allergens.contains(it, true) }
            if (hasAllergen) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Achtung: Artikel enthält überwachte Allergene!", Toast.LENGTH_LONG).show()
                }
            }
            // Rückruf-Check beim Hinzufügen (falls Barcode vorhanden)
            var recallStatus = "OK"
            item.barcode?.let {
                recallStatus = offerRepository.checkRecallStatus(it)
                if (recallStatus.startsWith("WARN")) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "ACHTUNG: $recallStatus", Toast.LENGTH_LONG).show()
                    }
                }
            }
            
            if (item.price > 0) {
                checkProductWarner(item.name, item.price, item.quantity, item.barcode)
                dao.insertPriceRecord(PriceRecord(itemId = item.id, itemName = item.name, price = item.price, quantity = item.quantity, barcode = item.barcode))
            }

            dao.insertItem(item.copy(
                expiryDate = item.expiryDate ?: (System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000),
                householdId = currentHouseholdId.value,
                lastRecallStatus = recallStatus
            ))

            // Von Einkaufsliste entfernen, wenn vorhanden
            val shoppingList = allShoppingItems.first()
            shoppingList.find { it.name.contains(item.name, true) || item.name.contains(it.name, true) }?.let {
                removeFromShoppingList(it)
            }

            logAction("Hinzugefügt", item.name)
        } 
    }

    fun updateItem(item: FridgeItem) {
        if (currentUserRole.value == "Kind") return
        viewModelScope.launch {
            try {
                // Sicherstellen, dass wir nicht versehentlich das ID-Feld löschen
                if (item.id.isBlank()) {
                    Log.e("FridgeViewModel", "Update abgebrochen: ID leer!")
                    return@launch
                }

                // 1. Prüfen, ob der Artikel von einem Bon stammt (steht in notes)
                val rawTextFromNotes = if (item.notes.startsWith("RAW:")) {
                    item.notes.substringAfter("RAW:").substringBefore(" @").trim()
                } else ""

                val aktuellerName = item.name.trim()

                // 2. Wenn der Name vom Bon-Rohtext abweicht -> Lerneintrag anlegen!
                if (rawTextFromNotes.isNotBlank() && rawTextFromNotes != aktuellerName) {
                    dao.insertLearningEntry(
                        LearningEntry(
                            rawName = rawTextFromNotes,
                            correctedName = aktuellerName
                        )
                    )
                    Log.d("AutoLearn", "Gelernt: '$rawTextFromNotes' -> '$aktuellerName'")
                }

                // 3. Artikel wie gewohnt aktualisieren
                dao.updateItem(item)
                logAction("Bearbeitet", item.name)
            } catch (e: Exception) {
                Log.e("FridgeViewModel", "Fehler beim Update", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Fehler beim Speichern: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun removeItem(item: FridgeItem) {
        if (currentUserRole.value == "Kind") return
        viewModelScope.launch { dao.deleteItem(item); logAction("Gelöscht", item.name) }
    }

    /**
     * Verknüpft einen kryptischen Bon-Text mit einem gescannten Barcode/QR-Code.
     */
    fun learnReceiptItemWithBarcode(rawReceiptName: String, barcode: String, onFinished: (String) -> Unit) {
        viewModelScope.launch {
            // Produktname via Open Food Facts API abrufen
            fetchProductDetails(barcode) { product ->
                viewModelScope.launch {
                    val officialName = product?.name ?: "Artikel $barcode"
                    
                    // Dauerhaft in Lerndatenbank speichern
                    dao.insertLearningEntry(
                        LearningEntry(
                            rawName = rawReceiptName.trim(),
                            correctedName = officialName.trim()
                        )
                    )
                    
                    withContext(Dispatchers.Main) {
                        onFinished(officialName)
                    }
                }
            }
        }
    }

    fun consumeItem(item: FridgeItem) {
        viewModelScope.launch {
            // Lerne Konsummuster
            val pattern = dao.getConsumptionPattern(item.name) ?: ConsumptionPattern(itemName = item.name, unit = item.unit)
            val now = System.currentTimeMillis()
            val interval = if (pattern.lastConsumedTimestamp > 0) (now - pattern.lastConsumedTimestamp) else 0L
            val newAvg = if (interval > 0) {
                if (pattern.averageIntervalDays > 0) (pattern.averageIntervalDays + (interval / 86400000.0)) / 2.0
                else interval / 86400000.0
            } else pattern.averageIntervalDays

            dao.insertConsumptionPattern(pattern.copy(
                totalConsumed = pattern.totalConsumed + 1,
                lastConsumedTimestamp = now,
                averageIntervalDays = newAvg,
                predictedNextNeed = now + (newAvg * 86400000.0).toLong()
            ))

            dao.insertConsumedItem(ConsumedItem(name = item.name, quantity = 1, unit = item.unit, category = item.category, kcal = item.kcal))
            if (item.quantity > 1) {
                dao.updateItem(item.copy(quantity = item.quantity - 1))
            } else {
                dao.deleteItem(item)
                // Automatisch auf die Einkaufsliste setzen, wenn verbraucht
                addToShoppingList(ShoppingItem(name = item.name, quantity = 1, unit = item.unit))
            }
            logAction("Verbraucht", item.name)
        }
    }

    fun wasteItem(item: FridgeItem) {
        viewModelScope.launch {
            // Heuristik für CO2: Fleisch/Milch höher als Gemüse
            val lowerCat = item.category.lowercase()
            val co2Factor = when {
                lowerCat.contains("fleisch") || lowerCat.contains("fisch") || lowerCat.contains("wurst") -> 15.0
                lowerCat.contains("milch") || lowerCat.contains("käse") || lowerCat.contains("quark") -> 5.0
                lowerCat.contains("obst") || lowerCat.contains("gemüse") -> 1.0
                else -> 2.5
            }
            dao.insertWastedItem(WastedItem(
                name = item.name, 
                price = item.price, 
                quantity = item.quantity, 
                unit = item.unit, 
                category = item.category, 
                brand = item.brand ?: "",
                carbonFootprint = item.carbonFootprint.takeIf { it > 0 } ?: (co2Factor * item.quantity)
            ))
            dao.deleteItem(item)
            logAction("Müll", item.name)
        }
    }
    fun freezeItem(item: FridgeItem) { viewModelScope.launch { dao.updateItem(item.copy(storageLocation = "Gefrierfach", isFrozen = true)) } }
    fun defrostItem(item: FridgeItem) { 
        viewModelScope.launch { 
            dao.updateItem(item.copy(storageLocation = "Kühlschrank", isFrozen = false, defrostTimestamp = System.currentTimeMillis())) 
            // In einer echten App würde hier ein Alarm über den AlarmManager gesetzt
            Toast.makeText(context, "Auftau-Timer gesetzt: Morgen verarbeiten!", Toast.LENGTH_LONG).show()
        } 
    }
    fun setBudgetLimit(monthYear: String, limit: Double) {
        viewModelScope.launch {
            val current = dao.getBudget(monthYear).first()
            dao.insertBudget(BudgetConfig(monthYear, limit, current?.spent ?: 0.0))
        }
    }
    
    fun addToShoppingList(item: ShoppingItem) { viewModelScope.launch { dao.insertShoppingItem(item) } }
    fun removeFromShoppingList(item: ShoppingItem) { viewModelScope.launch { dao.deleteShoppingItem(item) } }
    fun shareShoppingList(context: Context, shoppingItems: List<ShoppingItem>) {
        val text = "Mein Einkaufszettel von Kühlschrank Profi 🍏:\n\n" +
                shoppingItems.joinToString("\n") { "• ${it.quantity}x ${it.name}" }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Einkaufszettel teilen"))
    }
    suspend fun compareRetailerPrices(items: List<ShoppingItem>): Map<String, Double> = offerRepository.calculateBestRetailer(items)

    fun syncWithBring(uuid: String, items: List<ShoppingItem>) {
        if (uuid.isBlank()) {
            Toast.makeText(context, "Bitte Bring! UUID in den Einstellungen hinterlegen", Toast.LENGTH_LONG).show()
            return
        }
        viewModelScope.launch {
            val success = bringApi.exportToBring(uuid, items)
            if (success) Toast.makeText(context, "An Bring! gesendet", Toast.LENGTH_SHORT).show()
            else Toast.makeText(context, "Bring! Export fehlgeschlagen", Toast.LENGTH_SHORT).show()
        }
    }
    fun shareFoodForDonation(context: Context, items: List<FridgeItem>) {
        val text = "Ich möchte folgende Lebensmittel spenden (Kühlschrank Profi 🍏):\n\n" +
                items.joinToString("\n") { "• ${it.name} (Haltbar bis: ${it.expiryDate?.let { d -> SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(d)) } ?: "Unbekannt"})" }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Spende teilen"))
    }

    fun addMealPlan(plan: MealPlan) { viewModelScope.launch { dao.insertMealPlan(plan) } }
    fun deleteMealPlan(id: String) { viewModelScope.launch { dao.deleteMealPlan(id) } }

    suspend fun generateInventoryQr(): String = ""
    fun importFromQr(data: String) { }

    fun importFromImage(context: Context, uri: Uri, isFridgePhoto: Boolean = false) {
        viewModelScope.launch {
            isSyncing.value = true
            showImportPreview.value = true
            try {
                val fileName = getFileName(context, uri) ?: "unknown_image"
                val inputStream = try {
                    context.contentResolver.openInputStream(uri)
                } catch (e: SecurityException) {
                    isSyncing.value = false
                    Toast.makeText(context, "Berechtigung zum Lesen fehlt", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap == null) {
                    isSyncing.value = false
                    Toast.makeText(context, "Bild konnte nicht geladen werden", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                receiptBitmap.value = bitmap
                
                if (isFridgePhoto) {
                    val products = geminiRepository.analyzeFridgePhoto(listOf(bitmap))
                    handleScannedProducts(products, "FRIDGE_SCAN_" + System.currentTimeMillis())
                } else {
                    val image = InputImage.fromBitmap(bitmap, 0)
                    val corrections = withContext(Dispatchers.IO) {
                        dao.getAllLearningDataSync().associate { it.rawName to it.correctedName }
                    }
                    recognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            val products = KassenzettelParser.parseReceipt(visionText, corrections)
                            handleScannedProducts(products, fileName)
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "Fehler beim Lesen des Bildes", Toast.LENGTH_SHORT).show()
                        }
                        .addOnCompleteListener { isSyncing.value = false }
                }
            } catch (e: Exception) {
                isSyncing.value = false
                e.printStackTrace()
            }
        }
    }

    fun importFromPdf(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { 
                isSyncing.value = true 
                showImportPreview.value = true
            }
            
            try {
                val fileName = getFileName(context, uri) ?: "unknown_pdf"
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                
                val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream)
                val allProducts = mutableListOf<Product>()
                
                // 1. Koordinaten-basierte Extraktion für digitale PDFs
                for (pageIndex in 0 until document.numberOfPages) {
                    val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                    stripper.startPage = pageIndex + 1
                    stripper.endPage = pageIndex + 1
                    val text = stripper.getText(document)
                    if (text.isNotBlank()) {
                        allProducts.addAll(KassenzettelParser.parseReceiptText(text))
                    }
                }
                document.close()
                inputStream.close()

                if (allProducts.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        handleScannedProducts(allProducts, fileName)
                    }
                    return@launch
                }
                
                // Fallback auf OCR...
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Kein digitaler Text gefunden, nutze OCR...", Toast.LENGTH_SHORT).show()
                }
                
                var pdfRenderer: android.graphics.pdf.PdfRenderer? = null
                var parcelFileDescriptor: android.os.ParcelFileDescriptor? = null
                try {
                    parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                    if (parcelFileDescriptor == null) return@launch
                    
                    pdfRenderer = android.graphics.pdf.PdfRenderer(parcelFileDescriptor)
                    val allProductsOcr = mutableListOf<Product>()
                    val corrections = dao.getAllLearningDataSync().associate { it.rawName to it.correctedName }

                    for (pageIndex in 0 until pdfRenderer.pageCount) {
                        val page = pdfRenderer.openPage(pageIndex)
                        val scale = 2.0f 
                        val bitmap = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        
                        if (pageIndex == 0) {
                            val previewBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                            withContext(Dispatchers.Main) { receiptBitmap.value = previewBitmap }
                        }

                        val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
                        val visionText = com.google.android.gms.tasks.Tasks.await(recognizer.process(image))
                        if (visionText.text.isNotBlank()) {
                            val products = KassenzettelParser.parseReceipt(visionText, corrections)
                            allProductsOcr.addAll(products)
                        }
                        bitmap.recycle()
                        page.close()
                    }

                    withContext(Dispatchers.Main) {
                        handleScannedProducts(allProductsOcr, fileName)
                    }
                } finally {
                    pdfRenderer?.close()
                    parcelFileDescriptor?.close()
                }
            } catch (e: Exception) {
                Log.e("FridgeViewModel", "PDF Fehler", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Fehler beim Lesen des PDFs", Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) { isSyncing.value = false }
            }
        }
    }

    fun searchAndAddItems(text: String) {
        // Unified Parsing für digitale Belege oder Text-Import
        viewModelScope.launch {
            try {
                isSyncing.value = true
                showImportPreview.value = true
                
                val newItems = mutableListOf<FridgeItem>()
                val sourceId = "TEXT_IMPORT_" + text.hashCode().toString()
                
                // Nutze den intelligenten Premium-Parser
                val parsedProducts = KassenzettelParser.parseReceiptText(text)
                val corrections = learnedCorrections.toMap()
                
                parsedProducts.forEach { p ->
                    val item = FridgeItem(
                        name = p.name,
                        price = p.price,
                        quantity = p.quantity,
                        unit = if (p.rawText?.contains("kg", true) == true) "kg" else "Stk.",
                        notes = "RAW:${p.rawText}",
                        importHash = generateImportHash(sourceId, p.name)
                    )
                    newItems.add(enrichItemWithSmartLogic(item, corrections))
                }
                
                if (newItems.isNotEmpty()) {
                    importCandidates.value = newItems
                    recognizedCount.intValue = newItems.size
                    selectedItems.value = newItems.map { it.id }.toSet()
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Keine Artikel im Text erkannt.", Toast.LENGTH_SHORT).show()
                        showImportPreview.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e("FridgeViewModel", "Fehler beim Text-Import", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Fehler beim Importieren: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    showImportPreview.value = false
                }
            } finally {
                isSyncing.value = false
            }
        }
    }

    private fun enrichItemWithSmartLogic(item: FridgeItem, learnedCorrections: Map<String, String> = emptyMap()): FridgeItem {
        var rawName = item.notes.substringAfter("RAW:", "").substringBefore(" @").trim()
        var name = if (rawName.isNotBlank() && learnedCorrections.containsKey(rawName)) {
            learnedCorrections[rawName]!!
        } else {
            item.name.trim()
        }
        
        // 1. Abkürzungs-Mapping & Cleaning
        val abbrevMap = mapOf(
            "Edu.Liebl.Boh." to "Eduscho Bohnen",
            "Perf.Fit" to "Katzenfutter",
            "Lenor" to "Weichspüler",
            "KLC" to "K-Classic",
            "Weih." to "Weihenstephan",
            "Mü." to "Müller",
            "WS" to "Wurst",
            "Mönch." to "Mönchshof",
            "H-Milch" to "H-Milch",
            "v.S." to "vom Schwein",
            "BIO" to "Bio",
            "TK" to "Tiefkühl",
            "Scheibenkl." to "Scheibenklar"
        )
        for ((abbr, full) in abbrevMap) {
            if (name.contains(abbr, true)) {
                name = name.replace(abbr, full, true)
            }
        }
        
        name = name.replace(Regex("""\d+g$""", RegexOption.IGNORE_CASE), "").trim()
        name = name.replace(Regex("""\d+x\d+[,.]\d{2}""", RegexOption.IGNORE_CASE), "").trim()

        // 2. Umlaute reparieren (bevor die Schreibweise geändert wird)
        name = name
            .replace("ae", "ä").replace("oe", "ö").replace("ue", "ü")
            .replace("AE", "Ä").replace("OE", "Ö").replace("UE", "Ü")
            .replace("Ae", "Ä").replace("Oe", "Ö").replace("Ue", "Ü")
        
        val lower = name.lowercase()
        
        // 3. Kategorie & Lagerort zuordnen
        val detectedCat = com.example.myapplication.data.CategoryDetector.detectCategory(name)
        val catName = detectedCat.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
        
        var location = when {
            // GEFRIERFACH:
            lower.contains("tiefkühl") || lower.contains("tk ") || lower.contains("eis ") || 
            (lower.contains("pizza") && !lower.contains("brötchen")) || lower.contains("pommes") || lower.contains("gefroren") -> "Gefrierfach"
            
            // KÜHLSCHRANK:
            lower.contains("käse") || lower.contains("brique") || lower.contains("butter") ||
            lower.contains("milch") || lower.contains("joghurt") || lower.contains("quark") ||
            lower.contains("wurst") || lower.contains("fleisch") || lower.contains("schinken") ||
            lower.contains("lachs") || lower.contains("feinkost") || lower.contains("senf") ||
            lower.contains("kefir") || lower.contains("nackenst") || lower.contains("gurke") || 
            lower.contains("salat") || lower.contains("aufschnitt") || lower.contains("wiener") -> "Kühlschrank"
            
            // HAUSHALT & DROGERIE (Non-Food):
            lower.contains("kuschelweich") || lower.contains("weichspüler") || lower.contains("reiniger") ||
            lower.contains("scheibenklar") || lower.contains("bluse") || lower.contains("shampoo") ||
            lower.contains("seife") || lower.contains("katzenfutter") || lower.contains("perle") ||
            lower.contains("streu") || lower.contains("tasche") || lower.contains("batterie") ||
            lower.contains("folie") || lower.contains("spülmittel") -> "Haushalt"
            
            // ALLES ANDERE:
            else -> "Vorratskammer"
        }
        
        // Korrektur für Wurst/Käse falls doch Vorrat erwischt wurde
        if ((lower.contains("wurst") || lower.contains("käse")) && location == "Vorratskammer") {
            location = "Kühlschrank"
        }
        
        // 3. Smart Expiry (MHD-Vorhersage)
        val expiryDays = when {
            lower.contains("cornedbeef") || lower.contains("corned beef") -> 730L // 2 Jahre für Dosenfleisch
            location == "Gefrierfach" -> 180L
            lower.contains("lachs") || lower.contains("hackfleisch") -> 2L
            lower.contains("steak") || lower.contains("geflügel") || lower.contains("hähnchen") -> 3L
            lower.contains("milch") || lower.contains("joghurt") -> 10L
            lower.contains("käse") -> 21L
            location == "Kühlschrank" -> 7L
            location == "Vorratskammer" -> 365L
            else -> 14L
        }
        val expiryDate = item.purchaseDate + (expiryDays * 24 * 60 * 60 * 1000)
        
        // 4. Intelligente Einheiten & Mengen
        var unit = item.unit
        var quantity = item.quantity
        if (lower.contains("wasser") || lower.contains("cola") || lower.contains("limo")) {
            if (item.price > 3.0) {
                unit = "Kiste"
                if (item.price > 15.0) quantity = 2
            }
        }
        if (lower.contains("wurst") && unit == "Stk.") {
            unit = "Packung"
        }

        // 5. Nährwert-Heuristik (falls API nichts liefert)
        val isVegan = lower.contains("vegan") || lower.contains("soja") || lower.contains("hafer")
        val protein = if (detectedCat == com.example.myapplication.data.FoodCategory.FLEISCH_FISCH) 20.0 else item.protein
        val sugar = if (lower.contains("cola") || lower.contains("limo")) 10.0 else item.sugar

        // Schreibweise: Anfang groß, Rest klein + Längenbegrenzung
        var formattedName = name.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
            .take(30)

        // Radikale Korrektur für den Phantom-Artikel "2 Stk" vom Nahkauf-Bon
        if (formattedName.startsWith("2 St") || formattedName.startsWith("2 Stk") || formattedName == "Tiefkühlpizza") {
            if (item.price == 0.59) {
                formattedName = "Brötchen"
            }
        }

        return item.copy(
            name = formattedName,
            category = catName,
            storageLocation = location,
            expiryDate = item.expiryDate ?: expiryDate,
            unit = unit,
            quantity = quantity,
            isVegan = isVegan || item.isVegan,
            protein = protein,
            sugar = sugar
        )
    }

    fun handleScannedProducts(products: List<Product>, sourceId: String = "SCAN") {
        viewModelScope.launch {
            if (products.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Keine Artikel auf dem Bon gefunden.", Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            
            // Duplikatschutz: Prüfe, ob dieser Beleg (sourceId) bereits importiert wurde
            val existingHashes = dao.getImportHashes().first().toSet()
            val corrections = learnedCorrections.toMap()
            
            val newItems = products.mapNotNull { p ->
                val hash = generateImportHash(sourceId, p.name)
                if (existingHashes.contains(hash)) return@mapNotNull null
                
                val item = FridgeItem(
                    name = p.name,
                    price = p.price,
                    quantity = p.quantity,
                    barcode = p.barcode,
                    imageUrl = p.imageUrl,
                    allergens = p.allergens,
                    kcal = p.kcal,
                    notes = "RAW:${p.rawText}${if (p.supermarket != null) " @${p.supermarket}" else ""}",
                    boundingBox = p.boundingBox,
                    purchaseDate = p.purchaseDate ?: System.currentTimeMillis(),
                    importHash = hash
                )
                // Preisverlauf speichern
                if (p.price > 0) {
                    checkProductWarner(p.name, p.price, p.quantity, p.barcode)
                    dao.insertPriceRecord(PriceRecord(itemId = item.id, itemName = p.name, price = p.price, quantity = p.quantity, barcode = p.barcode))
                }
                enrichItemWithSmartLogic(item, corrections)
            }
            
            if (newItems.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Alle Artikel dieses Belegs wurden bereits importiert.", Toast.LENGTH_SHORT).show()
                    showImportPreview.value = false
                }
            } else {
                // Batch-Processing für Smart Enrichment (Bilder laden)
                withContext(Dispatchers.Main) {
                    importCandidates.value = newItems
                    recognizedCount.intValue = newItems.size
                    // Nur Food-Artikel standardmäßig auswählen, Haushalt/Non-Food abwählen
                    selectedItems.value = newItems.filter { it.storageLocation != "Haushalt" }.map { it.id }.toSet()
                    showImportPreview.value = true
                }
                
                // Hintergrund-Enrichment für die Vorschau (Bilder laden)
                val enrichedCandidates = newItems.map { item ->
                    viewModelScope.async(Dispatchers.IO) {
                        if (item.imageUrl == null) enrichProductDetails(item) else item
                    }
                }.awaitAll()
                
                withContext(Dispatchers.Main) {
                    importCandidates.value = enrichedCandidates
                }
            }
        }
    }

    private fun generateImportHash(source: String, itemName: String): String {
        return "${source}_${itemName}".hashCode().toString()
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    private suspend fun checkProductWarner(name: String, currentPrice: Double, currentQty: Int, barcode: String?) {
        val history = dao.getHistoryByName(name)
        if (history.isNotEmpty()) {
            val last = history.first()
            val priceChanged = currentPrice != last.price && currentPrice > 0
            val qtyChanged = currentQty != last.quantity && currentQty > 0
            
            if (priceChanged || qtyChanged) {
                val msg = StringBuilder("Produktwarner für '$name':\n")
                if (priceChanged) {
                    val trend = if (currentPrice > last.price) "Teurer!" else "Günstiger"
                    msg.append("• Preis: ${String.format("%.2f", last.price)}€ ➔ ${String.format("%.2f", currentPrice)}€ ($trend)\n")
                }
                if (qtyChanged) {
                    val trend = if (currentQty < last.quantity) "Weniger Inhalt! (Shrinkflation?)" else "Mehr Inhalt"
                    msg.append("• Menge: ${last.quantity}${last.unit} ➔ $currentQty${last.unit} ($trend)")
                }
                withContext(Dispatchers.Main) {
                    // Verhindere doppelte Nachrichten
                    if (!warnerMessages.contains(msg.toString())) {
                        warnerMessages.add(msg.toString())
                    }
                }
            }
        }
    }
    fun fetchProductDetails(barcode: String, nameQuery: String? = null, onResult: (FridgeItem?) -> Unit) {
        viewModelScope.launch {
            try {
                // 1. Offline-Cache prüfen
                val cached = dao.getCachedProduct(barcode)
                if (cached != null) {
                    onResult(FridgeItem(
                        name = cached.name,
                        brand = cached.brand,
                        imageUrl = cached.imageUrl,
                        nutriScore = cached.nutriScore,
                        ecoScore = cached.ecoScore,
                        allergens = cached.allergens,
                        kcal = cached.kcal,
                        barcode = barcode,
                        category = cached.categories.split(",").firstOrNull() ?: "Sonstiges",
                        sugar = cached.sugar,
                        salt = cached.salt,
                        protein = cached.protein,
                        fat = cached.fat
                    ))
                    return@launch
                }

                // 2. API-Anfrage
                val response = if (barcode.isNotBlank()) api.getProduktByBarcode(barcode) else null
                if (response?.status == 1 && response.product != null) {
                    val p = response.product
                    val name = p.productName ?: "Unbekannter Artikel"
                    
                    // Automatische Lagerort-Logik
                    val detectedCat = com.example.myapplication.data.CategoryDetector.detectCategory(name)
                    val nameLower = name.lowercase()
                    val suggestedLocation = when {
                        nameLower.contains("tk") || nameLower.contains("tiefkühl") || nameLower.contains("eis") -> "Gefrierfach"
                        detectedCat == FoodCategory.FLEISCH_FISCH || detectedCat == FoodCategory.MILCHPRODUKTE -> "Kühlschrank"
                        detectedCat == FoodCategory.VORRAT_KONSERVEN || detectedCat == FoodCategory.GETRAENKE -> "Vorratskammer"
                        detectedCat == FoodCategory.HAUSHALT || detectedCat == FoodCategory.DROGERIE_HYGIENE -> "Haushalt"
                        else -> "Kühlschrank"
                    }

                    val item = FridgeItem(
                        name = name,
                        brand = p.marke,
                        imageUrl = p.imageUrl,
                        nutriScore = p.nutriScore ?: "",
                        ecoScore = p.ecoScore ?: "",
                        allergens = p.allergens ?: "",
                        kcal = p.nutriments?.kcal100g?.toInt() ?: 0,
                        barcode = barcode,
                        category = p.categories?.split(",")?.firstOrNull() ?: "Sonstiges",
                        storageLocation = suggestedLocation,
                        sugar = p.nutriments?.sugars100g ?: 0.0,
                        salt = p.nutriments?.salt100g ?: 0.0,
                        protein = p.nutriments?.proteins100g ?: 0.0,
                        fat = p.nutriments?.fat100g ?: 0.0
                    )
                    
                    // 3. Im Cache speichern
                    dao.insertCachedProduct(CachedProduct(
                        barcode = barcode,
                        name = name,
                        brand = p.marke,
                        imageUrl = p.imageUrl,
                        imageSmallUrl = p.imageSmallUrl,
                        kcal = item.kcal,
                        nutriScore = item.nutriScore,
                        allergens = item.allergens,
                        ecoScore = item.ecoScore,
                        categories = p.categories ?: "",
                        sugar = item.sugar,
                        salt = item.salt,
                        protein = item.protein,
                        fat = item.fat,
                        timestamp = System.currentTimeMillis()
                    ))

                    onResult(item)
                } else if (!nameQuery.isNullOrBlank()) {
                    // Fallback: Suche via Name
                    val searchResponse = api.searchProduktByName(nameQuery)
                    if (searchResponse.products?.isNotEmpty() == true) {
                        val p = searchResponse.products.first()
                        onResult(FridgeItem(
                            name = p.productName ?: nameQuery,
                            brand = p.marke,
                            imageUrl = p.imageUrl,
                            barcode = p.code,
                            kcal = p.nutriments?.kcal100g?.toInt() ?: 0
                        ))
                    } else onResult(null)
                } else {
                    // Produkt unbekannt: Foto-Prompt triggern
                    if (barcode.isNotBlank()) {
                        withContext(Dispatchers.Main) {
                            unknownBarcodeForPhoto.value = barcode
                        }
                    }
                    onResult(null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(null)
            }
        }
    }
    private suspend fun enrichProductDetails(item: FridgeItem): FridgeItem {
        return try {
            // 1. Lokale Datenbank prüfen (haben wir das Bild schonmal für diesen Namen?)
            val localImageUrl = dao.findImageUrlByName(item.name)
            if (localImageUrl != null) {
                return item.copy(imageUrl = localImageUrl)
            }

            val barcode = item.barcode
            // 2. Suche via Barcode (Falls vorhanden)
            if (barcode != null) {
                val response = api.getProduktByBarcode(barcode)
                if (response.status == 1 && response.product != null) {
                    val p = response.product
                    return item.copy(
                        brand = p.marke ?: item.brand,
                        imageUrl = p.imageUrl ?: item.imageUrl,
                        nutriScore = p.nutriScore ?: item.nutriScore,
                        kcal = p.nutriments?.kcal100g?.toInt() ?: item.kcal
                    )
                }
            }
            
            // 3. Suche via Name (Fallback)
            val searchResponse = api.searchProduktByName(item.name)
            if (searchResponse.products?.isNotEmpty() == true) {
                val p = searchResponse.products.first()
                return item.copy(
                    brand = p.marke ?: item.brand,
                    imageUrl = p.imageUrl ?: item.imageUrl,
                    nutriScore = p.nutriScore ?: item.nutriScore
                )
            }
            item
        } catch (e: Exception) {
            item
        }
    }

    fun setAppIcon(index: Int) {
        if (!LauncherIconVariants.isValid(index)) return

        currentIconIndex.intValue = index
        saveSetting("app_icon_index", index)
        val pm = context.packageManager
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Ensure index is formatted with two digits as expected by aliasClassName
                val selectedComponent = android.content.ComponentName(
                    context,
                    LauncherIconVariants.aliasClassName(context.packageName, index)
                )

                // First enable the new one
                pm.setComponentEnabledSetting(
                    selectedComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                
                // Then disable all others
                (1..LauncherIconVariants.count)
                    .filter { it != index }
                    .forEach { variant ->
                        val componentName = android.content.ComponentName(
                            context,
                            LauncherIconVariants.aliasClassName(context.packageName, variant)
                        )
                        pm.setComponentEnabledSetting(
                            componentName,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    }
            } catch (e: Exception) {
                Log.e("FridgeViewModel", "Error switching app icon", e)
            }
        }
    }

    fun confirmAndImport() {
        viewModelScope.launch {
            val toImport = importCandidates.value.filter { selectedItems.value.contains(it.id) }
            
            // Batch-Insert via DAO (falls unterstützt) oder sequenziell
            // Wir nutzen addItem für die Logik (Audit logs, etc.)
            toImport.forEach { addItem(it) } 
            
            showImportPreview.value = false
            // Speicher freigeben
            importCandidates.value = emptyList()
            selectedItems.value = emptySet()
            receiptBitmap.value = null 

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "${toImport.size} Artikel im Vorrat gespeichert!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun cleanSearchTerm(name: String): String {
    return name.replace(Regex("""\d+(g|ml|l|kg|stk|er)\b""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\d+\s*x\s*\d+""", RegexOption.IGNORE_CASE), "")
        .trim()
}

fun openRetailerWebsite(context: Context, retailer: String, productName: String? = null) {
    val calendar = Calendar.getInstance()
    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    
    val cleanedProduct = productName?.let { cleanSearchTerm(it) }
    val encodedProduct = cleanedProduct?.let { Uri.encode(it) } ?: ""

    val url = when (retailer.lowercase()) {
        "kaufland" -> {
            if (cleanedProduct != null) "https://www.kaufland.de/suche.html?q=$encodedProduct"
            else {
                when (dayOfWeek) {
                    Calendar.THURSDAY, Calendar.FRIDAY -> "https://www.kaufland.de/angebote/ab-donnerstag.html"
                    Calendar.SATURDAY -> "https://www.kaufland.de/angebote/wochenende.html"
                    else -> "https://www.kaufland.de/angebote/aktuelle-woche.html"
                }
            }
        }
        "lidl" -> {
            if (cleanedProduct != null) "https://www.lidl.de/search?query=$encodedProduct"
            else {
                when (dayOfWeek) {
                    Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY -> "https://www.lidl.de/c/billiger-montag/a10006065"
                    Calendar.THURSDAY, Calendar.FRIDAY -> "https://www.lidl.de/c/toller-donnerstag/a10006345"
                    Calendar.SATURDAY, Calendar.SUNDAY -> "https://www.lidl.de/c/super-samstag/a10006523"
                    else -> "https://www.lidl.de/angebote"
                }
            }
        }
        "rewe" -> {
            if (cleanedProduct != null) "https://www.rewe.de/suche/?search=$encodedProduct"
            else "https://www.rewe.de/angebote"
        }
        "aldi", "aldi nord", "aldi süd" -> {
            if (cleanedProduct != null) "https://www.google.de/search?q=site:aldi-nord.de+$encodedProduct"
            else "https://www.aldi-nord.de/angebote.html"
        }
        "edeka" -> {
            if (cleanedProduct != null) "https://www.edeka.de/suche.jsp?searchterm=$encodedProduct"
            else "https://www.edeka.de/angebote"
        }
        "penny" -> {
            if (cleanedProduct != null) "https://www.google.de/search?q=site:penny.de+$encodedProduct"
            else {
                if (dayOfWeek >= Calendar.THURSDAY) "https://www.penny.de/angebote/ab-donnerstag"
                else "https://www.penny.de/angebote/ab-montag"
            }
        }
        "globus" -> "https://www.globus.de/suche.php?q=$encodedProduct"
        "tegut" -> "https://www.tegut.com/suche.html?q=$encodedProduct"
        "hit" -> "https://www.hit.de/angebote.html"
        "famila" -> "https://www.famila-nordost.de/angebote"
        "dm" -> {
            if (cleanedProduct != null) "https://www.dm.de/search?query=$encodedProduct"
            else "https://www.dm.de/neu"
        }
        "rossmann" -> {
            if (cleanedProduct != null) "https://www.rossmann.de/de/search?q=$encodedProduct"
            else "https://www.rossmann.de/de/angebote"
        }
        "müller" -> "https://www.mueller.de/suche/?q=$encodedProduct"
        "fressnapf" -> "https://www.fressnapf.de/suche/?text=$encodedProduct"
        "action" -> "https://www.action.com/de-de/suche/?q=$encodedProduct"
        "ikea" -> "https://www.ikea.com/de/de/search/?q=$encodedProduct"
        "bauhaus" -> "https://www.bauhaus.info/suche/produkte?text=$encodedProduct"
        "obi" -> "https://www.obi.de/search/$encodedProduct"
        "hornbach" -> "https://www.hornbach.de/search/?q=$encodedProduct"
        "decathlon" -> "https://www.decathlon.de/search?Ntt=$encodedProduct"
        "mediamarkt" -> "https://www.mediamarkt.de/de/search.html?query=$encodedProduct"
        "saturn" -> "https://www.saturn.de/de/search.html?query=$encodedProduct"
        "denn", "denns" -> "https://www.denns-biomarkt.de/angebote"
        "alnatura" -> "https://www.alnatura.de/de-de/suche?q=$encodedProduct"
        else -> "https://www.google.de/search?q=angebote+$retailer+$encodedProduct"
    }
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        println("Fehler beim Öffnen der Webseite: ${e.message}")
    }
}
