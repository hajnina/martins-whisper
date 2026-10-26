# Martins Whisper - Product + Technical Spec (v2, Android)

Updated: 2026-10-26T15:05Z
Owner: PM (orchestrator)
Target: Android phone/tablet, arm64-v8a, Android 10+ (minSdk 29), sideloaded APK.

## 1. Product requirements

R1. **Always-on overlay.** A small floating mic bubble stays above other apps while a foreground service runs. Tap-and-hold the bubble to record; release to transcribe. A small settings affordance is also available. The overlay must not steal input focus from the app the user is typing in.
R2. **Insert into the focused text field.** On release, after transcription, insert text at the current cursor/selection in the previously focused editable field. Preferred mechanism: Android `AccessibilityService` (`ACTION_SET_TEXT`, fallback clipboard + `ACTION_PASTE`). If no editable field is focused, put the text on the clipboard and show a clear notification/toast.
R3. **Local-only inference.** No cloud ASR/TTS/LLM. Network is used only for explicit model downloads. Audio is never persisted (unless a debug recording flag is explicitly enabled). Transcripts are not logged by default.
R4. **Default model = Whisper base, configurable.** Settings offers at least tiny/base/small/medium/large-v3-turbo (multilingual and/or English), plus import of a custom GGML model file. ONNX/custom-engine import is a documented v1 non-goal (ADR-004). Model choice persists.
R5. **GPU by default, CPU fallback.** Prefer a Vulkan/NNAPI GPU path. Detect availability, fall back to CPU, and surface the active backend in settings/doctor UI.
R6. **Idle unload after 300 s.** Free the model from RAM/VRAM after 5 minutes without use. Reload lazily on the next hold. A new hold may start recording while the model is loading; transcription waits for load.
R7. **Chunking.** Long holds are split at silences using a VAD, with a max chunk length (~25 s) and overlapping forced splits. Chunks are transcribed incrementally while the user is still holding; final text is merged without duplicated boundary words. Tap < 400 ms cancels (Wispr Flow-like).
R8. **Robust failure modes.** Mic denied, overlay denied, accessibility off, model missing, OOM, engine crash, insertion failure. Always keep the transcript recoverable (clipboard + notification + optional local history off by default).
R9. **Tidy + testable.** Pure-Kotlin core logic (VAD/chunker/merge/model state) runs in JVM unit tests. Android shell has Robolectric/instrumented tests. Build and device validation instructions included.
R10. **Mega simple first.** No accounts, no sync, no cloud, no LLM cleanup in v1. Minimal screens: onboarding/permissions + settings + overlay.

## 2. Non-goals (v1)

- Play Store distribution / accessibility-policy review.
- iOS, desktop, web.
- Multi-language UI.
- Punctuation/rewrite LLM post-processing.
- Speaker diarization.
- Syncing/history across devices.
- Hands-free mode (hold-only is the requirement).

## 3. Architecture

```
┌─────────────────────────────────────────────────────────────┐
│ ForegroundService (microphone type)                         │
│  ├─ OverlayController (WindowManager TYPE_APPLICATION_OVERLAY)
│  │    └─ MicBubbleView (custom Canvas view, non-focusable)
│  ├─ AudioCapture (AudioRecord 16 kHz mono PCM16)            │
│  ├─ DictationPipeline                                       │
│  │    ├─ ring buffer + pre-roll                              │
│  │    ├─ EnergyVad -> AudioChunker (core, pure Kotlin)       │
│  │    ├─ TranscriptMerger (core, pure Kotlin)               │
│  │    └─ SpeechEngine (whisper.cpp Vulkan/CPU, GGML)         │
│  ├─ ModelManager (lazy load, idle unload 300 s, model swap)  │
│  └─ SessionStateMachine                                     │
├─────────────────────────────────────────────────────────────┤
│ AccessibilityService (separate system-bound service)        │
│  ├─ tracks focused editable node + selection                 │
│  ├─ TextInsertionPlanner (pure logic)                        │
│  └─ ACTION_SET_TEXT -> clipboard/ACTION_PASTE -> toast       │
├─────────────────────────────────────────────────────────────┤
│ Activity: onboarding/permissions + settings + model manager  │
└─────────────────────────────────────────────────────────────┘
```

