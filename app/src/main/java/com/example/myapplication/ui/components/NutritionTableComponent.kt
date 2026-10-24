package com.example.myapplication.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun NutritionTableComponent(
    caloriesKcal: Double?,
    fat: Double?,
    saturatedFat: Double?,
    carbohydrates: Double?,
    sugars: Double?,
    proteins: Double?,
    salt: Double?,
    modifier: Modifier = Modifier
) {
    val hasAnyData = caloriesKcal != null || fat != null || saturatedFat != null ||
            carbohydrates != null || sugars != null || proteins != null || salt != null

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Nährwerte",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "pro 100g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(4.dp))

            if (!hasAnyData) {
                Text(
                    text = "Keine Nährwertangaben vorhanden",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                NutritionRow(label = "Brennwert", value = caloriesKcal?.let { "${it.toInt()} kcal" }, isBold = true)
                NutritionRow(label = "Fett", value = fat?.let { formatGram(it) })
                NutritionRow(label = "  davon gesättigte Fettsäuren", value = saturatedFat?.let { formatGram(it) }, isIndented = true)
                NutritionRow(label = "Kohlenhydrate", value = carbohydrates?.let { formatGram(it) })
                NutritionRow(label = "  davon Zucker", value = sugars?.let { formatGram(it) }, isIndented = true)
                NutritionRow(label = "Eiweiß", value = proteins?.let { formatGram(it) })
                NutritionRow(label = "Salz", value = salt?.let { formatGram(it) })
            }
        }
    }
}

@Composable
private fun NutritionRow(
    label: String,
    value: String?,
    isBold: Boolean = false,
    isIndented: Boolean = false
) {
    if (value == null) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (isBold) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            else MaterialTheme.typography.bodyMedium,
            color = if (isIndented) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            fontSize = if (isIndented) 13.sp else 14.sp
        )
        Text(
            text = value,
            style = if (isBold) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp
        )
    }
}

private fun formatGram(value: Double): String {
    return String.format(Locale.GERMANY, "%.1f g", value)
}
