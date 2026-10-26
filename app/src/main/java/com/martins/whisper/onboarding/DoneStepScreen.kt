package com.martins.whisper.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DoneSteps = listOf(
    "1. Tap the floating mic in any text box.",
    "2. Speak, then tap again to stop.",
    "3. Your words appear where the cursor is.",
)

@Composable
fun DoneStepScreen(onFinish: () -> Unit) {
    OnboardingBackdrop {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("You are set", color = OnboardingInk, fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(32.dp))
            GlassCard {
                DoneSteps.forEachIndexed { index, line ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(OnboardingAccent), contentAlignment = Alignment.Center) {
                            Text("${index + 1}", color = Color(0xFF071012), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.size(14.dp))
                        Text(line.substringAfter(". "), color = OnboardingInk.copy(alpha = 0.9f), fontSize = 17.sp, lineHeight = 24.sp)
                    }
                    if (index != DoneSteps.lastIndex) Spacer(Modifier.height(20.dp))
                }
            }
            Spacer(Modifier.height(40.dp))
            SpringyContainer(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OnboardingAccent, contentColor = Color(0xFF071012)),
                ) {
                    Text("Start using it", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
