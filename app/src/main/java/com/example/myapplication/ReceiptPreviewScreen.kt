package com.example.myapplication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.Product
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptPreviewScreen(
    viewModel: ReceiptScanViewModel,
    onConfirm: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val scannedProducts by viewModel.scannedProducts.collectAsState()
    var editingProductIndex by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kassenbon Vorschau", fontWeight = FontWeight.Bold) },
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
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = product.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "${product.quantity}x • ${String.format(Locale.GERMANY, "%.2f", product.price)} €",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val pDate = product.purchaseDate ?: System.currentTimeMillis()
                                val dateFormatted = java.text.SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(java.util.Date(pDate))
                                Text(
                                    text = "Kaufdatum: $dateFormatted",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                if (!product.receiptUri.isNullOrBlank()) {
                                    Text(
                                        text = "Beleg: ${product.receiptUri}",
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.outline
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
                onSave = { newName, category, unit, pDate, rUri ->
                    viewModel.updateProductName(index, newName, category, unit, pDate, rUri)
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
    onSave: (newName: String, category: String, unit: String, purchaseDate: Long?, receiptUri: String?) -> Unit
) {
    val suggestions by viewModel.suggestions.collectAsState()
    val initialText = remember(product) { product.rawText ?: product.name }
    var textInput by remember { mutableStateOf(product.name) }
    var categoryInput by remember { mutableStateOf(product.category) }
    var unitInput by remember { mutableStateOf(product.unit) }
    val initialDate = remember(product) {
        val millis = product.purchaseDate ?: System.currentTimeMillis()
        java.text.SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(java.util.Date(millis))
    }
    var dateInput by remember { mutableStateOf(initialDate) }
    var receiptUriInput by remember { mutableStateOf(product.receiptUri ?: "") }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(product) {
        viewModel.onQueryChanged(textInput)
    }

    LaunchedEffect(suggestions) {
        if (suggestions.isNotEmpty()) {
            expanded = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Artikel korrigieren", fontWeight = FontWeight.Bold) },
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
                    expanded = expanded && suggestions.isNotEmpty(),
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { newText ->
                            textInput = newText
                            expanded = true
                            viewModel.onQueryChanged(newText)
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

                    ExposedDropdownMenu(
                        expanded = expanded && suggestions.isNotEmpty(),
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        suggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(suggestion.cleanName, fontWeight = FontWeight.Medium)
                                        Text(
                                            "${suggestion.category} • ${suggestion.unit}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    textInput = suggestion.cleanName
                                    categoryInput = suggestion.category
                                    unitInput = suggestion.unit
                                    expanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = categoryInput,
                        onValueChange = { categoryInput = it },
                        label = { Text("Kategorie") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unitInput,
                        onValueChange = { unitInput = it },
                        label = { Text("Einheit") },
                        singleLine = true,
                        modifier = Modifier.width(90.dp)
                    )
                }

                OutlinedTextField(
                    value = dateInput,
                    onValueChange = { dateInput = it },
                    label = { Text("Kaufdatum (dd.MM.yyyy)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedDateMillis = try {
                        java.text.SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).parse(dateInput.trim())?.time
                    } catch (_: Exception) { null } ?: product.purchaseDate ?: System.currentTimeMillis()

                    onSave(
                        textInput.trim(),
                        categoryInput.trim(),
                        unitInput.trim(),
                        parsedDateMillis,
                        receiptUriInput.trim().takeIf { it.isNotBlank() }
                    )
                },
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
