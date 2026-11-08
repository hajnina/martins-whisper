package com.martins.whisper.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.martins.whisper.ui.PermissionItem
import com.martins.whisper.ui.theme.WhisperPillShape

object PermissionCopy {
    fun title(item: PermissionItem): String = when (item) {
        PermissionItem.MICROPHONE -> "Let me hear you"
        PermissionItem.OVERLAY -> "Show the mic bubble"
        PermissionItem.ACCESSIBILITY -> "Type for you"
        PermissionItem.NOTIFICATIONS -> "Stay informed"
    }

    fun why(item: PermissionItem): String = when (item) {
        PermissionItem.MICROPHONE -> "So Martins Whisper can turn your voice into text. Audio stays on your phone."
        PermissionItem.OVERLAY -> "A small floating button over any app, so you can dictate anywhere."
        PermissionItem.ACCESSIBILITY -> "Lets the app put the words into the text box you are using. If Android says restricted: open App info, tap the three dots, Allow restricted settings."
        PermissionItem.NOTIFICATIONS -> "A quiet notice while listening, so you always know the mic is on."
    }

    fun action(item: PermissionItem): String = when (item) {
        PermissionItem.MICROPHONE -> "Allow microphone"
        PermissionItem.OVERLAY -> "Allow overlay"
        PermissionItem.ACCESSIBILITY -> "Open accessibility"
        PermissionItem.NOTIFICATIONS -> "Allow notifications"
    }
}

@Composable
internal fun OnboardingBackdrop(content: @Composable BoxScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(scheme.surfaceContainer, scheme.surface))),
        content = content,
    )
}

@Composable
internal fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceContainerHigh)
            .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.large)
            .padding(28.dp),
        content = content,
    )
}

@Composable
internal fun rememberPressScale(pressed: Boolean): Float {
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = Motion.fastEffectsSpec(),
        label = "press",
    )
    return scale
}

@Composable
internal fun SpringyContainer(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .scale(rememberPressScale(pressed))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    waitForUpOrCancellation()
                    pressed = false
                }
            },
        content = content,
    )
}

@Composable
internal fun ScreenTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun PermissionStepScreen(
    item: PermissionItem,
    granted: Boolean,
    onOpen: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    HapticOnGrant(granted)
    OnboardingBackdrop {
        val scheme = MaterialTheme.colorScheme
        val check by animateFloatAsState(
            targetValue = if (granted) 1f else 0f,
            animationSpec = Motion.defaultSpatialSpec(),
            label = "granted",
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            ScreenTitle(PermissionCopy.title(item))
            Spacer(Modifier.height(28.dp))
            GlassCard {
                Text(
                    PermissionCopy.why(item),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                if (granted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = scheme.tertiary,
                            modifier = Modifier
                                .size(30.dp)
                                .graphicsLayer {
                                    scaleX = check
                                    scaleY = check
                                },
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Granted", style = MaterialTheme.typography.titleMedium, color = scheme.tertiary)
                    }
                } else {
                    Button(onClick = onOpen, shape = WhisperPillShape) {
                        Text(PermissionCopy.action(item))
                    }
                }
            }
            Spacer(Modifier.height(36.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Text("Back", color = scheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                SpringyContainer {
                    Button(onClick = onNext, shape = WhisperPillShape) {
                        Text(if (granted) "Next" else "Skip for now")
                    }
                }
            }
        }
    }
}
