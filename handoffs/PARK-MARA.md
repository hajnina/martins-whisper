# PARK - MARA (2026-10-26, ~20:30Z, before refill/killall)

## DONE (master, push-ready, clean)
- Wave 3: G2 merged (`01fa735`, handoff `PM-2026-10-26-6`; gate 155/0).
- Apache-2.0 LICENSE/NOTICE commit (`2dbac3e`) per owner order.
- Wave 4 merge queue: NORA-1 CI fix (`c3c45bb`), ASHA G3a (`5bc822d`) + G3b (`c53b6ef`), DORA-3 checklist (`39ca92d`), TESS-2 svc (`25ea766`) + edge (`ed8d96c`), T1 fail-closed gate (`agent/t1-harness` tip). Final gate: **217 tests, 0 failures**; APK 8,970,737 B sha256 `5809b1ecfb...`. Handoff `PM-2026-10-26-7`; KANBAN/LOGBOOK current (`83a65df`).
- Push-ready state: 34 commits ahead of origin/master; junk/secret scan over the wave diff = 0 hits.
- MFVP scope order received; MFVP audit NOT started yet (see NEXT).

## RUNNING at park (stopped by killall, note only)
- W1 CODER, branch `agent/m1-release`, ticket `M1-release` (issue 07 signing/versioning), model deepseek (default). ~25 min in, NO commits beyond base ba6b160 reached; was writing `scripts/create-release-keystore.sh` + `release-check.sh`, touched `.gitignore`/`app/build.gradle.kts`. Parked as wip `94d76e6`. **MFVP stops this scope** (release/signing) - do NOT resume as-is.
- W2 CRITIC, branch `agent/m2-checklist`, ticket `M2-checklist` (issue 10 verify + gap tickets), glm (default). No commits; was verifying via `git log`. Killed before completing. Branch still base `ba6b160`.
- Extra managed branches not mine: `agent/n2-release` (release workflow, parked `f5f600e` - MFVP-stopped scope), `agent/t3-lint` (parked `1e7e3fd` - MFVP-stopped scope).

## NEXT (ordered, MFVP critical path only)
1. **MFVP flow audit (MARA, ~1 session):** read bubble -> record -> VAD/chunk -> engine -> insert end-to-end in `DictationService`/`OverlayController`/`MainActivity`; write missing links as small tickets `MFVP-<n>` in `.scratch/device-ready-apk/tickets/` for ASHA. (Owner already ordered this; started-not-finished.)
2. **MFVP-1 real engine into the default APK path:** default `-Pmartins.native=true` arm64 build contains `WhisperCppEngine`; verify green build in GitHub Actions after owner push (NORA-1 fix merged). Blocked until refill: needs a worker session.
3. **MFVP-2 wire the end-to-end flow:** fix whatever the audit finds (ASHA via tickets above), gate = local 217/0 + flow test names in `TESS-2` suites.
4. **MFVP-3 one default model:** confirm in-app one-tap download (base/tiny) + SHA check with the G3a loopback tests; kill dead model UI options if they block the path (delete, don't polish).
5. **MFVP-4 H1 device session:** wireless adb on Martin's phone (owner provides device); debug-signed APK (`scripts/install-apk.sh` + NORA's arm64 APK), fix real bugs; end-to-end manual test = the MFVP definition of done.
6. **MFVP-5 README:** short install/permissions/use doc (one file, replaces doc sprawl duties).
- Explicitly do NOT resume: W1 release/signing, `agent/n2-release`, `agent/t3-lint`, W2 checklist sprawl, u2-*/d2-* integration, benches.

## TRAPS (learned)
- `opencode run -m <model>` throws "Unexpected server error" (all 9 Zen models, 3 probes incl. neutral dir); plain `opencode run` (default model) works. Set default per worker instead of `-m`.
- Gradle: never touch shared `/work/android-toolchain/gradle-home`; per-worker copies (gh-mara / gh-mara) + `--offline --rerun-tasks`. `/home/node/*` dies with the container - re-copy on refill.
- androidTest deps (`androidx.test.ext`, espresso) needed ONE online fetch after TESS's cached copy vanished; now in gh-mara caches, but refill should redo it once.
- DORA merges without handoff files (D1-3) - require one next time even for docs.
- TESS F1 finding: `AudioChunker.splitAtLengthOf` duplicates post-cut audio (reproduced) - production ticket before H1.
- Park procedure worked: killall after `wip: park` commits everywhere; nothing lost in-flight (both stopped workers had no commits).
