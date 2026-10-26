# Models

Martins Whisper runs **whisper.cpp GGML** models on the phone. A model file is
just data; the app never uploads it. v1 supports **GGML only** (magic `6c 6d 67
67`). GGUF and ONNX files are not supported (ADR-004).

> **Status in 0.1.0:** the catalog, hash checks, downloader and on-disk layout
> exist and are covered by JVM tests. The **in-app download/import/delete screen
> is not wired yet (G1)**. Until it lands, use the adb push path below.
> Device commands are **UNVERIFIED (device, H1-blocked)**.

## Which model to use

The default is **`base` multilingual**. It is the best speed/quality trade-off
for a 6–8 GB phone.

| Id | Name | File on device | Download | SHA-256 (first 16) | RAM (upstream) | v1 role |
|---|---|---|---:|---|---:|---|
| `tiny` | Tiny (multilingual) | `tiny.bin` | 74.1 MiB | `be07e048e1e599ad` | ~273 MB | first-class low-end |
| `tiny.en` | Tiny (English) | `tiny.en.bin` | 74.1 MiB | `921e4cf8686fdd99` | ~273 MB | English-only |
| `base` | Base (multilingual) | `base.bin` | 141.1 MiB | `60ed5bc3dd14eea8` | ~388 MB | **default** |
| `base.en` | Base (English) | `base.en.bin` | 141.1 MiB | `a03779c86df33230` | ~388 MB | English-only |
| `small` | Small (multilingual) | `small.bin` | 465.0 MiB | `1be3a9b2063867b9` | ~852 MB | optional, 8 GB |
| `small.en` | Small (English) | `small.en.bin` | 465.0 MiB | `c6138d6d58ecc832` | ~852 MB | optional, 8 GB |
| `medium` | Medium (multilingual) | `medium.bin` | 1462.7 MiB | `6c14d5adee5f8639` | ~2.1 GB | experimental |
| `medium.en` | Medium (English) | `medium.en.bin` | 1462.7 MiB | `cc37e93478338ec7` | ~2.1 GB | experimental |
| `large-v3-turbo` | Large v3 Turbo (multilingual) | `large-v3-turbo.bin` | 1549.3 MiB | `1fc70f774d38eb16` | ~2 GB class | catalog only |

"RAM" is whisper.cpp's upstream figure, not an Android PSS measurement. Android
adds app/framework overhead, so measure on the real device for H2. `medium` and
`large-v3-turbo` are not realistic defaults on a 6 GB phone; treat them as
opt-in experiments.

Full 64-character SHA-256 values are in `research/01-model-facts.md` and in
`core/src/main/kotlin/com/martins/whisper/core/asr/ModelCatalog.kt`.

## Where models live

The app stores one file per model, named by **catalog id** (not the upstream
file name):

```text
/data/data/com.martins.whisper/files/models/<id>.bin
```

Examples: `base.bin`, `tiny.en.bin`, `large-v3-turbo.bin`. The app's
`ModelStorage` maps a catalog spec to `<id>.bin` and uses `<id>.bin.part` while
writing, then atomically replaces the final file after verification.

## Download from the canonical source (operator)

The canonical source is the MIT-licensed Hugging Face repo
[`ggerganov/whisper.cpp`](https://huggingface.co/ggerganov/whisper.cpp), pinned to
revision `5359861c739e955e79d9a303bcbc70fb988958b1`.

**VERIFIED (host, 2026-10-26):** the tiny download below completed, 77,691,713
bytes, SHA-256 `be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21`.

```bash
REV=5359861c739e955e79d9a303bcbc70fb988958b1
curl -fL --retry 2 -o ggml-base.bin \
  "https://huggingface.co/ggerganov/whisper.cpp/resolve/$REV/ggml-base.bin"
sha256sum ggml-base.bin
# expected: 60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe
```

If `sha256sum` is not available, `shasum -a 256 ggml-base.bin` works on macOS.
Do not use the SHA column on the Hugging Face model card: it is the file's
SHA-1, not SHA-256.

License: Whisper weights © 2022 OpenAI, MIT. Converted to GGML by whisper.cpp,
MIT. The app must keep this attribution visible; see
`research/01-model-facts.md` for the exact notice.

## Operator: adb push

This is the supported path in 0.1.0. It needs the **debug-signed APK** because it
uses `adb shell run-as`.

```bash
./scripts/push-model.sh ggml-base.bin
```

What the script does:

1. Derives the id from `ggml-<id>.bin` (or accepts `--id ID`).
2. Requires a catalog id and compares the local SHA-256 with the catalog value,
   refusing a mismatch (`--no-verify` is an explicit escape hatch; do not use it
   for a normal catalog download).
3. Refuses to overwrite an existing model unless `--force` is given.
4. `adb push` to `/data/local/tmp/`, then `run-as com.martins.whisper cp` into
   `files/models/<id>.bin`, `chmod 600`, and removes the temporary copy.
5. Prints the final file listing on the device.

**VERIFIED (host):** `bash -n`, `--help`, hash-match path up to the device check,
and the no-device error. **UNVERIFIED (device):** the copy into app storage.

Manual equivalent (if you prefer not to use the script):

```bash
adb push ggml-base.bin /data/local/tmp/
adb shell run-as com.martins.whisper mkdir -p files/models
adb shell run-as com.martins.whisper cp /data/local/tmp/ggml-base.bin files/models/base.bin
adb shell run-as com.martins.whisper chmod 600 files/models/base.bin
adb shell rm -f /data/local/tmp/ggml-base.bin
```

Then in the app choose **Settings → Model → Base (multilingual)**. The choice is
persisted in SharedPreferences (`model_id`).

If `run-as` fails with `package not debuggable`, you are running a release build
without the debug key; use a debug APK for the model-push workflow.

## In-app download and import (planned, G1)

The intended v1 flow, already backed by `ModelCatalog`, `ModelRepository`,
`ModelDownloader` and `ModelStorage`:

- **Download**: an explicit tap per model, with progress, resume (`.part`),
  streaming SHA-256 verification, then atomic replace. Network is used only for
  this action. No silent downloads.
- **Import**: pick an existing GGML file with the Android file picker; verify its
  SHA-256 if supplied, otherwise accept it after the magic/load check.
- **Delete**: remove the model file; the next dictation reports a clear "model
  missing" state instead of crashing.

These screens do **not exist in 0.1.0**; do not look for a download button.

## Storage and memory guidance

| Phone RAM | Safe models | Notes |
|---:|---|---|
| 6 GB | `tiny`, `base` | Supported v1 baseline. Use `tiny` if the phone is loaded with other apps. |
| 8 GB | `tiny`, `base`, `small` | `small` is optional; validate it before calling it supported. |
| 8 GB+ | above + `medium` / `large-v3-turbo` (experimental) | Large download and ~2 GB class RAM; may be killed by the low-memory killer. |

Model files live in app-private storage. A download needs the file size plus a
little working space; a `base` model is about 0.15 GB. Uninstalling the app
deletes the models.

## Idle unload

**Settings → Unload model when idle** offers 1, 5 (default), 10 and 15 minutes.
The service frees the engine after the chosen idle time; the next hold reloads
it. The real native free path is part of G1/whisper.cpp; the state machine and
timer are implemented and tested. Verify on device with
[DEVICE-MATRIX.md](DEVICE-MATRIX.md) and the doctor report.

## Delete a model

- In-app delete is planned (G1).
- Operator (debug APK):

  ```bash
  adb shell run-as com.martins.whisper rm files/models/base.bin
  ```

  **UNVERIFIED (device).**
- Uninstalling the app removes all models and settings.
