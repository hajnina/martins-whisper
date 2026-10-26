# Gate evidence - agent/capture1

Base: master `c62f533` (my branch `b379cb2` capture fix, `e62732a` CI/fetch).
Private Gradle home: `/home/node/gh-nora` (copy of the shared home).

Command:

```
GRADLE_USER_HOME=/home/node/gh-nora \
JAVA_HOME=/work/android-toolchain/jdk \
ANDROID_HOME=/work/android-toolchain/sdk \
./gradlew --offline --rerun-tasks --console=plain \
  -Pandroid.aapt2FromMavenOverride=/work/android-toolchain/sdk/build-tools/35.0.1/aapt2 \
  :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

Result: **BUILD SUCCESSFUL** (`73 actionable tasks: 73 executed`).

| module | suites | tests | failures | errors |
|---|---|---|---|---|
| `core/build/test-results/test` | 19 | 157 | 0 | 0 |
| `app/build/test-results/testDebugUnitTest` | 24 | 114 | 0 | 0 |

- `AudioRecordSourceTest`: 4 tests, 0 failures, 0 errors.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest` APKs produced (non-native
  `-Pmartins.native=true` variant is CI-only; this change is pure Kotlin/JVM-testable).
- `actionlint` on `.github/workflows/native-build.yml`: clean (nix actionlint).
- `bash -n scripts/fetch-latest-apk.sh`: clean; fake-`gh` end-to-end download tested.

Note: master moved to `cbb17d5` (MARA, `SpeechEnvelope` relative gate) after I branched.
No file overlap with this branch (`AudioRecordSource`, scripts, workflow, handoffs), so the
merge is expected clean; MARA re-runs the gate on the merge.
