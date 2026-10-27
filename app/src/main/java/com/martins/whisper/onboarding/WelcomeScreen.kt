package com.martins.whisper.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BreathingOrb()
        Spacer(Modifier.height(40.dp))
        Text(
            text = "Talk. It types.",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Speak naturally \u2014 your words appear in any app, instantly and privately.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onStart()
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text("Get started", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun BreathingOrb() {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "orb")
    val scale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orbScale",
    )
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orbGlow",
    )
    Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(230.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f
            for (ring in 1..3) {
                drawCircle(
                    color = scheme.primary.copy(alpha = 0.05f * ring * glow),
                    radius = radius * (0.6f + 0.13f * ring),
                    center = center,
                )
            }
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(scheme.primaryContainer, scheme.primary, scheme.primary.copy(alpha = 0f)),
                    center = center,
                    radius = radius,
                ),
                radius = radius * 0.82f,
                center = center,
            )
        }
    }
}
