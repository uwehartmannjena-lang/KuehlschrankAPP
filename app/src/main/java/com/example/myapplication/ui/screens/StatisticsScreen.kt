package com.example.myapplication.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.StatisticsViewModel
import com.example.myapplication.data.StatisticsHelper
import com.example.myapplication.data.StatisticsTimeFrame
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel = viewModel()
) {
    val context = LocalContext.current
    var selectedTimeFrame by remember { mutableStateOf(StatisticsTimeFrame.DIESER_MONAT) }

    val allItems by viewModel.allItems.collectAsState()
    val wastedItems by viewModel.wastedItems.collectAsState()
    val consumedItems by viewModel.consumedItems.collectAsState()
    val isExportingPdf by viewModel.isExportingPdf.collectAsState()

    val filteredWasted = remember(wastedItems, selectedTimeFrame) {
        StatisticsHelper.filterWastedByTimeframe(wastedItems, selectedTimeFrame)
    }
    val filteredConsumed = remember(consumedItems, selectedTimeFrame) {
        StatisticsHelper.filterConsumedByTimeframe(consumedItems, selectedTimeFrame)
    }

    val totalValue = remember(allItems) { allItems.sumOf { it.price * it.quantity } }
    val totalCount = remember(allItems) { allItems.sumOf { it.quantity } }
    val sustainability = remember(filteredConsumed, filteredWasted) {
        StatisticsHelper.calculateSustainabilityStats(filteredConsumed, filteredWasted)
    }
    val topWasted = remember(filteredWasted) {
        StatisticsHelper.calculateTopWasted(filteredWasted)
    }
    val topConsumed = remember(filteredConsumed) {
        StatisticsHelper.calculateTopConsumed(filteredConsumed)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header mit Zeitraum-Filter & PDF-/Share-Export
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.BarChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Statistik & Analyse",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // PDF-Export Button
                    IconButton(
                        onClick = {
                            viewModel.exportStatisticsPdf(context, selectedTimeFrame)
                        },
                        enabled = !isExportingPdf
                    ) {
                        if (isExportingPdf) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "PDF-Bericht exportieren",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Text-Share Button
                    IconButton(
                        onClick = {
                            val report = StatisticsHelper.generateShareReport(
                                timeFrameTitle = selectedTimeFrame.title,
                                inventoryCount = totalCount,
                                inventoryValue = totalValue,
                                consumedCount = sustainability.consumedCount,
                                wastedCount = sustainability.wastedCount,
                                wastedValue = sustainability.wastedValue,
                                wastedCo2 = sustainability.wastedCo2,
                                freshnessScore = 100,
                                nutritionStats = null,
                                wasteTrend = null,
                                costProjection = null,
                                topConsumedItems = topConsumed,
                                topWastedItems = topWasted
                            )
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, report)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Statistikbericht teilen"))
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Bericht als Text teilen",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StatisticsTimeFrame.values()) { tf ->
                    FilterChip(
                        selected = (selectedTimeFrame == tf),
                        onClick = { selectedTimeFrame = tf },
                        label = { Text(tf.title) }
                    )
                }
            }
        }

        // 2. Vorrat & Verlust KPI Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ElevatedCard(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Vorratswert",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = String.format(Locale.GERMANY, "%.2f €", totalValue),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$totalCount Artikel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ElevatedCard(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Müll-Verlust",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = String.format(Locale.GERMANY, "%.2f €", sustainability.wastedValue),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "${sustainability.wastedCount} weggeworfen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 3. Nachhaltigkeit & CO2 Card
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Nachhaltigkeit & CO₂-Auswirkung",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Verbrauchte Lebensmittel:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${sustainability.consumedCount} Artikel",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("CO₂ durch Wegwerfen:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            String.format(Locale.GERMANY, "%.2f kg CO₂", sustainability.wastedCo2),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // 4. Häufigste Müll-Artikel
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Häufigste Müll-Artikel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    if (topWasted.isEmpty()) {
                        Text(
                            text = "Keine weggeworfenen Artikel in diesem Zeitraum – hervorragend! 🎉",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        topWasted.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}. ${item.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${item.count}x (${String.format(Locale.GERMANY, "%.2f €", item.totalLoss)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
