# VOICE-UI-RESEARCH (ORIN, 2026-10-26). Evidence-based principles for our voice feedback and visualisation.

Audience: MOTIF (audio-features/visual мост) and LENA (UX lane). Goal: the bubble must FEEL right.
Every claim is labelled [SOURCED] (with URL) or [GUESS] (our inference). Sources were gathered by web search on 2026-10-26.

## 1. Feedback latency thresholds

- [SOURCED] 0.1 s (~100 ms) is the limit for the user to feel the system reacts *instantaneously* — the outcome feels caused by the user, not the computer. NN/g, https://www.nngroup.com/articles/response-times-3-important-limits
- [SOURCED] < 200 ms preserves the feeling of *continuous control* for direct-manipulation-style actions (tap -> state change). CHI 2026, https://dl.acm.org/doi/10.1145/3772318.3790716
- [SOURCED] ~1 s is the limit the user's flow of thought stays uninterrupted; > 10 s loses attention entirely. NN/g (same URL); Card et al. lineage.
- [SOURCED] When response time is *variable* (ours is: model load, audio length), feedback during the delay is especially important — a percent-done / state indicator is the fix. NN/g (same URL).
- [SOURCED] Turn-taking gap norms: speakers respond ~200 ms in conversation; systems that sit at multi-second gaps feel "stilted". https://www.sciencedirect.com/science/article/pii/S088523082030111X
- [SOURCED] Our own MFVP acceptance (owner): tap -> visual+haptic < 100 ms; stop -> text median ~2 s for 5-10 s speech. /work/owner/MFVP.md §Device acceptance.
- Implication for us [GUESS -> actually a hard requirement already in MFVP 4b]: the *state change* of the bubble must be immediate (< 100 ms from tap); only the *text* may take ~2 s. Never let the model-load delay stall the visual state change.

## 2. What the user needs to see (the four questions)

A voice UI must answer, at a glance and at all times [SOURCED, synthesis of state-machine literature]:
1. Is it listening? (mic open — am I being captured?)
2. Is it hearing ME? (real signal: my voice levels move the visual; silence shows silence)
3. Is it working? (processing after stop, especially while the model loads/infers)
4. Did it succeed or not? (text inserted / "didn't catch that" / error)

- [SOURCED] Canonical lifecycle states: idle, listening, thinking/processing, speaking, error. orb-ui docs model exactly these and state "the important job of a voice agent UI is trust: a user should understand whether the app heard them, whether the assistant is waiting". https://orb-ui.com/docs/guides/voice-agent-ui ; same set in https://github.com/deepgram/ui (VoiceButton full lifecycle incl. error).
- [SOURCED] "Without real-time motion feedback during listen/think/speak, silence feels like the mic died." Anti-pattern: *the same animation for listening and speaking* (state ambiguous). https://aiuxplayground.com/pattern/voice-visualizer ; mirrored in https://github.com/slb2248/ai-ux-patterns/blob/main/patterns/audio/voice-visualizer.md
- [SOURCED] Apple Voice Memos shows a live waveform *and* live transcription while recording; Otter/WisprFlow-style dictation pills show a live waveform plus partial preview of what was said. https://support.apple.com/guide/iphone/view-a-transcription-iph00953a982/ios ; https://github.com/FrigadeHQ/yap ; https://github.com/the-ihor/vtt
- [SOURCED] Gemini's dictation pill: live waveform while listening, swaps to a pulsing thinking-dots animation while processing. https://movin.design/video/gemini-macos-voice-dictation-pill
- [SOURCED] Siri intentionally works eyes-free: visual + audible states, content descriptions, never depends on visuals alone. https://developer.apple.com/design/human-interface-guidelines/siri
- Translation to our four moments (tap -> "listening"; stop -> text; what is inserted; never crash), per state:
  | our state | question answered | honest visual |
  |---|---|---|
  | idle | ready | static bubble, slight rest, no motion [GUESS] |
  | recording | listening + hearing ME | driven by real mic RMS, dances only with my voice |
  | working | thinking | distinct motion (dots/pulse) NOT shaped by audio [GUESS: audio-shaping here would be a lie — mic is already closed] |
  | inserted / empty / error | success? | text present, or explicit "didn't catch that", distinct error double-tick (MFVP 4b) |

