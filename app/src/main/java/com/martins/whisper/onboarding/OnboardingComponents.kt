package com.martins.whisper.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.martins.whisper.R

@Composable
fun OnboardingScaffold(
    step: OnboardingStep,
    canGoBack: Boolean,
    onBack: () -> Unit,
    primaryLabel: String?,
    onPrimary: () -> Unit,
    showProgress: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backLabel = stringResource(R.string.ob_back)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canGoBack) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onBack)
                            .semantics { role = Role.Button; contentDescription = backLabel },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
                Spacer(Modifier.weight(1f))
                if (showProgress) {
                    Text(
                        text = OnboardingRules.progressLabel(step),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
            if (primaryLabel != null) {
                Button(
                    onClick = onPrimary,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .height(56.dp),
                ) {
                    Text(primaryLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun ScreenHeading(title: String, subtitle: String? = null) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    if (subtitle != null) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(28.dp))
}

/** The living orb: a calm breathing ring around the mic mark. */
@Composable
fun AnimatedMic(
    diameter: Dp,
    listening: Boolean = false,
    level: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val durations = LocalMotionDurations.current
    val transition = rememberInfiniteTransition(label = "mic")
    val breath by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                if (durations.ring > 0) durations.ring else 1,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val accent = MaterialTheme.colorScheme.primary
    val safeLevel = level.coerceIn(0f, 1f)
    Canvas(modifier = modifier.size(diameter)) {
        val r = size.minDimension / 2f
        drawCircle(
            color = accent.copy(alpha = 0.16f),
            radius = r * if (listening) breath else 1f,
        )
        if (listening) {
            drawCircle(
                color = accent.copy(alpha = 0.24f + safeLevel * 0.24f),
                radius = r * (0.74f + safeLevel * 0.24f),
            )
        }
        val bodyW = r * 0.5f
        val bodyH = r * 0.76f
        drawRoundRect(
            color = accent,
            topLeft = Offset(r - bodyW / 2f, r * 0.4f),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(bodyW / 2f),
        )
        drawArc(
            color = accent,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(r - bodyW * 0.9f, r * 0.62f),
            size = Size(bodyW * 1.8f, bodyH * 0.8f),
            style = Stroke(width = size.minDimension * 0.05f, cap = StrokeCap.Round),
        )
        drawLine(
            color = accent,
            start = Offset(r, r * 1.52f),
            end = Offset(r, r * 1.72f),
            strokeWidth = size.minDimension * 0.05f,
            cap = StrokeCap.Round,
        )
    }
}
