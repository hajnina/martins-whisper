package com.martins.whisper.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.martins.whisper.MainActivity
import com.martins.whisper.core.Flags

/**
 * Full-screen onboarding (view-hosted Compose). Gated by [Flags.onboardingV2]; the default
 * MainActivity path stays the plain settings screen until the lane's acceptance passes.
 */
class OnboardingActivity : ComponentActivity() {

    private lateinit var store: OnboardingStore
    private var step by mutableStateOf(OnboardingStep.WELCOME)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = OnboardingStore(this)
        if (store.isCompleted() && !intent.getBooleanExtra(EXTRA_FORCE, false)) {
            openMain()
            return
        }
        setContent {
            MartinsOnboardingTheme {
                Flow()
            }
        }
    }

    @Composable
    private fun Flow() {
        val durations = LocalMotionDurations.current
        BackHandler(enabled = step != OnboardingStep.WELCOME) { goBack() }
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (durations.enter == 0) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    fadeIn(tween(durations.enter, easing = FastOutSlowInEasing)) togetherWith
                        fadeOut(tween(durations.exit))
                }
            },
            label = "onboarding-step",
        ) { current ->
            when (current) {
                OnboardingStep.WELCOME -> WelcomeScreen(
                    onStart = { finishOnboarding() },
                )
            }
        }
    }

    private fun goBack() {
        OnboardingRules.stepBefore(step)?.let { step = it }
    }

    private fun finishOnboarding() {
        store.markCompleted()
        openMain()
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        const val EXTRA_FORCE = "com.martins.whisper.onboarding.FORCE"
    }
}
