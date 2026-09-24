package com.example.myapplication.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.FridgeViewModel
import com.example.myapplication.data.ChefkochHelper
import com.example.myapplication.data.FridgeItem
import com.example.myapplication.data.MealPlan
import com.example.myapplication.data.SeasonHelper
import com.example.myapplication.data.ShoppingItem
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@Composable
fun PlanScreen(
    viewModel: FridgeViewModel,
    plans: List<MealPlan>,
    inventory: List<FridgeItem>
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    // Filter expiring / overdue items sorted by urgency
    val expiringItems = remember(inventory) {
        inventory
            .filter { it.expiryDate != null }
            .sortedBy { it.expiryDate }
    }

    val topExpiring = remember(expiringItems) { expiringItems.take(3) }

    var showCookedDialog by remember { mutableStateOf<MealPlan?>(null) }
    var showAddMissingDialog by remember { mutableStateOf<String?>(null) }
    var showRouletteDialog by remember { mutableStateOf(false) }
    var rouletteIngredients by remember { mutableStateOf<List<FridgeItem>>(emptyList()) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Navigation Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Heute 📌", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Woche 📅", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Chefkoch & Reste 👨‍🍳", fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(Modifier.height(8.dp))

        when (selectedTab) {
            0 -> TodayPlanTab(
                viewModel = viewModel,
                plans = plans,
                topExpiring = topExpiring,
                onCooked = { showCookedDialog = it },
                onAddMissing = { showAddMissingDialog = it },
                onTriggerRoulette = {
                    if (inventory.isNotEmpty()) {
                        rouletteIngredients = inventory.shuffled().take(3)
                        showRouletteDialog = true
                    } else {
                        Toast.makeText(context, "Kühlschrank ist leer!", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            1 -> WeeklyPlanTab(
                viewModel = viewModel,
                plans = plans,
                onCooked = { showCookedDialog = it },
                onAddMissing = { showAddMissingDialog = it }
            )
            2 -> ChefkochLeftoversTab(
                viewModel = viewModel,
                inventory = inventory,
                onTriggerRoulette = {
                    if (inventory.isNotEmpty()) {
                        rouletteIngredients = inventory.shuffled().take(3)
                        showRouletteDialog = true
                    } else {
                        Toast.makeText(context, "Kühlschrank ist leer!", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    // Dialog 1: Gekocht & Abbuchen
    showCookedDialog?.let { plan ->
        CookedDeductDialog(
            plan = plan,
            inventory = inventory,
            onDismiss = { showCookedDialog = null },
            onConfirmDeduct = { itemsToDeduct ->
                itemsToDeduct.forEach { item ->
                    viewModel.consumeItem(item)
                }
                viewModel.deleteMealPlan(plan.id)
                Toast.makeText(context, "'${plan.recipeTitle}' gekocht! Vorräte wurden abgebucht.", Toast.LENGTH_SHORT).show()
                showCookedDialog = null
            }
        )
    }

    // Dialog 2: Fehlende Zutaten zur Einkaufsliste
    showAddMissingDialog?.let { recipeTitle ->
        AddMissingIngredientsDialog(
            recipeTitle = recipeTitle,
            onDismiss = { showAddMissingDialog = null },
            onAdd = { ingredientName ->
                viewModel.addToShoppingList(ShoppingItem(name = ingredientName, quantity = 1, isChecked = false))
                Toast.makeText(context, "'$ingredientName' zur Einkaufsliste hinzugefügt", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Dialog 3: Reste-Roulette Popup
    if (showRouletteDialog) {
        AlertDialog(
            onDismissRequest = { showRouletteDialog = false },
            title = { Text("🎰 Reste-Roulette", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Dein Zufallstopf für heute:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    rouletteIngredients.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            val color = getItemUrgencyColor(item.expiryDate)
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${item.name} (${item.quantity} ${item.unit})", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    ChefkochHelper.openChefkoch(context, rouletteIngredients.map { it.name })
                    showRouletteDialog = false
                }) {
                    Icon(Icons.Default.Restaurant, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Chefkoch Suche")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val recipeTitle = "Reste-Pfanne mit " + rouletteIngredients.joinToString(", ") { it.name }
                    viewModel.addMealPlan(MealPlan(date = System.currentTimeMillis(), recipeTitle = recipeTitle))
                    Toast.makeText(context, "Als Mahlzeit geplant!", Toast.LENGTH_SHORT).show()
                    showRouletteDialog = false
                }) {
                    Text("Heute planen")
                }
            }
        )
    }
}

@Composable
fun TodayPlanTab(
    viewModel: FridgeViewModel,
    plans: List<MealPlan>,
    topExpiring: List<FridgeItem>,
    onCooked: (MealPlan) -> Unit,
    onAddMissing: (String) -> Unit,
    onTriggerRoulette: () -> Unit
) {
    val context = LocalContext.current
    val todayPlans = remember(plans) {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis
        val todayEnd = todayStart + TimeUnit.DAYS.toMillis(1)
        plans.filter { it.date in todayStart..todayEnd }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // MHD Retter Menü Prominente Karte
        if (topExpiring.isNotEmpty()) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "MHD-Retter-Menü 🚨",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }

                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Diese ${topExpiring.size} Vorräte müssen dringend verbraucht werden:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            topExpiring.forEach { item ->
                                val color = getItemUrgencyColor(item.expiryDate)
                                FilterChip(
                                    selected = true,
                                    onClick = {},
                                    label = { Text(item.name, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = color.copy(alpha = 0.2f),
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Button(
                            onClick = {
                                ChefkochHelper.openChefkoch(context, topExpiring.map { it.name })
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Restaurant, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Chefkoch-Rezepte anzeigen 👨‍🍳")
                        }
                    }
                }
            }
        }

        // Action Row: Roulette Button & Manual Add
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onTriggerRoulette,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🎰 Was koche ich heute?", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        viewModel.addMealPlan(MealPlan(date = System.currentTimeMillis(), recipeTitle = "Neues Gericht"))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Mahlzeit planen")
                }
            }
        }

        item {
            Text(
                "Heutige Mahlzeiten",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (todayPlans.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Noch keine Mahlzeiten für heute geplant.", color = Color.Gray)
                    }
                }
            }
        } else {
            items(todayPlans, key = { it.id }) { plan ->
                MealPlanCard(
                    plan = plan,
                    viewModel = viewModel,
                    onCooked = { onCooked(plan) },
                    onAddMissing = { onAddMissing(plan.recipeTitle) }
                )
            }
        }
    }
}

@Composable
fun WeeklyPlanTab(
    viewModel: FridgeViewModel,
    plans: List<MealPlan>,
    onCooked: (MealPlan) -> Unit,
    onAddMissing: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Wochenplan 🗓️", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (plans.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Keine geplanten Mahlzeiten vorhanden.", color = Color.Gray)
                }
            }
        } else {
            items(plans.sortedBy { it.date }, key = { it.id }) { plan ->
                MealPlanCard(
                    plan = plan,
                    viewModel = viewModel,
                    onCooked = { onCooked(plan) },
                    onAddMissing = { onAddMissing(plan.recipeTitle) }
                )
            }
        }
    }
}

@Composable
fun ChefkochLeftoversTab(
    viewModel: FridgeViewModel,
    inventory: List<FridgeItem>,
    onTriggerRoulette: () -> Unit
) {
    val context = LocalContext.current
    var selectedIngredients by remember { mutableStateOf(setOf<String>()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Chefkoch Reste-Suche 👨‍🍳", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Wähle Zutaten aus deinem Vorrat und finde sofort Rezepte auf Chefkoch.de:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                ChefkochHelper.openChefkoch(context, selectedIngredients.toList())
                            },
                            enabled = selectedIngredients.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Chefkoch (${selectedIngredients.size})")
                        }

                        OutlinedButton(
                            onClick = onTriggerRoulette,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🎰 Reste-Roulette")
                        }
                    }
                }
            }
        }

        item {
            val seasonal = SeasonHelper.getCurrentSeasonalProduce()
            if (seasonal.isNotEmpty()) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Eco, contentDescription = "Saison", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.width(8.dp))
                            Text("Jetzt frisch aus der Region 🍂", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(seasonal.joinToString(" • "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }

        item {
            Text("Verfügbare Vorräte auswählen:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }

        items(inventory, key = { it.id }) { item ->
            val isSelected = selectedIngredients.contains(item.name)
            val urgencyColor = getItemUrgencyColor(item.expiryDate)

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedIngredients = if (isSelected) selectedIngredients - item.name else selectedIngredients + item.name
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = {
                            selectedIngredients = if (it) selectedIngredients + item.name else selectedIngredients - item.name
                        }
                    )
                    Spacer(Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(urgencyColor)
                    )

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.Bold)
                        Text("${item.quantity} ${item.unit} • ${item.category}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun MealPlanCard(
    plan: MealPlan,
    viewModel: FridgeViewModel,
    onCooked: () -> Unit,
    onAddMissing: () -> Unit
) {
    val context = LocalContext.current
    val dateStr = remember(plan.date) { SimpleDateFormat("EEEE, dd.MM.yyyy", Locale.GERMANY).format(Date(plan.date)) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dateStr, fontWeight = FontWeight.Bold, color = viewModel.themeColor.value, style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = { viewModel.deleteMealPlan(plan.id) }) {
                    Icon(Icons.Default.Delete, "Löschen", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }

            Text(plan.recipeTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCooked,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Done, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Gekocht! 🍳", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { ChefkochHelper.openChefkochUrl(context, plan.recipeTitle) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Restaurant, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Rezept 👨‍🍳", fontSize = 12.sp)
                }

                IconButton(onClick = onAddMissing) {
                    Icon(Icons.Default.AddShoppingCart, "Einkaufsliste", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun CookedDeductDialog(
    plan: MealPlan,
    inventory: List<FridgeItem>,
    onDismiss: () -> Unit,
    onConfirmDeduct: (List<FridgeItem>) -> Unit
) {
    var selectedItemIds by remember { mutableStateOf(inventory.map { it.id }.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gekocht & Abbuchen 🍳", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Welche Vorräte wurden für '${plan.recipeTitle}' verbraucht?", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(inventory, key = { it.id }) { item ->
                        val isChecked = selectedItemIds.contains(item.id)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedItemIds = if (isChecked) selectedItemIds - item.id else selectedItemIds + item.id
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(checked = isChecked, onCheckedChange = {
                                selectedItemIds = if (it) selectedItemIds + item.id else selectedItemIds - item.id
                            })
                            Spacer(Modifier.width(8.dp))
                            Text("${item.name} (${item.quantity} ${item.unit})")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val selected = inventory.filter { selectedItemIds.contains(it.id) }
                onConfirmDeduct(selected)
            }) {
                Text("Abbuchen & Erledigt")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
fun AddMissingIngredientsDialog(
    recipeTitle: String,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var ingredientName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fehlende Zutat hinzufügen 🛒") },
        text = {
            Column {
                Text("Füge eine fehlende Zutat für '$recipeTitle' zur Einkaufsliste hinzu:")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = ingredientName,
                    onValueChange = { ingredientName = it },
                    label = { Text("Zutat (z. B. Sahne, Knoblauch)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (ingredientName.isNotBlank()) {
                    onAdd(ingredientName.trim())
                    ingredientName = ""
                }
            }) { Text("Auf die Einkaufsliste") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        }
    )
}

fun getItemUrgencyColor(expiryDate: Long?): Color {
    if (expiryDate == null) return Color.Gray
    val daysLeft = (expiryDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)
    return when {
        daysLeft < 0 -> Color(0xFFD32F2F) // Overdue RED
        daysLeft <= 3 -> Color(0xFFFF9800) // Expiring soon ORANGE
        else -> Color(0xFF388E3C) // OK GREEN
    }
}
