package com.xnvalabs.smarteyex

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.xnvalabs.smarteyex.data.auth.AuthRepository
import com.xnvalabs.smarteyex.ui.screens.activation.ActivationScreen
import com.xnvalabs.smarteyex.ui.screens.auth.PinLockScreen
import com.xnvalabs.smarteyex.ui.screens.auth.PinMode
import com.xnvalabs.smarteyex.ui.screens.call.CallScreen
import com.xnvalabs.smarteyex.ui.screens.device.DeviceScreen
import com.xnvalabs.smarteyex.ui.screens.emergency.EmergencyScreen
import com.xnvalabs.smarteyex.ui.screens.enterprise.EnterpriseScreen
import com.xnvalabs.smarteyex.ui.screens.library.LibraryScreen
import com.xnvalabs.smarteyex.ui.screens.listener.NotificationListenerScreen
import com.xnvalabs.smarteyex.ui.screens.loading.LoadingScreen
import com.xnvalabs.smarteyex.ui.screens.media.MediaScreen
import com.xnvalabs.smarteyex.ui.screens.memory.MemoryScreen
import com.xnvalabs.smarteyex.ui.screens.navigation.NavigationScreen
import com.xnvalabs.smarteyex.ui.screens.privacy.PrivacyPolicyScreen
import com.xnvalabs.smarteyex.ui.screens.privacy.PrivacySettingsScreen
import com.xnvalabs.smarteyex.ui.screens.profile.ProfileScreen
import com.xnvalabs.smarteyex.ui.screens.progress.ProgressScreen
import com.xnvalabs.smarteyex.ui.screens.reminder.ReminderScreen
import com.xnvalabs.smarteyex.ui.screens.system.SystemScreen
import com.xnvalabs.smarteyex.ui.screens.translation.TranslationScreen
import com.xnvalabs.smarteyex.ui.screens.vision.VisionScreen
import com.xnvalabs.smarteyex.ui.screens.xnai.XNAICoreScreen
import com.xnvalabs.smarteyex.data.companion.VoiceCommand
import com.xnvalabs.smarteyex.data.companion.VoiceIntent
import com.xnvalabs.smarteyex.ui.theme.SmartEyeXTheme

/**
 * MainActivity — single entry point for the native app.
 *
 * Navigation is kept local and deterministic so the app startup path stays
 * small and predictable. Every feature returns to the main system surface.
 */
class MainActivity : ComponentActivity() {
    private var backgroundAtElapsed = 0L

    override fun onStart() {
        super.onStart()
        if (backgroundAtElapsed > 0L && SystemClock.elapsedRealtime() - backgroundAtElapsed >= APP_LOCK_AFTER_MS) {
            AuthRepository.lock()
        }
        backgroundAtElapsed = 0L
    }

    override fun onStop() {
        backgroundAtElapsed = SystemClock.elapsedRealtime()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartEyeXTheme {
                SmartEyeXApp()
            }
        }
    }

    companion object {
        private const val APP_LOCK_AFTER_MS = 5 * 60 * 1000L
    }
}

/** All production screens exposed by the current application shell. */
private enum class Screen {
    Loading, PinUnlock, Activation, System, Profile, XnaiCore, Listener,
    PrivacySettings, PrivacyPolicy, PinSetup, Memory, Vision, Reminder, Media, Translation,
    Device, Emergency, Navigation, Call, Library, Progress, Enterprise,
}

