package com.martins.whisper.onboarding
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.martins.whisper.ui.theme.WhisperPillShape

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BreathingOrb()
        Spacer(Modifier.height(40.dp))
        Text(
            text = "Talk. It types.",
            style = MaterialTheme.typography.displaySmall,
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Speak naturally \u2014 your words appear in any app, instantly and privately.",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
                shape = WhisperPillShape,
        ) {
            Text(
                text = "Get started",
                style = MaterialTheme.typography.titleMedium,
            )
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
    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(230.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
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
                    listOf(
                        scheme.onPrimaryContainer.copy(alpha = 0.92f),
                        scheme.primary,
                        scheme.tertiary,
                        Color.Transparent,
                    ),
                    center = center,
                    radius = radius,
                ),
                radius = radius * 0.82f,
                center = center,
            )
        }
    }
}
