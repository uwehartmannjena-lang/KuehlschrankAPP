package com.example.myapplication.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.myapplication.FridgeViewModel
import com.example.myapplication.data.FridgeItem
import java.util.concurrent.TimeUnit

enum class StorageZoneType(val title: String) {
    FRIDGE("Kühlschrank"),
    FREEZER("Gefrierfach"),
    PANTRY("Vorratskammer")
}

@Composable
fun ShelfView(
    items: List<FridgeItem>,
    viewModel: FridgeViewModel,
    onEdit: (FridgeItem) -> Unit,
    onLongClick: (FridgeItem) -> Unit
) {
    // Animation: "Tür öffnen" Animation (Tür schwingt auf -> Fade-In + Horizontal Slide + Scale)
    var isDoorOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isDoorOpen = true
    }

    // ScrollState für 3D-Parallax-Effekt beim Scrollen durch die Fächer
    val scrollState = rememberLazyListState()

    // Regale & Fächer gruppieren
    val shelves = remember(items) {
        val mapped = items.groupBy { item ->
            when (item.storageLocation.lowercase()) {
                "gefrierfach" -> Pair(StorageZoneType.FREEZER, "Gefrierfach 🧊")
                "vorratskammer" -> Pair(StorageZoneType.PANTRY, "Vorratskammer 📦")
                else -> {
                    val label = when {
                        item.category.contains("gemüse", true) || item.category.contains("obst", true) -> "Gemüsefach 🥦"
                        item.category.contains("getränke", true) || item.category.contains("soße", true) -> "Türfächer 🍾"
                        item.category.contains("fleisch", true) || item.category.contains("fisch", true) -> "Unteres Fach (Kalt) 🥩"
                        item.category.contains("milch", true) || item.category.contains("käse", true) -> "Mittleres Fach 🧀"
                        else -> "Oberes Fach 🥛"
                    }
                    Pair(StorageZoneType.FRIDGE, label)
                }
            }
        }
        val order = listOf(
            "Oberes Fach 🥛", "Mittleres Fach 🧀", "Unteres Fach (Kalt) 🥩", "Gemüsefach 🥦", "Türfächer 🍾",
            "Gefrierfach 🧊", "Vorratskammer 📦"
        )
        mapped.toSortedMap(compareBy { order.indexOf(it.second).takeIf { idx -> idx != -1 } ?: 99 })
    }

    AnimatedVisibility(
        visible = isDoorOpen,
        enter = fadeIn(animationSpec = tween(650, easing = LinearOutSlowInEasing)) +
                slideInHorizontally(initialOffsetX = { -it / 2 }, animationSpec = tween(650, easing = FastOutSlowInEasing)) +
                scaleIn(initialScale = 0.92f, animationSpec = tween(650, easing = FastOutSlowInEasing))
    ) {
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.height(2.dp)) }

            shelves.forEach { (zoneInfo, shelfItems) ->
                val (zoneType, shelfName) = zoneInfo
                item(key = "shelf_$shelfName") {
                    PhotorealisticShelfContainer(
                        zoneType = zoneType,
                        shelfName = shelfName,
                        shelfItems = shelfItems,
                        scrollState = scrollState,
                        viewModel = viewModel,
                        onEdit = onEdit,
                        onLongClick = onLongClick
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun PhotorealisticShelfContainer(
    zoneType: StorageZoneType,
    shelfName: String,
    shelfItems: List<FridgeItem>,
    scrollState: LazyListState,
    viewModel: FridgeViewModel,
    onEdit: (FridgeItem) -> Unit,
    onLongClick: (FridgeItem) -> Unit
) {
    // Parallax-Versatz der Rückwand für räumliche 3D-Tiefe
    val parallaxOffsetY = remember(scrollState.firstVisibleItemScrollOffset) {
        (scrollState.firstVisibleItemScrollOffset * 0.08f)
    }

    // Gestaltungsstile je nach Bereich (Kühlschrank, Gefrierfach, Vorratskammer)
    val (bgBrush, borderColor, titleColor, shelfBoardBrush, shelfBorderColor, topGlowColor) = when (zoneType) {
        StorageZoneType.FREEZER -> Tuple6(
            Brush.verticalGradient(listOf(Color(0xFFE0F7FA), Color(0xFFB2EBF2), Color(0xFF00838F))),
            Color(0xFF00ACC1),
            Color(0xFF004D40),
            Brush.verticalGradient(listOf(Color(0xEEECF9FF), Color(0xFF80DEEA), Color(0xFF00838F))),
            Color(0xFF4DD0E1),
            Color(0xFF80DEEA)
        )
        StorageZoneType.PANTRY -> Tuple6(
            Brush.verticalGradient(listOf(Color(0xFF3E2723), Color(0xFF4E342E), Color(0xFF271C19))),
            Color(0xFF8D6E63),
            Color(0xFFFFECB3),
            Brush.verticalGradient(listOf(Color(0xFFA1887F), Color(0xFF6D4C41), Color(0xFF3E2723))),
            Color(0xFFD7CCC8),
            Color(0xFFFFD54F)
        )
        StorageZoneType.FRIDGE -> Tuple6(
            Brush.verticalGradient(listOf(Color(0xFFEBF3F5), Color(0xFFD0DDE1), Color(0xFFB8C8CE))),
            Color(0xFF90A4AE),
            Color(0xFF263238),
            Brush.verticalGradient(listOf(Color(0xDDFFFFFF), Color(0xFFB0BEC5), Color(0xFF78909C))),
            Color(0x88CFD8DC),
            Color(0xFF80DEEA)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .graphicsLayer { translationY = -parallaxOffsetY }
            .background(bgBrush)
            .border(2.dp, borderColor, RoundedCornerShape(18.dp))
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // LED / Licht-Effektleiste oben
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .shadow(4.dp, RoundedCornerShape(3.dp)),
                shape = RoundedCornerShape(3.dp),
                color = topGlowColor
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(topGlowColor.copy(alpha = 0.4f), Color.White, topGlowColor.copy(alpha = 0.4f))
                            )
                        )
                )
            }

            Spacer(Modifier.height(6.dp))

            // Fach-Titel & Anzahl
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = shelfName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                Text(
                    text = "${shelfItems.size} Artikel",
                    style = MaterialTheme.typography.labelSmall,
                    color = titleColor.copy(alpha = 0.8f)
                )
            }

            Spacer(Modifier.height(4.dp))

            // Artikel exakt auf dem Regalboden platziert
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(shelfItems, key = { it.id }) { item ->
                    ShelfProductItem(
                        item = item,
                        viewModel = viewModel,
                        onEdit = { onEdit(item) },
                        onLongClick = { onLongClick(item) }
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            // Dynamischer Schlagschatten der Artikel auf den Regalboden
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0x66000000), Color.Transparent)
                        )
                    )
            )

            // Optischer Regalboden (Glas, Eis oder Holzplanke)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .shadow(8.dp, RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(shelfBoardBrush)
                    .border(1.dp, shelfBorderColor, RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
fun ShelfProductItem(
    item: FridgeItem,
    viewModel: FridgeViewModel,
    onEdit: () -> Unit,
    onLongClick: () -> Unit
) {
    val daysLeft = remember(item.expiryDate) {
        item.expiryDate?.let {
            TimeUnit.MILLISECONDS.toDays(it - System.currentTimeMillis()).toInt()
        }
    }

    val statusColor = when {
        daysLeft == null -> Color.Gray
        daysLeft < 0 -> Color(0xFFE53935)
        daysLeft <= 3 -> Color(0xFFFB8C00)
        else -> Color(0xFF4CAF50)
    }

    val initial = remember(item.name) {
        item.name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    }

    Surface(
        onClick = onEdit,
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.94f),
        shadowElevation = 8.dp,
        modifier = Modifier
            .width(110.dp)
            .wrapContentHeight()
            .graphicsLayer {
                shadowElevation = 8.dp.toPx()
                shape = RoundedCornerShape(12.dp)
                clip = true
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            // Status-Punkt & Menge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    text = "${item.quantity}x",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF37474F)
                )
            }

            Spacer(Modifier.height(6.dp))

            // Produktbild / Anfangsbuchstabe
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFECEFF1)),
                contentAlignment = Alignment.Center
            ) {
                if (!item.imageUrl.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = {
                            Text(
                                text = initial,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                } else {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Name
            Text(
                text = item.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF263238),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // MHD Status
            Text(
                text = when {
                    daysLeft == null -> "Kein MHD"
                    daysLeft < 0 -> "Abgelaufen!"
                    daysLeft == 0 -> "Heute fällig"
                    else -> "Noch $daysLeft T."
                },
                fontSize = 10.sp,
                color = statusColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// Hilfs-Tupel für sauberes Layout-Styling
private data class Tuple6<A, B, C, D, E, F>(
    val a: A, val b: B, val c: C, val d: D, val e: E, val f: F
)
