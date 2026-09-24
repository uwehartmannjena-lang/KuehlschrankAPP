package com.example.myapplication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.FridgeViewModel
import com.example.myapplication.data.FridgeItem

@Composable
fun ShelfView(
    items: List<FridgeItem>,
    viewModel: FridgeViewModel,
    onEdit: (FridgeItem) -> Unit,
    onLongClick: (FridgeItem) -> Unit
) {
    // Fächer simulieren anhand des Lagerorts oder Kategorien
    val shelves = remember(items) {
        val mapped = items.groupBy { item ->
            when (item.storageLocation.lowercase()) {
                "gefrierfach" -> "Gefrierfach"
                "vorratskammer" -> "Vorratskammer"
                else -> {
                    // Falls im Kühlschrank, teilen wir nach Kategorie auf für eine Regal-Optik
                    when {
                        item.category.contains("gemüse", true) || item.category.contains("obst", true) -> "Gemüsefach"
                        item.category.contains("getränke", true) || item.category.contains("soße", true) -> "Türfächer"
                        item.category.contains("fleisch", true) || item.category.contains("fisch", true) -> "Unteres Fach (Kalt)"
                        item.category.contains("milch", true) || item.category.contains("käse", true) -> "Mittleres Fach"
                        else -> "Oberes Fach"
                    }
                }
            }
        }
        val order = listOf("Oberes Fach", "Mittleres Fach", "Unteres Fach (Kalt)", "Gemüsefach", "Türfächer", "Gefrierfach", "Vorratskammer")
        mapped.toSortedMap(compareBy { order.indexOf(it).takeIf { idx -> idx != -1 } ?: 99 })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        
        shelves.forEach { (shelfName, shelfItems) ->
            item(key = "header_$shelfName") {
                Column {
                    Text(
                        text = shelfName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    )
                    // "Brett" Darstellung
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF8D6E63)) // Holz- oder Fach-Farbe
                    )
                }
            }

            items(shelfItems, key = { it.id }) { item ->
                ModernFridgeItemCard(
                    item = item,
                    viewModel = viewModel,
                    isSelected = viewModel.selectedItems.value.contains(item.id),
                    onEdit = { onEdit(item) },
                    onLongClick = { onLongClick(item) }
                )
            }
        }
        
        item { Spacer(Modifier.height(80.dp)) }
    }
}
