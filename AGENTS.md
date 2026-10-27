# Agent Rules - Martins Whisper

Trunk-based development by a small agent swarm. The REVIEW/INTEGRATION PM owns all landings on master; you own one short-lived worktree and one small slice.

## Process (current, 2026-10)
- Work ONLY in your assigned worktree `/work/martins-whisper-worktrees/<task>` on a short branch, created from master. Never edit the main checkout `/work/martins-whisper`, except the PM landing.
- Commit wip at least every 10 minutes with clear messages.
- Landing is the PM's job, via `owner/tools/land.sh --cloud` (fast-forward only, gate = the "Android CI" workflow). Do NOT push to GitHub; do not merge/rebase into master yourself.
- No handoff documents, no KANBAN tickets, no coordination docs. Your final message is your report: files changed (relative paths), the acceptance command with its real output pasted, and any leftovers.
- Keep slices <= 300 changed lines. One vertical behaviour per slice; do not rename/move shared contract files without the PM's say-so. If an interface change is needed, report it, do not edit the shared contract silently.
- Hard scope rule: only onboarding, model download, first dictation, home screen, theme/redesign work. Anything else is scope creep; refuse and report.

## Build and test
```
. /work/android-toolchain/scripts/env.sh
export GRADLE_USER_HOME=/work/.gradle-home
/work/owner/tools/gradle-slot.sh :core:test :app:testDebugUnitTest --offline
/work/owner/tools/gradle-slot.sh :app:compileDebugKotlin --offline   # when you only need a compile check
```
- Gradle only through `owner/tools/gradle-slot.sh` (never plain `gradle`): it caps RAM and never leaves a daemon. Robolectric is pinned to SDK 34; do not change test pins or budgets without PM approval.
- Include one fresh-state launch test when you touch a launch entry point (a fresh install must not crash).
- If blocked by environment (no SDK/KVM, network), still deliver code + a precise blocked report + exact unblock steps. Never fake test output. Run the tests you create and paste counts.

## Code style
- Kotlin idiomatic, no comments except non-obvious constraints.
- No secrets, no telemetry, no cloud inference.
- UI work is Material 3 Expressive: colours only from `MaterialTheme.colorScheme`, motion only from theme motion (`MaterialTheme.motionScheme` / onboarding `Motion.kt` tokens while material3 stays on 1.4.x), haptics via `LocalHapticFeedback`. Copy official examples; do not invent components.
- Keep dependencies minimal; justify any new dependency in your final message.

## Domain
- App id `com.martins.whisper`; onboarding is a Compose flow in `app/src/main/java/com/martins/whisper/onboarding/`; legacy settings UI stays behind a gear.
- Terminology and standing decisions: `DECISIONS.md`, `SPEC.md`. `KANBAN.md` is historical; ignore it.
- Never touch the adb serial `R3CW40M8PLZ` (infra phone).
