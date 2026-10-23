package com.example.myapplication.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.myapplication.FridgeViewModel
import com.example.myapplication.QrLabelActivity
import com.example.myapplication.ui.FilterAndSortSection
import com.example.myapplication.ui.components.FridgeItemCard
import java.util.concurrent.TimeUnit

@Composable
fun FridgeScreen(viewModel: FridgeViewModel) {
    val context = LocalContext.current
    val allItems by viewModel.allFridgeItems.collectAsState(initial = emptyList())
    val favoriteIconName by viewModel.favoriteIconName

    val displayedItems = remember(
        allItems, 
        viewModel.currentHouseholdId.value,
        viewModel.searchInput.value,
        viewModel.filterExpiryDays.intValue,
        viewModel.filterCategory.value,
        viewModel.filterLocation.value,
        viewModel.sortOrder.value
    ) {
        allItems.filter { item ->
            val matchesHousehold = item.householdId == viewModel.currentHouseholdId.value || viewModel.currentHouseholdId.value == "default"
            val matchesSearch = viewModel.searchInput.value.isBlank() || item.name.contains(viewModel.searchInput.value, ignoreCase = true)
            val matchesExpiry = when (viewModel.filterExpiryDays.intValue) {
                0 -> true
                -1 -> item.expiryDate != null && item.expiryDate!! < System.currentTimeMillis()
                else -> item.expiryDate != null && (item.expiryDate!! - System.currentTimeMillis()) < TimeUnit.DAYS.toMillis(viewModel.filterExpiryDays.intValue.toLong())
            }
            val matchesCategoryFilter = when (viewModel.filterCategory.value) {
                null, "Alle Kategorien" -> true
                "Protein" -> item.protein >= 10.0
                "LowCarb" -> item.sugar in 0.0..5.0
                "Vegan" -> item.isVegan
                else -> item.category.equals(viewModel.filterCategory.value, ignoreCase = true)
            }
            val locFilter = viewModel.filterLocation.value
            val matchesLocation = if (locFilter == "BALD_ABLAUFEND") {
                item.expiryDate != null && (item.expiryDate!! - System.currentTimeMillis()) < TimeUnit.DAYS.toMillis(3)
            } else {
                locFilter == null || item.storageLocation.equals(locFilter, ignoreCase = true)
            }
            matchesHousehold && matchesSearch && matchesExpiry && matchesCategoryFilter && matchesLocation
        }.let { filtered ->
            when (viewModel.sortOrder.value) {
                "name" -> filtered.sortedBy { it.name.lowercase() }
                "price" -> filtered.sortedByDescending { it.price }
                "category" -> filtered.sortedBy { it.category }
                "protein" -> filtered.sortedByDescending { it.protein }
                else -> filtered.sortedBy { it.expiryDate ?: Long.MAX_VALUE }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FilterAndSortSection(viewModel)
        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(displayedItems, key = { it.id }) { item ->
                FridgeItemCard(
                    item = item,
                    isSelected = viewModel.selectedItems.value.contains(item.id),
                    isCompact = viewModel.isCompactMode.value,
                    cornerRadius = viewModel.cardCornerRadius.intValue,
                    onDelete = { viewModel.removeItem(item) },
                    onEdit = { /* Set state in parent or use navigation */ },
                    onConsume = { viewModel.consumeItem(item) },
                    onWaste = { viewModel.wasteItem(item) },
                    onToggleFav = { viewModel.toggleFavorite(item) },
                    onLongClick = { viewModel.toggleSelection(item.id) },
                    onInfo = { /* Set state in parent */ },
                    onImageClick = { /* Set state in parent */ },
                    onPrintQr = { 
                        val intent = Intent(context, QrLabelActivity::class.java).apply {
                            putExtra("QR_CONTENT", "ITEM:${item.id}")
                            putExtra("ITEM_NAME", item.name)
                        }
                        context.startActivity(intent)
                    },
                    onOpen = { viewModel.markAsOpened(item) },
                    favoriteIconName = favoriteIconName,
                    onFreeze = { viewModel.freezeItem(item) },
                    onDefrost = { viewModel.defrostItem(item) }
                )
            }
        }
    }
}