### 3.1 Modules

- `core/` - pure Kotlin/JVM, no Android imports.
  - `config/` AppSettings data model + validation (persistence in app).
  - `audio/` `EnergyVad`, `AudioChunker`, `AudioMath`, `RingBuffer`.
  - `text/` `TranscriptMerger`, `TextNormalizer`, hallucination filter.
  - `asr/` `SpeechEngine` interface, `Transcript`, `ModelSpec`, `ModelCatalog`, `ModelManager` state machine with injectable clock.
  - `insert/` `TextInsertionPlanner` (given current text + selection + transcript -> newText + cursor).
  - Unit tests (JUnit) for all of the above.
- `app/` - Android.
  - `service/DictationService` foreground service; owns pipeline + overlay + engine.
  - `overlay/` window + custom view + drag/position persistence.
  - `audio/AudioRecordSource`.
  - `asr/WhisperCppEngine` (JNI), `SherpaOnnxEngine` fallback if chosen, `NativeModelLoader`.
  - `accessibility/DictationAccessibilityService`, `AccessibilityTextInserter`.
  - `data/SettingsStore` (SharedPreferences/DataStore), `ModelRepository` (download/import/verify).
  - `ui/` MainActivity, onboarding, settings, diagnostics.
  - `BootReceiver`.
- `native/` (or `app/src/main/cpp`): CMake + JNI wrapper + vendored whisper.cpp (submodule/pinned source).
- `scripts/`: fetch models, adb push, build, doctor.
- `docs/`: build, device, testing, permissions.

### 3.2 Core contracts (do not break without PM approval)

```kotlin
// core
data class Frame(val samples: ShortArray, val sampleRate: Int = 16000, val capturedAtMs: Long)
data class AudioChunk(
  val index: Int,
  val samples: FloatArray,      // mono, [-1, 1], 16 kHz
  val sampleRate: Int = 16000,
  val startMs: Long,
  val endMs: Long,
  val overlapMs: Int = 0,
  val reason: ChunkReason
)
enum class ChunkReason { SILENCE, MAX_LENGTH, FLUSH }

interface Vad { fun isSpeech(frame: ShortArray): Boolean; fun reset() }
class EnergyVad(...) : Vad
class AudioChunker(vad: Vad, ...) {
  fun push(frame: ShortArray, capturedAtMs: Long): List<AudioChunk>
  fun flush(capturedAtMs: Long): AudioChunk?
  fun reset()
  val speechMs: Long
}

data class Transcript(val text: String, val language: String?, val avgLogProb: Float?, val noSpeechProb: Float?)
interface SpeechEngine {
  val backend: String            // "whisper.cpp/vulkan", "whisper.cpp/cpu", ...
  suspend fun transcribe(chunk: AudioChunk, language: String?): Transcript
  fun close()
}
class ModelManager<T: Any>(
  loader: () -> T, closer: (T) -> Unit, clock: () -> Long, idleMs: Long = 300_000
) {
  fun getOrLoad(): T
  fun markUsed()
  fun maybeUnload()
  fun unloadNow()
  fun <R> withModel(block: (T) -> R): R
  val state: ModelState
}

object ModelCatalog { val specs: List<ModelSpec>; fun byId(id: String): ModelSpec? }
object TranscriptMerger { fun merge(parts: List<String>, overlapsMs: List<Int> = emptyList()): String }
object TextInsertionPlanner {
  data class Result(val text: String, val selectionStart: Int, val selectionEnd: Int)
  fun insert(current: String?, selectionStart: Int, selectionEnd: Int, insert: String): Result
}
```

App-only interfaces:

```kotlin
interface AudioSource { fun start(onFrame: (ShortArray, Long) -> Unit); fun stop(); fun release() }
interface TextInserter { fun insert(text: String): InsertResult }
sealed interface InsertResult { data object Inserted; data class Copied(val reason: String); data class Failed(val reason: String) }
```

