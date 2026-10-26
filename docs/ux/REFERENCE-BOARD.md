# L0 reference board — onboarding (COPY MODE)

Owner direction: modern, minimal, one primary action per screen, generous whitespace,
one type scale / two weights, one accent + neutrals, dynamic colour with calm fallback,
true-dark, rounded shapes, motion 150–400 ms, shared-axis steps, spring press feedback,
ring driven by real RMS, calm success check, smooth progress, fade-through states,
respect "remove animations".

Copy rule: only Apache-2.0 / MIT / BSD files are COPIED (header kept, attribution in
`NOTICE`). GPL / source-available apps are STUDY ONLY. All reference checkouts are
read-only and untrusted; we never run their builds.

| Reference | URL | License | Local checkout |
|---|---|---|---|
| Now in Android | https://github.com/android/nowinandroid | Apache-2.0 | `/tmp/refs/nowinandroid` |
| Material Components for Android (catalog) | https://github.com/material-components/material-components-android | Apache-2.0 | `/tmp/refs/mc` |
| Compose samples (Jetsnack, Jetcaster, Reply) | https://github.com/android/compose-samples | Apache-2.0 | `/tmp/refs/cs` |

Material 3 motion + type specs: https://m3.material.io/styles/motion/easing-and-duration
and https://m3.material.io/styles/motion/transitions (values below are the published tokens).

## Shared visual system (applies to every screen)

| Concern | Reference file | Copy |
|---|---|---|
| Theme, dynamic colour + calm fallback | `nowinandroid/core/designsystem/.../theme/Theme.kt` | `dynamicLightColorScheme` / `dynamicDarkColorScheme` with a teal fallback (`ThemeColors` values), `MaterialTheme` wrapper |
| Type scale (one scale, 2 weights) | `nowinandroid/core/designsystem/.../theme/Type.kt` | displaySmall 36/44, headlineMedium 28/36, titleLarge 22/28 Bold, bodyLarge 16/24, labelLarge 14/20 Medium |
| Surface | `nowinandroid/core/designsystem/.../component/Background.kt` | `NiaBackground` shape: full-bleed `Surface` + zero tonal elevation (no card shadows) |
| Spacing | NIA `InterestsScreen.kt` / `TopicScreen.kt` | 24 dp side gutters, 16 dp block rhythm, 8 dp intra-group; 48 dp minimum targets |
| Motion | `mc/catalog/.../transition/SharedAxisHelper.java`, `TransitionFadeThroughDemoFragment.java`; Compose `AnimatedContent` in `cs/JetNews/.../ListDetailScene.kt` | X shared axis for forward/back steps, Z fade-through for state swaps; 300 ms spatial, 200 ms fade |
| Spring press | `cs/Jetsnack/.../components/Filters.kt` (`collectIsPressedAsState` + `animateColorAsState`) | scale 0.97 on press with `spring(dampingRatio = 0.6f, stiffness = 900f)`, colour anim 150 ms |
| Accessibility | `nowinandroid/.../InterestsScreen.kt` semantics + `Jetsnack/.../Filters.kt` `toggleable` | `contentDescription`, `stateDescription`, live region, `toggleable` instead of click for chips |
| Motion-off | Compose `LocalMotionDurationScale` / `Settings.Global.ANIMATOR_DURATION_SCALE` | all durations multiply by 0 (no animation) when animations are removed |

Motion tokens used: enter 300 ms `emphasizedDecelerate`; exit 200 ms `emphasizedAccelerate`;
fade-through 200 ms; press 150 ms; progress 300 ms `linear`; ring breath 1800 ms loop
`FastOutSlowInEasing`; success check 600 ms one-shot (400 ms path draw + 200 ms hold).

## Screen-by-screen

### 1. Welcome
- Reference: `nowinandroid/core/designsystem/.../component/Background.kt` (surface) +
  `nowinandroid/core/designsystem/.../component/LoadingWheel.kt` (animated mark) +
  `nowinandroid/feature/foryou/impl/.../ForYouScreen.kt` (centered hero text block).
- Repo/license: nowinandroid, Apache-2.0.
- Copy: full-bleed calm surface, centered 48 dp animated mark (`Canvas`, ring breathing
  1800 ms), headline `displaySmall`, one-line promise `bodyLarge` on `onSurfaceVariant`,
  bottom-anchored full-width primary button with 24 dp gutters and nav-bar inset.
- Change: mic glyph instead of NIA wheel; copy "100% on-device. No accounts."

### 2. Primary language (searchable list + top chips)
- Reference: `cs/Jetsnack/app/src/main/java/com/example/jetsnack/ui/home/search/Search.kt`
  (foreground search field, clear action) + `cs/Jetsnack/.../ui/components/Filters.kt`
  (`LazyRow` of toggle chips) + `nowinandroid/.../InterestsScreen.kt` + `Chip.kt`
  (filter chip with leading check) + `mc/catalog/.../chip/ChipGroupDemoFragment.java`
  (single-selection group semantics).
- Repo/license: compose-samples / nowinandroid / material-components, Apache-2.0.
- Copy: sticky search `BasicTextField` row (no heavy outlined box), 8 dp chip gap,
  selected chip = `primaryContainer` + leading check, `FlowRow`/`LazyRow` top languages,
  `LazyColumn` results with 24 dp gutters and 56 dp rows; `focusRequester` on open;
  `toggleable`/`selectable` semantics. Default = device locale (ASHA `LanguagePrefs`).
