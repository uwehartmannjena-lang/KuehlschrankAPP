package com.example.myapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NutriScoreBadge(
    score: String?,
    modifier: Modifier = Modifier
) {
    val cleanScore = score?.trim()?.uppercase()
    if (cleanScore.isNullOrBlank()) return

    val (backgroundColor, textColor) = when (cleanScore) {
        "A" -> Color(0xFF038141) to Color.White
        "B" -> Color(0xFF85BB2F) to Color.White
        "C" -> Color(0xFFFECB02) to Color.Black
        "D" -> Color(0xFFEE8100) to Color.White
        "E" -> Color(0xFFE63E11) to Color.White
        else -> Color(0xFF9E9E9E) to Color.White
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "NUTRI-SCORE ",
                color = textColor.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = cleanScore,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}
