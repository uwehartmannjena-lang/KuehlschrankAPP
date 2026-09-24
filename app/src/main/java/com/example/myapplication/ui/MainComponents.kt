package com.example.myapplication.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import coil.compose.SubcomposeAsyncImage
import com.example.myapplication.FridgeViewModel
import com.example.myapplication.ui.help.AppHelpManager
import com.example.myapplication.ui.help.HelpDialog
import com.example.myapplication.ui.help.OnboardingOverlay
import com.example.myapplication.KassenzettelScannerActivity
import com.example.myapplication.KassenzettelParser
import com.example.myapplication.ScannerActivity
import com.example.myapplication.data.FridgeItem
import com.example.myapplication.data.MealPlan
import com.example.myapplication.data.ShoppingItem
import com.example.myapplication.data.BudgetConfig
import com.example.myapplication.data.CategoryDetector
import com.example.myapplication.data.ConsumedItem
import com.example.myapplication.data.FoodCategory
import com.example.myapplication.data.Product
import com.example.myapplication.data.SeasonHelper
import com.example.myapplication.data.WastedItem
import com.example.myapplication.data.StatisticsHelper
import com.example.myapplication.data.StatisticsTimeFrame
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

fun getFavoriteIconVector(iconName: String): ImageVector {
    return when (iconName) {
        "Star", "star" -> Icons.Default.Star
        "Heart", "heart" -> Icons.Default.FavoriteBorder
        "Favorite", "favorite" -> Icons.Default.Favorite
        "Bookmark", "bookmark" -> Icons.Default.Bookmark
        "PushPin", "pushpin" -> Icons.Default.PushPin
        "WorkspacePremium", "premium", "krone" -> Icons.Default.WorkspacePremium
        "LocalFireDepartment", "flamme", "fire" -> Icons.Default.LocalFireDepartment
        "Lightbulb", "lightbulb" -> Icons.Default.Lightbulb
        "CheckCircle", "check" -> Icons.Default.CheckCircle
        "ShoppingBag", "shopping_bag" -> Icons.Default.ShoppingBag
        "LocalMall", "mall" -> Icons.Default.LocalMall
        "Loyalty", "loyalty" -> Icons.Default.Loyalty
        "Diamond", "diamond" -> Icons.Default.Diamond
        "Celebration", "celebration" -> Icons.Default.Celebration
        "Eco", "eco" -> Icons.Default.Eco
        else -> Icons.Default.Star
    }
}

fun executeCardAction(action: String, item: FridgeItem, viewModel: FridgeViewModel, onEdit: () -> Unit) {
    when (action) {
        "Verbraucht" -> viewModel.consumeItem(item)
        "Müll" -> viewModel.wasteItem(item)
        "Löschen" -> viewModel.removeItem(item)
        "Geöffnet", "Offen" -> viewModel.markAsOpened(item)
        "Favorit" -> viewModel.toggleFavorite(item)
        "Bearbeiten" -> onEdit()
    }
}

fun getActionIconAndColor(action: String): Pair<ImageVector, Color> {
    return when (action) {
        "Verbraucht" -> Pair(Icons.Default.Check, Color(0xFF2E7D32))
        "Müll" -> Pair(Icons.Default.Delete, Color(0xFFD32F2F))
        "Löschen" -> Pair(Icons.Default.DeleteForever, Color(0xFFB71C1C))
        "Geöffnet", "Offen" -> Pair(Icons.Default.Schedule, Color(0xFFF57C00))
        "Favorit" -> Pair(Icons.Default.Star, Color(0xFFFFB300))
        "Bearbeiten" -> Pair(Icons.Default.Edit, Color(0xFF1976D2))
        else -> Pair(Icons.Default.Check, Color(0xFF2E7D32))
    }
}



