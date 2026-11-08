package com.martins.whisper.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.martins.whisper.ui.theme.WhisperPillShape

private val DoneSteps = listOf(
    "1. Tap the floating mic in any text box.",
    "2. Speak, then tap again to stop.",
    "3. Your words appear where the cursor is.",
)

@Composable
fun DoneStepScreen(onFinish: () -> Unit) {
    OnboardingBackdrop {
        val scheme = MaterialTheme.colorScheme
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            ScreenTitle("You are set")
            Spacer(Modifier.height(32.dp))
            GlassCard {
                DoneSteps.forEachIndexed { index, line ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(scheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary)
                        }
                        Spacer(Modifier.size(14.dp))
                        Text(
                            line.substringAfter(". "),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurface,
                        )
                    }
                    if (index != DoneSteps.lastIndex) Spacer(Modifier.height(20.dp))
                }
            }
            Spacer(Modifier.height(40.dp))
            SpringyContainer(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = WhisperPillShape,
                ) {
                    Text("Start using it")
                }
            }
        }
    }
}
