# MFVP1 HANDOFF — AccessibilityInserter wired into DictationService

Commit: `d5c8521153b3ec81b7359fbf10971c3682d44160` on branch `agent/mfvp1-inserter` (from master @ dec2999). Second commit with this handoff: `git log -1` on the branch after this file.

## Executive summary (<= 10 lines)
- MFVP-1 done: transcripts now attempt real insertion via accessibility, with clipboard only as fallback.
- New `AccessibilityTextInserter(context, fallback = ClipboardTextInserter(context)) : returning-service-InsertResult adapter, `runBlocking` over `AccessibilityInserter.insert` (suspend), mapping accessibility `InsertResult` -> service `InsertResult`.
- When `AccessibilityInserter.connected.value == false`, it delegates to `ClipboardTextInserter` (previous behavior); no throw when the accessibility route errors (mapped to `Failed(reason)`).
- `DictationService.inserterFactory` default (companion, line ~514) switched from `ClipboardTextInserter(it)` to `AccessibilityTextInserter(it)`.
- `AccessibilityInserter` public surface untouched; `DictationAccessibilityService` untouched.
- Tests: 4 new Robolectric unit tests covering both routing paths (adapter direct + through default factory). Full `:app:testDebugUnitTest --offline`: 84 tests, 0 failures, 19 classes.

## Files (exact)
- `app/src/main/kotlin/com/martins/whisper/accessibility/AccessibilityTextInserter.kt` (new): adapter. Maps the two `InsertResult` types (note: accessibility and service packages both define `InsertResult`; service one imported as `ServiceInsertResult`).
- `app/src/main/java/com/martins/whisper/service/DictationService.kt` (2-line change): import `AccessibilityTextInserter`; companion default `inserterFactory = { AccessibilityTextInserter(it) }`.
- `app/src/test/kotlin/com/martins/whisper/accessibility/AccessibilityTextInserterTest.kt` (new): Robolectric test.
- `handoffs/MFVP1-HANDOFF.md`, `handoffs/SUMMARY.txt` (new, this).

## Interfaces exposed / invariants
- `AccessibilityTextInserter(context) : com.martins.whisper.service.TextInserter` — sync facade over `AccessibilityInserter.insert` (suspend). Secondary ctor param `fallback: TextInserter` for test seams.
- Invariant: when the accessibility service is not connected, behavior is exactly the old clipboard copy (`InsertResult.Copied("clipboard")` on success).
- Any Throwable from the accessibility/dispatcher path is mapped to `InsertResult.Failed(message)` so the dictation coroutine cannot crash.
- No contract changes; `TextInserter`, `InsertResult`, `AccessibilityInserter` shapes unchanged.

## How to run + evidence
Env: `JAVA_HOME=/work/android-toolchain/jdk`, `local.properties` copied from main checkout (`sdk.dir=/work/android-toolchain/sdk`), `GRADLE_USER_HOME=/home/node/gh-mara` (cloned from `/work/android-toolchain/gradle-home` because it was missing).

Exact command (inside worktree):
```
JAVA_HOME=/work/android-toolchain/jdk GRADLE_USER_HOME=/home/node/gh-mara ./gradlew :app:testDebugUnitTest --offline --rerun-tasks
```
Output tail:
```
> Task :app:compileDebugUnitTestKotlin
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 14s
30 actionable tasks: 30 executed
```
Test counts (from `app/build/test-results/testDebugUnitTest/TEST-*.xml`): `TOTAL=84 SKIPPED=0 FAILURES=0 FILES=19`.
New tests: `TEST-com.martins.whisper.accessibility.AccessibilityTextInserterTest.xml` -> `tests="4" failures="0" errors="0"`:
1. adapter routes insert through AccessibilityInserter when service is connected (empty text yields `Failed("empty text")` — proves routing through `AccessibilityInserter.insert`)
2. adapter falls back to clipboard when service is not connected (`Copied("clipboard")`)
3. default `DictationService` factory uses accessibility inserter when connected
4. default `DictationService` factory falls back to clipboard when disconnected

Technique note for reviewers: `AccessibilityInserter.attach/detach` are `internal` but same-module, so the Robolectric test drives connection state directly on a `Robolectric.buildService(DictationAccessibilityService)` instance; empty-string insert short-circuits before any node interaction, giving deterministic routing proofs without node shadows.

## Known limitations / next agent
- Real insertion on a physical device needs the accessibility service actually connected (H1/H2 physical device); not verified on device here — covered by existing `AccessibilityInsertionReadBackTest` (androidTest).
- `runBlocking` runs inside the service's `Dispatchers.Main.immediate` coroutine; `AccessibilityInserter.insert` uses `Main.immediate` too, so it executes in place (no deadlock). Long accessibility work can still jank the frame; acceptable per ticket.
- MFVP-2 (feel/bubble focus) remains open; not touched.

## References
- Ticket `.scratch/device-ready-apk/tickets/MFVP-1-inserter-wired.md` (MARA audit evidence: DictationService.kt:514, DictationAccessibilityService.kt:267).
- No external repos used; adapted existing in-repo seams (`DictationServiceRobolectricTest` factory swap pattern).