- Change: language data + localized names; no web images.

### 3. Secondary languages (0–3)
- Reference: `nowinandroid/feature/interests/impl/.../InterestsScreen.kt`,
  `core/designsystem/.../component/Chip.kt` (`NiaFilterChip`),
  `cs/Jetsnack/.../ui/home/FilterScreen.kt` (`FlowRow` + `heightIn`),
  `core/designsystem/.../theme/Background.kt` (`NiaGradientBackground` variant).
- Repo/license: nowinandroid / compose-samples, Apache-2.0.
- Copy: `FlowRow` chip grid, circular chips, leading check when selected, 8 dp gaps;
  counter `2 of 3` as `labelLarge` in `onSurfaceVariant`; primary button disabled until
  selection valid; "Skip" as text button (secondary action).
- Change: cap at 3, exclude primary language, no images.

### 4. Choose your engine (new owner requirement)
- Reference: `mc/catalog/.../card` demo layouts (`cat_card_*`) for the two honest cards +
  `mc/catalog/.../button/ButtonGroupDemoFragment.java` for the segmented "preferred engine"
  choice; `nowinandroid/.../InterestsItem` for row anatomy (title, supporting, trailing state).
- Repo/license: material-components / nowinandroid, Apache-2.0.
- Copy: two `OutlinedCard`/`Card` tiles, 16 dp radius, 1 dp selected border (no shadow),
  radio semantics (`selectable` + `Role.RadioButton`), title `titleMedium` Bold,
  two lines max `bodyMedium`; selected tile tinted `primaryContainer` at 12%.
- Change: Whisper (small, fast, offline) vs Gemma E2B (multilingual, bigger, slower);
  honest sizes/speeds from `docs/LANGUAGES.md`; UI only, engine work owned by GEMI.

### 5. Model
- Reference: `nowinandroid/feature/interests/impl/.../InterestsItem` row anatomy +
  `mc/catalog/.../progressindicator/ProgressIndicatorMainDemoFragment.java`
  (determinate linear indicator, animated `setProgressCompat`) +
  `nowinandroid/.../component/LoadingWheel.kt` for the working state.
- Repo/license: nowinandroid / material-components, Apache-2.0.
- Copy: three plain-language cards (Tiny/Base/Small) with size + speed + "good for your
  languages" hint, selected state, one primary Download/Continue; `LinearProgressIndicator`
  determinate with percent + bytes, smooth 300 ms; resume hint.
- Change: real `ModelRepository` progress; no benchmark table UI.

### 6. Permissions (guided stepper, one at a time)
- Reference: `nowinandroid/core/designsystem/.../component/Button.kt` (single primary CTA) +
  `mc/catalog/.../progressindicator/...` (step progress) +
  `nowinandroid/.../core/designsystem/.../component/Tag.kt` (status pill) +
  `cs/Reply/.../ui/components/ReplyEmailListItem.kt` (leading icon + title + supporting).
- Repo/license: nowinandroid / material-components / compose-samples, Apache-2.0.
- Copy: 3-dot step progress (linear, animated 300 ms), one icon + "why" paragraph +
  one primary button per step, live granted tick (`AnimatedVisibility` fade+scale),
  deep link to the exact settings page; restricted-settings help card with a short
  looping illustration (`Canvas` + `rememberInfiniteTransition`), 200 ms fade.
- Change: microphone / overlay / accessibility only (notifications optional, not blocking).

### 7. Practice + engine compare (new owner requirement)
- Reference: `cs/JetLagged/.../HomeScreenCards.kt` (side-by-side stat cards),
  `cs/Reply/.../ReplyListContent.kt` (two-pane adaptive split),
  `nowinandroid/.../LoadingWheel.kt` (per-card working state),
  `mc/catalog/.../progressindicator` (per-card speed bar), `cs/Jetsnack/.../Filters.kt`
  (spring press for the mic).
- Repo/license: compose-samples / nowinandroid / material-components, Apache-2.0.
- Copy: adaptive `Row`/`Column` two result cards; each card fills independently as its
  engine finishes (fade + rise 12 dp, 250 ms); result text `bodyLarge` max 4 lines; speed
  as a small determinate bar + `x realtime` label; winner gets a calm check + haptic;
  mic button with breathing ring driven by real RMS. Fake `CompareRunner` until GEMI's
  `agent/engines1` interface lands.
- Change: "Tap the mic and say: <sentence>" per primary language; error card per engine.

### 8. You are set
- Reference: `nowinandroid/core/designsystem/.../theme/Background.kt` + `LoadingWheel.kt`
  (success mark) + `nowinandroid/.../InterestsItem` (how-to rows).
- Repo/license: nowinandroid, Apache-2.0.
- Copy: centered animated check (path draw 400 ms, then still), three short how-to rows
  with icons, one primary "Start dictating" button.
- Change: content only.

## Settings "Compare engines"
- Same two-card component as practice, reachable from Settings, no recording required
  (runs on a bundled 3 s fixture); keeps the last comparison + speed factor.
- Reference and license as screen 7.

## Not copied (study only)
FUTO Voice Input, HeliBoard, Signal, SaySo (GPL/source-available): interaction ideas only
(bubble states, permission wording, compare copy). No code, no assets, no strings copied.