@Composable
fun RefrigeratorApp(fridgeViewModel: FridgeViewModel) {
    MainScreenContent(fridgeViewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(fridgeViewModel: FridgeViewModel) {
    val context = LocalContext.current
    val tutorialCompleted = remember {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("tutorial_completed", false)
    }
    var showTutorial by remember { mutableStateOf(!tutorialCompleted) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var itemForInfoDialog by remember { mutableStateOf<FridgeItem?>(null) }

    val allItems by fridgeViewModel.allFridgeItems.collectAsState(initial = emptyList())
    val displayedItems = remember(
        allItems, 
        fridgeViewModel.currentHouseholdId.value,
        fridgeViewModel.searchInput.value,
        fridgeViewModel.filterExpiryDays.intValue,
        fridgeViewModel.filterCategory.value,
        fridgeViewModel.filterLocation.value,
        fridgeViewModel.sortOrder.value
    ) {
        allItems.filter { item ->
            val matchesHousehold = item.householdId == fridgeViewModel.currentHouseholdId.value || fridgeViewModel.currentHouseholdId.value == "default"
            val matchesSearch = fridgeViewModel.searchInput.value.isBlank() || item.name.contains(fridgeViewModel.searchInput.value, ignoreCase = true)
            val matchesExpiry = when (fridgeViewModel.filterExpiryDays.intValue) {
                0 -> true
                -1 -> item.expiryDate != null && item.expiryDate!! < System.currentTimeMillis()
                else -> item.expiryDate != null && (item.expiryDate!! - System.currentTimeMillis()) < TimeUnit.DAYS.toMillis(fridgeViewModel.filterExpiryDays.intValue.toLong())
            }
            
            val matchesCategoryFilter = when (fridgeViewModel.filterCategory.value) {
                null, "Alle Kategorien" -> true
                "Protein" -> item.protein >= 10.0
                "LowCarb" -> item.sugar in 0.0..5.0
                "Vegan" -> item.isVegan
                else -> item.category.equals(fridgeViewModel.filterCategory.value, ignoreCase = true)
            }
            
            val locFilter = fridgeViewModel.filterLocation.value
            val matchesLocation = if (locFilter == "BALD_ABLAUFEND") {
                item.expiryDate != null && (item.expiryDate!! - System.currentTimeMillis()) < TimeUnit.DAYS.toMillis(3)
            } else {
                locFilter == null || item.storageLocation.equals(locFilter, ignoreCase = true)
            }
            
            matchesHousehold && matchesSearch && matchesExpiry && matchesCategoryFilter && matchesLocation
        }.let { filtered ->
            when (fridgeViewModel.sortOrder.value) {
                "name" -> filtered.sortedBy { it.name.lowercase() }
                "price" -> filtered.sortedByDescending { it.price }
                "category" -> filtered.sortedBy { it.category }
                "protein" -> filtered.sortedByDescending { it.protein }
                else -> filtered.sortedBy { it.expiryDate ?: Long.MAX_VALUE }
            }
        }
    }

    val wastedItems by fridgeViewModel.wastedItems.collectAsState(initial = emptyList())
    val consumedItems by fridgeViewModel.consumedItems.collectAsState(initial = emptyList())
    val mealPlans by fridgeViewModel.mealPlans.collectAsState(initial = emptyList())

    val receiptScannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val json = result.data?.getStringExtra("SCAN_RESULTS")
            val imagePath = result.data?.getStringExtra("SCAN_BITMAP_PATH")
            
            if (imagePath != null) {
                val bitmap = BitmapFactory.decodeFile(imagePath)
                if (bitmap != null) {
                    fridgeViewModel.receiptBitmap.value = bitmap
                }
            }
            
            if (json != null) {
                val type = object : TypeToken<List<Product>>() {}.type
                val products: List<Product> = Gson().fromJson(json, type)
                
                // Wir nehmen einen Hash über alle Produkte, um den Beleg zu identifizieren
                val contentHash = products.joinToString { it.name }.hashCode().toString()
                fridgeViewModel.handleScannedProducts(products, "SCAN_$contentHash")
            }
        }
    }

    val tutorialTargets = remember { mutableStateMapOf<String, Rect>() }

    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val barcode = result.data?.getStringExtra("SCANNED_BARCODE")
            if (barcode != null) {
                fridgeViewModel.fetchProductDetails(barcode) { item ->
                    if (item != null) fridgeViewModel.addItem(item)
                    else Toast.makeText(context, "Unbekannter Artikel", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val multiScanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val barcodes = result.data?.getStringArrayExtra("SCANNED_BARCODES")
            if (!barcodes.isNullOrEmpty()) {
                barcodes.forEach { code ->
                    fridgeViewModel.fetchProductDetails(code) { item ->
                        if (item != null) fridgeViewModel.addItem(item)
                        else Toast.makeText(context, "Barcode $code unbekannt", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val photoScanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            fridgeViewModel.analyzePhotoForItems(bitmap)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            if (it.toString().contains("pdf", true)) fridgeViewModel.importFromPdf(context, it)
            else fridgeViewModel.importFromImage(context, it)
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { fridgeViewModel.processVoiceInput(it) }
        }
    }

    val dealAlertItem = fridgeViewModel.dealAlertItem.value
    var showFabSheet by remember { mutableStateOf(false) }
    var itemToEditForSheet by remember { mutableStateOf<FridgeItem?>(null) }
    var showItemSheet by remember { mutableStateOf(false) }
    
    val showOfferAlert by fridgeViewModel.showOfferAlert
    val currentOffers = fridgeViewModel.currentOffers

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
                    modifier = Modifier.onGloballyPositioned { tutorialTargets["nav_bar"] = Rect(it.positionInRoot(), it.size.toSize()) },
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.AutoMirrored.Default.List, null) },
                        label = { Text("Vorrat") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.ShoppingCart, null) },
                        label = { Text("Einkauf") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Restaurant, null) },
                        label = { Text("Plan") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.BarChart, null) },
                        label = { Text("Statistik") }
                    )
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showFabSheet = true },
                    containerColor = fridgeViewModel.themeColor.value,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, "Hinzufügen")
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                when (selectedTab) {
                    0 -> {
                        FilterAndSortSection(fridgeViewModel)
                        when (fridgeViewModel.viewMode.value) {
                            "grid" -> {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(4.dp)) }
                                    items(displayedItems, key = { it.id }) { item ->
                                        FridgeItemGridCard(
                                            item = item,
                                            viewModel = fridgeViewModel,
                                            onEdit = { itemToEditForSheet = item; showItemSheet = true },
                                            onLongClick = { itemForInfoDialog = item }
                                        )
                                    }
                                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) { Spacer(Modifier.height(80.dp)) }
                                }
                            }
                            "gallery" -> {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 120.dp),
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(4.dp)) }
                                    items(displayedItems, key = { it.id }) { item ->
                                        FridgeItemGalleryCard(
                                            item = item,
                                            viewModel = fridgeViewModel,
                                            onEdit = { itemToEditForSheet = item; showItemSheet = true },
                                            onLongClick = { itemForInfoDialog = item }
                                        )
                                    }
                                    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(80.dp)) }
                                }
                            }
                            "kanban" -> {
                                KanbanBoardView(
                                    items = displayedItems,
                                    viewModel = fridgeViewModel,
                                    onEdit = { itemToEditForSheet = it; showItemSheet = true },
                                    onLongClick = { itemForInfoDialog = it }
                                )
                            }
                            "carousel" -> {
                                CarouselView(
                                    items = displayedItems,
                                    viewModel = fridgeViewModel,
                                    onEdit = { itemToEditForSheet = it; showItemSheet = true },
                                    onLongClick = { itemForInfoDialog = it }
                                )
                            }
                            "calendar" -> {
                                CalendarView(
                                    items = displayedItems,
                                    viewModel = fridgeViewModel,
                                    onEdit = { itemToEditForSheet = it; showItemSheet = true },
                                    onLongClick = { itemForInfoDialog = it }
                                )
                            }
                            "tree" -> {
                                TreeView(
                                    items = displayedItems,
                                    viewModel = fridgeViewModel,
                                    onEdit = { itemToEditForSheet = it; showItemSheet = true },
                                    onLongClick = { itemForInfoDialog = it }
                                )
                            }
                            "shelf" -> {
                                ShelfView(
                                    items = displayedItems,
                                    viewModel = fridgeViewModel,
                                    onEdit = { itemToEditForSheet = it; showItemSheet = true },
                                    onLongClick = { itemForInfoDialog = it }
                                )
                            }
                            // Default: "list"
                            else -> {
                                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    item { Spacer(Modifier.height(4.dp)) }
                                    items(displayedItems, key = { it.id }) { item ->
                                        ModernFridgeItemCard(
                                            item = item,
                                            viewModel = fridgeViewModel,
                                            isSelected = fridgeViewModel.selectedItems.value.contains(item.id),
                                            onEdit = { itemToEditForSheet = item; showItemSheet = true },
                                            onLongClick = { itemForInfoDialog = item }
                                        )
                                    }
                                    item { Spacer(Modifier.height(80.dp)) }
                                }
                            }
                        }
                    }
                    1 -> ShoppingList(fridgeViewModel, fridgeViewModel.allShoppingItems.collectAsState(initial = emptyList()).value)
                    2 -> PlanScreen(fridgeViewModel, mealPlans, allItems)
                    3 -> StatisticsView(fridgeViewModel, allItems, wastedItems, consumedItems)
                }
            }
        }

        if (showOfferAlert && currentOffers.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { fridgeViewModel.showOfferAlert.value = false },
                title = { Text("Angebote für deine Favoriten! 🎉", fontWeight = FontWeight.Bold) },
                text = {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(currentOffers) { offer ->
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(offer.retailer, color = fridgeViewModel.themeColor.value, fontWeight = FontWeight.Bold)
                                        Text(offer.title, style = MaterialTheme.typography.bodyLarge)
                                        Text(String.format(Locale.GERMANY, "%.2f €", offer.price), color = Color.Red, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(onClick = {
                                        fridgeViewModel.addToShoppingList(ShoppingItem(name = offer.title, priceEstimate = offer.price, store = offer.retailer))
                                        Toast.makeText(context, "${offer.title} zur Liste hinzugefügt", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.AddShoppingCart, "Auf die Liste")
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { fridgeViewModel.showOfferAlert.value = false }) { Text("Schließen") }
                }
            )
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
                onMultiScanBarcode = {
                    showFabSheet = false
                    val intent = Intent(context, ScannerActivity::class.java).apply {
                        putExtra("MULTI_SCAN_MODE", true)
                    }
                    multiScanLauncher.launch(intent)
                },
                onPhotoScan = {
                    showFabSheet = false
                    photoScanLauncher.launch(null)
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
                    filePickerLauncher.launch("*/*")
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

        itemForInfoDialog?.let { item ->
            ProductInfoDialog(
                item = item,
                viewModel = fridgeViewModel,
                onDismiss = { itemForInfoDialog = null }
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

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("Einstellungen ⚙️") }, onClick = { showSettings = true; showMenu = false })
            DropdownMenuItem(text = { Text("Hilfe & Tour 💡") }, onClick = { showHelp = true; showMenu = false })
            DropdownMenuItem(text = { Text("Kassenbon scannen 🧾") }, onClick = {
                showMenu = false
                receiptScannerLauncher.launch(Intent(context, KassenzettelScannerActivity::class.java))
            })
        }

        if (showSettings) SettingsDialog(
            viewModel = fridgeViewModel,
            onDismiss = { showSettings = false },
            onStartTour = { showSettings = false; showTutorial = true },
            onOpenHelp = { showSettings = false; showHelp = true }
        )
        if (showHelp) HelpDialog(
            onDismiss = { showHelp = false },
            onStartTour = { showHelp = false; showTutorial = true }
        )
        if (showTutorial) OnboardingOverlay(
            targetBounds = tutorialTargets,
            onFinish = {
                showTutorial = false
                context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("tutorial_completed", true).apply()
            },
            onSkip = {
                showTutorial = false
                context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("tutorial_completed", true).apply()
            }
        )

        val isSubscribed by fridgeViewModel.billingManager.isSubscribed.collectAsState()
        var showPaywall by remember { mutableStateOf(false) }
        
        LaunchedEffect(isSubscribed) {
            // Zeige Paywall nach einer Verzögerung, wenn nicht abonniert (Simulation Testphase)
            if (!isSubscribed) {
                showPaywall = true
            }
        }

        if (showPaywall && !isSubscribed) {
            PaywallDialog(
                viewModel = fridgeViewModel,
                onDismiss = { showPaywall = false }
            )
        }

        if (fridgeViewModel.showImportPreview.value) {
            ImportPreviewDialog(
                viewModel = fridgeViewModel,
                onDismiss = { fridgeViewModel.showImportPreview.value = false }
            )
        }

        fridgeViewModel.unknownBarcodeForPhoto.value?.let { barcode ->
            UnknownProductDialog(barcode) { fridgeViewModel.unknownBarcodeForPhoto.value = null }
        }
    }
}

@Composable
fun ImportPreviewDialog(viewModel: FridgeViewModel, onDismiss: () -> Unit) {
    val candidates = viewModel.importCandidates.value
    val context = LocalContext.current
    var showFullReceiptImage by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Vorschau (${viewModel.recognizedCount.intValue} Artikel)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    val reportText = buildString {
                        appendLine("🧾 Kassenbon (${SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date())})")
                        appendLine("─────────────────")
                        candidates.forEach {
                            appendLine("${it.quantity}x ${it.name} (${String.format(Locale.GERMANY, "%.2f €", it.price)})")
                        }
                        appendLine("─────────────────")
                        val total = candidates.sumOf { it.price * it.quantity }
                        appendLine("Gesamt: ${String.format(Locale.GERMANY, "%.2f €", total)}")
                    }
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, reportText)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Kassenbon teilen"))
                }) {
                    Icon(Icons.Default.Share, "Bon teilen", tint = viewModel.themeColor.value)
                }
            }
        },
        confirmButton = {
            Button(onClick = { viewModel.confirmAndImport() }) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
        text = {
            Column {
                // Kompakte Bon-Vorschau oben (klickbar zum Vergrößern)
                viewModel.receiptBitmap.value?.let { bitmap ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(65.dp)
                            .padding(bottom = 6.dp)
                            .clickable { showFullReceiptImage = true },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Bon Vorschau",
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Bon-Foto", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(2.dp))
                                Icon(Icons.Default.ZoomIn, "Vergrößern", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                
                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(candidates, key = { it.id }) { candidate ->
                        val isSelected = viewModel.selectedItems.value.contains(candidate.id)
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { viewModel.toggleSelection(candidate.id) },
                                    modifier = Modifier.scale(0.85f)
                                )
                                Spacer(Modifier.width(4.dp))
                                ProductThumbnail(
                                    imageUrl = candidate.imageUrl,
                                    category = candidate.category,
                                    itemName = candidate.name,
                                    storageLocation = candidate.storageLocation,
                                    modifier = Modifier.size(34.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    var editName by remember(candidate.name) { mutableStateOf(candidate.name) }
                                    var isEditing by remember { mutableStateOf(false) }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        BasicTextField(
                                            value = editName,
                                            onValueChange = { editName = it; isEditing = true },
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isEditing && editName.isNotBlank() && editName != candidate.name) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.updateCandidateName(candidate.id, editName)
                                                    viewModel.teachItemCorrection(candidate, newName = editName)
                                                    isEditing = false
                                                    Toast.makeText(context, "Korrektur gelernt!", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(24.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                            ) {
                                                Icon(Icons.Default.Check, "Korrektur übernehmen", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                    Text("${candidate.quantity}x • ${String.format(Locale.GERMANY, "%.2f €", candidate.price)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    
                                    if (candidate.isDuplicate) {
                                        Text("🔄 Bereits vorhanden (überschreibt)", color = Color(0xFFF57C00), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.discardImportCandidate(candidate) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, "Verwerfen", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    )

    if (showFullReceiptImage && viewModel.receiptBitmap.value != null) {
        AlertDialog(
            onDismissRequest = { showFullReceiptImage = false },
            confirmButton = { TextButton(onClick = { showFullReceiptImage = false }) { Text("Schließen") } },
            text = {
                Image(
                    bitmap = viewModel.receiptBitmap.value!!.asImageBitmap(),
                    contentDescription = "Vollbild Bon",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp),
                    contentScale = ContentScale.Fit
                )
            }
        )
    }
}

@Composable
fun PaywallDialog(viewModel: FridgeViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val subDetails by viewModel.billingManager.subscriptionDetails.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Premium freischalten ⭐", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dein Testzeitraum ist abgelaufen. Schalte FrischeRadar Premium frei, um alle Funktionen weiterhin nutzen zu können:")
                Text("✅ Unbegrenzter Kassenbon-Scan")
                Text("✅ Automatischer Angebots-Alarm")
                Text("✅ Keine Werbung")
                Spacer(Modifier.height(8.dp))
                if (subDetails != null) {
                    val offer = subDetails?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()
                    val price = offer?.formattedPrice ?: "2,99 €"
                    Text("Nur $price / Monat", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("Lade Preise...", color = Color.Gray)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val activity = context as? Activity
                if (activity != null) {
                    viewModel.billingManager.launchBillingFlow(activity)
                }
            }) {
                Text("Jetzt abonnieren")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                viewModel.billingManager.queryPurchases()
                Toast.makeText(context, "Käufe werden wiederhergestellt...", Toast.LENGTH_SHORT).show()
            }) {
                Text("Käufe wiederherstellen")
            }
        }
    )
}

@Composable
fun UnknownProductDialog(barcode: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            Toast.makeText(context, "Foto empfangen! (Upload-Simulation)", Toast.LENGTH_LONG).show()
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
fun TopBarWithSearch(
    viewModel: FridgeViewModel,
    onMenuClick: () -> Unit,
    onBarcodeClick: () -> Unit,
    onPositioned: (String, Rect) -> Unit
) {
    var showWatchlistDialog by remember { mutableStateOf(false) }

    TopAppBar(
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Sensors,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Column {
                    Text("FrischeRadar", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text("Kühlschrank & MHD Tracker", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        actions = {
            IconButton(onClick = { showWatchlistDialog = true }) {
                Icon(Icons.Default.Favorite, "Watchlist")
            }
            IconButton(
                onClick = onBarcodeClick,
                modifier = Modifier.onGloballyPositioned { onPositioned("barcode_btn", Rect(it.positionInRoot(), it.size.toSize())) }
            ) {
                Icon(Icons.Default.QrCodeScanner, "Barcode scannen")
            }
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.MoreVert, "Menü")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )

    if (showWatchlistDialog) {
        WatchlistDialog(viewModel) { showWatchlistDialog = false }
    }
}

@Composable
fun WatchlistDialog(viewModel: FridgeViewModel, onDismiss: () -> Unit) {
    var newItem by remember { mutableStateOf("") }
    val watchlist = viewModel.watchlist.value.toList()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Angebots-Watchlist", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Diese Produkte werden dauerhaft bei lokalen Händlern auf Angebote geprüft.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newItem,
                        onValueChange = { newItem = it },
                        label = { Text("Neues Produkt") },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (newItem.isNotBlank()) {
                            viewModel.toggleWatchlist(newItem.trim())
                            newItem = ""
                        }
                    }) { Icon(Icons.Default.Add, "Hinzufügen") }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(watchlist) { item ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item)
                            IconButton(onClick = { viewModel.toggleWatchlist(item) }) {
                                Icon(Icons.Default.Delete, "Entfernen", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss() }) { Text("Schließen") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterAndSortSection(viewModel: FridgeViewModel) {
    var expandedSort by remember { mutableStateOf(false) }
    var expandedCategory by remember { mutableStateOf(false) }
    var expandedViewMode by remember { mutableStateOf(false) }
    
    val viewModes = listOf(
        "list" to Pair(Icons.AutoMirrored.Default.List, "Listenansicht"),
        "grid" to Pair(Icons.Default.GridView, "Kachelansicht"),
        "gallery" to Pair(Icons.Default.PhotoLibrary, "Galerieansicht"),
        "carousel" to Pair(Icons.Default.ViewCarousel, "Karussell"),
        "kanban" to Pair(Icons.Default.ViewColumn, "Board (Kanban)"),
        "tree" to Pair(Icons.Default.AccountTree, "Baumansicht"),
        "calendar" to Pair(Icons.Default.CalendarMonth, "Kalender"),
        "shelf" to Pair(Icons.Default.Kitchen, "Regal-Ansicht")
    )

    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                FilterChip(
                    selected = viewModel.showNutritionalDetails.value,
                    onClick = { viewModel.showNutritionalDetails.value = !viewModel.showNutritionalDetails.value },
                    label = { Text("Nährwerte") },
                    leadingIcon = { if (viewModel.showNutritionalDetails.value) Icon(Icons.Default.Check, null) }
                )
            }

            item {
                Box {
                    OutlinedButton(onClick = { expandedViewMode = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        val currentIcon = viewModes.find { it.first == viewModel.viewMode.value }?.second?.first ?: Icons.AutoMirrored.Default.List
                        Icon(currentIcon, null)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = expandedViewMode, onDismissRequest = { expandedViewMode = false }) {
                        viewModes.forEach { (key, info) ->
                            DropdownMenuItem(
                                text = { Text(info.second) },
                                leadingIcon = { Icon(info.first, null) },
                                onClick = { viewModel.viewMode.value = key; expandedViewMode = false }
                            )
                        }
                    }
                }
            }
            
            item {
                FilterChip(
                    selected = viewModel.showNutritionalDetails.value,
                    onClick = { viewModel.showNutritionalDetails.value = !viewModel.showNutritionalDetails.value },
                    label = { Text("Nährwerte") },
                    leadingIcon = { if (viewModel.showNutritionalDetails.value) Icon(Icons.Default.Check, null) }
                )
            }

            item {
                Box {
                    OutlinedButton(onClick = { expandedSort = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.AutoMirrored.Default.Sort, null)
                        Spacer(Modifier.width(4.dp))
                        Text(when (viewModel.sortOrder.value) {
                            "expiry" -> "MHD"
                            "name" -> "Name"
                            "price" -> "Preis"
                            else -> "Sort"
                        })
                    }
                    DropdownMenu(expanded = expandedSort, onDismissRequest = { expandedSort = false }) {
                        DropdownMenuItem(text = { Text("Nach MHD") }, onClick = { viewModel.sortOrder.value = "expiry"; expandedSort = false })
                        DropdownMenuItem(text = { Text("Nach Name") }, onClick = { viewModel.sortOrder.value = "name"; expandedSort = false })
                        DropdownMenuItem(text = { Text("Nach Preis") }, onClick = { viewModel.sortOrder.value = "price"; expandedSort = false })
                    }
                }
            }

            item {
                FilterChip(
                    selected = viewModel.showNutritionalDetails.value,
                    onClick = { viewModel.showNutritionalDetails.value = !viewModel.showNutritionalDetails.value },
                    label = { Text("Nährwerte") },
                    leadingIcon = { if (viewModel.showNutritionalDetails.value) Icon(Icons.Default.Check, null) }
                )
            }

            item {
                Box {
                    OutlinedButton(onClick = { expandedCategory = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Default.FilterList, null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (viewModel.filterCategory.value == null) "Kategorie" else viewModel.filterCategory.value!!.take(10) + "..")
                    }
                    DropdownMenu(expanded = expandedCategory, onDismissRequest = { expandedCategory = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                        DropdownMenuItem(text = { Text("Alle Kategorien") }, onClick = { viewModel.filterCategory.value = null; expandedCategory = false })
                        FoodCategory.values().forEach { cat ->
                            DropdownMenuItem(text = { Text(cat.displayName) }, onClick = { viewModel.filterCategory.value = cat.displayName; expandedCategory = false })
                        }
                    }
                }
            }

            item {
                FilterChip(
                    selected = viewModel.filterLocation.value == null && viewModel.filterCategory.value == null,
                    onClick = { viewModel.filterLocation.value = null; viewModel.filterCategory.value = null },
                    label = { Text("Alle") }
                )
            }
            item {
                FilterChip(
                    selected = viewModel.filterLocation.value == "BALD_ABLAUFEND",
                    onClick = { viewModel.filterLocation.value = if (viewModel.filterLocation.value == "BALD_ABLAUFEND") null else "BALD_ABLAUFEND" },
                    label = { Text("⚠️ Bald ablaufend") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFFFF3E0))
                )
            }
            item {
                FilterChip(
                    selected = viewModel.filterLocation.value == "Kühlschrank",
                    onClick = { viewModel.filterLocation.value = if (viewModel.filterLocation.value == "Kühlschrank") null else "Kühlschrank" },
                    label = { Text("Kühlschrank") }
                )
            }
        }
    }
}

@Composable
fun ProductThumbnail(
    imageUrl: String?,
    category: String,
    itemName: String,
    storageLocation: String,
    modifier: Modifier = Modifier
) {
    val initial = remember(itemName) {
        itemName.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!imageUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = itemName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp).size(16.dp), strokeWidth = 2.dp)
                    },
                    error = {
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            } else {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ModernFridgeItemCard(
    item: FridgeItem,
    viewModel: FridgeViewModel,
    isSelected: Boolean,
    onEdit: () -> Unit,
    onLongClick: () -> Unit
) {
    val daysLeft = remember(item.expiryDate) {
        item.expiryDate?.let { 
            TimeUnit.MILLISECONDS.toDays(it - System.currentTimeMillis()).toInt() 
        }
    }
    
    val indicatorColor = when {
        daysLeft == null -> Color.Gray
        daysLeft < 0 -> Color(0xFFE53935)
        daysLeft <= 3 -> Color(0xFFE65100)
        else -> Color(0xFF2E7D32)
    }

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val rightAction = viewModel.swipeRightAction.value
    val leftAction = viewModel.swipeLeftAction.value
    val upAction = viewModel.swipeUpAction.value
    val downAction = viewModel.swipeDownAction.value

    val (rightIcon, rightColor) = remember(rightAction) { getActionIconAndColor(rightAction) }
    val (leftIcon, leftColor) = remember(leftAction) { getActionIconAndColor(leftAction) }
    val (upIcon, upColor) = remember(upAction) { getActionIconAndColor(upAction) }
    val (downIcon, downColor) = remember(downAction) { getActionIconAndColor(downAction) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(viewModel.cardCornerRadius.intValue.dp))
    ) {
        // Swipe Hintergrund & Symbol-Anzeige
        if (offsetX != 0f || offsetY != 0f) {
            val isHorizontal = abs(offsetX) > abs(offsetY)
            
            val isSwipingRight = isHorizontal && offsetX > 0f
            val isSwipingLeft = isHorizontal && offsetX < 0f
            val isSwipingDown = !isHorizontal && offsetY > 0f
            val isSwipingUp = !isHorizontal && offsetY < 0f

            val currentAction = when {
                isSwipingRight -> rightAction
                isSwipingLeft -> leftAction
                isSwipingDown -> downAction
                else -> upAction
            }
            val currentIcon = when {
                isSwipingRight -> rightIcon
                isSwipingLeft -> leftIcon
                isSwipingDown -> downIcon
                else -> upIcon
            }
            val currentColor = when {
                isSwipingRight -> rightColor
                isSwipingLeft -> leftColor
                isSwipingDown -> downColor
                else -> upColor
            }
            
            val alignment = when {
                isSwipingRight -> Alignment.CenterStart
                isSwipingLeft -> Alignment.CenterEnd
                isSwipingDown -> Alignment.TopCenter
                else -> Alignment.BottomCenter
            }

            val thresholdReached = if (isHorizontal) abs(offsetX) > 100f else abs(offsetY) > 100f
            val alphaVal = (if (isHorizontal) abs(offsetX) else abs(offsetY) / 100f).coerceIn(0.3f, 1f)

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(if (thresholdReached) currentColor else currentColor.copy(alpha = 0.4f))
                    .padding(20.dp),
                contentAlignment = alignment
            ) {
                if (isHorizontal) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSwipingLeft) {
                            Text(
                                text = currentAction,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.alpha(alphaVal)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Icon(
                            imageVector = currentIcon,
                            contentDescription = currentAction,
                            tint = Color.White,
                            modifier = Modifier
                                .size(28.dp)
                                .scale(if (thresholdReached) 1.25f else 1.0f)
                                .alpha(alphaVal)
                        )
                        if (isSwipingRight) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = currentAction,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.alpha(alphaVal)
                            )
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isSwipingUp) {
                            Text(
                                text = currentAction,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.alpha(alphaVal)
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Icon(
                            imageVector = currentIcon,
                            contentDescription = currentAction,
                            tint = Color.White,
                            modifier = Modifier
                                .size(28.dp)
                                .scale(if (thresholdReached) 1.25f else 1.0f)
                                .alpha(alphaVal)
                        )
                        if (isSwipingDown) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = currentAction,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.alpha(alphaVal)
                            )
                        }
                    }
                }
            }
        }

        @OptIn(ExperimentalFoundationApi::class)
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (abs(offsetX) > abs(offsetY)) {
                                if (offsetX > 100f) {
                                    executeCardAction(rightAction, item, viewModel, onEdit)
                                } else if (offsetX < -100f) {
                                    executeCardAction(leftAction, item, viewModel, onEdit)
                                }
                            } else {
                                if (offsetY > 100f) {
                                    executeCardAction(downAction, item, viewModel, onEdit)
                                } else if (offsetY < -100f) {
                                    executeCardAction(upAction, item, viewModel, onEdit)
                                }
                            }
                            offsetX = 0f
                            offsetY = 0f
                        },
                        onDragCancel = { offsetX = 0f; offsetY = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount
                            offsetY += change.positionChange().y
                        }
                    )
                }
                .combinedClickable(onClick = onEdit, onLongClick = onLongClick),
            shape = RoundedCornerShape(viewModel.cardCornerRadius.intValue.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
        ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(indicatorColor))
            Spacer(modifier = Modifier.width(12.dp))
            ProductThumbnail(
                imageUrl = item.imageUrl,
                category = item.category,
                itemName = item.name,
                storageLocation = item.storageLocation,
                modifier = Modifier.size(46.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    val nutriUpper = item.nutriScore.uppercase().trim()
                    if (nutriUpper.isNotBlank() && nutriUpper in listOf("A", "B", "C", "D", "E")) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = when (nutriUpper) {
                                "A" -> Color(0xFF1B5E20)
                                "B" -> Color(0xFF4CAF50)
                                "C" -> Color(0xFFFFB300)
                                "D" -> Color(0xFFF57C00)
                                "E" -> Color(0xFFD32F2F)
                                else -> Color.Gray
                            }
                        ) {
                            Text(
                                text = nutriUpper,
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                val statusText = when {
                    item.openedDate != null -> "Geöffnet! Noch $daysLeft Tage"
                    daysLeft == null -> "Kein Ablaufdatum"
                    daysLeft < 0 -> "Abgelaufen!"
                    daysLeft == 0 -> "Läuft heute ab"
                    else -> "Noch $daysLeft Tage"
                }
                    Text(
                        text = buildString {
                            append(item.storageLocation)
                            if (item.isFrozen) append(" (Fach ${item.freezerDrawer})")
                            append(" • $statusText")
                            if (!item.brand.isNullOrBlank()) append(" • ${item.brand}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (viewModel.showNutritionalDetails.value) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (item.kcal > 0) {
                                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text("${item.kcal} kcal", fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                }
                            }
                            if (item.protein > 0) {
                                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text("${item.protein}g Protein", fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                }
                            }
                            if (item.sugar > 0) {
                                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text("${item.sugar}g Zucker", fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                }
                            }
                            if (item.fat > 0) {
                                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text("${item.fat}g Fett", fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                }
                            }
                        }
                    }

                    // MHD-Fortschrittsbalken (Batterie-Look)
                    if (item.expiryDate != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val totalDuration = (item.expiryDate!! - item.purchaseDate).coerceAtLeast(1L)
                        val passedDuration = (System.currentTimeMillis() - item.purchaseDate).coerceAtLeast(0L)
                        val progress = (1f - (passedDuration.toFloat() / totalDuration.toFloat())).coerceIn(0f, 1f)
                        val barColor = when {
                            progress > 0.5f -> Color(0xFF4CAF50)
                            progress > 0.15f -> Color(0xFFFF9800)
                            else -> Color(0xFFF44336)
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = barColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Vorratsschutz-Icon
                    if (item.minStock > 0) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Vorratsschutz aktiv",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    if (item.isFavorite) {
                        Icon(
                            imageVector = getFavoriteIconVector(viewModel.favoriteIconName.value),
                            contentDescription = "Favorit",
                            tint = viewModel.favoriteIconColor.value,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(text = "${item.quantity} ${item.unit}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (item.price > 0.0) {
                    Text(text = String.format(Locale.GERMANY, "%.2f €", item.price), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
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
    onMultiScanBarcode: () -> Unit,
    onPhotoScan: () -> Unit,
    onManualInput: () -> Unit,
    onVoiceInput: () -> Unit,
    onUpload: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Option wählen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ListItem(headlineContent = { Text("Kassenbon scannen", fontWeight = FontWeight.Bold) }, supportingContent = { Text("Mit KI & Foto-Erkennung") }, leadingContent = { Icon(Icons.Default.ReceiptLong, null, tint = MaterialTheme.colorScheme.primary) }, modifier = Modifier.clickable { onScanReceipt() })
            ListItem(headlineContent = { Text("Barcode scannen") }, supportingContent = { Text("Einzelartikel per EAN Barcode") }, leadingContent = { Icon(Icons.Default.QrCodeScanner, null) }, modifier = Modifier.clickable { onScanBarcode() })
            ListItem(headlineContent = { Text("Multi-Scan (Barcodes)") }, supportingContent = { Text("Mehrere Artikel hintereinander") }, leadingContent = { Icon(Icons.Default.CenterFocusWeak, null) }, modifier = Modifier.clickable { onMultiScanBarcode() })
            ListItem(headlineContent = { Text("Vorrats-Scanner (KI Foto)") }, supportingContent = { Text("Mehrere Artikel auf einem Foto") }, leadingContent = { Icon(Icons.Default.CameraAlt, null) }, modifier = Modifier.clickable { onPhotoScan() })
            ListItem(headlineContent = { Text("Manuelle Eingabe") }, supportingContent = { Text("Artikel händisch anlegen") }, leadingContent = { Icon(Icons.Default.Edit, null) }, modifier = Modifier.clickable { onManualInput() })
            ListItem(headlineContent = { Text("Spracheingabe") }, leadingContent = { Icon(Icons.Default.Mic, null) }, modifier = Modifier.clickable { onVoiceInput() })
            ListItem(headlineContent = { Text("Datei / PDF hochladen") }, leadingContent = { Icon(Icons.Default.UploadFile, null) }, modifier = Modifier.clickable { onUpload() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var drawer by remember { mutableIntStateOf(item?.freezerDrawer ?: 0) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (item == null) "Neuer Artikel" else "Artikel bearbeiten", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                QuantityStepper(quantity) { quantity = it }
                UnitSelector(unit) { unit = it }
            }
            LocationTileSelector(location) { location = it }
            if (location == "Gefrierfach") {
                Text("Gefrierfach", fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf(1 to "Fach 1", 2 to "Fach 2", 3 to "Fach 3", 4 to "Fach 4")) { (idx, title) ->
                        FilterChip(
                            selected = (drawer == idx),
                            onClick = { drawer = idx },
                            label = { Text(title) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
            if (item != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.consumeItem(item); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) { Text("Verbraucht") }
                    Button(onClick = { viewModel.markAsOpened(item); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00))) { Text("Offen") }
                    Button(onClick = { viewModel.wasteItem(item); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))) { Text("Müll") }
                    IconButton(onClick = { viewModel.removeItem(item); onDismiss() }, modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp))) {
                        Icon(Icons.Default.DeleteForever, "Komplett löschen", tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                OutlinedButton(
                    onClick = { viewModel.enrichItemManually(item); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Bilder & Infos online suchen")
                }
            }
            Button(
                onClick = {
                    val updatedItem = (item ?: FridgeItem(name = name)).copy(
                        name = name,
                        quantity = quantity,
                        unit = unit,
                        storageLocation = location,
                        freezerDrawer = if (location == "Gefrierfach") drawer else 0
                    )
                    if (item != null) {
                        viewModel.teachItemCorrection(item, newName = name, newLocation = location, newUnit = unit)
                    }
                    onConfirm(updatedItem)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Speichern") }
        }
    }
}

@Composable
fun QuantityStepper(value: Int, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { if (value > 1) onValueChange(value - 1) }) { Icon(Icons.Default.Remove, "Weniger") }
        Text("$value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
        IconButton(onClick = { onValueChange(value + 1) }) { Icon(Icons.Default.Add, "Mehr") }
    }
}

@Composable
fun UnitSelector(selected: String, onSelect: (String) -> Unit) {
    val units = listOf(
        "Stk.", "Pack.", "Kiste", "Flasche", "Dose", "Glas", 
        "Becher", "Beutel", "Netz", "Schale", "Bund", "Tafel", "Rolle", 
        "kg", "g", "l", "ml"
    )
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(selected) }
        DropdownMenu(
            expanded = expanded, 
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 300.dp)
        ) {
            units.forEach { u -> DropdownMenuItem(text = { Text(u) }, onClick = { onSelect(u); expanded = false }) }
        }
    }
}

@Composable
fun LocationTileSelector(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        LocationTile("Kühlschrank", Icons.Default.Kitchen, selected == "Kühlschrank", Modifier.weight(1f)) { onSelect("Kühlschrank") }
        LocationTile("Gefrierfach", Icons.Default.AcUnit, selected == "Gefrierfach", Modifier.weight(1f)) { onSelect("Gefrierfach") }
        LocationTile("Vorratskammer", Icons.Default.Inventory, selected == "Vorratskammer", Modifier.weight(1f)) { onSelect("Vorratskammer") }
    }
}

@Composable
fun LocationTile(label: String, icon: ImageVector, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
fun ShoppingList(viewModel: FridgeViewModel, items: List<ShoppingItem>) {
    ShoppingListScreen(viewModel = viewModel, items = items)
}

@Composable
fun MealPlanView(viewModel: FridgeViewModel, plans: List<MealPlan>, inventory: List<FridgeItem>) {
    var showRecipeIdeas by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = { showRecipeIdeas = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Lightbulb, null)
            Spacer(Modifier.width(8.dp))
            Text("Rezept-Vorschläge aus Vorräten")
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(plans, key = { it.id }) { plan ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            val dateStr = SimpleDateFormat("EEEE, dd.MM.yyyy", Locale.GERMANY).format(Date(plan.date))
                            Text(dateStr, fontWeight = FontWeight.Bold, color = viewModel.themeColor.value)
                            Text(plan.recipeTitle, style = MaterialTheme.typography.titleMedium)
                        }
                        IconButton(onClick = { viewModel.deleteMealPlan(plan.id) }) { Icon(Icons.Default.Delete, "Löschen", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    if (showRecipeIdeas) {
        RecipeIdeasDialog(viewModel, inventory, onDismiss = { showRecipeIdeas = false }) { suggestion ->
            viewModel.addMealPlan(MealPlan(date = System.currentTimeMillis(), recipeTitle = suggestion))
            showRecipeIdeas = false
        }
    }
}

@Composable
fun StatisticsView(
    viewModel: FridgeViewModel,
    items: List<FridgeItem>,
    wastedItems: List<WastedItem>,
    consumedItems: List<ConsumedItem>
) {
    val context = LocalContext.current
    var selectedTimeFrame by remember { mutableStateOf(StatisticsTimeFrame.DIESER_MONAT) }
    val currentMonthYear = remember { SimpleDateFormat("yyyy-MM", Locale.GERMANY).format(Date()) }
    val budget by viewModel.getBudget(currentMonthYear).collectAsState(initial = null)
    var showBudgetDialog by remember { mutableStateOf(false) }

    val filteredWasted = remember(wastedItems, selectedTimeFrame) {
        StatisticsHelper.filterWastedByTimeframe(wastedItems, selectedTimeFrame)
    }
    val filteredConsumed = remember(consumedItems, selectedTimeFrame) {
        StatisticsHelper.filterConsumedByTimeframe(consumedItems, selectedTimeFrame)
    }

    val totalValue = remember(items) { items.sumOf { it.price * it.quantity } }
    val totalCount = remember(items) { items.sumOf { it.quantity } }
    val sustainability = remember(filteredConsumed, filteredWasted) {
        StatisticsHelper.calculateSustainabilityStats(filteredConsumed, filteredWasted)
    }
    val freshness = remember(items) {
        StatisticsHelper.calculateFreshnessOverview(items)
    }
    val locationStats = remember(items) {
        StatisticsHelper.calculateLocationStats(items)
    }
    val categoryStats = remember(items) {
        StatisticsHelper.calculateCategoryStats(items)
    }
    val wasteByCategory = remember(filteredWasted) {
        StatisticsHelper.calculateWasteByCategory(filteredWasted)
    }
    val topWasted = remember(filteredWasted) {
        StatisticsHelper.calculateTopWasted(filteredWasted)
    }
    val topConsumed = remember(filteredConsumed) {
        StatisticsHelper.calculateTopConsumed(filteredConsumed)
    }
    val nutritionStats = remember(items) {
        StatisticsHelper.calculateNutritionStats(items)
    }
    val wasteTrend = remember(wastedItems, selectedTimeFrame) {
        StatisticsHelper.calculateWasteTrend(wastedItems, selectedTimeFrame)
    }
    val costProjection = remember(wastedItems, selectedTimeFrame) {
        StatisticsHelper.calculateCostProjection(wastedItems, selectedTimeFrame)
    }

    val limitVal = budget?.limit ?: 500.0
    val budgetRatio = if (limitVal > 0.0) (totalValue / limitVal).toFloat().coerceIn(0f, 1.5f) else 0f
    val budgetColor = when {
        budgetRatio > 1.0f -> Color(0xFFD32F2F)
        budgetRatio > 0.75f -> Color(0xFFF57C00)
        else -> Color(0xFF2E7D32)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Zeitraum-Filter & Teilen
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Statistik & Analyse 📊",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = {
                        val report = StatisticsHelper.generateShareReport(
                            timeFrameTitle = selectedTimeFrame.title,
                            inventoryCount = totalCount,
                            inventoryValue = totalValue,
                            consumedCount = sustainability.consumedCount,
                            wastedCount = sustainability.wastedCount,
                            wastedValue = sustainability.wastedValue,
                            wastedCo2 = sustainability.wastedCo2,
                            freshnessScore = freshness.freshnessScore,
                            nutritionStats = nutritionStats,
                            wasteTrend = wasteTrend,
                            costProjection = costProjection,
                            topConsumedItems = topConsumed,
                            topWastedItems = topWasted
                        )
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, report)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Statistikbericht teilen"))
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Bericht teilen", tint = viewModel.themeColor.value)
                }
            }

            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StatisticsTimeFrame.values()) { tf ->
                    FilterChip(
                        selected = (selectedTimeFrame == tf),
                        onClick = { selectedTimeFrame = tf },
                        label = { Text(tf.title) }
                    )
                }
            }
        }

        // 2. KPI Grid (4 Hero Kacheln)
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                // Vorratswert
                ElevatedCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Kitchen, null, tint = viewModel.themeColor.value, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Vorratswert", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = String.format(Locale.GERMANY, "%.2f €", totalValue),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("$totalCount Artikel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }

                // Rettungs-Quote
                ElevatedCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Eco, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Verwertung", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = String.format(Locale.GERMANY, "%.1f %%", sustainability.rescueRate),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (sustainability.rescueRate >= 80.0) Color(0xFF2E7D32) else Color(0xFFF57C00)
                        )
                        Text("${sustainability.consumedCount} verbraucht", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                // Müll / Verlust
                ElevatedCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Delete, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Müll-Verlust", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = String.format(Locale.GERMANY, "%.2f €", sustainability.wastedValue),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (sustainability.wastedValue > 0.0) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface
                        )
                        Text("${sustainability.wastedCount} weggeworfen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }

                // CO2
                ElevatedCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Public, null, tint = Color(0xFF1976D2), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("CO₂-Verlust", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = String.format(Locale.GERMANY, "%.1f kg", sustainability.wastedCo2),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("CO₂e Belastung", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }

        // 3. Monats-Budget & Tracker
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Monats-Budget ($currentMonthYear)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { showBudgetDialog = true }) {
                            Text("Anpassen")
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { budgetRatio.coerceAtMost(1f) },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                        color = budgetColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Aktueller Wert: ${String.format(Locale.GERMANY, "%.2f €", totalValue)}", style = MaterialTheme.typography.bodySmall)
                        Text("Limit: ${String.format(Locale.GERMANY, "%.2f €", limitVal)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    val diff = limitVal - totalValue
                    val diffText = if (diff >= 0) "Noch ${String.format(Locale.GERMANY, "%.2f €", diff)} verfügbar" else "⚠️ Budget um ${String.format(Locale.GERMANY, "%.2f €", -diff)} überschritten!"
                    Text(
                        text = diffText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = budgetColor
                    )
                }
            }
        }

        // 4. Frische-Score & MHD Ampel
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Frische-Gesundheit 🌟", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when {
                                freshness.freshnessScore >= 80 -> Color(0xFF2E7D32)
                                freshness.freshnessScore >= 50 -> Color(0xFFF57C00)
                                else -> Color(0xFFD32F2F)
                            }
                        ) {
                            Text(
                                text = "${freshness.freshnessScore} %",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🟢 Frisch", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            Text("${freshness.freshCount}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🟡 Bald fällig", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            Text("${freshness.expiringSoonCount}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFFF57C00))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔴 Abgelaufen", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            Text("${freshness.expiredCount}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFFD32F2F))
                        }
                    }
                }
            }
        }

        // Ernährungs-Analyse
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ernährungs-Analyse 🥗", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total kcal", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            Text("${nutritionStats.totalKcal} kcal", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = viewModel.themeColor.value)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Ø Protein", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            Text("${nutritionStats.averageProtein} g", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Vegan %", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            Text("${nutritionStats.veganPercentage}%", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFF2E7D32))
                        }
                    }
                }
            }
        }

        // 5. Lagerort-Verteilung
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Lagerort-Verteilung 📍", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    locationStats.forEach { loc ->
                        val locPct = if (totalValue > 0.0) (loc.totalValue / totalValue).toFloat() else 0f
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${loc.location} (${loc.count} Artikel)", style = MaterialTheme.typography.bodySmall)
                                Text(String.format(Locale.GERMANY, "%.2f €", loc.totalValue), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { locPct },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = viewModel.themeColor.value,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 6. Top Kategorien im Vorrat
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Top Kategorien im Vorrat 🛒", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (categoryStats.isEmpty()) {
                        Text("Noch keine Artikel im Vorrat vorhanden.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    } else {
                        categoryStats.take(4).forEach { cat ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val displayCatName = FoodCategory.values().find {
                                        it.displayName.equals(cat.category, ignoreCase = true) ||
                                        it.name.replace("_", " ").equals(cat.category, ignoreCase = true)
                                    }?.displayName ?: cat.category
                                    
                                    Text(displayCatName, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                                    Text("${cat.count} Artikel • ${cat.percentage} %", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Text(String.format(Locale.GERMANY, "%.2f €", cat.totalValue), fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }
        }

        // 7. Verschwendungs-Analyse (Müll-Kategorien & Top-Verluste)
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Lieblingsprodukte & Verlust (${selectedTimeFrame.title}) 📈", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                    // Trend-Anzeige
                    if (selectedTimeFrame != StatisticsTimeFrame.GESAMT && wasteTrend.previousPeriodLoss > 0.0) {
                        val trendIcon = if (wasteTrend.isImproving) "📉" else "📈"
                        val trendColor = if (wasteTrend.isImproving) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                        val trendText = if (wasteTrend.trendPercentage > 0) "+${wasteTrend.trendPercentage}%" else "${wasteTrend.trendPercentage}%"
                        
                        Surface(
                            color = trendColor.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(trendIcon, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Verlust-Trend zur Vorperiode", style = MaterialTheme.typography.labelMedium)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(trendText, fontWeight = FontWeight.Bold, color = trendColor)
                                        Text(" (${String.format(Locale.GERMANY, "%.2f €", wasteTrend.previousPeriodLoss)} → ${String.format(Locale.GERMANY, "%.2f €", wasteTrend.currentPeriodLoss)})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    
                    if (costProjection.isProjectionApplicable && selectedTimeFrame == StatisticsTimeFrame.DIESER_MONAT) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TrendingUp, contentDescription = "Prognose", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Verlust-Prognose für diesen Monat", style = MaterialTheme.typography.labelMedium)
                                    Text("~ ${String.format(Locale.GERMANY, "%.2f €", costProjection.projectedWasteValue)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    if (topConsumed.isNotEmpty()) {
                        Text("Meist verbrauchte Artikel:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        topConsumed.forEach { item ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("• ${item.name}", style = MaterialTheme.typography.bodyMedium)
                                Text("${item.count}x", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    if (filteredWasted.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                            Icon(Icons.Default.Celebration, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Ausgezeichnet! In diesem Zeitraum wurde nichts weggeworfen. 🎉", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Text("Häufigste Müll-Kategorien:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        wasteByCategory.take(3).forEach { wc ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                val displayWcName = FoodCategory.values().find {
                                    it.displayName.equals(wc.category, ignoreCase = true) ||
                                    it.name.replace("_", " ").equals(wc.category, ignoreCase = true)
                                }?.displayName ?: wc.category
                                
                                Text("• $displayWcName (${wc.count}x)", style = MaterialTheme.typography.bodyMedium)
                                Text("${String.format(Locale.GERMANY, "%.2f €", wc.totalLoss)} (-${wc.totalCo2} kg CO₂)", style = MaterialTheme.typography.bodySmall, color = Color(0xFFD32F2F))
                            }
                        }

                        if (topWasted.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text("Häufigste weggeworfene Artikel:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            topWasted.forEach { item ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("• ${item.name}", style = MaterialTheme.typography.bodyMedium)
                                    Text("${item.count}x (${String.format(Locale.GERMANY, "%.2f €", item.totalLoss)})", style = MaterialTheme.typography.bodySmall, color = Color(0xFFD32F2F))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 8. Nachhaltigkeits-Tipps
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text("Spar- & Frische-Tipp 💡", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    val tipText = when {
                        freshness.expiredCount > 0 -> "Du hast ${freshness.expiredCount} abgelaufene Produkte. Prüfe vor dem Wegwerfen mit Geruch & Geschmack, ob sie noch gut sind!"
                        freshness.expiringSoonCount > 0 -> "${freshness.expiringSoonCount} Artikel laufen bald ab. Nutze die Rezept-Ideen im 'Plan'-Tab, um sie zeitnah zu verwerten!"
                        wasteByCategory.any { it.category.contains("Obst", true) || it.category.contains("Gemüse", true) } -> "Obst & Gemüse landen bei dir am häufigsten im Müll. Lagere Bananen und Äpfel getrennt voneinander, um Nachreifen zu stoppen!"
                        else -> "Toll gemacht! Durch die Kühlschrank-Organisation sparst du bares Geld und schützt die Umwelt. 🌱"
                    }
                    Text(tipText, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    if (showBudgetDialog) {
        BudgetEditDialog(currentMonthYear, budget?.limit ?: 500.0, onDismiss = { showBudgetDialog = false }) { newLimit ->
            viewModel.setBudgetLimit(currentMonthYear, newLimit)
            showBudgetDialog = false
        }
    }
}

@Composable
fun BudgetEditDialog(monthYear: String, currentLimit: Double, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var limitInput by remember { mutableStateOf(currentLimit.toString()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Budget für $monthYear") }, text = { OutlinedTextField(value = limitInput, onValueChange = { limitInput = it }, label = { Text("Limit (€)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) }, confirmButton = { Button(onClick = { limitInput.toDoubleOrNull()?.let { onConfirm(it) } }) { Text("Speichern") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsDialog(
    viewModel: FridgeViewModel,
    onDismiss: () -> Unit,
    onStartTour: () -> Unit = {},
    onOpenHelp: () -> Unit = {}
) {
    val context = LocalContext.current
    var tempExpiryDays by remember { mutableIntStateOf(viewModel.expiryWarningDays.intValue) }
    var tempHouseholdSize by remember { mutableIntStateOf(viewModel.householdSize.intValue) }
    var tempCompactMode by remember { mutableStateOf(viewModel.isCompactMode.value) }
    var tempCornerRadius by remember { mutableIntStateOf(viewModel.cardCornerRadius.intValue) }
    var tempThemeColor by remember { mutableStateOf(viewModel.themeColor.value) }
    var tempFavoriteIconName by remember { mutableStateOf(viewModel.favoriteIconName.value) }
    var tempFavoriteIconColor by remember { mutableStateOf(viewModel.favoriteIconColor.value) }
    var tempAppIconIndex by remember { mutableIntStateOf(viewModel.currentIconIndex.intValue) }
    var tempDietProfile by remember { mutableStateOf(viewModel.selectedDietProfile.value) }
    var tempSwipeLeft by remember { mutableStateOf(viewModel.swipeLeftAction.value) }
    var tempSwipeRight by remember { mutableStateOf(viewModel.swipeRightAction.value) }
    var tempSwipeUp by remember { mutableStateOf(viewModel.swipeUpAction.value) }
    var tempSwipeDown by remember { mutableStateOf(viewModel.swipeDownAction.value) }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.restoreBackup(context, it) }
    }

    val themeColorPalette = listOf(
        Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFF009688), Color(0xFF9C27B0),
        Color(0xFFFF9800), Color(0xFFE53935), Color(0xFFFFB300), Color(0xFFE91E63)
    )

    val favoriteIconPalette = listOf(
        Color(0xFFFFB300), Color(0xFFE53935), Color(0xFF4CAF50), Color(0xFF2196F3),
        Color(0xFF9C27B0), Color(0xFFFF9800), Color(0xFFE91E63)
    )

    val fifteenIcons = listOf(
        "Star" to Icons.Default.Star,
        "Heart" to Icons.Default.FavoriteBorder,
        "Favorite" to Icons.Default.Favorite,
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

    val actionOptions = listOf("Verbraucht", "Müll", "Löschen", "Geöffnet", "Favorit", "Bearbeiten")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einstellungen ⚙️", fontWeight = FontWeight.Bold) },
        confirmButton = {
            Button(onClick = {
                viewModel.setExpiryWarningDays(tempExpiryDays)
                viewModel.setHouseholdSize(tempHouseholdSize)
                viewModel.setCompactMode(tempCompactMode)
                viewModel.setCardCornerRadius(tempCornerRadius)
                viewModel.setThemeColor(tempThemeColor)
                viewModel.setFavoriteIcon(tempFavoriteIconName)
                viewModel.setFavoriteIconColor(tempFavoriteIconColor)
                viewModel.setAppIcon(context, tempAppIconIndex)
                viewModel.setDietProfile(tempDietProfile)
                viewModel.setSwipeLeftAction(tempSwipeLeft)
                viewModel.setSwipeRightAction(tempSwipeRight)
                viewModel.setSwipeUpAction(tempSwipeUp)
                viewModel.setSwipeDownAction(tempSwipeDown)
                onDismiss()
            }) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Design & Farben
                item {
                    Text("Design & Akzentfarben", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text("Hauptfarbe wählen:", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        themeColorPalette.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (tempThemeColor.toArgb() == color.toArgb()) 3.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape
                                    )
                                    .clickable { tempThemeColor = color }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Karten Abrundung: ${tempCornerRadius}dp", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = tempCornerRadius.toFloat(),
                        onValueChange = { tempCornerRadius = it.toInt() },
                        valueRange = 4f..24f
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Kompaktmodus aktivieren", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = tempCompactMode, onCheckedChange = { tempCompactMode = it })
                    }
                }

                // Section 2: Dynamische Favoriten-Symbole (15 Icons)
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("Favoriten-Symbol (15 Optionen)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(fifteenIcons) { (iconKey, vector) ->
                            val isSelected = (tempFavoriteIconName == iconKey)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .border(if (isSelected) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                    .clickable { tempFavoriteIconName = iconKey }
                            ) {
                                Icon(
                                    imageVector = vector,
                                    contentDescription = iconKey,
                                    tint = if (isSelected) tempFavoriteIconColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Symbolfarbe wählen:", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        favoriteIconPalette.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (tempFavoriteIconColor.toArgb() == color.toArgb()) 3.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape
                                    )
                                    .clickable { tempFavoriteIconColor = color }
                            )
                        }
                    }
                }

                // Section: App-Icon Wahl (20 verschiedene Themes & Icons)
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("App-Icon (20 Varianten)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))

                    val iconTints = listOf(
                        Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFE53935), Color(0xFF9C27B0),
                        Color(0xFFFF9800), Color(0xFF009688), Color(0xFFFFB300), Color(0xFFE91E63),
                        Color(0xFF3F51B5), Color(0xFF00BCD4), Color(0xFF8BC34A), Color(0xFFCDDC39),
                        Color(0xFFFF5722), Color(0xFF795548), Color(0xFF607D8B), Color(0xFF00E676),
                        Color(0xFFFF1744), Color(0xFF651FFF), Color(0xFF00E5FF), Color(0xFFFFC400)
                    )

                    val iconNames = listOf(
                        "Classic", "Ocean Blue", "Ruby Red", "Neon Purple",
                        "Sunset Gold", "Emerald", "Amber", "Pink Blossom",
                        "Deep Indigo", "Cyan Ice", "Lime Fresh", "Citrus",
                        "Fiery Orange", "Wood Warm", "Slate Minimal", "Mint Bio",
                        "Crimson", "Royal Violet", "Electric Cyan", "Gold Premium"
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items((1..20).toList()) { index ->
                            val isSelected = (tempAppIconIndex == index)
                            val tintColor = iconTints.getOrElse(index - 1) { MaterialTheme.colorScheme.primary }
                            val iconName = iconNames.getOrElse(index - 1) { "Variant $index" }
                            val resId = remember(index) {
                                context.resources.getIdentifier(
                                    "ic_launcher_variant_%02d".format(Locale.US, index),
                                    "mipmap",
                                    context.packageName
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .border(
                                        width = if (isSelected) 2.5.dp else 0.dp,
                                        color = tintColor,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        tempAppIconIndex = index
                                    }
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(tintColor.copy(alpha = 0.2f))
                                    ) {
                                        if (resId != 0) {
                                            Image(
                                                painter = painterResource(id = resId),
                                                contentDescription = iconName,
                                                colorFilter = ColorFilter.tint(tintColor),
                                                modifier = Modifier.size(32.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Kitchen,
                                                contentDescription = iconName,
                                                tint = tintColor,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = iconName,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 3: Wischgesten & Aktionen
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("Wischgesten & Aktionen", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    
                    var expLeft by remember { mutableStateOf(false) }
                    var expRight by remember { mutableStateOf(false) }
                    var expUp by remember { mutableStateOf(false) }
                    var expDown by remember { mutableStateOf(false) }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Wischen Links ⬅️", style = MaterialTheme.typography.bodySmall)
                            Box {
                                OutlinedButton(onClick = { expLeft = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 4.dp)) {
                                    Text(tempSwipeLeft, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                DropdownMenu(expanded = expLeft, onDismissRequest = { expLeft = false }) {
                                    actionOptions.forEach { act ->
                                        DropdownMenuItem(text = { Text(act) }, onClick = { tempSwipeLeft = act; expLeft = false })
                                    }
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Wischen Rechts ➡️", style = MaterialTheme.typography.bodySmall)
                            Box {
                                OutlinedButton(onClick = { expRight = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 4.dp)) {
                                    Text(tempSwipeRight, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                DropdownMenu(expanded = expRight, onDismissRequest = { expRight = false }) {
                                    actionOptions.forEach { act ->
                                        DropdownMenuItem(text = { Text(act) }, onClick = { tempSwipeRight = act; expRight = false })
                                    }
                                }
                            }
                        }
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Wischen Oben ⬆️", style = MaterialTheme.typography.bodySmall)
                            Box {
                                OutlinedButton(onClick = { expUp = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 4.dp)) {
                                    Text(tempSwipeUp, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                DropdownMenu(expanded = expUp, onDismissRequest = { expUp = false }) {
                                    actionOptions.forEach { act ->
                                        DropdownMenuItem(text = { Text(act) }, onClick = { tempSwipeUp = act; expUp = false })
                                    }
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Wischen Unten ⬇️", style = MaterialTheme.typography.bodySmall)
                            Box {
                                OutlinedButton(onClick = { expDown = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 4.dp)) {
                                    Text(tempSwipeDown, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                DropdownMenu(expanded = expDown, onDismissRequest = { expDown = false }) {
                                    actionOptions.forEach { act ->
                                        DropdownMenuItem(text = { Text(act) }, onClick = { tempSwipeDown = act; expDown = false })
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 4: Warnung & Profil
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("Warnungen & Haushalt", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text("MHD-Warnung ab $tempExpiryDays Tage vor Ablauf", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = tempExpiryDays.toFloat(),
                        onValueChange = { tempExpiryDays = it.toInt() },
                        valueRange = 1f..14f
                    )
                    Text("Haushaltsgröße: $tempHouseholdSize Personen", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = tempHouseholdSize.toFloat(),
                        onValueChange = { tempHouseholdSize = it.toInt() },
                        valueRange = 1f..10f
                    )
                }

                // Section 5: Daten-Backup & Verwaltung
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("Daten-Backup & Verwaltung", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.exportBackup(context) }, modifier = Modifier.weight(1f)) { Text("Backup erstellen") }
                        Button(onClick = { restoreLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) { Text("Backup laden") }
                    }

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.clearInventory() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) { 
                        Text("Alle Artikel löschen (Temp)", fontWeight = FontWeight.Bold) 
                    }
                }

                // Section 6: Rechtliches & Richtlinien
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("Rechtliches & Richtlinien", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/privacy-policy"))
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Datenschutzerklärung (Privacy Policy)")
                    }
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/terms"))
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.Gavel, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Nutzungsbedingungen (Terms of Use)")
                    }
                }

                // Section 7: Hilfe & Support
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("Hilfe & Support", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onOpenHelp()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.HelpOutline, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Handbuch & FAQ", fontSize = 11.sp, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onStartTour()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Tour starten", fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    )
}



@Composable
fun RecipeIdeasDialog(viewModel: FridgeViewModel, inventory: List<FridgeItem>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.getRecipeSuggestions() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rezept-Ideen 💡") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (viewModel.isRecipeLoading.value) CircularProgressIndicator()
                else viewModel.recipeSuggestions.forEach { Text("• $it", modifier = Modifier.clickable { onSelect(it) }) }
                Button(onClick = { 
                    val items = inventory.filter { it.expiryDate != null }.map { it.name }.take(3).joinToString("+")
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.chefkoch.de/rs/s0/$items/Rezepte.html")))
                }, modifier = Modifier.fillMaxWidth()) { Text("Auf Chefkoch suchen") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )
}

@Composable
fun ImageZoomDialog(onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { }, text = { Text("Bild-Vorschau") })
}

@Composable
fun ProductInfoDialog(item: FridgeItem, viewModel: FridgeViewModel, onDismiss: () -> Unit) {
    val daysLeft = remember(item.expiryDate) {
        item.expiryDate?.let {
            TimeUnit.MILLISECONDS.toDays(it - System.currentTimeMillis()).toInt()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ProductThumbnail(
                        imageUrl = item.imageUrl,
                        category = item.category,
                        itemName = item.name,
                        storageLocation = item.storageLocation,
                        modifier = Modifier.size(100.dp)
                    )
                }

                // Status info
                val statusInfo = when {
                    item.openedDate != null -> "⚠️ Geöffnet am ${SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(item.openedDate!!))} (Noch $daysLeft Tage)"
                    daysLeft == null -> "Kein Ablaufdatum"
                    daysLeft < 0 -> "🔴 Abgelaufen!"
                    daysLeft == 0 -> "🟡 Läuft heute ab"
                    else -> "🟢 Noch $daysLeft Tage haltbar"
                }
                Text(statusInfo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)

                // Lagertipp
                val storageTip = remember(item.name) { SeasonHelper.getStorageTip(item.name) }
                if (storageTip != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(modifier = Modifier.padding(8.dp)) {
                            Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(storageTip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { viewModel.consumeItem(item); onDismiss() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Verbraucht", fontSize = 11.sp)
                    }

                    Button(
                        onClick = { viewModel.markAsOpened(item); onDismiss() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00))
                    ) {
                        Icon(Icons.Default.Schedule, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Offen", fontSize = 11.sp)
                    }

                    Button(
                        onClick = { viewModel.wasteItem(item); onDismiss() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Müll", fontSize = 11.sp)
                    }
                }
                
                OutlinedButton(
                    onClick = { viewModel.enrichItemManually(item); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Bilder & Infos online suchen")
                }

                Text("Lagerort: ${item.storageLocation}")
                Text("Menge & Einheit: ${item.quantity} ${item.unit}")
                if (!item.brand.isNullOrBlank()) Text("Marke: ${item.brand}")
                if (item.kcal > 0) Text("Kalorien: ${item.kcal} kcal/100g")
                if (item.nutriScore.isNotBlank()) {
                    val scoreUpper = item.nutriScore.uppercase().trim()
                    val displayScore = when {
                        scoreUpper in listOf("A", "B", "C", "D", "E") -> scoreUpper
                        scoreUpper.contains("NOT") || scoreUpper.contains("N/A") -> "Nicht anwendbar"
                        else -> scoreUpper
                    }
                    Text("Nutri-Score: $displayScore")
                }
                if (item.ecoScore.isNotBlank()) {
                    val ecoUpper = item.ecoScore.uppercase().trim()
                    val displayEco = when {
                        ecoUpper in listOf("A", "B", "C", "D", "E") -> ecoUpper
                        ecoUpper.contains("NOT") || ecoUpper.contains("N/A") -> "Nicht anwendbar"
                        else -> ecoUpper
                    }
                    Text("Eco-Score: $displayEco")
                }
                val priceHistory by viewModel.getPriceHistory(item.id).collectAsState(initial = emptyList())
                if (priceHistory.isNotEmpty()) {
                    Text("Preisverlauf:", fontWeight = FontWeight.Bold)
                    priceHistory.forEach { Text("${String.format(Locale.GERMANY, "%.2f", it.price)} € am ${SimpleDateFormat("dd.MM", Locale.GERMANY).format(Date(it.date))}") }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("OK") } }
    )
}

fun openRetailerWebsite(context: Context, retailer: String, productName: String? = null) {
    val encodedProduct = productName?.let { Uri.encode(it) } ?: ""
    val url = when (retailer.lowercase()) {
        "rewe" -> "https://www.rewe.de/angebote/?search=$encodedProduct"
        "edeka" -> "https://www.edeka.de/angebote.jsp"
        "lidl" -> "https://www.lidl.de/c/billiger-mit-lidl/a10008681"
        "aldi" -> "https://www.aldi-nord.de/angebote.html"
        "kaufland" -> "https://www.kaufland.de/angebote/"
        else -> "https://www.google.com/search?q=$encodedProduct+angebot+$retailer"
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(context, "Browser konnte nicht geöffnet werden.", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FridgeItemGridCard(
    item: FridgeItem,
    viewModel: FridgeViewModel,
    onEdit: () -> Unit,
    onLongClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onEdit, onLongClick = onLongClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ProductThumbnail(item.imageUrl, item.category, item.name, item.storageLocation, Modifier.size(60.dp))
            Spacer(Modifier.height(4.dp))
            Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${item.quantity} ${item.unit}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FridgeItemGalleryCard(
    item: FridgeItem,
    viewModel: FridgeViewModel,
    onEdit: () -> Unit,
    onLongClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).combinedClickable(onClick = onEdit, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ProductThumbnail(item.imageUrl, item.category, item.name, item.storageLocation, Modifier.fillMaxSize())
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    text = item.name,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.padding(4.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun KanbanBoardView(items: List<FridgeItem>, viewModel: FridgeViewModel, onEdit: (FridgeItem) -> Unit, onLongClick: (FridgeItem) -> Unit) {
    val grouped = items.groupBy { it.storageLocation }
    val locations = listOf("Kühlschrank", "Gefrierfach", "Vorratskammer", "Haushalt")
    
    LazyRow(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(locations) { loc ->
            val columnItems = grouped[loc] ?: emptyList()
            ElevatedCard(
                modifier = Modifier.width(280.dp).fillMaxHeight(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(loc, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(columnItems, key = { it.id }) { item ->
                            FridgeItemGridCard(item, viewModel, { onEdit(item) }, { onLongClick(item) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CarouselView(items: List<FridgeItem>, viewModel: FridgeViewModel, onEdit: (FridgeItem) -> Unit, onLongClick: (FridgeItem) -> Unit) {
    LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
        items(items, key = { it.id }) { item ->
            ElevatedCard(
                modifier = Modifier.width(200.dp).height(250.dp).combinedClickable(onClick = { onEdit(item) }, onLongClick = { onLongClick(item) })
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    ProductThumbnail(item.imageUrl, item.category, item.name, item.storageLocation, Modifier.size(100.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${item.quantity} ${item.unit}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun CalendarView(items: List<FridgeItem>, viewModel: FridgeViewModel, onEdit: (FridgeItem) -> Unit, onLongClick: (FridgeItem) -> Unit) {
    val grouped = items.filter { it.expiryDate != null }.groupBy { 
        SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(it.expiryDate!!))
    }.toSortedMap(compareBy { SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).parse(it)?.time ?: 0L })

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        grouped.forEach { (dateStr, items) ->
            item {
                Text("Ablaufdatum: $dateStr", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            }
            items(items, key = { it.id }) { item ->
                ModernFridgeItemCard(item, viewModel, false, { onEdit(item) }, { onLongClick(item) })
            }
        }
    }
}

@Composable
fun TreeView(items: List<FridgeItem>, viewModel: FridgeViewModel, onEdit: (FridgeItem) -> Unit, onLongClick: (FridgeItem) -> Unit) {
    val grouped = items.groupBy { it.category }
    var expandedCategories by remember { mutableStateOf(setOf<String>()) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        grouped.forEach { (category, categoryItems) ->
            item {
                val isExpanded = expandedCategories.contains(category)
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { 
                        expandedCategories = if (isExpanded) expandedCategories - category else expandedCategories + category
                    }.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("$category (${categoryItems.size})", fontWeight = FontWeight.Bold)
                        Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    }
                }
            }
            if (expandedCategories.contains(category)) {
                items(categoryItems, key = { it.id }) { item ->
                    ModernFridgeItemCard(item, viewModel, false, { onEdit(item) }, { onLongClick(item) })
                }
            }
        }
    }
}
