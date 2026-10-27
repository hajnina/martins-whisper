package com.martins.whisper.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.martins.whisper.onboarding.MartinsOnboardingTheme
import com.martins.whisper.onboarding.Motion
import com.martins.whisper.onboarding.hapticClick

@Composable
fun HomeScreen(
    serviceOn: Boolean,
    lastTranscript: String?,
    onToggleService: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onRestartOnboarding: () -> Unit,
) {
    MartinsOnboardingTheme {
        val scheme = MaterialTheme.colorScheme
        val haptics = LocalHapticFeedback.current
        val gearInteraction = remember { MutableInteractionSource() }
        val restartInteraction = remember { MutableInteractionSource() }
        val gearScale by rememberTapScale(gearInteraction)
        val restartScale by rememberTapScale(restartInteraction)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(scheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Martins Whisper",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = hapticClick(onOpenSettings),
                    interactionSource = gearInteraction,
                    modifier = Modifier.graphicsLayer {
                        scaleX = gearScale
                        scaleY = gearScale
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = scheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
            Surface(
                color = scheme.primaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(28.dp)) {
                    Text(
                        text = "Ready",
                        style = MaterialTheme.typography.displaySmall,
                        color = scheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(10.dp))
                    Crossfade(
                        targetState = serviceOn,
                        animationSpec = Motion.defaultEffectsSpec(),
                        label = "status",
                    ) { on ->
                        Text(
                            text = if (on) {
                                "Tap the bubble in any app."
                            } else {
                                "Turn the bubble on to dictate anywhere."
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onPrimaryContainer.copy(alpha = 0.78f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Surface(
                color = scheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Floating bubble",
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurface,
                        )
                        Text(
                            text = if (serviceOn) "On, visible over your apps" else "Off",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = serviceOn,
                        onCheckedChange = { checked ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleService(checked)
                        },
                    )
                }
            }
            lastTranscript?.takeIf { it.isNotBlank() }?.let { transcript ->
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = scheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            text = "Last transcript",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = transcript,
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurface,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(36.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(
                    onClick = hapticClick(onRestartOnboarding),
                    interactionSource = restartInteraction,
                    modifier = Modifier.graphicsLayer {
                        scaleX = restartScale
                        scaleY = restartScale
                    },
                ) {
                    Text("Restart onboarding", color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun rememberTapScale(interaction: InteractionSource) =
    animateFloatAsState(
        targetValue = if (interaction.collectIsPressedAsState().value) 0.9f else 1f,
        animationSpec = Motion.fastSpatialSpec(),
        label = "tapScale",
    )
