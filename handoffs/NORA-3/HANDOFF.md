# NORA-3 — probe -> capture handoff review (DEVICE-REPORT #2 scenario)

Ticket: why does a real capture not start after the dead-source probe, and does the probe's
stop cancel the session? Fix if it is NORA's code, else report the cause to MARA.

**Answer: the probe is NOT the cause. The probe never ran in that session (it needs 300 ms;
the AudioRecord session lasted 12 ms: 21:20:06.862 -> .874). The probe's stop is internal to
the capture thread and cannot cancel the service session.**

## Evidence (all from the exact build on the phone: 4fd07e6)

- Build 4fd07e6 contains capture1 (`git merge-base --is-ancestor agent/capture1 4fd07e6` ok),
  so `AudioRecordSource` MIC-first + probe was active; it also contains ui-device1/2.
- `AudioRecordSource` probe: `PROBE_FRAMES = 15` reads of 20 ms = 300 ms before any switch;
  on switch it stops/reopens the *AudioRecord only* and keeps feeding `onFrame`. It has no
  reference to the service and never publishes state. => "the probe stop cancels the session"
  is false.
- `DictationService.onPress()` starts capture on **ACTION_DOWN**; `onRelease()` runs on
  **ACTION_UP** and, when `holdMs < MIN_HOLD_MS (400)` and no speech, calls
  `engineHost.cancelSession()`, `publishState(Idle)` and shows `hold_to_talk`
  (`DictationService.kt:140-266`, `:589`). The bubble is **push-to-talk**, not tap-to-toggle.
- A scripted `adb shell input tap` is DOWN+UP in ~10-40 ms. That is exactly the observed
  12 ms AudioRecord start/stop: capture started, UP cancelled it, no frame ever reached the
  engine (`bufferedSamples()` at 12 ms is ~0 samples). The model load is a parallel coroutine,
  so it still finished ("Model loaded") and then returned early because `!pressed`.
- `OverlayController.update()` (`:93-120` in 4fd07e6) clamps `layoutParams.x` when the bubble
  expands (168 -> 660 px wide) but does **not restore x when it collapses**. On a bubble at
  x=952..1120 (right edge) expansion moves x to ~460; after Idle it stays at ~460. That is the
  "bubble moved" observation, and it explains the missed second tap at x=1036 (owner's own
  guess): the second tap never hit the bubble, so there is no second AudioRecord session at all.

## Root cause chain for the device run (in order)

1. DOWN -> onPress -> capture starts + bubble expands (x jumps left; looks like a move).
2. UP after 12 ms -> onRelease -> capture stopped (12 ms session), hold too short, session
   cancelled, bubble collapses but stays at the expanded x.
3. Model load completes in parallel (log). No capture, no gate, no transcribe, no insert.
4. Second tap at the old coordinates misses the moved/collapsed bubble -> nothing at all.

None of this is audio-capture logic. It is (a) the interaction model and (b) overlay geometry.

## What MARA/ASHA must fix (blocking MFVP item 2/4)

1. **Tap-to-toggle** (MFVP: "taps ... speaks ... taps again"). In `MicBubbleView`:
   ACTION_UP after DOWN without drag should call `onPress()` on the first tap and
   `onRelease()` on the next tap while `state is Recording` (or cancel if Loading/Idle);
   `onRelease()` must not be the sole stop path. Alternatively latch in the service
   (`onPress` toggles a `capturing` flag). Keep drag/cancel semantics. Add a Robolectric test
   with synthetic DOWN/UP (12 ms apart) asserting capture is still running after the first tap
   and finalizes on the second (that is exactly the device script).
2. **Overlay geometry**: remember the collapsed x/y and restore them when the bubble collapses
   (or expand symmetrically inside the screen bounds). Add a test: expand at the right edge ->
   collapse -> x/y unchanged. This also fixes DEVICE-REPORT #2 bullet 2b.
3. **State logging** (`tag MartinsWhisperState`) at tap/probe/capture/load/gate/infer/insert:
   `DictationService` has only one `Log.d` today (`:289`); `publishState()` should log every
   transition. Without it the owner cannot distinguish (1) from (2) on the next flash.

## My capture-side fixes (in my branch, not the cause above but real hazards)

- Probe fallback is currently fail-closed: it stops+releases the current recorder *before*
  opening the next source, and if that open/start fails it throws
  "Could not open fallback microphone source" -> `fail()` -> session aborted. On HyperOS the
  first ~300 ms after startRecording can be silent while the mic warms up, so this can abort a
  perfectly good session on a device that advertises UNPROCESSED but cannot open it.
- Fix: try the next source first, and on any failure fall back to reopening the source that was
  already working; never trade a working recorder for a failed one; log the decision. Also
  require 500 ms of *exact* digital silence (peak == 0) before switching, so HAL warm-up /
  quiet-room noise does not trigger a switch. See commit on `agent/jni2` (capture-only).
- `scripts/fetch-latest-apk.sh` + native CI (`apk` job) unchanged.

## For the next device build

With tap-to-toggle + restored collapsed position, a real tap must produce in logcat:
`MartinsWhisper: capture start: source=MIC ...` at tap 1, `captured N samples (... dBFS)`
at tap 2. If tap 1 logs the 12 ms start/stop again, the UP is still stopping the session
(check `hold_to_talk` toast).
