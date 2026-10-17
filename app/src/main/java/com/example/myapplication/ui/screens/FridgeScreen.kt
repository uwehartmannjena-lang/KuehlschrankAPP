package com.example.myapplication.ui.screens

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
import com.example.myapplication.ui.components.FridgeItemCard
import com.example.myapplication.FilterAndSortSection
import com.example.myapplication.QrLabelActivity
import android.content.Intent

@Composable
fun FridgeScreen(viewModel: FridgeViewModel) {
    val context = LocalContext.current
    val displayedItems by viewModel.displayedItems.collectAsState(initial = emptyList())
    val favoriteIconName by viewModel.favoriteIconName

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
