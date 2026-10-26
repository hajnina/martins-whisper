# PARK-VERA (lx: verification engineer) — live note 2026-10-26, shift 2, trunk-based workflow

## DONE (this shift, V6 multilingual on trunk)
- Read SHIFT/MFVP(v2 acceptance)/DEVICE-REPORT/ROADMAP(+trunk workflow). Worktree /work/martins-whisper-worktrees/v6-multilang, branch agent/v6-multilang.
- V6 fixtures: scripts/verify/fetch-golden-fleurs.sh (HF datasets-server /rows, google/fleurs CC-BY, clips 2.5-12.5 s) + scripts/verify/wav_pcm16.py (datasets-server serves 32-bit float WAV; converted to 16 kHz mono PCM16) — LANDED master bdffd46 (218 lines, cloud gate green).
- Manifest app/src/test/resources/golden/fleurs-manifest.json (28 clips, 14 languages, expected transcripts + FLEURS source ids, audio stays in gitignored recordings/golden-fleurs/) — LANDED master ed53417 (371 lines, cloud green).
- Fetched: cs,en,de,fr,ru,uk,pl,ar,he,hi,ja,zh,ko,tr (2/lang).

## RUNNING
- WER/CER harness: MultilingualGoldenWerTest (real engine + ggml-base multilingual, WER; CER ja/zh/th) -> docs/LANGUAGES.md (lap: /tmp/veramodel/ggml-base.bin downloading).
- Hallucination-strip multilingual tests with NORA's list (MFVP item 5 artifacts) + set activation patch (handoff to NORA/MARA).

## BLOCKED (workarounds in flight)
- datasets-server /rows FAILS for es_es ("server is busier", retried 3x) and th_th (parquet 312 MB > 300 MB scan limit). Fallback in flight: direct parquet pull + row extract.
- th/es will show in docs/LANGUAGES.md as NOT MEASURED until their mockup lands.
