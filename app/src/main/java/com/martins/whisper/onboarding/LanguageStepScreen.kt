package com.martins.whisper.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.martins.whisper.core.asr.WhisperLanguages

@Composable
fun LanguageStepScreen(
    selected: String,
    onSelect: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val languages = remember(query) {
        val needle = query.trim()
        if (needle.isEmpty()) WhisperLanguages.all
        else WhisperLanguages.all.filter {
            it.englishName.contains(needle, ignoreCase = true) ||
                it.nativeName.contains(needle, ignoreCase = true) || it.code.equals(needle, ignoreCase = true)
        }
    }
    OnboardingBackdrop {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 40.dp)) {
            Text("Your language", color = OnboardingInk, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search languages", color = OnboardingInk.copy(alpha = 0.5f)) },
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = OnboardingInk,
                    unfocusedTextColor = OnboardingInk,
                    cursorColor = OnboardingAccent,
                    focusedBorderColor = OnboardingAccent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                ),
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(languages, key = { it.code }) { language ->
                    val active = language.code == selected
                    val background by animateColorAsState(
                        if (active) OnboardingAccent.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f),
                        label = "languageRow",
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(background)
                            .border(1.dp, if (active) OnboardingAccent.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
                            .clickable { onSelect(language.code) }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(language.nativeName, color = OnboardingInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(language.englishName, color = OnboardingInk.copy(alpha = 0.6f), fontSize = 14.sp)
                        }
                        if (active) Text("✓", color = OnboardingAccent, fontSize = 20.sp)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Back", color = OnboardingInk.copy(alpha = 0.7f)) }
                Spacer(Modifier.weight(1f))
                SpringyContainer {
                    Button(onClick = onNext, shape = RoundedCornerShape(28.dp), colors = ButtonDefaults.buttonColors(containerColor = OnboardingAccent, contentColor = Color(0xFF071012))) {
                        Text("Continue", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
