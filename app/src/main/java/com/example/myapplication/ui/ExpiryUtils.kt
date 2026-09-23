package com.example.myapplication.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Berechnet die verbleibenden Tage bis zum Ablaufdatum (API 24 kompatibel).
 */
fun calculateDaysRemaining(expiryDateMs: Long): Int {
    return try {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val expiry = Calendar.getInstance().apply {
            timeInMillis = expiryDateMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val diffMs = expiry.timeInMillis - today.timeInMillis
        TimeUnit.MILLISECONDS.toDays(diffMs).toInt()
    } catch (e: Exception) {
        0
    }
}

@Composable
fun ExpiryLabel(expiryDateMs: Long, householdSize: Int = 1) {
    // Tage berechnen
    val daysRemaining = remember(expiryDateMs) { calculateDaysRemaining(expiryDateMs) }
    
    // Dynamische Warnschwelle: Singles haben mehr Zeit, Familien muessen schneller verbrauchen
    val warningThreshold = if (householdSize >= 4) 2 else if (householdSize >= 2) 3 else 5

    // 1. Text und Farbe je nach Dringlichkeit bestimmen
    val (labelText, labelColor) = when {
        daysRemaining < 0 -> {
            val absoluteDays = Math.abs(daysRemaining)
            "Seit $absoluteDays " + (if (absoluteDays == 1) "Tag" else "Tagen") + " abgelaufen!" to Color(0xFFD32F2F) // Dunkelrot
        }
        daysRemaining == 0 -> {
            "Läuft HEUTE ab!" to Color(0xFFC62828) // Helles Warn-Rot
        }
        daysRemaining == 1 -> {
            "Noch 1 Tag haltbar" to Color(0xFFE65100) // Dunkelorange
        }
        daysRemaining <= warningThreshold -> {
            "Noch $daysRemaining Tage" to Color(0xFFF57C00) // Orange
        }
        else -> {
            "MHD: Noch $daysRemaining Tage" to Color(0xFF388E3C) // Angenehmes Grün
        }
    }

    // 2. Die Textzeile in der Produktkarte anzeigen
    Text(
        text = labelText,
        color = labelColor,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}
