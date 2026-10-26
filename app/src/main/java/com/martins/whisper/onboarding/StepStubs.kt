package com.martins.whisper.onboarding
import androidx.compose.runtime.Composable
import com.martins.whisper.ui.PermissionItem
// Temporary stubs so this branch compiles before the step screens land (worker W2 replaces
// this file with PermissionStepScreen.kt / LanguageStepScreen.kt / DoneStepScreen.kt).
@Composable
fun PermissionStepScreen(
    item: PermissionItem,
    granted: Boolean,
    onOpen: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) = Unit
@Composable
fun LanguageStepScreen(
    selected: String,
    onSelect: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) = Unit
@Composable
fun DoneStepScreen(onFinish: () -> Unit) = Unit