## 3. Honest mappings from audio features to visuals and haptics

We compute per-frame features in MOTIF lane. What each feature can *honestly* mean:
- LEVEL (RMS): overall loudness. [SOURCED] The standard driver of bar/waveform amplitude; scale to perceptual (sqrt or dB) not linear — speech energy is concentrated, linear scaling makes speech look tiny. [GUESS for the mapping choice; amplitude-driving standard is sourced: https://github.com/chevgan/react-ai-voice-visualizer (FFT/volume-driven), CodeFronts voice-mic-pulse demos.]
- ONSETS (energy rises): a syllable/word start. [SOURCED] syllable-ish rate is the natural rhythm for pulse animations. [GUESS: map onset -> a subtle bar "hit"; do not map pitch to it.]
- PITCH (F0,Brightness/spectral centroid, VOICING: what the visual must NOT claim [SOURCED]: "Waveforms show timbre (harmonics) and amplitude. That information does not translate to specific words" — the famous 2025 Apple dictation story proved waveform shapes are NOT words. https://forums.appleinsider.com/discussion/comment/3470351. So: never arrange visuals to imply content; only imply presence/energy.
- HONEST assignments [GUESS from the above sourced constraints — MOTIF implement these]:
  - waveform/bars <- LEVEL (perceptually scaled)
  - colour/brightness shift <- VOICING state (voiced vs unvoiced/silence)
  - pulse rhythm <- ONSETS
  - spectral centroid (brightness) may modulate *hue* slightly only if we keep a single-at-a-time mapping; if it adds noise instead of meaning, drop it. [GUESS]
- HAPTICS [SOURCED]: Android haptics principles — "less is more"; favour *clear* haptics, strength corresponds to importance, co-design haptic with visual AND audio cues (congruence makes each stronger). https://developer.android.com/develop/ui/views/haptics/haptics-principles ; use View-based feedback or predefined VibrationEffect, avoid legacy buzzy one-shots: https://developer.android.com/develop/ui/views/haptics/haptic-feedback
  - Our mapping (already ordered by MFVP 4b) [GUESS, satisfies sourced principles]: one clear tick on start, one on stop, one distinct double-tick on error/empty, per state machine above. No haptics tied to voice content (that would be noise).
- TRUST erosion by decorative animation [SOURCED base + GUESS extrapolation]: 
  - [SOURCED base] A moving idle waveform is noise; "animate bars only when audio is actually flowing". https://scrimui.dev/components/voice-waveform
  - [SOURCED base] Users must not have to interpret an animation alone — pair motion with a state label/caption. https://orb-ui.com/docs/guides/voice-agent-ui ("failure understandable without forcing users to interpret an animation on its own"); aiuxplayground anti-pattern (visualizer with no caption/icon for deaf/low-vision users).
  - [GUESS extrapolation, stated as our principle]: any motion NOT driven by the measured signal teaches the user the visual is fake; once they learn bars move while they are silent, they cannot longer tell "am I being heard?" — the single most important surface signal. Decorative = trust-eroding. Keep exactly two animation drivers: owned state machine, real audio features.

## 4. How reference apps visualise listening (field survey)

- [SOURCED] Siri: multi-state orb + waveform, Ber-min chime/auditory cues calibrated for eyes-free status. https://developer.apple.com/design/human-interface-guidelines/siri ; third-party recreations show orbit layers scale with mic level: https://github.com/xremix/Voice-Assistant-UI
- [SOURCED] Google Assistant/Gemini: colour/shape state changes; Look-and-Talk turning on with visual + multi-signal validation on-device: https://research.google/blog/look-and-talk-natural-conversations-with-google-assistant
- [SOURCED] Gemini dictation pill on macOS: waveform -> thinking dots -> result. https://movin.design/video/gemini-macos-voice-dictation-pill
- [SOURCED] Alexa: light ring, pulses tied to audio activity. https://aiuxplayground.com/pattern/voice-visualizer
- [SOURCED] Apple Voice Memos / dictation: scrolling waveform + live transcription; playhead sync on word tap. https://support.apple.com/guide/iphone/view-a-transcription-iph00953a982/ios
- [SOURCED] Otter-style and Wispr Flow-style dictation overlays universally use: small pill + live waveform + partial text preview (u1 overlay = float). https://github.com/FrigadeHQ/yap ; https://github.com/the-ihor/vtt ; https://github.com/WW2-Master/voxtype-flow-pill
- [SOURCED] Open-source component libraries already standardise the pattern (idle/listening/thinking/speaking, colour per state, audio-reactive): https://github.com/deepgram/ui ; https://github.com/chevgan/react-ai-voice-visualizer. COPY their state naming, adapt visuals.

## 5. Accessibility (non-negotiable)

- [SOURCED] Reduce Motion / vestibular sensitivity: parallax, self-moving decorative animation, scrolljacking are the worst triggers; ask is the animation purposeful; provide content understandable without animation. https://www.kent.edu/equalaccess/news/android-accessibility-part-3-4-reduced-motion ; also Apple's expectation that animations respect user settings. [GUESS implement]: read system "remove animations" setting on Android and switch our waveform to a static state icon + label when set.
- [SOURCED] TalkBack needs a short, specific content description per state-telling element ("MartinsWhisper bubble, recording"); decorative items get a null description. https://developer.android.com/design/ui/mobile/guides/foundations/accessibility ; https://developer.android.com/guide/topics/ui/accessibility/principles
- [SOURCED] Do not rely only on gestures (all flows need accessibility actions); state changes should not be colour-only (pair with shape/position/pattern): https://developer.android.com/guide/topics/ui/accessibility/principles.
- [SOURCED] Haptic feedback helps low-vision users in real time — a reason the ticks are required, not decoration. https://developer.android.com/design/ui/mobile/guides/foundations/accessibility
- [SOURCED] Voice-response must stand alone without visuals (Siri guidance — applies to our app on a phone screen possibly off/glint): https://developer.apple.com/design/human-interface-guidelines/siri

## 6. CHECKLIST — what MOTIF and LENA's output must satisfy

Each item is testable on-device or in a host test. Derived from sections 1-5.
1. [ ] Tap -> visual state change < 100 ms (MFVP §Device acceptance; sourced 1s/5 in §1). Implement as a flag/class, not an inline handler, so a test can assert it.
2. [ ] Recording visual amplitude is driven by REAL RMS only (zero when silent), perceptually scaled, at >= ~24 Hz update rate. [SOURCED presence of driver; GUESS the scale].
3. [ ] "Working"/thinking state has a DIFFERENT visual than listening (dots vs waveform, colour/shape change), since mic is closed then.
4. [ ] No idle-fire: while idle or working, the animation does NOT process, even though the UI is in mid-conversation. Idle = static. [scrim UI sourced anti-pattern]
5. [ ] No pitch/shape/timbre mapping designed to hint CONTENT. A waveform may only suggest "energy is here", never a word. [apple meetup sourced]
6. [ ] One colour-at-a-time per state family; the state must be readable without animation via shape/colour/label (TalkBack/colour-blind).
7. [ ] Content descriptions per state; the full UI functions with TalkBack (each state announced).
8. [ ] Respect reduce-motion: halt or minimise decorative animation when the system reduces motion; state stays readable via static indicators.
9. [ ] Haptics exactly: start = 1 clear tick, stop = 1 tick, error/empty = the double tick (already MFVP 4b), congruent with the visual state change to ride the multi-sense lag.
10. [ ] No latency regressions: haptic is co-sent to visual in the same frame; both reach the user before the model load blocks the UI thread.
11. [ ] Error/empty transcript uses the "didn't catch that" honest path (MFVP), never autocorrects or inserts nothing silently forever.
12. [ ] The visual and hypothesis states are driven by our field names above, not invented names (deepgram/orb-ui naming is the copy-source style for pattern consistency).

Truth by construction: section 6 checklist is what MOTIF's PR must prove (unit-test the RMS/scale mapping, the 4-state machine) and what LENA's screen anatomy must show as final states. Keep MFVP scope: no extra overlay states beyond recording/working/error, no captions, no summarizer.
