package com.example.myapplication.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllergenHighlightComponent(
    allergens: String?,
    additives: String? = null,
    modifier: Modifier = Modifier
) {
    val allergenList = remember(allergens) {
        parseAllergenString(allergens)
    }

    val additiveList = remember(additives) {
        additives?.split(",", ";")
            ?.map { it.trim().removePrefix("en:").removePrefix("de:").uppercase() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    if (allergenList.isEmpty() && additiveList.isEmpty()) {
        return
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Allergene Bereich
            if (allergenList.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Allergene",
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Enthaltene Allergene",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC62828)
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allergenList.forEach { allergen ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFEBEE),
                            border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                            tonalElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "⚠️",
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = allergen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB71C1C)
                                )
                            }
                        }
                    }
                }
            }

            // Zusatzstoffe Bereich
            if (additiveList.isNotEmpty()) {
                if (allergenList.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Zusatzstoffe",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Zusatzstoffe",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    additiveList.forEach { additive ->
                        SuggestionChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = additive,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun parseAllergenString(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(",", ";")
        .map { cleanAndTranslateAllergen(it.trim()) }
        .filter { it.isNotBlank() }
        .distinct()
}

private fun cleanAndTranslateAllergen(tag: String): String {
    val clean = tag.removePrefix("en:").removePrefix("de:").replace('-', ' ').trim().lowercase(Locale.ROOT)
    return when (clean) {
        "gluten" -> "Gluten"
        "milk" -> "Milch"
        "eggs", "egg" -> "Eier"
        "nuts", "tree nuts" -> "Schalenfrüchte / Nüsse"
        "peanuts", "peanut" -> "Erdnüsse"
        "soybeans", "soya", "soy" -> "Soja"
        "fish" -> "Fisch"
        "crustaceans" -> "Krebstiere"
        "celery" -> "Sellerie"
        "mustard" -> "Senf"
        "sesame seeds", "sesame" -> "Sesam"
        "sulphur dioxide and sulphites", "sulphites", "sulfites" -> "Schwefeldioxid / Sulfite"
        "lupin" -> "Lupinen"
        "molluscs" -> "Weichtiere"
        else -> clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}
