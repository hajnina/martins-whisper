package com.martins.whisper.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.martins.whisper.ui.PermissionItem

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
fun PermissionStepScreen(
    item: PermissionItem,
    granted: Boolean,
    onOpen: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    HapticOnGrant(granted)
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            PermissionCopy.title(item),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(28.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    PermissionCopy.why(item),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                if (granted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp),
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            "Granted",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Button(
                        onClick = onOpen,
                        modifier = Modifier.height(56.dp),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(PermissionCopy.action(item), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        Spacer(Modifier.height(36.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onNext,
                modifier = Modifier.height(56.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(if (granted) "Next" else "Skip for now", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
