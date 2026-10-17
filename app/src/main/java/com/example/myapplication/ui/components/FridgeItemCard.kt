package com.example.myapplication.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.FridgeItem
import com.example.myapplication.data.FoodCategory
import com.example.myapplication.getFallbackIconForItem
import com.example.myapplication.ExpiryLabel
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FridgeItemCard(
    item: FridgeItem, 
    isSelected: Boolean, 
    isCompact: Boolean, 
    cornerRadius: Int, 
    favoriteIconName: String = "Star", 
    onDelete: () -> Unit, 
    onEdit: () -> Unit, 
    onConsume: () -> Unit, 
    onWaste: () -> Unit, 
    onToggleFav: () -> Unit, 
    onLongClick: () -> Unit, 
    onFreeze: () -> Unit = {}, 
    onDefrost: () -> Unit = {}, 
    onDonate: () -> Unit = {}, 
    householdSize: Int = 1, 
    onInfo: () -> Unit = {}, 
    onImageClick: () -> Unit = {}, 
    onPrintQr: () -> Unit = {}, 
    onOpen: () -> Unit = {}
) {
    val days = item.expiryDate?.let { TimeUnit.MILLISECONDS.toDays(it - System.currentTimeMillis()).toInt() }
    val color = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        days == null -> MaterialTheme.colorScheme.surfaceVariant
        days < 0 -> Color(0xFFFFCDD2)
        days <= 3 -> Color(0xFFFFF9C4)
        else -> Color(0xFFC8E6C9)
    }
    val (icon, iconColor) = getFallbackIconForItem(item.name, item.category, item.storageLocation)

    val favIcon = when(favoriteIconName) {
        "Heart" -> Icons.Default.FavoriteBorder
        "Favorite" -> Icons.Default.Favorite
        "Bookmark" -> Icons.Default.Bookmark
        "PushPin" -> Icons.Default.PushPin
        "WorkspacePremium" -> Icons.Default.WorkspacePremium
        "LocalFireDepartment" -> Icons.Default.LocalFireDepartment
        "Lightbulb" -> Icons.Default.Lightbulb
        "CheckCircle" -> Icons.Default.CheckCircle
        "ShoppingBag" -> Icons.Default.ShoppingBag
        "LocalMall" -> Icons.Default.LocalMall
        "Loyalty" -> Icons.Default.Loyalty
        "Diamond" -> Icons.Default.Diamond
        "Celebration" -> Icons.Default.Celebration
        "Eco" -> Icons.Default.Eco
        else -> Icons.Default.Star
    }
    val favIconBorder = when(favoriteIconName) {
        "Heart" -> Icons.Default.FavoriteBorder
        "Favorite" -> Icons.Default.FavoriteBorder
        "Bookmark" -> Icons.Default.BookmarkBorder
        "PushPin" -> Icons.Default.PushPin
        "WorkspacePremium" -> Icons.Default.WorkspacePremium
        "LocalFireDepartment" -> Icons.Default.LocalFireDepartment
        "Lightbulb" -> Icons.Default.Lightbulb
        "CheckCircle" -> Icons.Default.CheckCircle
        "ShoppingBag" -> Icons.Default.ShoppingBag
        "LocalMall" -> Icons.Default.LocalMall
        "Loyalty" -> Icons.Default.Loyalty
        "Diamond" -> Icons.Default.Diamond
        "Celebration" -> Icons.Default.Celebration
        "Eco" -> Icons.Default.Eco
        else -> Icons.Default.StarBorder
    }

    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onEdit() }, onLongClick = { onLongClick() }),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(cornerRadius.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(if (isCompact) 8.dp else 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.imageUrl != null && !isCompact) {
                    androidx.compose.foundation.Image(
                        painter = coil.compose.rememberAsyncImagePainter(item.imageUrl),
                        contentDescription = null,
                        modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).clickable { onImageClick() },
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(icon, null, modifier = Modifier.size(if (isCompact) 24.dp else 32.dp), tint = iconColor)
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, fontWeight = FontWeight.Bold, fontSize = if (isCompact) 14.sp else 16.sp, modifier = Modifier.weight(1f))
                        if (item.openedDate != null) {
                            Badge(containerColor = Color.Blue) { Text("OFFEN", color = Color.White, fontSize = 9.sp) }
                            Spacer(Modifier.width(4.dp))
                        }
                        if (item.freezerDrawer > 0) {
                            Badge(containerColor = Color(0xFF03A9F4)) { Text("FACH ${item.freezerDrawer}", color = Color.White, fontSize = 9.sp) }
                            Spacer(Modifier.width(4.dp))
                        }
                        IconButton(onClick = { onInfo() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        if (item.price > 0) Text("${String.format(Locale.getDefault(), "%.2f", item.price)} €", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    if (!isCompact) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${item.brand ?: ""} • ${item.kcal} kcal", fontSize = 10.sp)
                            Spacer(Modifier.width(8.dp))
                            Badge(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)) { Text(item.storageLocation, fontSize = 9.sp) }
                            if (item.isDonationCandidate) {
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.VolunteerActivism, null, modifier = Modifier.size(12.dp), tint = Color(0xFFE91E63))
                            }
                        }
                    }
                    ExpiryLabel(item.expiryDate ?: 0L, householdSize)
                }
                IconButton(onClick = onToggleFav) { Icon(if (item.isFavorite) favIcon else favIconBorder, null, tint = if (item.isFavorite) Color(0xFFFFB300) else Color.Gray) }
                Text("${item.quantity} ${item.unit}", fontWeight = FontWeight.Bold)
            }
            if (!isCompact) {
                Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = onConsume, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("Verbraucht", fontSize = 10.sp) }
                    Button(onClick = onWaste, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), contentPadding = PaddingValues(0.dp)) { Text("Müll", fontSize = 10.sp) }
                    
                    if (item.openedDate == null) {
                        Button(onClick = onOpen, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Blue), contentPadding = PaddingValues(0.dp)) { 
                            Icon(Icons.Default.Timer, null, modifier = Modifier.size(12.dp), tint = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text("Öffnen", fontSize = 10.sp) 
                        }
                    }
                    
                    if (item.isFrozen) {
                        IconButton(onClick = onDefrost, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.WbSunny, "Auftauen", tint = Color(0xFFFF9800)) }
                    } else {
                        IconButton(onClick = onFreeze, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.AcUnit, "Einfrieren", tint = Color(0xFF03A9F4)) }
                    }
                    
                    IconButton(onClick = onDonate, modifier = Modifier.size(36.dp)) { 
                        Icon(Icons.Default.VolunteerActivism, "Spenden", tint = if (item.isDonationCandidate) Color(0xFFE91E63) else Color.Gray)
                    }

                    IconButton(onClick = onPrintQr, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.QrCode, "Etikett", tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(20.dp)) }
                }
            }
        }
    }
}
