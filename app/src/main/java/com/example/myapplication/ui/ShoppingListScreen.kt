package com.example.myapplication.ui

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.FridgeViewModel
import com.example.myapplication.data.CategoryDetector
import com.example.myapplication.data.FoodCategory
import com.example.myapplication.data.FridgeItem
import com.example.myapplication.data.ShoppingItem
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.*

/**
 * Modernisierte Einkaufsliste für Kühlschrank Profi 🍏
 * Bietet:
 * 1. Intelligente Verknüpfung mit Kühlschrank & Vorrat ("Gekauft & Einräumen" Dialog)
 * 2. Supermarkt-Laufweg & Smarte Gruppierung nach Gang/Kategorie
 * 3. Moderner Supermarkt-Modus (Fokus-Ansicht mit haptischer Rückmeldung)
 * 4. Live-Kassensturz & Budget-Schätzung
 * 5. Schnelleingabe (Komma-getrennt) & WhatsApp-Teilen
 * 6. Modernes Compose Design (Mengen-Counter, Notizen, Dringlichkeits-Ampel)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(viewModel: FridgeViewModel, items: List<ShoppingItem>) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var newItemInput by remember { mutableStateOf("") }
    var isSupermarketMode by remember { mutableStateOf(false) }
    var showQuickInputDialog by remember { mutableStateOf(false) }
    var showCleanupMenu by remember { mutableStateOf(false) }

    // State for Transfer-Dialog ("Gekauft & Einräumen")
    var itemToTransfer by remember { mutableStateOf<ShoppingItem?>(null) }
    
    // State for Edit-Item Dialog (Notizen, Preis, Dringlichkeit)
    var itemToEdit by remember { mutableStateOf<ShoppingItem?>(null) }

    var isCheckedSectionExpanded by remember { mutableStateOf(true) }

    // Collect consumed and low stock items for refill recommendations
    val consumedItems by viewModel.consumedItems.collectAsState(initial = emptyList())
    val allFridgeItems by viewModel.allFridgeItems.collectAsState(initial = emptyList())

    val refillSuggestions = remember(consumedItems, allFridgeItems, items) {
        val existingNames = items.map { it.name.lowercase() }.toSet()
        val lowStockNames = allFridgeItems
            .filter { it.quantity <= it.minStock }
            .map { Pair(it.name, it.unit) }
        val consumedNames = consumedItems
            .take(15)
            .map { Pair(it.name, it.unit) }

        (lowStockNames + consumedNames)
            .distinctBy { it.first.lowercase() }
            .filter { !existingNames.contains(it.first.lowercase()) }
            .take(12)
    }

    // Split checked vs unchecked
    val uncheckedItems = remember(items) { items.filter { !it.isChecked } }
    val checkedItems = remember(items) { items.filter { it.isChecked } }

    // Total price calculations
    val totalCost = remember(items) { items.sumOf { it.quantity * it.priceEstimate } }
    val openCost = remember(uncheckedItems) { uncheckedItems.sumOf { it.quantity * it.priceEstimate } }
    val inCartCost = remember(checkedItems) { checkedItems.sumOf { it.quantity * it.priceEstimate } }

    // Group unchecked items by category sorted along supermarket aisle route
    val groupedUnchecked = remember(uncheckedItems) {
        uncheckedItems.groupBy { CategoryDetector.detectCategory(it.name) }
            .toSortedMap(compareBy<FoodCategory> { it.aisleOrder })
    }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                viewModel.parseSpeechInputToShoppingList(spokenText)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // --- 1. TOP HEADER & MODE SWITCHER ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode Switcher (Standard vs. Supermarkt-Modus)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                SegmentedButton(
                    selected = !isSupermarketMode,
                    onClick = { isSupermarketMode = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {}
                ) {
                    Icon(Icons.AutoMirrored.Filled.List, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Liste", style = MaterialTheme.typography.labelMedium)
                }
                SegmentedButton(
                    selected = isSupermarketMode,
                    onClick = {
                        isSupermarketMode = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    icon = {}
                ) {
                    Icon(Icons.Default.ShoppingCart, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Supermarkt 🛒", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.width(8.dp))

            // Quick Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Mikrofon (Speech-to-Text)
                IconButton(
                    onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Was möchtest du einkaufen?")
                        }
                        speechRecognizerLauncher.launch(intent)
                    },
                    modifier = Modifier.background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
                ) {
                    Icon(Icons.Default.Mic, "Spracheingabe", tint = viewModel.themeColor.value)
                }

                // Schnelleingabe Button
                IconButton(
                    onClick = { showQuickInputDialog = true },
                    modifier = Modifier.background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
                ) {
                    Icon(Icons.Default.Speed, "Schnelleingabe", tint = viewModel.themeColor.value)
                }

                // Messenger Teilen
                IconButton(
                    onClick = { viewModel.shareShoppingList(context, items) },
                    modifier = Modifier.background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
                ) {
                    Icon(Icons.Default.Share, "Teilen", tint = viewModel.themeColor.value)
                }

                // Options / Aufräumen Menu
                Box {
                    IconButton(
                        onClick = { showCleanupMenu = true },
                        modifier = Modifier.background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                    ) {
                        Icon(Icons.Default.MoreVert, "Optionen")
                    }
                    DropdownMenu(
                        expanded = showCleanupMenu,
                        onDismissRequest = { showCleanupMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("🧹 Abgehakte aufräumen") },
                            onClick = {
                                viewModel.clearCheckedShoppingItems()
                                showCleanupMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.DeleteSweep, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("📦 Alle abgehakten ins Lager einräumen") },
                            onClick = {
                                viewModel.transferAllCheckedShoppingItemsToInventory()
                                showCleanupMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Archive, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("📋 In Zwischenablage kopieren") },
                            onClick = {
                                viewModel.copyShoppingListToClipboard(context, items)
                                showCleanupMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
                        )
                    }
                }
            }
        }

        // --- 2. LIVE-KASSENSTURZ & BUDGET CARD ---
        LiveBudgetCard(
            totalCost = totalCost,
            openCost = openCost,
            inCartCost = inCartCost,
            checkedCount = checkedItems.size,
            totalCount = items.size,
            isSupermarketMode = isSupermarketMode,
            themeColor = viewModel.themeColor.value
        )

        Spacer(Modifier.height(8.dp))

        // --- 3. INPUT FIELD (If in standard mode) ---
        if (!isSupermarketMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newItemInput,
                    onValueChange = { newItemInput = it },
                    label = { Text("Artikel hinzufügen (z.B. Milch, 2x Butter)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (newItemInput.isNotBlank()) {
                            viewModel.addQuickInputShoppingItems(newItemInput)
                            newItemInput = ""
                        }
                    }),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        if (newItemInput.isNotBlank()) {
                            IconButton(onClick = { newItemInput = "" }) {
                                Icon(Icons.Default.Close, "Löschen")
                            }
                        }
                    }
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (newItemInput.isNotBlank()) {
                            viewModel.addQuickInputShoppingItems(newItemInput)
                            newItemInput = ""
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Icon(Icons.Default.Add, "Hinzufügen")
                }
            }
        }

        // --- 4. REFILL SUGGESTIONS (CHIP ROW) ---
        if (refillSuggestions.isNotEmpty() && !isSupermarketMode) {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Icon(
                    Icons.Default.Autorenew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = viewModel.themeColor.value
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Nachfüllen (Aufgebraucht & Niedrig):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                items(refillSuggestions, key = { it.first }) { (name, unit) ->
                    SuggestionChip(
                        onClick = {
                            viewModel.addToShoppingList(ShoppingItem(name = name, unit = unit))
                        },
                        label = { Text(name, style = MaterialTheme.typography.bodySmall) },
                        icon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp)) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // --- 5. SHOPPING LIST MAIN CONTENT ---
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.ShoppingCart,
                        null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Deine Einkaufsliste ist leer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Füge Artikel oben ein oder wähle Nachfüll-Vorschläge.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // A) UNCHECKED ITEMS GROUPED BY AISLE/CATEGORY
                var stationIndex = 1
                groupedUnchecked.forEach { (category, categoryItems) ->
                    val currentStation = stationIndex++
                    item(key = "header_${category.name}") {
                        AisleCategoryHeader(
                            category = category,
                            stationNumber = currentStation,
                            totalStations = groupedUnchecked.size,
                            itemCount = categoryItems.size,
                            themeColor = viewModel.themeColor.value
                        )
                    }

                    items(categoryItems, key = { it.id }) { item ->
                        ModernShoppingItemCard(
                            item = item,
                            viewModel = viewModel,
                            isSupermarketMode = isSupermarketMode,
                            onToggle = {
                                if (!item.isChecked) {
                                    // When checking off, trigger "Gekauft & Einräumen" dialog prompt
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    itemToTransfer = item
                                } else {
                                    viewModel.toggleShoppingItem(item)
                                }
                            },
                            onEdit = { itemToEdit = item }
                        )
                    }
                }

                // B) CHECKED / COMPLETED ITEMS SECTION (COLLAPSIBLE)
                if (checkedItems.isNotEmpty()) {
                    item(key = "checked_header") {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { isCheckedSectionExpanded = !isCheckedSectionExpanded }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isCheckedSectionExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                    null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Im Einkaufswagen (${checkedItems.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { viewModel.clearCheckedShoppingItems() }) {
                                    Text("Aufräumen", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    if (isCheckedSectionExpanded) {
                        items(checkedItems, key = { it.id }) { item ->
                            ModernShoppingItemCard(
                                item = item,
                                viewModel = viewModel,
                                isSupermarketMode = isSupermarketMode,
                                onToggle = { viewModel.toggleShoppingItem(item) },
                                onEdit = { itemToEdit = item }
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // --- DIALOGS ---

    // 1. "Gekauft & Einräumen" Transfer Dialog
    itemToTransfer?.let { item ->
        TransferToInventoryDialog(
            item = item,
            onDismiss = { itemToTransfer = null },
            onTransfer = { location, customDays ->
                viewModel.transferShoppingItemToInventory(item, location, customDays)
                itemToTransfer = null
            },
            onJustCheckOff = {
                viewModel.toggleShoppingItem(item)
                itemToTransfer = null
            }
        )
    }

    // 2. Edit Item Dialog (Notes, Price, Urgency)
    itemToEdit?.let { item ->
        EditShoppingItemDialog(
            item = item,
            viewModel = viewModel,
            onDismiss = { itemToEdit = null }
        )
    }

    // 3. Quick Input Dialog (Comma separated / WhatsApp style)
    if (showQuickInputDialog) {
        QuickInputDialog(
            onDismiss = { showQuickInputDialog = false },
            onConfirm = { text ->
                viewModel.addQuickInputShoppingItems(text)
                showQuickInputDialog = false
            }
        )
    }
}

// --- SUB-COMPONENTS ---

@Composable
fun LiveBudgetCard(
    totalCost: Double,
    openCost: Double,
    inCartCost: Double,
    checkedCount: Int,
    totalCount: Int,
    isSupermarketMode: Boolean,
    themeColor: Color
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSupermarketMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Calculate,
                        contentDescription = null,
                        tint = if (isSupermarketMode) MaterialTheme.colorScheme.primary else themeColor
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isSupermarketMode) "Einkaufstour-Kassensturz" else "Geschätzter Einkaufswert",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (totalCost > 0) {
                    Text(
                        String.format(Locale.GERMANY, "ca. %.2f €", totalCost),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSupermarketMode) MaterialTheme.colorScheme.primary else themeColor
                    )
                } else {
                    Text(
                        "Keine Preise hinterlegt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (totalCount > 0) {
                Spacer(Modifier.height(8.dp))
                val progress = (checkedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (isSupermarketMode) MaterialTheme.colorScheme.primary else themeColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "$checkedCount von $totalCount Artikeln (${(progress * 100).toInt()}%) im Wagen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isSupermarketMode && totalCost > 0) {
                        Text(
                            String.format(Locale.GERMANY, "Wagen: %.2f € | Offen: %.2f €", inCartCost, openCost),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AisleCategoryHeader(
    category: FoodCategory,
    stationNumber: Int,
    totalStations: Int,
    itemCount: Int,
    themeColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = themeColor.copy(alpha = 0.15f),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(category.icon, null, tint = themeColor, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    category.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = themeColor
                )
                Text(
                    "Supermarkt-Station $stationNumber von $totalStations",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Badge(containerColor = themeColor.copy(alpha = 0.2f), contentColor = themeColor) {
            Text("$itemCount", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
        }
    }
}

@Composable
fun ModernShoppingItemCard(
    item: ShoppingItem,
    viewModel: FridgeViewModel,
    isSupermarketMode: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    val urgencyColor = when (item.urgency) {
        "URGENT" -> Color(0xFFE53935) // Red
        "STOCK" -> Color(0xFF757575)  // Gray
        else -> Color.Transparent
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (urgencyColor != Color.Transparent && !item.isChecked) {
                    Modifier.border(1.5.dp, urgencyColor.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                } else Modifier
            ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (item.isChecked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(if (isSupermarketMode) 16.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox
            Checkbox(
                checked = item.isChecked,
                onCheckedChange = { onToggle() },
                modifier = if (isSupermarketMode) Modifier.size(28.dp) else Modifier
            )

            Spacer(Modifier.width(8.dp))

            // Item Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.urgency == "URGENT" && !item.isChecked) {
                        Text("🔴 ", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        item.name,
                        style = if (isSupermarketMode) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                        fontWeight = if (item.isChecked) FontWeight.Normal else FontWeight.Bold,
                        textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (item.quantity > 1 || item.unit != "Stk.") {
                        Text(
                            "${item.quantity} ${item.unit}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (!item.note.isNullOrBlank()) {
                        Text(
                            "📝 ${item.note}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (item.priceEstimate > 0) {
                        Text(
                            String.format(Locale.GERMANY, "ca. %.2f €", item.priceEstimate * item.quantity),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // Controls (Quantity Stepper & Edit Button in standard mode)
            if (!isSupermarketMode && !item.isChecked) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.updateShoppingItemQuantity(item, item.quantity - 1) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.RemoveCircleOutline, "Minus", modifier = Modifier.size(20.dp))
                    }

                    Text(
                        "${item.quantity}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    IconButton(
                        onClick = { viewModel.updateShoppingItemQuantity(item, item.quantity + 1) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.AddCircleOutline, "Plus", modifier = Modifier.size(20.dp))
                    }

                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, "Bearbeiten", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (isSupermarketMode && !item.isChecked) {
                // Large "Gekauft" button in Supermarket mode
                IconButton(onClick = onToggle) {
                    Icon(
                        Icons.Default.CheckCircle,
                        "Abhaken",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            if (item.isChecked) {
                IconButton(
                    onClick = { viewModel.removeFromShoppingList(item) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Delete, "Löschen", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// --- DIALOG COMPOSABLES ---

@Composable
fun TransferToInventoryDialog(
    item: ShoppingItem,
    onDismiss: () -> Unit,
    onTransfer: (targetLocation: String, customExpiryDays: Int?) -> Unit,
    onJustCheckOff: () -> Unit
) {
    var selectedLocation by remember { mutableStateOf("Kühlschrank") }
    val detectedCategory = remember(item.name) { CategoryDetector.detectCategory(item.name) }
    var expiryDays by remember { mutableStateOf(detectedCategory.expiryDays) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Gekauft – In den Bestand einräumen?", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    "Soll '${item.name}' (${item.quantity} ${item.unit}) direkt mit Standard-MHD in deine Vorräte übernommen werden?",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(12.dp))

                Text("Lagerort wählen:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("Kühlschrank", "Gefrierfach", "Vorratskammer").forEach { loc ->
                        FilterChip(
                            selected = selectedLocation == loc,
                            onClick = { selectedLocation = loc },
                            label = { Text(loc, style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Standard-Haltbarkeit:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "+ $expiryDays Tage (${detectedCategory.displayName})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onTransfer(selectedLocation, expiryDays) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Archive, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("In Bestand einräumen")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onJustCheckOff,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Nur abhaken")
            }
        }
    )
}

@Composable
fun EditShoppingItemDialog(
    item: ShoppingItem,
    viewModel: FridgeViewModel,
    onDismiss: () -> Unit
) {
    var noteInput by remember { mutableStateOf(item.note ?: "") }
    var priceInput by remember { mutableStateOf(if (item.priceEstimate > 0) String.format(Locale.US, "%.2f", item.priceEstimate) else "") }
    var selectedUrgency by remember { mutableStateOf(item.urgency) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Artikel bearbeiten: ${item.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Notiz
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Notiz (z.B. 'nur Bio', 'REWE Hausmarke')") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Geschätzter Einzelpreis
                OutlinedTextField(
                    value = priceInput,
                    onValueChange = { priceInput = it },
                    label = { Text("Geschätzter Preis in €") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Dringlichkeit (Urgency Traffic Light)
                Text("Dringlichkeit:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = selectedUrgency == "URGENT",
                        onClick = { selectedUrgency = "URGENT" },
                        label = { Text("🔴 Dringend") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedUrgency == "NORMAL",
                        onClick = { selectedUrgency = "NORMAL" },
                        label = { Text("🟡 Normal") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedUrgency == "STOCK",
                        onClick = { selectedUrgency = "STOCK" },
                        label = { Text("⚪ Auf Vorrat") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = priceInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                    viewModel.updateShoppingItemNote(item, noteInput)
                    viewModel.updateShoppingItemPrice(item, price)
                    viewModel.updateShoppingItemUrgency(item, selectedUrgency)
                    onDismiss()
                }
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
fun QuickInputDialog(
    onDismiss: () -> Unit,
    onConfirm: (text: String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Schnelleingabe (Mehrere Artikel)", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    "Mehrere Artikel durch Komma oder neue Zeilen getrennt eingeben:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("z.B. Milch, Butter, 6 Eier, 2x Saft, 500g Hackfleisch") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onConfirm(inputText)
                    }
                },
                enabled = inputText.isNotBlank()
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("Alle hinzufügen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