### 3.3 Dictation state machine

```
UNLOADED/READY --press--> ARMING (start audio + lazy load model)
ARMING --frames--> RECORDING (chunks queued to GPU worker)
RECORDING --release--> FINALIZING (flush last chunk, wait for worker, merge)
FINALIZING --text--> INSERTING --> IDLE
any --cancel--> IDLE (discard, keep model state)
any --error--> ERROR (surface, return to IDLE)
release with hold < 400 ms and no speech --> IDLE (cancel)
```

Model load and transcription run off the main thread (coroutines `Dispatchers.Default`/single-thread ASR dispatcher). UI gets state via `StateFlow` + main-thread rendering.

### 3.4 Chunking algorithm (v1)

- AudioRecord: 16 kHz mono PCM16, 20 ms frames (320 samples). Pre-roll ring buffer 300 ms.
- `EnergyVad`: adaptive noise floor (EMA of non-speech), RMS threshold = max(noiseFloor * k, absoluteFloor); speech start after 2 frames; silence after `minSilenceMs` (500 ms). Reset per session.
- `AudioChunker`:
  - accumulates frames after speech start (including pre-roll).
  - finalizes `SILENCE` chunk when trailing silence >= 500 ms and speech >= 250 ms.
  - finalizes `MAX_LENGTH` at 25 s; cut at lowest-energy frame in the last 2 s; add 250 ms overlap from the tail to the next chunk.
  - `flush()` finalizes trailing speech/partial chunk; drops pure-silence.
  - max session 10 min safety cap.
- `TranscriptMerger.merge`:
  - trims, strips duplicate boundary words between adjacent chunks (case/punctuation-insensitive longest suffix/prefix match, up to 8 words).
  - joins with exactly one space, preserves existing punctuation; never invents punctuation.
- `TextNormalizer`: drop empty/hallucination segments using `noSpeechProb`, `avgLogProb` threshold, compression ratio (engine-provided), plus a small blocklist ("Thank you.", "Subtitles by...", etc.) only when confidence is low.
- Engine `transcribe(chunk)` gets `condition_on_previous_text = false` and a stable language from the first detected chunk; no cross-chunk prompt loops.

### 3.5 Model lifecycle

- Default `base` (multilingual GGML, 16-bit). Catalog includes tiny/base/small/medium/large-v3-turbo, `.en` variants where available, plus custom file. Default backend `auto` (GPU if compiled+available else CPU).
- Model files live in `context.filesDir/models/<id>.bin`; SHA256 verified after download/import.
- First run: onboarding explains local-only and offers "download base" (or `adb push` path). App does not silently download.
- `ModelManager`: ref-counted or single ASR thread; load on first use; `markUsed()` after each transcription/insert; idle watchdog every 15 s; unload at 300 s, `whisper_free` + null reference + `System.gc()` best-effort. Model switch unloads immediately.
- While unloaded, hold still starts recording and shows "loading model"; once loaded, queued chunks transcribe.
- GPU: native build with Vulkan (`GGML_VULKAN=1`); runtime exposes backend string. If Vulkan init fails, retry CPU once and remember fallback. If native engine cannot be built, sherpa-onnx NNAPI/CPU is a fallback architecture under an ADR (pending spike).

### 3.6 Android insertion

- `DictationAccessibilityService` tracks `TYPE_VIEW_FOCUSED` / `TYPE_VIEW_TEXT_SELECTION_CHANGED` and `findFocus(FOCUS_INPUT)`.
- Overlay window uses `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL`, so pressing it does not move input focus.
- Insertion attempt order:
  1. Read focused node text + selection. Use `ACTION_SET_TEXT` with full new text and selection at end (works in many native fields).
  2. If unsupported/failed: set clipboard, perform `ACTION_PASTE` on focused editable node.
  3. If no editable node: copy to clipboard, toast + notification "Copied; paste manually".
