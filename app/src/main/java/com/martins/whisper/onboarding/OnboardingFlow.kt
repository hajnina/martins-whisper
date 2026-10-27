package com.martins.whisper.onboarding
import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.*
import androidx.lifecycle.*
import com.martins.whisper.accessibility.DictationAccessibilityService
import com.martins.whisper.data.SettingsStore
import com.martins.whisper.ui.PermissionGate
import com.martins.whisper.ui.PermissionItem
import com.martins.whisper.ui.PermissionSnapshot
import kotlinx.coroutines.delay
private fun permissionItemFor(step: OnboardingStep): PermissionItem? = when (step) {
    OnboardingStep.MIC -> PermissionItem.MICROPHONE
    OnboardingStep.OVERLAY -> PermissionItem.OVERLAY
    OnboardingStep.ACCESSIBILITY -> PermissionItem.ACCESSIBILITY
    OnboardingStep.NOTIFICATIONS -> PermissionItem.NOTIFICATIONS
    else -> null
}
private fun isGranted(snapshot: PermissionSnapshot, item: PermissionItem): Boolean = when (item) {
    PermissionItem.MICROPHONE -> snapshot.microphone
    PermissionItem.OVERLAY -> snapshot.overlay
    PermissionItem.ACCESSIBILITY -> snapshot.accessibility
    PermissionItem.NOTIFICATIONS -> snapshot.notifications
}
private fun readSnapshot(activity: ComponentActivity): PermissionSnapshot = PermissionSnapshot(
    microphone = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED,
    overlay = Settings.canDrawOverlays(activity),
    accessibility = isAccessibilityEnabled(activity),
    notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED,
)
private fun isAccessibilityEnabled(activity: ComponentActivity): Boolean {
    val component = ComponentName(activity, DictationAccessibilityService::class.java)
    val enabled = Settings.Secure.getString(
        activity.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    )
    if (PermissionGate.accessibilityServiceEnabled(enabled, component.packageName, component.className)) {
        return true
    }
    val manager = activity.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.packageName == activity.packageName }
}
private fun openPermission(activity: ComponentActivity, item: PermissionItem) {
    when (item) {
        PermissionItem.MICROPHONE ->
            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
        PermissionItem.NOTIFICATIONS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            }
        PermissionItem.OVERLAY -> runCatching {
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${activity.packageName}")),
            )
        }
        PermissionItem.ACCESSIBILITY -> runCatching {
            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}
@Composable
fun OnboardingFlow(activity: ComponentActivity, onFinished: () -> Unit) {
    MartinsOnboardingTheme {
        val scheme = MaterialTheme.colorScheme
        val context = LocalContext.current
        val settings = remember { SettingsStore(context) }
        var step by rememberSaveable { mutableStateOf<OnboardingStep>(OnboardingStep.WELCOME) }
        var forward by remember { mutableStateOf(true) }
        var snapshot by remember { mutableStateOf(readSnapshot(activity)) }
        var language by rememberSaveable { mutableStateOf<String>(settings.language) }
        val currentItem = permissionItemFor(step)
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) snapshot = readSnapshot(activity)
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(currentItem) {
            while (currentItem != null) {
                delay(1_000)
                snapshot = readSnapshot(activity)
            }
        }
        val progress by animateFloatAsState(
            targetValue = step.index.toFloat() / (OnboardingStep.total - 1).toFloat(),
            animationSpec = Motion.defaultSpatialSpec(),
            label = "progress",
        )
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(scheme.surface, scheme.primaryContainer, scheme.surface),
                    ),
                ),
        ) {
            Column(Modifier.fillMaxSize()) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(3.dp),
                    color = scheme.primary,
                    trackColor = scheme.onSurface.copy(alpha = 0.12f),
                )
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val direction = if (forward) 1 else -1
                        val spec = Motion.defaultSpatialSpec<IntOffset>()
                        (slideInHorizontally(spec) { width -> direction * width / 3 } + fadeIn())
                            .togetherWith(
                                slideOutHorizontally(spec) { width -> -direction * width / 3 } + fadeOut(),
                            )
                    },
                    label = "step",
                ) { target ->
                    val next: () -> Unit = {
                        forward = true
                        OnboardingRules.stepAfter(target)?.let { step = it }
                    }
                    val back: () -> Unit = {
                        forward = false
                        OnboardingRules.stepBefore(target)?.let { step = it }
                    }
                    when (target) {
                        OnboardingStep.WELCOME -> WelcomeScreen(onStart = hapticClick(next))
                        OnboardingStep.LANGUAGE -> LanguageStepScreen(
                            selected = language,
                            onSelect = { language = it },
                            onNext = hapticClick {
                                settings.language = language
                                next()
                            },
                            onBack = back,
                        )
                        OnboardingStep.MODEL -> ModelStepScreen(
                            onNext = hapticClick(next),
                            onBack = back,
                        )
                        OnboardingStep.FIRST_DICTATION -> FirstDictationScreen(
                            onNext = hapticClick(next),
                            onBack = back,
                        )
                        OnboardingStep.DONE -> DoneStepScreen(onFinish = hapticClick {
                            settings.language = language
                            OnboardingStore(context).markCompleted()
                            onFinished()
                        })
                        else -> {
                            val item = permissionItemFor(target)!!
                            PermissionStepScreen(
                                item = item,
                                granted = isGranted(snapshot, item),
                                onOpen = hapticClick { openPermission(activity, item) },
                                onNext = hapticClick(next),
                                onBack = back,
                            )
                        }
                    }
                }
            }
        }
    }
}
private const val REQUEST_MIC = 210
private const val REQUEST_NOTIFICATIONS = 211