@Composable
private fun SmartEyeXApp() {
    var currentScreen by remember { mutableStateOf(Screen.Loading) }
    val isUnlocked by AuthRepository.isUnlocked

    LaunchedEffect(isUnlocked) {
        if (!isUnlocked && currentScreen !in setOf(Screen.Loading, Screen.PinUnlock, Screen.PinSetup)) {
            currentScreen = Screen.PinUnlock
        }
    }

    when (currentScreen) {
        Screen.Loading -> LoadingScreen(
            onFinished = {
                // Feature #26: if a PIN is set, it gates entry every
                // process start (AuthRepository.isUnlocked resets to
                // false on fresh launch) — otherwise skip straight to
                // Activation, same as before this feature existed.
                currentScreen = if (AuthRepository.isUnlocked.value) Screen.Activation else Screen.PinUnlock
            },
        )

        Screen.PinUnlock -> PinLockScreen(
            mode = PinMode.UNLOCK,
            onDone = { currentScreen = Screen.Activation },
        )

        Screen.Activation -> ActivationScreen(
            onActivate = { currentScreen = Screen.System },
        )

        Screen.System -> SystemScreen(
            onNodeSelected = { nodeId ->
                when (nodeId) {
                    "profile" -> currentScreen = Screen.Profile
                    "xnai" -> currentScreen = Screen.XnaiCore
                    "listener" -> currentScreen = Screen.Listener
                    "memory" -> currentScreen = Screen.Memory
                    "vision" -> currentScreen = Screen.Vision
                    "schedule" -> currentScreen = Screen.Reminder
                    "media" -> currentScreen = Screen.Media
                    "translation" -> currentScreen = Screen.Translation
                    "device" -> currentScreen = Screen.Device
                    "emergency" -> currentScreen = Screen.Emergency
                    "navigation" -> currentScreen = Screen.Navigation
                    "call" -> currentScreen = Screen.Call
                    "library" -> currentScreen = Screen.Library
                    "progress" -> currentScreen = Screen.Progress
                    "enterprise" -> currentScreen = Screen.Enterprise
                }
                // Each new destination gets an added branch here as
                // it's built — same incremental pattern as every screen
                // so far.
            },
            onOpenSettings = {
                currentScreen = Screen.PrivacySettings
            },
        )

        Screen.Profile -> ProfileScreen(
            onBack = { currentScreen = Screen.System },
        )

        Screen.XnaiCore -> XNAICoreScreen(
            onBack = { currentScreen = Screen.System },
            onVoiceCommand = { command -> handleVoiceCommand(command) { currentScreen = it } },
        )

        Screen.Listener -> NotificationListenerScreen(
            onBack = { currentScreen = Screen.System },
        )

        Screen.PrivacySettings -> PrivacySettingsScreen(
            onBack = { currentScreen = Screen.System },
            onSetPin = { currentScreen = Screen.PinSetup },
            onOpenPrivacyPolicy = { currentScreen = Screen.PrivacyPolicy },
        )

        Screen.PrivacyPolicy -> PrivacyPolicyScreen(
            onBack = { currentScreen = Screen.PrivacySettings },
        )

        Screen.PinSetup -> PinLockScreen(
            mode = PinMode.SETUP,
            onDone = { currentScreen = Screen.PrivacySettings },
            onCancel = { currentScreen = Screen.PrivacySettings },
        )

        Screen.Memory -> MemoryScreen(
            onBack = { currentScreen = Screen.System },
        )

        Screen.Vision -> VisionScreen(
            onBack = { currentScreen = Screen.System },
        )

        Screen.Reminder -> ReminderScreen(
            onBack = { currentScreen = Screen.System },
        )

        Screen.Media -> MediaScreen(
            onBack = { currentScreen = Screen.System },
        )

        Screen.Translation -> TranslationScreen(
            onBack = { currentScreen = Screen.System },
        )

        Screen.Device -> DeviceScreen(
            onBack = { currentScreen = Screen.System },
        )

        Screen.Emergency -> EmergencyScreen(
            onBack = { currentScreen = Screen.System },
        )

        Screen.Navigation -> NavigationScreen(
            onBack = { currentScreen = Screen.System },
        )

        Screen.Call -> CallScreen(
            onBack = { currentScreen = Screen.System },
        )

        Screen.Library -> LibraryScreen(
            onBack = { currentScreen = Screen.System },
        )

        Screen.Progress -> ProgressScreen(
            onBack = { currentScreen = Screen.System },
        )

        Screen.Enterprise -> EnterpriseScreen(
            onBack = { currentScreen = Screen.System },
        )
    }
}
private fun handleVoiceCommand(command: VoiceCommand, navigate: (Screen) -> Unit) {
    when (command.intent) {
        VoiceIntent.OPEN_MEMORY -> navigate(Screen.Memory)
        VoiceIntent.OPEN_VISION -> navigate(Screen.Vision)
        VoiceIntent.OPEN_TRANSLATION -> navigate(Screen.Translation)
        VoiceIntent.OPEN_NAVIGATION -> navigate(Screen.Navigation)
        VoiceIntent.OPEN_PRIVACY -> navigate(Screen.PrivacySettings)
        VoiceIntent.OPEN_PROFILE -> navigate(Screen.Profile)
        VoiceIntent.OPEN_SYSTEM -> navigate(Screen.System)
        VoiceIntent.READ_NOTIFICATIONS -> navigate(Screen.Listener)
        VoiceIntent.REPLY_NOTIFICATION -> navigate(Screen.Listener)
        VoiceIntent.CREATE_REMINDER -> navigate(Screen.Reminder)
        VoiceIntent.START_EMERGENCY -> navigate(Screen.Emergency)
        VoiceIntent.ASK_XNAI, VoiceIntent.UNKNOWN -> Unit
    }
}