- Do not clear clipboard automatically (Android background clipboard read is restricted; leaving text is safer/private enough and useful). Optional future setting.
- Handle WebView/Compose fields that expose editable nodes but not text: use paste fallback.

### 3.7 Overlay UX

- Idle: 56 dp circular bubble, bottom-end by default, draggable; position persisted. Small gear target inside expanded pill or via notification action.
- Press: haptic, expand to pill, start recording, show elapsed time + live level bars.
- Loading: spinner + "Loading base..."
- Processing: shimmer + "Transcribing"; success: check flash; error: short red message.
- Release before 400 ms: cancel with subtle "Hold to talk" toast.
- Non-focusable always; do not show IME; no full-screen overlay.
- Respect screen-off: stop overlay rendering when display off? Service stays; optional.

### 3.8 Permissions / onboarding

- Microphone (`RECORD_AUDIO`), overlay (`SYSTEM_ALERT_WINDOW`), accessibility service (settings deep link), notifications (`POST_NOTIFICATIONS` on 13+), foreground service microphone (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`).
- Battery optimization exemption prompt (optional, Samsung/Xiaomi especially).
- Boot start (`RECEIVE_BOOT_COMPLETED`) to restore overlay after reboot; user can disable.
- Internet permission only for model download; app works with network disabled after download. No analytics.

## 4. Build / test plan

- JDK 17, Gradle 8.x, AGP 8.x, Kotlin 2.x, compileSdk 35, minSdk 29, targetSdk 35.
- ABI: arm64-v8a primary; x86_64 only if emulator/build feasibility allows.
- Native: CMake + NDK, whisper.cpp pinned; Vulkan build for arm64.
- Tests:
  - `./gradlew :core:test` pure JVM (chunking, merge, model idle, insertion planning).
  - `./gradlew :app:testDebugUnitTest` Robolectric (settings, service state helpers, permission gates).
  - `./gradlew :app:assembleDebug` (APK).
  - Device/emulator: instrumentation for overlay + accessibility + fake engine + real model smoke. Blocked here (no KVM, aarch64 host); run on real device later.
  - GPU/Vulkan validation: blocked on hardware; `doctor` must print backend. A CPU-only emulator does not satisfy GPU acceptance.
- CI: GitHub Actions workflow for x86_64 Linux runner (SDK/NDK) to build + unit tests; artifacts. Local Dockerfile for same.

## 5. Acceptance criteria (v1)

1. Install APK, grant mic + overlay + accessibility, download/import base model.
2. Overlay bubble is visible above apps and does not move focus from a text field.
3. Hold bubble 2 s, say "hello world", release; "hello world" appears at cursor in a test EditText and in a common chat app.
4. Hold 60 s of speech with pauses; text is complete, no duplicated boundary words, survives 25 s splits.
5. Settings switch to `tiny` and reload; next dictation uses tiny. Custom model import works.
6. Backend shows GPU on a Vulkan device; falls back to CPU with a clear indication if not.
7. After 5 min idle, model is unloaded (status + memory/VRAM drop); next hold reloads and works.
8. Tap < 400 ms does not insert; cancel path discards audio.
9. No network traffic during dictation; audio not persisted.
10. Unit tests + build pass; device test checklist documented.

## 6. Open questions / risks

- **ASR engine choice**: whisper.cpp+JNI+Vulkan vs sherpa-onnx AAR (NNAPI/CPU) vs both. Native build on this aarch64 sandbox is hard; ADR pending research spike.
- **GPU reality on Android**: Vulkan whisper.cpp speedup is modest on some devices; NNAPI may not use GPU. Must measure on the eventual device.
- **Accessibility insertion** varies by app (Compose/WebView/custom editors). Need field matrix + fallback.
- **Model licensing/size**: Whisper weights MIT; base ~142 MB. Bundling vs download.
- **Emulator**: no `/dev/kvm`, host aarch64; official Android emulator likely unavailable here. Build/tests may be limited to JVM/Robolectric until a device/x86_64 runner is available.
- **Background service OEM killers**: document battery settings.
- **Target device specifics** (Android version, GPU vendor, RAM) unknown until provided.
