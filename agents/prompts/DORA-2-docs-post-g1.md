You are DORA, documentation/operator-scripts engineer of Martins Whisper. Follow-up ticket DORA-2. MARA integrates; you never merge.

WORKTREE: /work/martins-whisper-worktrees/d1-docs, branch agent/d1-docs (now fast-forwarded to master, which includes ASHA's G1 merge and your D1 merge).
READ: /work/owner/BRIEF-DORA.md, docs/*, handoffs/ASHA-1/HANDOFF.md, handoffs/DORA-1/HANDOFF.md.

PROBLEM TO FIX: your docs were written before G1 merged and now contain stale statements that 0.1.0 "uses FakeSpeechEngine" and has "no in-app model download/import screen". Master now has ASHA's G1: real WhisperCppEngine wiring, ModelRepositoryProvider (<filesDir>/models/<id>.bin), catalog model screen with installed state, download / SAF import / delete, progress, and diagnostics (native availability, active backend, fallback reason, GPU failures, last error). FakeSpeechEngine is deleted from app main (core testFixtures copy remains for tests).

DO (docs/scripts only, no app/core code):
1. Grep docs/README for stale claims ("FakeSpeechEngine", "fake engine", "no in-app model", "G1 gap", "G1 not") and correct them to the post-G1 reality. Keep the honest limitation: the DEFAULT APK has no native libs, so real transcription needs `-Pmartins.native=true` on an arm64 device; untested on hardware.
2. Update docs/MODELS.md for the in-app flow (select catalog model -> download or import -> installed state -> engine loads it) and docs/TROUBLESHOOTING/DOCTOR for the typed errors (`native-unavailable`, `model-missing`, `gpu-unavailable`, `auto-cpu`, `engine-error`) and where diagnostics show them.
3. Update the verification numbers in docs/BUILD.md to the latest integrated gate (MARA will confirm; if you rebuild, use your own GRADLE_USER_HOME=/home/node/gh-dora and copy real output).
4. Remove the duplicate older handoff directory handoffs/D1-docs (keep handoffs/DORA-1 as the D1 handoff) OR explain in DORA-2 why both exist. Do not rewrite history in DORA-1's evidence.
5. Keep every device step marked UNVERIFIED (no device). Do not touch app/ or core/.

TOOLCHAIN if you rebuild: cp -r /work/android-toolchain/gradle-home /home/node/gh-dora (once); export JAVA_HOME=/work/android-toolchain/jdk ANDROID_HOME=/work/android-toolchain/sdk ANDROID_SDK_ROOT=/work/android-toolchain/sdk GRADLE_USER_HOME=/home/node/gh-dora; ./gradlew ... --offline. Keep calls <5 min; wip-commit every 10 min. Retry cap 2.

ACCEPTANCE:
  grep -rniE "fake ?speech ?engine|no in-app model|G1 gap" docs README.md   # no stale hits (testFixtures mentions are OK if clearly test-only)
  bash -n scripts/*.sh
  ./scripts/doctor.sh; echo $?   # non-zero + clear no-device message
  git status                     # clean after commit

FINISH: handoffs/DORA-2/HANDOFF.md (<=10 lines summary, files, commands + real output, SHAs) + SUMMARY.txt; commit on agent/d1-docs; final message with branch/head SHA.
If "Free usage exceeded": stop, wip-commit, note it, do not retry.
