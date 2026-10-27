package com.martins.whisper.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.martins.whisper.core.asr.WhisperLanguages

@Composable
fun LanguageStepScreen(
    selected: String,
    onSelect: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val haptics = LocalHapticFeedback.current
    val languages = remember(query) {
        val needle = query.trim()
        if (needle.isEmpty()) WhisperLanguages.all
        else WhisperLanguages.all.filter {
            it.englishName.contains(needle, ignoreCase = true) ||
                it.nativeName.contains(needle, ignoreCase = true) || it.code.equals(needle, ignoreCase = true)
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 40.dp)) {
        Text(
            "Your language",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search languages", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(languages, key = { it.code }) { language ->
                val active = language.code == selected
                val background by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    label = "languageRow",
                )
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(background)
                        .border(
                            1.dp,
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            MaterialTheme.shapes.medium,
                        )
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(language.code)
                        }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            language.nativeName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            language.englishName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (active) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onNext,
                modifier = Modifier.height(56.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Text("Continue", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
