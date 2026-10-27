package com.martins.whisper.onboarding

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.martins.whisper.asr.ModelRepositoryProvider
import com.martins.whisper.core.asr.ModelCatalog
import com.martins.whisper.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MB = 1024L * 1024L

@Composable
internal fun defaultInstaller(
    context: Context,
): suspend ((Float?, Long, Long?) -> Unit) -> Result<Unit> {
    val installer: suspend ((Float?, Long, Long?) -> Unit) -> Result<Unit> = remember(context) {
        val repo = ModelRepositoryProvider.forContext(context)
        val spec = ModelCatalog.specs.firstOrNull { it.id == SettingsStore(context).modelId }
            ?: ModelCatalog.specs.first()
        { onProgress ->
            withContext(Dispatchers.IO) {
                if (repo.isInstalled(spec)) {
                    Result.success(Unit)
                } else {
                    runCatching {
                        repo.install(spec) { p ->
                            onProgress(p.fraction, p.bytesDownloaded, p.totalBytes)
                        }
                    }.fold({ Result.success(Unit) }, { Result.failure(it) })
                }
            }
        }
    }
    return installer
}

private fun statusText(phase: ModelStepPhase): String = when (phase) {
    is ModelStepPhase.Downloading -> {
        val percent = phase.fraction?.let { "${(it.coerceIn(0f, 1f) * 100).toInt()}%" } ?: "Preparing"
        val mb = phase.totalBytes?.let { " · ${phase.bytesDownloaded / MB} / ${it / MB} MB" } ?: ""
        "$percent$mb"
    }
    is ModelStepPhase.Failed -> phase.message
    else -> "Model ready."
}

@Composable
fun ModelStepScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    installer: suspend ((Float?, Long, Long?) -> Unit) -> Result<Unit> = defaultInstaller(LocalContext.current),
) {
    val machine = remember(installer) { ModelStepState(installer) }
    val phase by machine.phase.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(machine) { machine.start() }
    val feedback = LocalHapticFeedback.current
    val installed = phase is ModelStepPhase.Installed
    val progress = (phase as? ModelStepPhase.Downloading)?.fraction
    LaunchedEffect(installed) {
        if (installed) feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Choose your model",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            "A model downloads once, then every word stays on your phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(36.dp))
        Card(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                when (val p = phase) {
                    ModelStepPhase.Idle -> {
                        Text(
                            "Waiting to download…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is ModelStepPhase.Downloading -> {
                        Text(
                            statusText(p),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { progress ?: 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    ModelStepPhase.Installed -> {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            statusText(p),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    is ModelStepPhase.Failed -> {
                        Text(
                            statusText(p),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = { scope.launch { machine.retry() } }) {
                            Text("Retry", style = MaterialTheme.typography.labelLarge)
                        }
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
                enabled = installed,
                modifier = Modifier.height(56.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Text("Continue", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
