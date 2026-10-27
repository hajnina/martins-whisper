package com.martins.whisper.onboarding

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.martins.whisper.asr.ModelRepositoryProvider
import com.martins.whisper.core.asr.DownloadProgress
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
                        repo.install(spec) { p: DownloadProgress ->
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
    LaunchedEffect(installed) {
        if (installed) feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    OnboardingBackdrop {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Choose your model", color = OnboardingInk, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(28.dp))
            GlassCard {
                Text(
                    "A model downloads once, then every word stays on your phone.",
                    color = OnboardingInk.copy(alpha = 0.86f),
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                )
                when (val p = phase) {
                    is ModelStepPhase.Downloading, is ModelStepPhase.Failed -> {
                        Spacer(Modifier.height(20.dp))
                        Text(statusText(p), color = OnboardingInk, fontSize = 15.sp, lineHeight = 22.sp)
                        if (p is ModelStepPhase.Failed) {
                            TextButton(onClick = { scope.launch { machine.retry() } }) {
                                Text("Retry", color = OnboardingInk, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    ModelStepPhase.Installed -> {
                        Spacer(Modifier.height(20.dp))
                        Text(statusText(p), color = OnboardingInk, fontSize = 15.sp)
                    }
                    ModelStepPhase.Idle -> Unit
                }
            }
            Spacer(Modifier.height(36.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Back", color = OnboardingInk.copy(alpha = 0.7f)) }
                Spacer(Modifier.weight(1f))
                SpringyContainer {
                    Button(
                        onClick = onNext,
                        enabled = installed,
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
