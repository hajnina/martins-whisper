# 04 — What must the E1 ASR/model layer prove before it merges?

Type: grilling
Status: resolved
Blocked by: 01, 02

## Question

E1 has an uncommitted ASR model layer (catalog, repository/downloader, storage, manager, whisper.cpp engine seam) and tests on `agent/e1-asr`. Decide the merge gate:

- Which public contracts are frozen for v1 (`SpeechEngine`, `ModelManager`, `ModelCatalog`, `ModelRepository`) and which may still change with PM approval?
- What must be unit-tested on the JVM: idle unload at 300 s, model swap, download verification/SHA256, failure/retry, fake engine path?
- Is the native whisper.cpp JNI library required at merge time, or can the engine seam ship with a clearly failing/stubbed native load until G1/device? What does ADR-004 require?
- Does the catalog match the model facts from ticket 01, and how are licenses/attribution surfaced?
- What evidence goes in the E1 handoff, and what is the exact PM verification command?

Output: merge checklist, accepted limitations, and follow-ups.

## Answer

**Merge gate (all must be true before `agent/e1-asr` merges):**

1. **Committed, clean branch + handoff.** E1 commits the ASR layer on `agent/e1-asr`, `git status` is clean, and `handoffs/E1-asr/HANDOFF.md` has real commands/output. The PM does not edit the sibling PM's E1 worktree.
2. **Contracts frozen for v1:** `SpeechEngine`, `Transcript`, `ModelSpec`, `ModelManager` public API, `ModelCatalog`, `ModelRepository`, and the persisted backend/fallback reason. Any signature change needs PM approval and a migration note.
3. **Catalog matches ticket 01:** default `base` f16, `tiny` first-class, `small` optional on 8 GB, `medium`/`large-v3-turbo` catalog-only and RAM-gated. Exact pinned Hugging Face revision + SHA-256 per file; MIT attribution. Custom GGML only, not GGUF; pin the whisper.cpp revision.
4. **JVM tests green:** catalog/lookup, SHA-256 verification, download failure/retry/partial cleanup, idle unload at 300 s with injectable clock, model swap, and a `FakeSpeechEngine` end-to-end (chunks → VAD → merge → transcript). `:app:assembleDebug` green.
5. **Native policy (per ticket 02):** ship the whisper.cpp arm64 libs if they build; otherwise the engine seam must fail with a typed "engine unavailable" error, log it, and keep the app running. Vulkan is not a merge blocker; CPU-only is acceptable with a clear status.
6. **No secrets/model files:** no keystore, tokens, or model binaries in the diff; model files come from the pinned download/import path.
7. **PM verification and merge:** fetch into a scratch worktree, run `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --offline`, run `code-review`, then merge `--no-ff` into `master`; re-run the full gate; update map/KANBAN/STATUS.

**Abort/return:** uncommitted work, failing tests, native crash on startup, unpinned models, contract changes without approval, or a handoff without real output. Do not merge on a claim.
