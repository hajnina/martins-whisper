# Models

Martins Whisper runs **whisper.cpp GGML** models on the phone. A model file is
just data; the app never uploads it. v1 supports **GGML only** (magic `6c 6d 67
67`). GGUF and ONNX files are not supported (ADR-004).

> **Status after G1 (on `master`):** the **Model** section lists the catalog and
> has **Download**, **Import file** and **Delete** actions, with progress and
> SHA-256 verification. The engine is wired to the installed model
> (`EngineFactory` → `WhisperCppEngine`). The **default debug APK still has no
> native library**, so it can download/manage models but cannot transcribe; use a
> `-Pmartins.native=true` build for real dictation. Device runs remain
> **UNVERIFIED (H1-blocked)**.

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

A model file alone is not enough: the engine also needs the native library. The
default host APK is built with `-Pmartins.native=false`, so its Diagnostics shows
**Native whisper.cpp: missing** and dictation fails with a typed error until you
install a native build. See [FIRST-DICTATION.md](FIRST-DICTATION.md) step 0.

## Where models live

The app stores one file per model, named by **catalog id** (not the upstream
file name):

```text
/data/data/com.martins.whisper/files/models/<id>.bin
```

Examples: `base.bin`, `tiny.en.bin`, `large-v3-turbo.bin`. The app's
`ModelStorage` maps a catalog spec to `<id>.bin` and uses `<id>.bin.part` while
writing, then atomically replaces the final file after verification.

## Download from the canonical source

The canonical source is the MIT-licensed Hugging Face repo
[`ggerganov/whisper.cpp`](https://huggingface.co/ggerganov/whisper.cpp). The
in-app **Download** button uses the catalog's `resolve/main` URL (the same repo)
and **verifies the catalog SHA-256** before installing; if upstream `main` changes,
the hash check fails closed. The operator command below pins the revision
explicitly for a reproducible download:

**VERIFIED (host, 2026-10-26):** both the `base` command below (147,951,465
bytes, SHA-256 `60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe`)
and the `tiny` file (77,691,713 bytes, SHA-256
`be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21`) were
downloaded from the pinned revision and matched `ModelCatalog`.

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

## Operator fallback: adb push

Use this when the phone has no network, or you already have the file. It needs
the **debug-signed APK** because it uses `adb shell run-as`; the normal user path
is the in-app **Download** button.

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

Then open the app, find **Model → Base (multilingual)** and tap that row to
select it (it should show **Installed · selected**). The choice is persisted in
SharedPreferences (`model_id`).

If `run-as` fails with `package not debuggable`, you are running a release build
without the debug key; use a debug APK for the model-push workflow.

## In-app model screen (G1)

The single **Model** section lists every catalog row with its size and status:

- `Not installed` — no file on the phone.
- `Not installed · selected` — this id is the current choice but its file is
  missing; dictation fails with a typed *"not installed"* message.
- `Installed` / `Installed · selected` — the file exists; the engine can plan it.

Tap a row to make it the selected `model_id`. Actions:

- **Download** (only for a not-installed catalog model): explicit tap, progress
  bar, resume via `<id>.bin.part`, streaming SHA-256 verification, atomic replace.
  Network is used only for this action; no silent downloads. On success the
  message is **Model ready**.
- **Import file**: opens the Android file picker. The app copies the picked file
  to a cache file, verifies the **catalog SHA-256 for the selected row**, then
  installs it. A wrong/truncated file fails with a mismatch and is not installed.
  Only catalog ids are selectable; arbitrary custom GGML ids are a follow-up.
- **Delete** (only for an installed model): removes `<id>.bin` and its `.part`.
  The status returns to `Not installed`; the next dictation reports the typed
  *"not installed"* message instead of crashing.

The engine and the model screen share one repository
(`ModelRepositoryProvider`, rooted at `filesDir/models`), so a successful
download or import is immediately visible to the engine. Switching models while
the engine is loaded causes the service to close the old engine and load the new
one on the next hold.

**VERIFIED (host):** the G1 build compiles and its JVM tests cover
download/import/delete transitions, planning and engine-state persistence.
**UNVERIFIED (device):** the actual download/import over the phone's network and
file picker.

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

**Recognition → Unload model when idle** offers 1, 5 (default), 10 and 15
minutes. The service closes the engine after the chosen idle time
(`WhisperCppEngine.close()` frees the native context); the next hold reloads it,
so that dictation is slower once. Diagnostics shows the backend from the last
load. Verify the timing and memory on device with
[DEVICE-MATRIX.md](DEVICE-MATRIX.md).

## Delete a model

- In-app: tap **Delete** under an installed row. The status becomes
  `Not installed`.
- Operator (debug APK):

  ```bash
  adb shell run-as com.martins.whisper rm files/models/base.bin
  ```

  **UNVERIFIED (device).**
- Uninstalling the app removes all models and settings.
