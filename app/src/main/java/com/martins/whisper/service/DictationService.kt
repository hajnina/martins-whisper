package com.martins.whisper.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.martins.whisper.MainActivity
import com.martins.whisper.R
import com.martins.whisper.audio.AudioRecordSource
import com.martins.whisper.audio.AudioSource
import com.martins.whisper.core.AudioChunk
import com.martins.whisper.core.ChunkReason
import com.martins.whisper.core.SpeechEngine
import com.martins.whisper.data.SettingsStore
import com.martins.whisper.overlay.MicBubbleView
import com.martins.whisper.overlay.OverlayController
import com.martins.whisper.core.asr.ModelCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

class DictationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var settings: SettingsStore
    private lateinit var overlay: OverlayController

    private var engine: SpeechEngine? = null
    private var engineModelId: String? = null
    private lateinit var audioSource: AudioSource
    private val frames = ArrayList<ShortArray>()
    private var sessionId = 0
    private var pressed = false
    private var sessionActive = false
    private var speechDetected = false
    @Volatile
    private var currentLevel = 0f
    private var sessionStartElapsed = 0L
    private var timerJob: Job? = null
    private var idleUnloadJob: Job? = null
    private var stateResetJob: Job? = null
    private var lastNotificationKey: String? = null

    private val overlayListener = object : MicBubbleView.Listener {
        override fun onPress() = this@DictationService.onPress()
        override fun onRelease() = this@DictationService.onRelease()
        override fun onCancel() = cancelSession()
        override fun onDrag(dx: Float, dy: Float) = overlay.dragBy(dx, dy)
        override fun onDragEnd() = overlay.persistPosition()
        override fun onSettingsTap() = openSettings()
        override fun onStopTap() = stopEverything()
    }

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        audioSource = audioSourceFactory(this)
        overlay = OverlayController(this)
        createNotificationChannel()
        publishState(DictationState.Idle)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            ACTION_SETTINGS -> openSettings()
        }
        val prohibitMicType = intent?.getBooleanExtra(EXTRA_NO_MIC, false) == true
        ensureForeground(withMicrophone = !prohibitMicType && hasMicrophonePermission())
        startOverlay()
        return START_STICKY
    }

    private fun startOverlay() {
        if (overlay.isShowing) {
            overlay.update(state.value)
            return
        }
        if (!overlay.show(overlayListener)) {
            notifyOverlayMissing()
        } else {
            overlay.update(state.value)
        }
    }

    private fun onPress() {
        if (pressed) return
        if (state.value.isBusy) return
        pressed = true
        sessionActive = true
        sessionId += 1
        val id = sessionId
        sessionStartElapsed = SystemClock.elapsedRealtime()
        speechDetected = false
        currentLevel = 0f
        synchronized(frames) { frames.clear() }
        idleUnloadJob?.cancel()
        stateResetJob?.cancel()
        val modelName = ModelCatalog.byId(settings.modelId)?.displayName ?: settings.modelId
        publishState(DictationState.Loading("Loading $modelName\u2026"))

        scope.launch {
            val loaded = try {
                obtainEngine()
            } catch (t: Throwable) {
                fail(t.message ?: "Engine failed to load")
                return@launch
            }
            if (!pressed || id != sessionId) return@launch
            if (!hasMicrophonePermission()) {
                fail(getString(R.string.error_mic))
                return@launch
            }
            ensureForeground(withMicrophone = true)
            if (!startAudio(id)) return@launch
            publishState(DictationState.Recording(0L, 0f, false))
            startTimer(id)
        }
    }

    private suspend fun obtainEngine(): SpeechEngine {
        val modelId = settings.modelId
        engine?.let { active ->
            if (engineModelId == modelId) return active
            runCatching { active.close() }
            engine = null
            engineModelId = null
        }
        val created = withContext(Dispatchers.Default) {
            EngineFactory.create(this@DictationService, settings)
        }
        engine = created
        engineModelId = modelId
        return created
    }

    private fun startAudio(id: Int): Boolean {
        var started = true
        try {
            audioSource.start(
                onFrame = { samples, capturedAt ->
                    if (!sessionActive || id != sessionId) return@start
                    synchronized(frames) {
                        if (frames.size < MAX_FRAMES) frames.add(samples)
                    }
                    val rms = AudioRecordSource.rms(samples)
                    currentLevel = max(rms * 4f, currentLevel * 0.7f).coerceIn(0f, 1f)
                    if (rms > SPEECH_RMS_THRESHOLD) speechDetected = true
                    mainHandler.post {
                        if (pressed && id == sessionId) {
                            publishState(
                                DictationState.Recording(
                                    SystemClock.elapsedRealtime() - sessionStartElapsed,
                                    currentLevel,
                                    speechDetected,
                                ),
                            )
                        }
                    }
                },
                onError = { throwable ->
                    if (sessionActive && id == sessionId) {
                        scope.launch { fail(throwable.message ?: "Audio capture failed") }
                    }
                },
            )
        } catch (t: Throwable) {
            started = false
            fail(t.message ?: "Audio capture failed")
        }
        return started
    }

    private fun startTimer(id: Int) {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && pressed && id == sessionId) {
                val elapsed = SystemClock.elapsedRealtime() - sessionStartElapsed
                if (elapsed >= MAX_HOLD_MS) {
                    onRelease()
                    break
                }
                publishState(DictationState.Recording(elapsed, currentLevel, speechDetected))
                delay(100L)
            }
        }
    }

    private fun onRelease() {
        if (!pressed) return
        pressed = false
        timerJob?.cancel()
        if (!sessionActive) return
        sessionActive = false
        val holdMs = SystemClock.elapsedRealtime() - sessionStartElapsed
        stopAudio()

        val sampleCount = synchronized(frames) { frames.sumOf { it.size } }
        val durationMs = sampleCount * 1000L / SAMPLE_RATE
        if (holdMs < MIN_HOLD_MS && !speechDetected) {
            synchronized(frames) { frames.clear() }
            publishState(DictationState.Idle)
            Toast.makeText(this, R.string.hold_to_talk, Toast.LENGTH_SHORT).show()
            return
        }
        if (durationMs < MIN_AUDIO_MS) {
            synchronized(frames) { frames.clear() }
            publishState(DictationState.Idle)
            return
        }
        finalizeSession(sampleCount, durationMs)
    }

    private fun finalizeSession(sampleCount: Int, durationMs: Long) {
        val samples = FloatArray(sampleCount)
        var write = 0
        synchronized(frames) {
            for (frame in frames) {
                for (sample in frame) {
                    if (write >= samples.size) break
                    samples[write++] = sample / 32768f
                }
            }
            frames.clear()
        }
        publishState(DictationState.Processing())
        val language = settings.effectiveLanguage()
        scope.launch {
            val activeEngine = engine
            if (activeEngine == null) {
                fail("Model not loaded")
                return@launch
            }
            try {
                val transcript = withContext(Dispatchers.Default) {
                    activeEngine.transcribe(
                        AudioChunk(
                            index = 0,
                            samples = samples,
                            startMs = 0L,
                            endMs = durationMs,
                            reason = ChunkReason.FLUSH,
                        ),
                        language,
                    )
                }
                val text = transcript.text.trim()
                if (text.isEmpty()) {
                    publishState(DictationState.Error("No speech detected"))
                    scheduleReset()
                    return@launch
                }
                emitTranscript(text)
                val result = inserterFactory(this@DictationService).insert(text)
                val message = when (result) {
                    InsertResult.Inserted -> "Inserted"
                    is InsertResult.Copied -> "Transcript copied to clipboard"
                    is InsertResult.Failed -> "Copy failed: ${result.reason}"
                }
                publishState(DictationState.Success(message))
                updateNotification(DictationState.Success(message), force = true)
                scheduleReset()
                scheduleIdleUnload()
            } catch (t: Throwable) {
                fail(t.message ?: "Transcription failed")
            }
        }
    }

    private fun emitTranscript(text: String) {
        val intent = Intent(ACTION_TRANSCRIPT)
            .setPackage(packageName)
            .putExtra(EXTRA_TRANSCRIPT, text)
        sendBroadcast(intent)
    }

    private fun cancelSession() {
        if (!pressed && !sessionActive) return
        pressed = false
        sessionActive = false
        sessionId += 1
        timerJob?.cancel()
        stopAudio()
        synchronized(frames) { frames.clear() }
        publishState(DictationState.Idle)
    }

    private fun fail(message: String) {
        pressed = false
        sessionActive = false
        sessionId += 1
        timerJob?.cancel()
        stopAudio()
        synchronized(frames) { frames.clear() }
        publishState(DictationState.Error(message))
        scheduleReset()
    }

    private fun scheduleReset() {
        stateResetJob?.cancel()
        stateResetJob = scope.launch {
            delay(STATE_RESET_MS)
            if (!pressed && !sessionActive) publishState(DictationState.Idle)
        }
    }

    private fun scheduleIdleUnload() {
        idleUnloadJob?.cancel()
        val idleMs = settings.idleUnloadSeconds.coerceAtLeast(30) * 1000L
        idleUnloadJob = scope.launch {
            delay(idleMs)
            if (!pressed && !sessionActive) {
                runCatching { engine?.close() }
                engine = null
                engineModelId = null
            }
        }
    }

    private fun stopAudio() {
        runCatching { audioSource.stop() }
    }

    private fun stopEverything() {
        settings.overlayEnabled = false
        pressed = false
        sessionActive = false
        sessionId += 1
        timerJob?.cancel()
        idleUnloadJob?.cancel()
        stateResetJob?.cancel()
        stopAudio()
        synchronized(frames) { frames.clear() }
        runCatching { engine?.close() }
        engine = null
        engineModelId = null
        overlay.hide()
        publishState(DictationState.Idle)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun openSettings() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureForeground(withMicrophone: Boolean = hasMicrophonePermission()) {
        val type = if (withMicrophone &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(state.value), type)
        } catch (t: Throwable) {
            updateNotification(
                DictationState.Error(t.message ?: "Foreground service denied"),
                force = true,
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(current: DictationState): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            REQUEST_CONTENT,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val settingsIntent = PendingIntent.getActivity(
            this,
            REQUEST_SETTINGS,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, DictationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = notificationText(current)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, getString(R.string.action_settings), settingsIntent)
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .build()
    }

    private fun notificationText(current: DictationState): String = when (current) {
        is DictationState.Idle -> getString(R.string.notification_text)
        is DictationState.Loading -> current.label
        is DictationState.Recording -> getString(R.string.notification_recording)
        is DictationState.Processing -> getString(R.string.notification_processing)
        is DictationState.Success -> current.message
        is DictationState.Error -> current.message
    }

    private fun notifyOverlayMissing() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_overlay_missing))
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    REQUEST_CONTENT,
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun updateNotification(current: DictationState, force: Boolean = false) {
        val key = current::class.simpleName ?: "state"
        if (!force && key == lastNotificationKey) return
        lastNotificationKey = key
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification(current)) }
    }

    private fun publishState(newState: DictationState) {
        _state.value = newState
        overlay.update(newState)
        updateNotification(newState)
    }

    override fun onDestroy() {
        pressed = false
        sessionActive = false
        sessionId += 1
        timerJob?.cancel()
        idleUnloadJob?.cancel()
        stateResetJob?.cancel()
        stopAudio()
        runCatching { audioSource.release() }
        runCatching { engine?.close() }
        engine = null
        engineModelId = null
        overlay.hide()
        _state.value = DictationState.Idle
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.martins.whisper.action.START"
        const val ACTION_STOP = "com.martins.whisper.action.STOP"
        const val ACTION_SETTINGS = "com.martins.whisper.action.SETTINGS"
        const val ACTION_TRANSCRIPT = "com.martins.whisper.action.TRANSCRIPT"
        const val EXTRA_TRANSCRIPT = "transcript"
        const val EXTRA_NO_MIC = "no_mic_type"

        const val CHANNEL_ID = "dictation"
        const val NOTIFICATION_ID = 4201
        private const val REQUEST_CONTENT = 10
        private const val REQUEST_SETTINGS = 11
        private const val REQUEST_STOP = 12

        private const val SAMPLE_RATE = 16_000
        private const val MIN_HOLD_MS = 400L
        private const val MIN_AUDIO_MS = 150L
        private const val MAX_HOLD_MS = 600_000L
        private const val MAX_FRAMES = 30_000
        private const val STATE_RESET_MS = 1_800L
        private const val SPEECH_RMS_THRESHOLD = 0.015f

        private val _state = MutableStateFlow<DictationState>(DictationState.Idle)
        val state: StateFlow<DictationState> = _state.asStateFlow()

        @Volatile
        var inserterFactory: (Context) -> TextInserter = { ClipboardTextInserter(it) }

        @Volatile
        internal var audioSourceFactory: (Context) -> AudioSource = { AudioRecordSource(it) }

        fun start(context: Context) {
            val intent = Intent(context, DictationService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, DictationService::class.java).setAction(ACTION_STOP)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
