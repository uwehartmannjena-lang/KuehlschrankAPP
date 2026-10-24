package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.FridgeViewModel
import com.example.myapplication.SettingsViewModel
import com.example.myapplication.data.UserLearnedCorrection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: FridgeViewModel? = null,
    settingsViewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onStartTour: () -> Unit = {},
    onExportBackup: () -> Unit = {},
    onOpenWebCompanion: () -> Unit = {},
    onClearInventory: () -> Unit = {}
) {
    var expiryDays by remember { mutableIntStateOf(viewModel?.expiryWarningDays?.intValue ?: 3) }
    var householdSize by remember { mutableIntStateOf(viewModel?.householdSize?.intValue ?: 1) }
    var compactMode by remember { mutableStateOf(viewModel?.isCompactMode?.value ?: false) }
    var cornerRadius by remember { mutableIntStateOf(viewModel?.cardCornerRadius?.intValue ?: 12) }
    var favoriteIconName by remember { mutableStateOf(viewModel?.favoriteIconName?.value ?: "Star") }
    var favoriteIconColor by remember { mutableStateOf(viewModel?.favoriteIconColor?.value ?: Color(0xFFFFB300)) }

    val learnedCorrections by settingsViewModel.learnedCorrections.collectAsState()
    val scanIntervalMinutes by settingsViewModel.scanIntervalMinutes.collectAsState()

    var showLearnedDialog by remember { mutableStateOf(false) }
    var editingCorrection by remember { mutableStateOf<UserLearnedCorrection?>(null) }
    var editedCorrectionName by remember { mutableStateOf("") }
    var intervalDropdownExpanded by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Settings, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Einstellungen", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onOpenHelp,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("SettingsIconBottom_0")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Handbuch & FAQ",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = onStartTour,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("SettingsIconBottom_1")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Tour starten",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = onExportBackup,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("SettingsIconBottom_2")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = "Backup",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenWebCompanion,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("SettingsIconBottom_3")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = "PC Web Companion",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = onClearInventory,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("SettingsIconBottom_4")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Inventar leeren",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Palette, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Design & Favoriten-Symbole", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }

                Spacer(Modifier.height(8.dp))
                Text("Karten-Abrundung: $cornerRadius dp", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = cornerRadius.toFloat(),
                    onValueChange = { cornerRadius = it.toInt(); viewModel?.setCardCornerRadius(cornerRadius) },
                    valueRange = 4f..24f
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Kompaktmodus aktivieren", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = compactMode,
                        onCheckedChange = { compactMode = it; viewModel?.setCompactMode(it) }
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text("Favoriten-Symbol (15 Optionen):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(fifteenIcons) { (iconKey, vector) ->
                        val isSelected = (favoriteIconName == iconKey)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .border(if (isSelected) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                .clickable {
                                    favoriteIconName = iconKey
                                    viewModel?.setFavoriteIcon(iconKey)
                                }
                        ) {
                            Icon(
                                imageVector = vector,
                                contentDescription = iconKey,
                                tint = if (isSelected) favoriteIconColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                Text("MHD-Vorwarnung: ab $expiryDays Tage vor Ablauf", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = expiryDays.toFloat(),
                    onValueChange = { expiryDays = it.toInt(); viewModel?.setExpiryWarningDays(expiryDays) },
                    valueRange = 1f..14f
                )

                Text("Haushaltsgröße: $householdSize Personen", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = householdSize.toFloat(),
                    onValueChange = { householdSize = it.toInt(); viewModel?.setHouseholdSize(householdSize) },
                    valueRange = 1f..10f
                )

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // Kassenbon-Hintergrundscan & Gelernte Begriffe
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ReceiptLong, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Kassenbon-Scan & Gelernte Begriffe", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))

                Text("Scan-Intervall für Kassenbon-Ordner:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    val currentLabel = when (scanIntervalMinutes) {
                        0L -> "Manuell (kein Hintergrund-Scan)"
                        60L -> "1 Stunde"
                        1440L -> "24 Stunden"
                        else -> "$scanIntervalMinutes Minuten"
                    }
                    OutlinedButton(
                        onClick = { intervalDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(currentLabel)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = intervalDropdownExpanded,
                        onDismissRequest = { intervalDropdownExpanded = false }
                    ) {
                        listOf(
                            0L to "Manuell (kein Hintergrund-Scan)",
                            60L to "1 Stunde",
                            1440L to "24 Stunden"
                        ).forEach { (minutes, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    settingsViewModel.setScanInterval(minutes)
                                    intervalDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { showLearnedDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.School, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Gelernte Begriffe verwalten (${learnedCorrections.size})")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showLearnedDialog) {
        AlertDialog(
            onDismissRequest = { showLearnedDialog = false },
            title = {
                Text("Gelernte Kassenbon-Begriffe (${learnedCorrections.size})", fontWeight = FontWeight.Bold)
            },
            text = {
                if (learnedCorrections.isEmpty()) {
                    Text("Noch keine Begriffe gelernt. Beim Kassenbon-Scan korrigierte Begriffe werden hier automatisch hinterlegt.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(learnedCorrections, key = { it.receiptRawText }) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.receiptRawText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "➔ ${item.correctedName}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            editingCorrection = item
                                            editedCorrectionName = item.correctedName
                                        }
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Bearbeiten", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(
                                        onClick = { settingsViewModel.deleteCorrection(item) }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLearnedDialog = false }) {
                    Text("Schließen")
                }
            }
        )
    }

    editingCorrection?.let { correction ->
        AlertDialog(
            onDismissRequest = { editingCorrection = null },
            title = { Text("Begriff bearbeiten") },
            text = {
                Column {
                    Text("Original-OCR:", style = MaterialTheme.typography.bodySmall)
                    Text(correction.receiptRawText, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editedCorrectionName,
                        onValueChange = { editedCorrectionName = it },
                        label = { Text("Korrigierter Produktname") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        settingsViewModel.updateCorrection(correction.receiptRawText, editedCorrectionName)
                        editingCorrection = null
                    },
                    enabled = editedCorrectionName.isNotBlank()
                ) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCorrection = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}
