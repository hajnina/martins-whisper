package com.martins.whisper.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.martins.whisper.core.asr.WhisperLanguages
import androidx.compose.ui.unit.sp
import com.martins.whisper.ui.theme.WhisperPillShape

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
        val scheme = MaterialTheme.colorScheme
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 40.dp)) {
            ScreenTitle("Your language")
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search languages", color = scheme.onSurfaceVariant) },
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = scheme.onSurface,
                    unfocusedTextColor = scheme.onSurface,
                    cursorColor = scheme.primary,
                    focusedBorderColor = scheme.primary,
                    unfocusedBorderColor = scheme.outlineVariant,
                    focusedContainerColor = scheme.surfaceContainerHigh,
                    unfocusedContainerColor = scheme.surfaceContainerHigh,
                ),
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(languages, key = { it.code }) { language ->
                    val active = language.code == selected
                    val background by animateColorAsState(
                        if (active) scheme.primary.copy(alpha = 0.14f) else scheme.surfaceContainerHigh,
                        label = "languageRow",
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(background)
                            .border(1.dp, if (active) scheme.primary.copy(alpha = 0.7f) else scheme.outlineVariant, MaterialTheme.shapes.medium)
                            .clickable { onSelect(language.code) }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(language.nativeName, color = scheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(language.englishName, color = scheme.onSurfaceVariant, fontSize = 14.sp)
                        }
                        if (active) Text("✓", color = scheme.primary, fontSize = 20.sp)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
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
