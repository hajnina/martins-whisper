# G3a — Real-HTTP downloader proof for the app

## Executive summary (<=10 lines)

Added `HttpModelDownloaderTest` under `app/src/test/java/com/martins/whisper/asr/` that drives the
existing production path (`ModelRepository` -> `ResumableModelDownloader` -> `HttpModelSource`) against
a real loopback `ServerSocket` speaking minimal HTTP/1.1. Four cases pass: happy path (bytes, SHA-256,
monotonic progress, `.part` removed), resume after a mid-body cut (`Range: bytes=N-`, final bytes
correct, `resumed == true`, `attempts == 2`), bad hash (`ModelVerificationException`, no target/`.part`),
and 404 (`DownloadException` containing `404`, no target/`.part`). No production code changed. Full
`:app:testDebugUnitTest` run: 70 tests, 0 failures, 0 errors. No device or real network involved.

## What was built

- `app/src/test/java/com/martins/whisper/asr/HttpModelDownloaderTest.kt`
  - `FakeHttpServer`: daemon thread over `ServerSocket(0)` bound to `127.0.0.1`; parses request line
    and headers, records method/path/`Range`, serves chunked-free fixed `Content-Length` responses,
    206 responses with `Content-Range` when a `Range` header is present, a 404 path, and a `/cut`
    behaviour that writes 500 bytes of a 2,000-byte body then closes the socket.
  - Repository under test built exactly as the ticket asks:
    `ModelRepository(ModelStorage(tempDir), ResumableModelDownloader(storage, HttpModelSource(connectTimeoutMs = 2000, readTimeoutMs = 5000), retryDelayMs = 10))`.
  - No external dependencies, no internet, plain JUnit 4 (already in `app` test classpath).

## Files changed

- `app/src/test/java/com/martins/whisper/asr/HttpModelDownloaderTest.kt` (new, 273 lines)

No production files changed. No new dependencies.

## Commands and real output

Environment:

```
export JAVA_HOME=/work/android-toolchain/jdk
export ANDROID_HOME=/work/android-toolchain/sdk ANDROID_SDK_ROOT=/work/android-toolchain/sdk
export GRADLE_USER_HOME=/home/node/gh-g3a
```

Targeted run:

```
./gradlew :app:testDebugUnitTest --offline --tests 'com.martins.whisper.asr.HttpModelDownloaderTest'
...
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 24s
```

Acceptance run:

```
./gradlew :app:testDebugUnitTest --offline
...
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 10s
```

XML evidence from `app/build/test-results/testDebugUnitTest/*.xml`:

```
TEST-com.martins.whisper.asr.HttpModelDownloaderTest.xml tests=4 failures=0 errors=0
total tests=70 total failures=0 total errors=0
```

New class test cases recorded in the XML:

```
happy path downloads verifies and promotes over real http
resumes after server cuts the connection mid-body
bad hash fails verification and leaves no files
404 is reported and leaves no files
```

## Interfaces / invariants verified

- `HttpModelSource` sends `Range: bytes=<offset>-` on resume and sets `Accept-Encoding: identity`.
- 206 handling uses `Content-Range` total; resumed result is reported as `resumed == true`.
- Partial `.part` file survives a short/cut response and its byte length becomes the next offset.
- SHA-256 mismatch raises `ModelVerificationException` and deletes the `.part`.
- Non-2xx (404) surfaces as `DownloadException` with `HTTP 404` in the message; no files created.
- Successful download promotes `.part` to `<id>.bin` atomically and emits a final progress event.

## Limitations / notes for the integrator

- Tests use loopback TCP only; they do not exercise a real device, Android network security config,
  redirects, TLS, or a remote CDN. No internet access was used.
- The fake server is single-threaded per accept loop and only needs to satisfy these four cases.
- No production gap was found; the existing downloader logic handled cut/resume, verification and
  404 correctly without changes.
- Branch `agent/g3-download`; tree was clean after both commits.

## References

- Ticket: `.scratch/device-ready-apk/build-tickets/G3a-http-downloader.md`.
- `core/src/main/kotlin/com/martins/whisper/core/asr/ModelDownloader.kt` (production path under test).
- `core/src/test/kotlin/com/martins/whisper/core/asr/ModelDownloaderTest.kt` (scripted-source test
  patterns adapted to a real socket; not modified).
- JDK `HttpURLConnection` behaviour for fixed `Content-Length` and 206 responses.

## Commit SHAs

- Code/tests: `3111cf737af65b71e643a9bfb809afb620fa2060`
- Handoff: this commit (see `git log --oneline -2` on `agent/g3-download`)
