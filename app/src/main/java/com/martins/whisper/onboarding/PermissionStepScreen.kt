package com.martins.whisper.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.martins.whisper.ui.PermissionItem

internal val OnboardingAccent = Color(0xFF5EEAD4)
internal val OnboardingInk = Color(0xFFF3FAFA)
private val DeepTop = Color(0xFF06363B)
private val DeepBottom = Color(0xFF071012)
private val GrantedGreen = Color(0xFF6EE7B7)

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
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(DeepTop, DeepBottom))), content = content)
}

@Composable
internal fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(28.dp))
            .padding(24.dp),
        content = content,
    )
}

@Composable
internal fun rememberPressScale(pressed: Boolean): Float {
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    return scale
}

@Composable
internal fun SpringyContainer(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.scale(rememberPressScale(pressed)).pointerInput(Unit) {
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
fun PermissionStepScreen(
    item: PermissionItem,
    granted: Boolean,
    onOpen: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    OnboardingBackdrop {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(PermissionCopy.title(item), color = OnboardingInk, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(28.dp))
            GlassCard {
                Text(PermissionCopy.why(item), color = OnboardingInk.copy(alpha = 0.86f), fontSize = 17.sp, lineHeight = 26.sp)
                Spacer(Modifier.height(24.dp))
                if (granted) {
                    val check by animateFloatAsState(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "granted")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = GrantedGreen, modifier = Modifier.size(30.dp).scale(check))
                        Spacer(Modifier.size(12.dp))
                        Text("Granted", color = GrantedGreen, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(onClick = onOpen, shape = RoundedCornerShape(28.dp), colors = ButtonDefaults.buttonColors(containerColor = OnboardingAccent, contentColor = DeepBottom)) {
                        Text(PermissionCopy.action(item), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(36.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Back", color = OnboardingInk.copy(alpha = 0.7f)) }
                Spacer(Modifier.weight(1f))
                SpringyContainer {
                    Button(
                        onClick = onNext,
                        shape = RoundedCornerShape(28.dp),
                        colors = if (granted) ButtonDefaults.buttonColors(containerColor = OnboardingAccent, contentColor = DeepBottom)
                        else ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.14f), contentColor = OnboardingInk),
                    ) {
                        Text(if (granted) "Next" else "Skip for now", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
