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

open (provider OK 18:36Z)

## Result

-
