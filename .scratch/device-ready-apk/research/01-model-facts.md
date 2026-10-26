# 01 — Which Whisper GGML models can v1 ship, from where, at what size?

- Access date: **2026-10-26** (UTC)
- Ticket: `.scratch/device-ready-apk/issues/01-model-facts.md`
- Status: **resolved** (primary-source facts; device PSS still belongs to H2)
- Scope: model files, sizes/hashes, license, download path, GGML-format compatibility, storage/RAM guidance.

## Answer

**Recommended v1 model set**

1. **Default: `base` multilingual f16** (`base.en` is the English-only alternative) — `ggml-base.bin`, 147,951,465 B (141.1 MiB), upstream-reported ~388 MB memory. Safe on a 6–8 GB phone.
2. **First-class low-end: `tiny` multilingual f16** (`tiny.en` available) — `ggml-tiny.bin`, 77,691,713 B (74.1 MiB), ~273 MB memory. Good for 6 GB devices and small downloads.
3. **Optional/advanced: `small` f16** (`small.en` available) — `ggml-small.bin`, 487,601,967 B (465.0 MiB), ~852 MB memory. Comfortable on 8 GB; validate on 6 GB before calling it supported.
4. **Catalog, but not first-class:** `medium` and `large-v3-turbo` are required by SPEC R4 but are **not realistic defaults on 6–8 GB**. Keep them as opt-in/experimental, gate on available RAM, and prefer quantized variants (`medium-q5_0` 514.2 MiB, `large-v3-turbo-q5_0` 547.4 MiB). Do not ship f16 `medium` (~2.1 GB) or `large-v3` (~3.9 GB) as v1 defaults; `large-v3-turbo` f16 has no published memory figure but is in the ~2 GB class at best (8 GB devices only).
5. **No mirror is required for v1.** Download directly from `https://huggingface.co/ggerganov/whisper.cpp/resolve/main/…` (302 to Hugging Face's CDN; public, no auth). Pin the HF revision `5359861c739e955e79d9a303bcbc70fb988958b1` and verify each file's SHA-256. Show the MIT notices.
6. **Pin the engine + format:** current whisper.cpp loads **custom GGML**, magic `0x67676d6c`, not GGUF. `GGML_FILE_VERSION` is defined but not enforced by the Whisper loader. E1 must pin an exact whisper.cpp commit/tag **and** the model revision + SHA-256, then load-test on the device.

---

## 1. Canonical source and format

The canonical converted Whisper weights are the **`ggerganov/whisper.cpp`** model repository on Hugging Face:
`https://huggingface.co/ggerganov/whisper.cpp` (revision `5359861c739e955e79d9a303bcbc70fb988958b1`, last modified 2024-10-29; tagged `license:mit`).

- These are **custom GGML** files, not GGUF. Current whisper.cpp's Whisper loader checks `GGML_FILE_MAGIC` (`0x67676d6c`, bytes `6c 6d 67 67` on disk) and then reads Whisper hparams/vocab/tensors (`src/whisper.cpp`, v1.9.5 and master `231638a`, magic check around lines 1507–1514).
- There is **no canonical GGUF distribution for whisper.cpp Whisper models**. Current whisper.cpp has no GGUF loader in `src/whisper.cpp`; community GGUF Whisper repos are for other runtimes and will fail this loader's magic check.
- Upstream's own downloader uses `https://huggingface.co/ggerganov/whisper.cpp` + `resolve/main/ggml-<model>.bin` (`models/download-ggml-model.sh`). The old `ggml.ggerganov.com` host is commented out.
- `ggml-org` is the **GitHub source org** (`github.com/ggml-org/whisper.cpp`), not a Hugging Face model repo. `huggingface.co/ggml-org/whisper.cpp` is not a model repo (HF API returns not found); use `ggerganov/whisper.cpp`.

Base URL for every file below:

```text
https://huggingface.co/ggerganov/whisper.cpp/resolve/main/<filename>
```

Pinned-revision form (recommended for E1):

```text
https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/<filename>
```

Example: `https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-base.bin`

## 2. Canonical f16 model files

All sizes and SHA-256 values below come from the HF API `siblings[].size` / `siblings[].lfs.sha256` at the access date, cross-checked with `HEAD` on each `/resolve/main/` URL (`X-Linked-Size`, `X-Linked-Etag`). The SHA column in the HF model card is the file's 40-character SHA-1 (verified for `ggml-base.bin`), **not** the SHA-256; use the values below.

| Model | File | Bytes | MiB | SHA-256 | Upstream memory | License |
|---|---|---:|---:|---|---:|---|
| tiny | `ggml-tiny.bin` | 77,691,713 | 74.1 | `be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21` | ~273 MB | MIT |
| tiny.en | `ggml-tiny.en.bin` | 77,704,715 | 74.1 | `921e4cf8686fdd993dcd081a5da5b6c365bfde1162e72b08d75ac75289920b1f` | ~273 MB | MIT |
| base | `ggml-base.bin` | 147,951,465 | 141.1 | `60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe` | ~388 MB | MIT |
| base.en | `ggml-base.en.bin` | 147,964,211 | 141.1 | `a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002` | ~388 MB | MIT |
| small | `ggml-small.bin` | 487,601,967 | 465.0 | `1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b` | ~852 MB | MIT |
| small.en | `ggml-small.en.bin` | 487,614,201 | 465.0 | `c6138d6d58ecc8322097e0f987c32f1be8bb0a18532a3f88f734d1bbf9c41e5d` | ~852 MB | MIT |
| medium | `ggml-medium.bin` | 1,533,763,059 | 1462.7 | `6c14d5adee5f86394037b4e4e8b59f1673b6cee10e3cf0b11bbdbee79c156208` | ~2.1 GB | MIT |
| medium.en | `ggml-medium.en.bin` | 1,533,774,781 | 1462.7 | `cc37e93478338ec7700281a7ac30a10128929eb8f427dda2e865faa8f6da4356` | ~2.1 GB | MIT |
| large-v3-turbo | `ggml-large-v3-turbo.bin` | 1,624,555,275 | 1549.3 | `1fc70f774d38eb169993ac391eea357ef47c88757ef72ee5943879b7e8e2bc69` | not published (~2 GB class; measure) | MIT |

Notes:

- `large-v3-turbo` has **no `.en` variant** in the canonical repo; it is multilingual only.
- `medium`/`small`/`base`/`tiny` have `.en` variants, same architecture and memory class as their multilingual counterparts.
- `large-v3` (not turbo) is 3,095,033,483 B (2951.7 MiB) and ~3.9 GB memory; not a v1 candidate. It is not in the table above.
- "Upstream memory" is whisper.cpp's README `Mem` column (`README.md`, "Memory usage": tiny ~273 MB, base ~388 MB, small ~852 MB, medium ~2.1 GB, large ~3.9 GB). It is a model/runtime figure, not an Android PSS measurement; add Android app/framework overhead and measure on H2.

### 2.1 Realistic quantized variants (same HF repo)

Quantization reduces storage and weight RAM, but the compute buffers remain; upstream does not publish memory figures per quantized file. Use these as opt-in choices, not as a substitute for H2 measurement.

| File | Bytes | MiB | SHA-256 |
|---|---:|---:|---|
| `ggml-tiny-q5_1.bin` | 32,152,673 | 30.7 | `818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7` |
| `ggml-tiny-q8_0.bin` | 43,537,433 | 41.5 | `c2085835d3f50733e2ff6e4b41ae8a2b8d8110461e18821b09a15c40c42d1cca` |
| `ggml-base-q5_1.bin` | 59,707,625 | 56.9 | `422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898` |
| `ggml-base-q8_0.bin` | 81,768,585 | 78.0 | `c577b9a86e7e048a0b7eada054f4dd79a56bbfa911fbdacf900ac5b567cbb7d9` |
| `ggml-small-q5_1.bin` | 190,085,487 | 181.3 | `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb` |
| `ggml-small-q8_0.bin` | 264,464,607 | 252.2 | `49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f` |
| `ggml-medium-q5_0.bin` | 539,212,467 | 514.2 | `19fea4b380c3a618ec4723c3eef2eb785ffba0d0538cf43f8f235e7b3b34220f` |
| `ggml-medium-q8_0.bin` | 823,369,779 | 785.2 | `42a1ffcbe4167d224232443396968db4d02d4e8e87e213d3ee2e03095dea6502` |
| `ggml-large-v3-turbo-q5_0.bin` | 574,041,195 | 547.4 | `394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2` |
| `ggml-large-v3-turbo-q8_0.bin` | 874,188,075 | 833.7 | `317eb69c11673c9de1e1f0d459b253999804ec71ac4c23c17ecf5fbe24e259a1` |

## 3. Storage / RAM on a 6–8 GB Android phone

### Base and tiny (the safe v1 pair)

| Model | APK-external storage | Upstream peak memory | Practical v1 read |
|---|---:|---:|---|
| tiny f16 | 74.1 MiB | ~273 MB | Safe on 6–8 GB; low download cost |
| base f16 | 141.1 MiB | ~388 MB | Safe on 6–8 GB; SPEC R4 default |

- Storage: model files live in app-private storage (`filesDir/models/…`). A first download needs about the file size plus a little working space; atomic replace can briefly need a second copy. Base is ~0.15 GB, not a storage problem on 6–8 GB phones.
- RAM: current `whisper_init_from_file_with_params` opens the file with `std::ifstream` and loads tensors into backend buffers (not mmap) (`src/whisper.cpp:3746–3770`). `whisper_free` releases those buffers. Therefore the 300 s idle unload really returns roughly the model's memory, and the upstream `Mem` column is the right order of magnitude for the resident cost.
- Android adds process/framework overhead on top of the upstream number; the upstream figure is not an Android PSS measurement. A foreground service is not immunity from the low-memory killer. The app should:
  - gate medium/large downloads on `ActivityManager.MemoryInfo` (total/available RAM), not only on model size;
  - treat `getMemoryClass()` as a Dalvik-heap signal only (native buffers are outside that heap);
  - handle native allocation failure / `OutOfMemoryError` and return to clipboard+notification fallback.

### small / medium / turbo / large

| Model | Disk | Upstream peak | 6 GB phone | 8 GB phone |
|---|---:|---:|---|---|
| small f16 | 465 MiB | ~852 MB | Likely works; test under load | Comfortable |
| medium f16 | 1.43 GiB | ~2.1 GB | Risky (LMK, other apps) | Usually works; not a good default |
| large-v3-turbo f16 | 1.51 GiB | not published; ~2 GB class | Not recommended | Opt-in only; measure |
| large-v3 f16 | 2.88 GiB | ~3.9 GB | Not realistic | Not realistic |

Conclusion for v1: **tiny + base** are the realistic first-class set; **small** is acceptable for 8 GB devices and should be verified on 6 GB. **Medium and large are not realistic v1 defaults** on 6–8 GB; `large-v3` f16 is out. If SPEC R4 requires medium/turbo to be offered, use opt-in + RAM gate + quantized variants and state the risk in the UI.

## 4. Direct download, redirects, CORS, rate limits, attribution

### Is direct download from upstream allowed?

Yes. `ggerganov/whisper.cpp` is a public HF model repo; no account, token, or license click-through is needed. The files are MIT-licensed. The upstream downloader itself fetches from this host.

### Redirect / CDN behaviour (verified 2026-10-26)

- `GET /resolve/main/ggml-base.bin` returns **HTTP 302** to a signed `https://us.aws.cdn.hf.co/xet-bridge-us/...` URL, then **HTTP 200** with the file. The signed URL has an `Expires` about **1 hour** in the future. Do not cache or hard-code the CDN URL; always start from the `resolve` URL and follow redirects.
- `HEAD` on the `resolve` URL returns `X-Linked-Size` and `X-Linked-Etag` (the SHA-256) before the body is downloaded. Useful for preflight and verification.
- HTTP `Range` requests work on `/resolve/` (tested at byte 0 and at ~70 MB on `ggml-tiny.bin`), so resume is technically possible. Use `/resolve/`, not `/raw/`; a ranged `/raw/` request returned the 134-byte Git-LFS pointer rather than model bytes.
- CORS is **not** a blocker: with an `Origin` header the 302 echoes that origin, and the final CDN response sends `access-control-allow-origin: *`. Native Android HTTP clients are not subject to CORS anyway. A WebView/JS fetch would also work, but native is the right path.
- Android needs `INTERNET`; the download path must allow redirects (OkHttp default does; `DownloadManager` follows redirects). Keep downloads explicit per SPEC R3 (no silent download).

### Hosts / proxy allowlist

Hugging Face documents that file bytes are served from separate storage/CDN hostnames, and that clients must allow them in addition to `huggingface.co`:

- `huggingface.co` (API/redirects)
- `cas-server.xethub.hf.co`, `cas-server.xethub-eu.hf.co`
- `transfer.xethub.hf.co`, `transfer.xethub-eu.hf.co`
- `us.aws.cdn.hf.co`, `us.gcp.cdn.hf.co`
- `cdn-lfs-us-1.hf.co`, `cdn-lfs-eu-1.hf.co`

For normal Android traffic no allowlist work is needed; this matters only for a corporate proxy/firewall or a future mirror.

### Rate limits / usage

- Anonymous resolver requests are rate-limited. The observed anonymous headers on 2026-10-26 were `RateLimit: "resolvers";q=3000;w=300` (3,000 requests per 300 s, i.e. 5-minute window) and a remaining count. HF docs describe `429 Too Many Requests` + `RateLimit`/`RateLimit-Policy` headers and recommend a token for higher limits.
- Do **not** embed an HF token in the APK. One model download is a handful of resolver requests; a normal user will not hit the limit. Set a descriptive `User-Agent` and honor `429`/`Retry-After` rather than retrying tightly.
- No mirror is needed for v1. A mirror is optional if HF is blocked in a target region; MIT permits redistribution if the notice is included, but then the mirror owner must keep the exact bytes/hashes and serve them over HTTPS.

### Attribution / license text that must ship

The HF repo is tagged `license:mit`; the underlying OpenAI Whisper weights are MIT (`openai/whisper/LICENSE`), and whisper.cpp is MIT (`ggml-org/whisper.cpp/LICENSE`). MIT requires the copyright notice and permission notice to be included in copies/substantial portions. Put this in the app's open-source-licenses screen and (for the model download screen) show a one-line credit:

```text
Whisper speech models
Copyright (c) 2022 OpenAI. Licensed under the MIT License.
Converted to GGML by the whisper.cpp project
(https://github.com/ggml-org/whisper.cpp), MIT License.

whisper.cpp / ggml
Copyright (c) 2023-2026 The ggml authors. Licensed under the MIT License.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

This is not an endorsement by OpenAI; keep the attribution factual ("Whisper models by OpenAI", "converted by whisper.cpp").

## 5. GGML format compatibility and what E1 must pin

### What current whisper.cpp actually expects

Verified against release **v1.9.5** (`d2a764c648779feb36d44363f212a29b00aad1af`, 2026-10-06) and current master **`231638a0717b7822784297e5980965ffcec05810`** (2026-10-19):

- File starts with magic `0x67676d6c` (`GGML_FILE_MAGIC`, "ggml"). On disk the first four bytes are `6c 6d 67 67`.
- Immediately after the magic come the Whisper hparams as native int32s:
  `n_vocab, n_audio_ctx, n_audio_state, n_audio_head, n_audio_layer, n_text_ctx, n_text_state, n_text_head, n_text_layer, n_mels, ftype`.
  Then mel filters, vocab, and tensor records. This is the layout written by `models/convert-pt-to-ggml.py` (magic at line 268, hparams 269–279).
- There is **no explicit format-version field** after the magic. `GGML_FILE_VERSION 2` is defined in `ggml/include/ggml.h:216–220`, but `src/whisper.cpp` never reads it. The loader only checks the magic, then hparams, vocab, tensor names/shapes/types. The quantization version is packed into `ftype / 1000` and read into `qntvr`, but it is only logged (`src/whisper.cpp:1561–1563`), not rejected.
- Therefore a file can pass the magic check and still fail later. Compatibility is by **exact tensor names/shapes + ggml type support**, not by a version number. The current code's tensor expectations and the HF canonical files are from the same upstream family; still load-test the chosen combination.
- GGUF is a different container ("GGUF" magic). Current whisper.cpp's Whisper loader does not accept GGUF; a GGUF file fails immediately at the magic check. Do not rename `.gguf` to `.bin` and expect it to load.

### What E1 must pin

1. **Engine revision:** pin an exact whisper.cpp commit/tag, not `master`. At the access date:
   - release `v1.9.5` = `d2a764c648779feb36d44363f212a29b00aad1af` (recommended for a release build), or
   - master `231638a0717b7822784297e5980965ffcec05810` (the revision R1 reviewed; current on 2026-10-19).
   Both use the same custom-GGML loader/magic. Record which one E1 vendors.
2. **Model repository revision:** `5359861c739e955e79d9a303bcbc70fb988958b1` (HF `main` at access date, unchanged since 2024-10-29). Use `resolve/<revision>/<file>` for reproducible downloads.
3. **Per-file SHA-256:** the values in §2/§2.1. Verify while streaming to a temp file; only atomically rename after the hash matches. Do not trust the HF model-card SHA column (it is the file's SHA-1, not SHA-256).
4. **Format guard:** before load, check the 4-byte magic `6c 6d 67 67`; after load, treat `whisper_init_from_file_with_params == null` as a clear "unsupported/corrupt GGML" error. For custom import, accept only a file that passes magic + successful load; do not claim GGUF support in v1.
5. **Pin the exact model URL in the catalog** (or the revision + filename), and keep the SHA in the same catalog entry. A future HF `main` change then fails closed at hash verification rather than silently changing behavior.

The code-side format is stable enough that the canonical 2024 files and the 2026 loader share the magic/layout, but the only safe compatibility claim is the tested pair: **pinned engine commit + pinned model revision + verified hashes + device load test**.

## 6. Verification log (what was actually checked)

- HF API: `https://huggingface.co/api/models/ggerganov/whisper.cpp?blobs=true`; read `sha`, `lastModified`, `tags` (`license:mit`), `siblings[].size`, `siblings[].lfs.sha256`.
- `HEAD` for all nine §2 files: HTTP 302 to `us.aws.cdn.hf.co`, with matching `X-Linked-Size` / `X-Linked-Etag`; final CDN GET returned `200` and the exact `Content-Length`.
- Full download of `ggml-base.bin`: 147,951,465 bytes; `sha256sum` = `60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe` (matches API/header); first bytes `6c 6d 67 67` (GGML magic).
- Range checks on `/resolve/main/ggml-tiny.bin`: byte 0–99 and byte 70,000,000–70,000,099 returned HTTP 206 with 100 bytes each (the 0–99 slice starts with the GGML magic, not the LFS pointer). A ranged `/raw/main/...` request returned the 134-byte LFS pointer, so `/raw/` is not the download endpoint.
- CORS check with `Origin: https://example.com`: the 302 echoed that origin; the final CDN response sent `access-control-allow-origin: *`.
- Source check: v1.9.5 and master `231638a` `src/whisper.cpp` magic check (lines 1507–1514 / 4925–4930), `ggml/include/ggml.h` constants, `models/convert-pt-to-ggml.py` writer, `models/download-ggml-model.sh` canonical URL.
- No build, no APK, no device/emulator, no llama.cpp/GGUF run; device PSS and Vulkan/CPU performance remain H2.

## 7. Sources

All accessed **2026-10-26**.

- Hugging Face model repo (canonical GGML files, MIT tag, model card) — https://huggingface.co/ggerganov/whisper.cpp
- HF API (revision `5359861c…`, sizes, `lfs.sha256`) — https://huggingface.co/api/models/ggerganov/whisper.cpp?blobs=true
- HF model card raw (license metadata, size table, note that SHA column is SHA-1) — https://huggingface.co/ggerganov/whisper.cpp/raw/main/README.md
- HF downloading docs (resolve endpoint, CDN redirects, host allowlist) — https://huggingface.co/docs/hub/models-downloading
- HF rate limits (resolver bucket, 429, headers) — https://huggingface.co/docs/hub/rate-limits
- whisper.cpp source repo (v1.9.5 tag `d2a764c…`; master `231638a…`) — https://github.com/ggml-org/whisper.cpp
- whisper.cpp loader, magic check — https://github.com/ggml-org/whisper.cpp/blob/v1.9.5/src/whisper.cpp (and master `src/whisper.cpp`; magic check at ~1507 and VAD at ~4925)
- ggml format constants — https://github.com/ggml-org/whisper.cpp/blob/v1.9.5/ggml/include/ggml.h (`GGML_FILE_MAGIC`, `GGML_FILE_VERSION`, `GGML_QNT_VERSION`)
- whisper.cpp model docs / canonical URL — https://github.com/ggml-org/whisper.cpp/blob/master/models/README.md and https://github.com/ggml-org/whisper.cpp/blob/master/models/download-ggml-model.sh
- whisper.cpp memory table — https://github.com/ggml-org/whisper.cpp/blob/master/README.md#memory-usage
- OpenAI Whisper MIT license — https://github.com/openai/whisper/blob/main/LICENSE
- whisper.cpp MIT license — https://github.com/ggml-org/whisper.cpp/blob/master/LICENSE
- Android memory management / per-app heap — https://developer.android.com/topic/performance/memory-overview
- `ActivityManager.getMemoryClass()` — https://developer.android.com/reference/android/app/ActivityManager#getMemoryClass()
- Hugging Face terms of service — https://huggingface.co/terms (general use; model files are MIT)

## 8. Open items for H2 (not blockers for this ticket)

- Measure actual Android peak PSS / native RSS for tiny, base, small, medium, and turbo on both a 6 GB and an 8 GB device, CPU and Vulkan.
- Confirm small is acceptable on the lower-RAM device under a realistic foreground-app load.
- Measure quantized medium/turbo quality and memory; decide whether either is worth offering beyond the catalog.
- Measure real download throughput/resume on the target device/network; confirm the 302→CDN path through any OEM/proxy quirks.

No unresolved primary-source gap remains for ticket 01; the missing numbers are device measurements owned by H2.
