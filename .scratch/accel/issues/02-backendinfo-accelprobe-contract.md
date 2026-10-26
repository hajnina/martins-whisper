# ACCEL-2: BackendInfo / AccelProbe contract (lane 5, ACCEL)

Type: task
Status: resolved
Owner: KAI PM, branch `agent/accel2`
Night-0 consumer: GEMI (EngineRegistry), ASHA/LENA (Settings 'Compute backend' list)

## Why
Committed FIRST so other lanes can build against the interface with fakes (ROADMAP anti-blocking rule).

## Deliverable
NEW `core/src/main/kotlin/com/martins/whisper/core/accel/Accel.kt` (pure JVM, no Android deps):

```kotlin
package com.martins.whisper.core.accel

enum class BackendKind { GPU, NPU, DSP, CPU }

data class BackendBenchmark(
    val backendId: String,
    val modelId: String,
    val loadMs: Long,
    val inferMs: Long,
    val audioMs: Long,
) {
    val rtf: Double
        get() = if (audioMs > 0) inferMs.toDouble() / audioMs else 0.0
}

data class BackendInfo(
    val id: String,
    val name: String,
    val kind: BackendKind,
    val vendor: String? = null,
    val available: Boolean,
    val reason: String? = null,
    val benchmark: BackendBenchmark? = null,
) {
    init { require(available || reason != null) { "unavailable BackendInfo must carry a reason" } }
}

interface AccelProbe {
    /** One-shot capability scan. MUST always return at least a CPU entry and MUST NOT throw. */
    fun probe(): List<BackendInfo>
}

object AccelSelection {
    /**
     * requested == CPU -> the CPU entry (reason from the CPU entry kept).
     * requested == GPU -> first available GPU entry (probe order = preference order), else CPU entry.
     * requested == AUTO -> first available non-CPU entry, else CPU entry.
     * Throws IllegalStateException if no CPU entry exists (probe contract violated).
     */
    fun select(requested: ComputeBackend, backends: List<BackendInfo>): BackendInfo
}
```

NEW `core/src/testFixtures/kotlin/com/martins/whisper/core/accel/FakeAccelProbe.kt`:
constructor takes the backends list it returns; used as the fake for GEMI/ASHA tests.

NEW `core/src/test/kotlin/com/martins/whisper/core/accel/AccelProbeTest.kt` covering:
probe of FakeAccelProbe returns CPU entry always; unavailable entry requires reason;
select() for AUTO/GPU/CPU incl. fallback-to-CPU when nothing non-CPU is available;
opencl-adreno style vendor naming sanity.

## Rules
- `source /work/android-toolchain/scripts/env.sh` then build ONLY via `/work/owner/tools/gradle-slot.sh :core:test` (from the worktree root).
- Commit message: `feat(core): BackendInfo/AccelProbe contract + selection + fakes (ACCEL-2)`.
- wip commit every 10 min (`git add <explicit files>`; never `git add -A`).
- Handoff: `handoffs/ACCEL-2-contract/HANDOFF.md` + `SUMMARY.txt`. Never touch `handoffs/PARK-KAI.md`.

## Acceptance
`:core:test` green incl. the new tests; exit code pasted into the handoff.
