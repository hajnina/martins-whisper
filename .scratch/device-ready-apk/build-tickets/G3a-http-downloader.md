# G3a — Real-HTTP downloader proof for the app (local fake server)

Owner: G3A-CODER (branch `agent/g3-download`). One session, one commit. Stacked on `agent/g2-engine`.

## Goal

Prove the app's model-download path against a **real local HTTP server** in
`:app:testDebugUnitTest`: `HttpURLConnection` + Range resume + SHA-256 verification + progress
callback, with the existing model-screen progress as the UI surface. Do not reimplement the core
downloader; the app already uses `core.asr.ModelRepository` -> `ResumableModelDownloader` ->
`HttpModelSource`.

The required tests (plain JVM, no external deps, no internet) use a `ServerSocket` speaking minimal
HTTP/1.1:

1. **Happy path** — serves a known payload with `Content-Length`; assert downloaded bytes/hash,
   target file exists, `.part` gone, progress is monotonic.
2. **Resume after cut** — first response writes N bytes then closes the socket; the client must
   retry with `Range: bytes=N-`; assert the server saw the Range header, the final file matches the
   full payload, `DownloadResult.resumed == true`, `attempts == 2`.
3. **Bad hash** — serve the payload but request a wrong SHA-256; expect
   `ModelVerificationException`; assert neither target nor `.part` remains.
4. **404** — request a missing path; expect `DownloadException` whose message contains `404`; assert
   no target/`.part`.

Build the repository under test as
`ModelRepository(ModelStorage(tempDir), ResumableModelDownloader(storage, HttpModelSource(...)))`
so the test controls timeouts and never touches the real network. If (and only if) the production
path has a real gap, fix it minimally in `core/.../asr` and say so in the handoff.

## Inputs

- `core/src/main/kotlin/com/martins/whisper/core/asr/ModelDownloader.kt` (`ResumableModelDownloader`,
  `HttpModelSource`, `ModelSource`, `DownloadResult`, `DownloadProgress`)
- `core/src/main/kotlin/com/martins/whisper/core/asr/ModelRepository.kt`, `ModelStorage.kt`
- `app/src/main/java/com/martins/whisper/MainActivity.kt` (download progress already wired)
- Existing core tests `core/src/test/.../ModelDownloaderTest.kt` (scripted source; do not delete)

## Acceptance

```
cd /work/martins-whisper-worktrees/g3-download
export JAVA_HOME=/work/android-toolchain/jdk
export ANDROID_HOME=/work/android-toolchain/sdk ANDROID_SDK_ROOT=/work/android-toolchain/sdk
export GRADLE_USER_HOME=/home/node/gh-g3a
./gradlew :app:testDebugUnitTest --offline
```

BUILD SUCCESSFUL; the new class is counted in the `:app:testDebugUnitTest` XML totals; all four
cases above exist and pass. `git status` clean on the branch.

## Status

open

## Result

-
