# G2a — Import custom (non-catalog) GGML models

Owner: ASHA (branch `agent/g2-engine`). One session, one commit.

## Goal

Let the user import a local GGML file that is not in `core.asr.ModelCatalog`: persist its descriptor,
select it, use it through `EnginePlanner`/`EngineFactory`, list it on the model screen with delete,
and forget it when deleted. Catalog models keep their existing download / import-by-id behaviour.

## Inputs

- `core/.../asr/CustomModelCodec.kt` (new), `core/.../asr/ModelCatalog.custom()`
- `app/.../data/SettingsStore.kt`, `app/.../asr/EnginePlanner.kt`, `app/.../service/EngineFactory.kt`
- `app/.../ui/ModelBrowser.kt`, `app/.../MainActivity.kt`, `app/src/main/res/values/strings.xml`

## Acceptance

```
./gradlew :core:test :app:testDebugUnitTest --offline
```

green; host-JVM tests prove: a custom descriptor round-trips and corrupt records are rejected
(`CustomModelCodecTest`), a persisted custom id resolves to its own model file (`EnginePlannerTest`),
and the browser adds/installs/removes a custom spec (`ModelBrowserTest`). Deleting a custom model
also clears it from settings and resets the selection to the catalog default.

## Status

done (2026-10-26)

## Result

WIP `dd04afd`; final branch tip and gate evidence in `handoffs/ASHA-2/HANDOFF.md`.
