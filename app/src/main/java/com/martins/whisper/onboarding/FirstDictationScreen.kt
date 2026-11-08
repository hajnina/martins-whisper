package com.martins.whisper.onboarding

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.martins.whisper.accessibility.InsertionPolicy
import com.martins.whisper.service.DictationService
import com.martins.whisper.service.DictationState
import com.martins.whisper.ui.theme.WhisperPillShape

/**
 * Live test of the floating bubble. While this screen is visible the transcript may land in its
 * own practice field: [InsertionPolicy.allowOwnPackageForOnboardingTest] is set only while the
 * test session is active (start tap) and cleared the moment the session ends — success, error,
 * stop, or leaving the step — so it can never leak to an ongoing session of a real phone.
 */
@Composable
fun FirstDictationScreen(onNext: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var practice by rememberSaveable { mutableStateOf("") }
    var sessionStarted by rememberSaveable { mutableStateOf(false) }
    var guidanceRequested by rememberSaveable { mutableStateOf(false) }
    var overlayGranted by rememberSaveable { mutableStateOf(Settings.canDrawOverlays(context)) }
    val dictation by DictationService.state.collectAsState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            InsertionPolicy.allowOwnPackageForOnboardingTest = false
        }
    }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receivedContext: Context, intent: Intent) {
                intent.getStringExtra(DictationService.EXTRA_TRANSCRIPT)?.let { practice = it }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DictationService.ACTION_TRANSCRIPT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            InsertionPolicy.allowOwnPackageForOnboardingTest = false
        }
    }
    LaunchedEffect(dictation) {
        if (dictation is DictationState.Success || dictation is DictationState.Error) {
            InsertionPolicy.allowOwnPackageForOnboardingTest = false
        }
    }

    fun endSession() {
        InsertionPolicy.allowOwnPackageForOnboardingTest = false
    }

    val status = when (val current = dictation) {
        is DictationState.Idle -> "Bubble ready — tap it to talk."
        is DictationState.Loading -> current.label
        is DictationState.Recording -> "Listening\u2026"
        is DictationState.Processing -> current.label
        is DictationState.Success -> current.message
        is DictationState.Error -> current.message
    }

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
                    "Tap the floating bubble, say a sentence, then tap it again. Your words land in the box.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = practice,
                    onValueChange = { practice = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Your words land here", color = scheme.onSurfaceVariant) },
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
                Spacer(Modifier.height(12.dp))
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                )
                if (!overlayGranted || guidanceRequested) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "The floating bubble needs the overlay permission first. " +
                            "Go back to the \u201cShow the mic bubble\u201d step, tap Allow overlay, then return here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                    )
                }
                Spacer(Modifier.height(20.dp))
                SpringyContainer {
                    Button(
                        onClick = {
                            if (!overlayGranted) {
                                endSession()
                                guidanceRequested = true
                            } else {
                                guidanceRequested = false
                                endSession()
                                InsertionPolicy.allowOwnPackageForOnboardingTest = true
                                DictationService.start(context)
                                sessionStarted = true
                            }
                        },
                        shape = WhisperPillShape,
                    ) {
                        Text("Try it now")
                    }
                }
            }
            if (sessionStarted) {
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = {
                    endSession()
                    DictationService.stop(context)
                    practice = ""
                    sessionStarted = false
                }) {
                    Text("Stop bubble", color = scheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(36.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    endSession()
                    onBack()
                }) {
                    Text("Back", color = scheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                SpringyContainer {
                    Button(onClick = {
                        endSession()
                        onNext()
                    }, shape = WhisperPillShape) {
                        Text("Continue")
                    }
                }
            }
        }
    }
}
