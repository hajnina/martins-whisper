# ACCEL-2: BackendInfo / AccelProbe contract — HANDOFF

## Executive summary
- Implemented the exact ACCEL-2 contract in a new pure-JVM file `core/src/main/kotlin/com/martins/whisper/core/accel/Accel.kt`: `BackendKind` (GPU/NPU/DSP/CPU), `BackendBenchmark` with derived `rtf` (0.0 when `audioMs <= 0`), `BackendInfo` with `init { require(available || reason != null) }`, interface `AccelProbe`, and `AccelSelection.select(requested: ComputeBackend, backends)` with CPU-first fallback (AUTO = first available non-CPU, GPU = first available GPU, CPU = the CPU entry; `IllegalStateException` when no CPU entry exists).
- Imported `com.martins.whisper.core.ComputeBackend` (AUTO/GPU/CPU) from core `Contracts.kt` for the `select()` argument — no signature changed relative to the ticket.
- Added `FakeAccelProbe` in `core/src/testFixtures/...` (constructor takes the backends list it returns) for GEMI/ASHA/LENA night-0 consumers.
- Added `AccelProbeTest` with 14 tests, including self-challenging cases: unavailable-without-reason throws, GPU-requested with only CPU falls back, AUTO with zero non-CPU backends returns CPU, `rtf` for `audioMs=0`, no-CPU-entry throws.
- `:core:test` real run: BUILD SUCCESSFUL in 2m 7s, exit 0; AccelProbeTest XML: tests=14, failures=0, errors=0, skipped=0.
- Issue `.scratch/accel/issues/02-backendinfo-accelprobe-contract.md` set to `Status: resolved`.

## Files changed (exact list)
- `core/src/main/kotlin/com/martins/whisper/core/accel/Accel.kt` (NEW)
- `core/src/testFixtures/kotlin/com/martins/whisper/core/accel/FakeAccelProbe.kt` (NEW)
- `core/src/test/kotlin/com/martins/whisper/core/accel/AccelProbeTest.kt` (NEW)
- `.scratch/accel/issues/02-backendinfo-accelprobe-contract.md` (Status: claimed -> resolved)
- `handoffs/ACCEL-2-contract/HANDOFF.md` (NEW), `handoffs/ACCEL-2-contract/SUMMARY.txt` (NEW)

Not touched: `handoffs/PARK-KAI.md`, `KANBAN.md`, `LOGBOOK.md`.

## How to run (copy-paste)
```
cd /work/martins-whisper-worktrees/accel2
source /work/android-toolchain/scripts/env.sh
/work/owner/tools/gradle-slot.sh :core:test
```

## Evidence (real output)
```
> Task :core:compileTestFixturesKotlin
> Task :core:compileTestKotlin
> Task :core:test

BUILD SUCCESSFUL in 2m 7s
6 actionable tasks: 6 executed
[gradle-slot] slot 1 released (exit 0)
```

JUnit XML `core/build/test-results/test/TEST-com.martins.whisper.core.accel.AccelProbeTest.xml`:
```
tests="14" skipped="0" failures="0" errors="0"
```
14 tests total, 0 failed. Whole `:core:test` suite also green (all pre-existing test classes pass; no regressions).

## Interfaces exposed / invariants
- `com.martins.whisper.core.accel.BackendKind { GPU, NPU, DSP, CPU }`
- `BackendBenchmark(backendId, modelId, loadMs, inferMs, audioMs)` with `val rtf: Double` = `inferMs / audioMs` if `audioMs > 0` else `0.0`.
- `BackendInfo(id, name, kind, vendor = null, available, reason = null, benchmark = null)`; invariant: `available || reason != null` enforced by `require` in `init`.
- `interface AccelProbe { fun probe(): List<BackendInfo> }` — probe order = preference order; must always contain a CPU entry; must not throw.
- `AccelSelection.select(requested: ComputeBackend, backends: List<BackendInfo>): BackendInfo`
  - CPU -> the CPU entry (its `reason` kept).
  - GPU -> first available GPU entry, else CPU entry.
  - AUTO -> first available non-CPU entry (GPU/NPU/DSP, probe order), else CPU entry.
  - Throws `IllegalStateException` if no CPU entry exists (probe contract violated).
- `FakeAccelProbe(backends: List<BackendInfo>) : AccelProbe` in testFixtures — the fake for GEMI EngineRegistry and ASHA/LENA settings tests.

## Self-challenge (tests that would fail if the logic were wrong)
- Constructing an unavailable `BackendInfo` without a reason throws `IllegalArgumentException` with the require message.
- `AccelSelection.select` without any CPU entry throws `IllegalStateException` (both AUTO and CPU requests, incl. empty list).
- GPU requested with only-CPU backends and with an unavailable GPU entry both fall back to the CPU entry.
- AUTO with zero non-CPU backends returns the CPU entry; AUTO skips unavailable non-CPU entries; AUTO picks DSP when GPU unavailable.
- `rtf` for `audioMs=0` is `0.0` (no divide-by-zero); for `inferMs=4000, audioMs=10000` is `0.4`.
- `select(CPU, ...)` returns the very same `BackendInfo` instance/data (reason preserved), not a synthesized one.

## Commit SHAs
- `1f77866` wip(accel2): BackendInfo/AccelProbe contract + selection + fakes + tests
- (final): see `git log --oneline -1 agent/accel2` after handoff commit — handoff + issue resolution land in the final commit.

## Known limitations / next steps
- `select()` does not compare benchmarks (preference is pure probe order); if the PM wants RTF-based auto-ranking, that is a follow-up against this same interface (no signature change needed).
- No Android bindings yet; lane 5 probe implementations (real device scan for Adreno/Mali/Hexagon) plug in by implementing `AccelProbe` and keeping the CPU-first contract.
- GEMI/ASHA/LENA should depend on the `core` testFixtures configuration to consume `FakeAccelProbe`.

## References
- In-repo ticket `.scratch/accel/issues/02-backendinfo-accelprobe-contract.md` (API taken verbatim from it).
- Core code style: `core/src/main/kotlin/com/martins/whisper/core/Contracts.kt` (KDoc Javadoc style, minimal comments), test style: `core/src/test/kotlin/com/martins/whisper/core/EnergyVadTest.kt` (kotlin.test + JUnit4 runner, backtick names).
- No external repos/URLs used.
