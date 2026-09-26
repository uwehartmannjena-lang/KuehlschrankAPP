package com.example.myapplication

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import com.example.myapplication.data.Product
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptPreviewScreen(
    viewModel: ReceiptScanViewModel,
    forceRecompose: Int = 0,
    onConfirm: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val scannedProducts by viewModel.scannedProducts.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    var editingProductIndex by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kassenbon Vorschau 🧾 (v$forceRecompose)", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onConfirm) {
                        Icon(Icons.Default.Check, contentDescription = "Übernehmen")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "${scannedProducts.size} erkannte Artikel",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(scannedProducts) { index, product ->
                    var expanded by remember { mutableStateOf(false) }
                    var itemText by remember(product.name) { mutableStateOf(product.name) }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Direct ExposedDropdownMenuBox for editing/displaying item name
                                Box(modifier = Modifier.weight(1f)) {
                                    ExposedDropdownMenuBox(
                                        expanded = expanded,
                                        onExpandedChange = { expanded = it },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedTextField(
                                            value = itemText,
                                            onValueChange = { newText ->
                                                itemText = newText
                                                expanded = true
                                                viewModel.searchSuggestions(newText)
                                                viewModel.updateProductName(index, newText)
                                            },
                                            label = { Text("Artikelname") },
                                            singleLine = true,
                                            trailingIcon = {
                                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                            },
                                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .menuAnchor()
                                        )

                                        if (suggestions.isNotEmpty()) {
                                            ExposedDropdownMenu(
                                                expanded = expanded,
                                                onDismissRequest = { expanded = false },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                suggestions.forEach { suggestion ->
                                                    DropdownMenuItem(
                                                        text = { Text(suggestion, fontWeight = FontWeight.Medium) },
                                                        onClick = {
                                                            itemText = suggestion
                                                            viewModel.updateProductName(index, suggestion)
                                                            expanded = false
                                                        },
                                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Bon-Verlinkung Icon
                                    if (!product.receiptUri.isNullOrBlank()) {
                                        IconButton(onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    data = Uri.parse(product.receiptUri)
                                                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.List,
                                                contentDescription = "Beleg anzeigen",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    IconButton(onClick = { editingProductIndex = index }) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Bearbeiten",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${product.quantity}x • ${String.format(Locale.GERMANY, "%.2f", product.price)} €",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                // Kaufdatum zwingend zeichnen
                                val formattedDate = if (product.purchaseDate != null && product.purchaseDate > 0L) {
                                    SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(product.purchaseDate))
                                } else {
                                    SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date())
                                }
                                Text(
                                    text = "Kaufdatum: $formattedDate",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                enabled = scannedProducts.isNotEmpty()
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Artikel in den Kühlschrank übernehmen")
            }
        }
    }

    editingProductIndex?.let { index ->
        if (index in scannedProducts.indices) {
            val product = scannedProducts[index]
            ArticleCorrectionDialog(
                product = product,
                viewModel = viewModel,
                onDismiss = { editingProductIndex = null },
                onSave = { newName ->
                    viewModel.updateProductName(index, newName)
                    editingProductIndex = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleCorrectionDialog(
    product: Product,
    viewModel: ReceiptScanViewModel,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val suggestions by viewModel.suggestions.collectAsState()
    val initialText = remember(product) { product.rawText ?: product.name }
    var textInput by remember { mutableStateOf(product.name) }
    var expanded by remember { mutableStateOf(false) }

    // Zwinge das ViewModel, direkt beim Öffnen erste Vorschläge zu laden
    LaunchedEffect(product) {
        viewModel.searchSuggestions(textInput)
    }

    // ACHTUNG: Hier ist der Fix! Das Menü darf nur offen sein, wenn es auch Vorschläge gibt.
    // Wenn die suggestions-Liste sich ändert, prüfen wir, ob wir das Menü aufklappen müssen.
    LaunchedEffect(suggestions) {
        if (suggestions.isNotEmpty() && textInput.isNotBlank()) {
            expanded = true
        } else {
            expanded = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Artikel korrigieren ✏️", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (initialText.isNotBlank()) {
                    Text(
                        text = "Original OCR: \"$initialText\"",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = {
                        // Verhindere, dass das Menü aufklappt, wenn es leer ist
                        if (suggestions.isNotEmpty()) {
                            expanded = it
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { newText ->
                            textInput = newText
                            // Suche im ViewModel anstoßen
                            viewModel.searchSuggestions(newText)
                        },
                        label = { Text("Artikelname") },
                        singleLine = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor() // Wichtig: Hiermit wird das Textfeld an das Menü gebunden
                    )

                    // Das Menü selbst
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        suggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion, fontWeight = FontWeight.Medium) },
                                onClick = {
                                    textInput = suggestion
                                    expanded = false
                                    // Damit das Menü nicht direkt wieder aufklappt, Suchfeld leeren
                                    viewModel.searchSuggestions("")
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(textInput) },
                enabled = textInput.isNotBlank()
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Speichern & Lernen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}