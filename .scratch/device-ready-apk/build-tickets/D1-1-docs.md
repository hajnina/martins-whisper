# D1-1 — Operator docs and device matrix

Owner: DORA (branch `agent/d1-docs`). One session, one commit.

## Goal

README plus BUILD / INSTALL / PERMISSIONS / MODELS / PRIVACY / DEVICE-MATRIX / TROUBLESHOOTING /
DOCTOR docs. Every documented command must be run here or marked UNVERIFIED. Support statement:
arm64 Android 10-15, 6 GB min / 8 GB recommended, GPU optional, local-only/no telemetry. Do not
touch app/core code.

## Acceptance

A doc-lint script or `scripts/doctor.sh --help` runs; all commands are either executed with output
captured or explicitly marked UNVERIFIED. `git status` clean on the branch.

## Status

done (2026-10-26)

## Result

Merged: ASHA-1 `fcb8299` (G1), DORA-1 `b33c8c3` (D1). MARA gate: 135 tests, 0 failures, APK 8,938,813 B.
