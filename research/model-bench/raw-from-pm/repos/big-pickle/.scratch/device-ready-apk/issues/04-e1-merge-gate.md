# 04 — What must the E1 ASR/model layer prove before it merges?

Type: grilling
Status: open
Blocked by: 01, 02

## Question

E1 has an uncommitted ASR model layer (catalog, repository/downloader, storage, manager, whisper.cpp engine seam) and tests on `agent/e1-asr`. Decide the merge gate:

- Which public contracts are frozen for v1 (`SpeechEngine`, `ModelManager`, `ModelCatalog`, `ModelRepository`) and which may still change with PM approval?
- What must be unit-tested on the JVM: idle unload at 300 s, model swap, download verification/SHA256, failure/retry, fake engine path?
- Is the native whisper.cpp JNI library required at merge time, or can the engine seam ship with a clearly failing/stubbed native load until G1/device? What does ADR-004 require?
- Does the catalog match the model facts from ticket 01, and how are licenses/attribution surfaced?
- What evidence goes in the E1 handoff, and what is the exact PM verification command?

Output: merge checklist, accepted limitations, and follow-ups.
