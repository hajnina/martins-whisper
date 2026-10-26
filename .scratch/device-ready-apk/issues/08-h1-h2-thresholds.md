# 08 — What H1/H2 thresholds and app matrix define hardware acceptance?

Type: grilling
Status: resolved
Blocked by: 02, 06

## Question

Make hardware acceptance measurable before a device exists:

- Which apps/fields form the insertion matrix (Compose, classic EditText, WebView, chat apps, browser address bar, password/PIN must-refuse, multiline, RTL/emoji)? Pick a realistic short list.
- H1 pass: install, permissions, overlay focus retention, insertion with accessibility on, graceful fallback with it off, reboot re-arm notification, idle unload, no crash across the matrix.
- H2 thresholds: cold/warm latency for base and tiny, peak RAM, idle-unload frees memory (measure method), GPU vs CPU on the actual device, thermal/battery sanity. What numbers pass, what is "report only"?
- How is each result captured (screen recording, `dumpsys meminfo`, logcat excerpt) and attached to the handoff without secrets?
- What blocks sign-off vs what becomes a documented known issue?

Output: H1 checklist + H2 measurement table with pass/fail criteria.

## Answer

**Insertion matrix (run on the real device; keep it short):**

1. Compose Material text field.
2. Classic `EditText`.
3. WebView input.
4. Chrome/browser omnibox.
5. Chat compose (WhatsApp or Signal).
6. Multiline notes field.
7. Emoji + RTL text (e.g. Arabic/Hebrew) — no corruption.
8. Password/PIN field — must refuse and never insert.

**H1 pass (functional):** APK installs; mic + overlay + accessibility grants work; overlay never steals focus from the target field; insertion succeeds in at least 5 of the 7 non-password fields; accessibility-off path falls back to clipboard + notification without crashing; password/PIN refused in all cases; reboot shows the tap-to-re-arm notification; 300 s idle unload is visible and the next dictation reloads; no crash across the matrix.

**H2 measurement table (proposed v1 thresholds; confirm on the device):**

| Metric | How measured | Pass |
|---|---|---|
| End-of-speech → text inserted, `base` CPU, 1 s utterance | Screen recording + logcat timestamps, 10 runs | p50 ≤ 2.0 s, p95 ≤ 4.0 s |
| Same, `tiny` | same | p50 ≤ 1.5 s, p95 ≤ 3.0 s |
| Cold model load | doctor/logcat | `base` ≤ 5 s, `tiny` ≤ 3 s on 8 GB |
| Peak app RSS, `base` | `dumpsys meminfo <pkg>` during/after load | ≤ 1.2 GB, no OOM over 10 dictations |
| Peak app RSS, `tiny` | same | ≤ 800 MB |
| Idle unload after 300 s | doctor + `dumpsys meminfo` before/after | model footprint freed (RSS drop ≥ 80% of loaded model, or ≤ 400 MB total) and next dictation reloads |
| GPU vs CPU | same 10 utterances, backend forced each way | report speedup; GPU must not crash. No minimum speedup for v1 |
| GPU failure handling | force/observe a load failure | fallback to CPU once, persisted reason, 2-strike counter then CPU default, no crash |
| Stability | 10 consecutive dictations, mixed apps | no crash, RSS growth ≤ 10% after warm-up |
| Battery/thermal | battery stats + device temp, 10 min mixed use | report only; fail only if thermal throttling stops dictation |

**Evidence per run:** screen recording of the matrix, `dumpsys meminfo` before/after idle unload, logcat excerpt with backend/fallback and timestamps, doctor output, and a filled `DEVICE-MATRIX.md` row. No secrets or transcript text in the handoff.

**Sign-off:** any H1 functional failure blocks; an H2 threshold miss is a known issue unless it causes OOM, crash, or unusable latency (> 2× the p95 target). Record the device model/Android/RAM/GPU with every number.
