package com.martins.whisper.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FirstDictationScreen(onNext: () -> Unit, onBack: () -> Unit) {
    OnboardingBackdrop {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Try your first dictation", color = OnboardingInk, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(28.dp))
            GlassCard {
                Text(
                    "Tap the mic bubble, say a sentence, and watch it land in the text box.",
                    color = OnboardingInk.copy(alpha = 0.86f),
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                )
            }
            Spacer(Modifier.height(36.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Back", color = OnboardingInk.copy(alpha = 0.7f)) }
                Spacer(Modifier.weight(1f))
                SpringyContainer {
                    Button(
                        onClick = onNext,
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OnboardingAccent, contentColor = Color(0xFF071012)),
                    ) {
                        Text("Continue", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
