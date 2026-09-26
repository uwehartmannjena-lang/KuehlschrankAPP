package com.example.myapplication

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
                title = { Text("Kassenbon Vorschau 🧾", fontWeight = FontWeight.Bold) },
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

    LaunchedEffect(product) {
        viewModel.searchSuggestions(textInput)
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
                    expanded = expanded && suggestions.isNotEmpty(),
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { newText ->
                            textInput = newText
                            expanded = suggestions.isNotEmpty()
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
                            .menuAnchor(MenuAnchorType.PrimaryEditable, true)
                    )

                    ExposedDropdownMenu(
                        expanded = expanded && suggestions.isNotEmpty(),
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        suggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion, fontWeight = FontWeight.Medium) },
                                onClick = {
                                    textInput = suggestion
                                    expanded = false
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
