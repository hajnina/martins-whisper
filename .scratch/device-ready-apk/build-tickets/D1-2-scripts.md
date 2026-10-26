# D1-2 — Install and model-push scripts

Owner: DORA (branch `agent/d1-docs`). One session, one commit.

## Goal

`scripts/install-apk.sh` (adb install with clear no-device error), `scripts/push-model.sh` (adb push a
GGML file into the app's files/models dir for debug builds, with sha256 check), and a secret-free
doctor report. Must be safe to run without a device (print what is missing, exit non-zero).

## Acceptance

Run both scripts with no device attached: they exit non-zero with actionable messages; `bash -n`
passes for every script.

## Status

done (2026-10-26)

## Result

Merged: ASHA-1 `fcb8299` (G1), DORA-1 `b33c8c3` (D1). MARA gate: 135 tests, 0 failures, APK 8,938,813 B.
