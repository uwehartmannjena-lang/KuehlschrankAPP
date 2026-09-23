package com.example.myapplication.ui.help

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingOverlay(
    targetBounds: Map<String, Rect>,
    onFinish: () -> Unit,
    onSkip: () -> Unit
) {
    val steps = AppHelpManager.onboardingSteps
    var currentStepIndex by remember { mutableIntStateOf(0) }
    val currentStep = steps.getOrNull(currentStepIndex) ?: return

    val targetRect = currentStep.targetKey?.let { targetBounds[it] }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Block clicks behind overlay */ }
    ) {
        // Spotlight Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = Color.Black.copy(alpha = 0.78f))

            if (targetRect != null && targetRect.width > 0 && targetRect.height > 0) {
                val padding = 12.dp.toPx()
                val expandedRect = Rect(
                    left = (targetRect.left - padding).coerceAtLeast(0f),
                    top = (targetRect.top - padding).coerceAtLeast(0f),
                    right = (targetRect.right + padding).coerceAtMost(size.width),
                    bottom = (targetRect.bottom + padding).coerceAtMost(size.height)
                )

                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(expandedRect.left, expandedRect.top),
                    size = Size(expandedRect.width, expandedRect.height),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    blendMode = BlendMode.Clear
                )
            }
        }

        // Floating Onboarding Card
        val isTargetInTopHalf = targetRect != null && (targetRect.top + targetRect.bottom) / 2 < 1200f
        val alignment = if (targetRect == null) Alignment.Center else if (isTargetInTopHalf) Alignment.BottomCenter else Alignment.TopCenter

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = alignment
        ) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Row: Skip & Icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = currentStep.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        TextButton(onClick = onSkip) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Überspringen")
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = currentStep.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = currentStep.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(20.dp))

                    // Dots Progress
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        steps.forEachIndexed { index, _ ->
                            Box(
                                modifier = Modifier
                                    .height(8.dp)
                                    .width(if (index == currentStepIndex) 24.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == currentStepIndex) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Buttons: Back / Next
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentStepIndex > 0) {
                            OutlinedButton(onClick = { currentStepIndex-- }) {
                                Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Zurück")
                            }
                        } else {
                            Spacer(Modifier.width(100.dp))
                        }

                        Button(
                            onClick = {
                                if (currentStepIndex < steps.size - 1) {
                                    currentStepIndex++
                                } else {
                                    onFinish()
                                }
                            }
                        ) {
                            Text(if (currentStepIndex < steps.size - 1) "Weiter" else "Fertig")
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = if (currentStepIndex < steps.size - 1) Icons.Default.ArrowForward else Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
