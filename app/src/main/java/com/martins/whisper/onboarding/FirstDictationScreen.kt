package com.martins.whisper.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.martins.whisper.ui.theme.WhisperPillShape

@Composable
fun FirstDictationScreen(onNext: () -> Unit, onBack: () -> Unit) {
    OnboardingBackdrop {
        val scheme = MaterialTheme.colorScheme
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            ScreenTitle("Try your first dictation")
            Spacer(Modifier.height(28.dp))
            GlassCard {
                Text(
                    "Tap the mic bubble, say a sentence, and watch it land in the text box.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(36.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Back", color = scheme.onSurfaceVariant) }
                Spacer(Modifier.weight(1f))
                SpringyContainer {
                    Button(onClick = onNext, shape = WhisperPillShape) {
                        Text("Continue")
                    }
                }
            }
        }
    }
}
