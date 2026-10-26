#!/usr/bin/env python3
"""V2 mutation runner. Each mutant: (id, file, old, new).
Killed = :core:test fails (test failure, error, or compile failure).
Survivor = suite stays green -> test ticket owed.
"""
import os, re, subprocess, sys, xml.etree.ElementTree as ET, shutil

base = sys.argv[1]
env = dict(os.environ)
env.update({
    "JAVA_HOME": "/work/android-toolchain/jdk",
    "ANDROID_HOME": "/work/android-toolchain/sdk",
    "ANDROID_SDK_ROOT": "/work/android-toolchain/sdk",
    "GRADLE_USER_HOME": "/work/android-toolchain/gradle-home",
    "PATH": env["JAVA_HOME"] + "/bin:/work/android-toolchain/gradle/gradle-8.9/bin:" + env["PATH"],
    "ORG_GRADLE_PROJECT_androidAapt2FromMavenOverride": "/work/android-toolchain/sdk/build-tools/35.0.1/aapt2",
})
P = "core/src/main/kotlin/com/martins/whisper/core/"
NORM = P + "text/TextNormalizer.kt"
VAD = P + "audio/EnergyVad.kt"
CHUNK = P + "audio/AudioChunker.kt"
MATH = P + "audio/AudioMath.kt"
CONTRACTS = P + "Contracts.kt"

MUTANTS = [
 ("N1 drop-hallucination", NORM, "if (hallucinations.contains(value.lowercase())) return \"\"", "if (false) return \"\""),
 ("N2 drop-trim", NORM, "val value = text.replace(whitespace, \" \").trim()", "val value = text.replace(whitespace, \" \")"),
 ("N3 punctuation-keep-space", NORM, "value = spaceBeforePunctuation.replace(value, \"$1\")", "value = value"),
 ("N4 blank-passes-through", NORM, "if (value.isBlank()) return \"\"", "if (value.isBlank()) return value"),
 ("N5 drop-you-hallucination", NORM, "        \"you\",", "        \"you?-dead-code\","),
 ("N6 collapse-to-nbsp", NORM, "\\s+\", \")", "\\s+\", \"\\u00a0\")"),
 ("V1 speech-gte", VAD, "val speech = db > thresholdDb", "val speech = db >= thresholdDb"),
 ("V2 floor-negative", VAD, "noiseFloorDb + 6.0,", "noiseFloorDb - 6.0,"),
 ("V3 no-hangover-decrement", VAD, "hangoverRemaining--", "hangoverRemaining = hangoverRemaining"),
 ("V4 hangover-inverted", VAD, "if (hangoverRemaining > 0)", "if (hangoverRemaining >= 0)"),
 ("V5 multiplier-zero", VAD, "20.0 * log10(thresholdMultiplier)", "20.0 * log10(1.001)"),
 ("V6 minus-one-hangover", VAD, "hangoverFrames.coerceAtLeast(0)", "hangoverFrames - 1"),
 ("V7 floor-static", VAD, "noiseFloorDb += noiseFloorAlpha * (db - noiseFloorDb)", "noiseFloorDb += 0.0"),
 ("V8 minSpeech-narrow", VAD, "max(minSpeechRmsDb, noiseFloorDb + multiplierDb)", "max(minSpeechRmsDb - 6.0, noiseFloorDb + multiplierDb)"),
 ("A1 rms-off-by-one", MATH, "sqrt(sum / samples.size)", "sqrt(sum / (samples.size + 1))"),
 ("A2 rms-div-swap", MATH, "val value = sample / Short.MAX_VALUE.toDouble()", "val value = sample * Short.MAX_VALUE.toDouble()"),
 ("A3 shortToFloat-mul", MATH, "FloatArray(samples.size) { samples[it] / Short.MAX_VALUE.toFloat() }", "FloatArray(samples.size) { samples[it] * Short.MAX_VALUE.toFloat() }"),
 ("A4 floatToShort-noclamp", MATH, "(samples[it].coerceIn(-1f, 1f) * Short.MAX_VALUE)", "(samples[it] * Short.MAX_VALUE)"),
 ("C1 silence-finalize-strict", CHUNK, "if (silenceMs >= SILENCE_FINALIZE_MS)", "if (silenceMs > SILENCE_FINALIZE_MS)"),
 ("C2 maxlength-strict", CHUNK, "chunkDurationMs() >= MAX_CHUNK_MS", "chunkDurationMs() > MAX_CHUNK_MS"),
 ("C3 preRoll-kept", CHUNK, "chunk = preRoll.toMutableList()", "chunk = mutableListOf()"),
 ("C4 speech-ms-if-not", CHUNK, "speechMsInChunk = if (isAudible(frameRms)) durationMs else 0L", "speechMsInChunk = if (isAudible(frameRms)) 0L else durationMs"),
 ("C5 min-speech-lower", CHUNK, "speechMsInChunk >= MIN_SPEECH_MS", "speechMsInChunk >= 0"),
 ("C6 session-cap-early", CHUNK, "capturedAtMs >= sessionStart + SESSION_CAP_MS", "capturedAtMs >= sessionStart + SESSION_CAP_MS / 2"),
 ("C7 overlap-dropped", CHUNK, "val emitted = buildChunk(emittedFrames, ChunkReason.MAX_LENGTH, pendingOverlapMs)", "val emitted = buildChunk(emittedFrames, ChunkReason.MAX_LENGTH, 0)"),
 ("M1 merger-limit-half", CONTRACTS, "val maxOverlapWords = 8", "val maxOverlapWords = 4"),
 ("M2 merger-longest-first", CONTRACTS, "for (candidate in limit downTo 1)", "for (candidate in 1..limit)"),
 ("M3 merger-no-dedupe", CONTRACTS, "shouldDedupe = overlapsMs.isEmpty() ||", "shouldDedupe = overlapsMs.isEmpty() &&"),
 ("M4 merger-case-sensitive", CONTRACTS, "token.lowercase().filter", "token.filter"),
 ("M5 merger-ostensible", CONTRACTS, "overlapsMs.getOrNull(partIndex - 1) ?: 0) > 0", "overlapsMs.getOrNull(partIndex - 1) ?: 0) >= 0"),
 ("P1 planner-caret-start", CONTRACTS, "val caret = start + insert.length", "val caret = start"),
 ("P2 planner-clamp-tight", CONTRACTS, "val rawStart = selectionStart.coerceIn(0, base.length)", "val rawStart = selectionStart.coerceIn(1, base.length)"),
 ("P3 planner-bound-le", CONTRACTS, "val boundedEnd = end.coerceAtLeast(start)", "val boundedEnd = end.coerceAtMost(start)"),
 ("P4 surrogate-idx", CONTRACTS, "val start = if (base.splitsSurrogatePair(rawStart)) rawStart - 1 else rawStart", "val start = rawStart"),
 ("P5 end-sameside", CONTRACTS, "val end = if (base.splitsSurrogatePair(rawEnd)) (rawEnd + 1).coerceAtMost(base.length) else rawEnd", "val end = rawEnd"),
]

def results():
    tot = f = e = 0
    for p in glob_run(base):
        root = ET.parse(p).getroot()
        tot += int(root.get("tests", 0)); f += int(root.get("failures", 0)); e += int(root.get("errors", 0))
    return tot, f, e

def glob_run(b):
    import glob as g
    return g.glob(os.path.join(b, "core/build/test-results/test/*.xml"))

def run_tests():
    proc = subprocess.run(
        ["gradle", "--console=plain", ":core:test"],
        cwd=base, env=env, capture_output=True, text=True, timeout=2400,
    )
    return proc

def clean_results():
    import glob as g
    for p in g.glob(os.path.join(base, "core/build/test-results/test/*.xml")):
        os.remove(p)

killed, survived, build_break = [], [], []
total0 = None
for mid, path, old, new in MUTANTS:
    if mid == "C7 overlap-dropped":
        survivors_note = None  # placeholder, actual mutant applied below only if pattern found
    src = open(path).read()
    if old not in src:
        print(f"TABLE-ERROR {mid}: pattern not found in {os.path.basename(path)}")
        survived.append((mid, "pattern-missing"))
        continue
    bak = path + ".mutbak"
    shutil.copyfile(path, bak)
    try:
        open(path, "w").write(src.replace(old, new, 1))
        clean_results()
        proc = run_tests()
        rc = proc.returncode
        if rc != 0:
            killed.append(mid)
            print(f"KILLED    {mid} (gradle rc={rc})")
        else:
            tot, f, e = results()
            if f or e or tot == 0:
                killed.append(mid)
                print(f"KILLED    {mid} (xml failures={f} errors={e})")
            else:
                survived.append((mid, f"{tot} tests green"))
                print(f"SURVIVED  {mid} ({tot} tests green)")
    finally:
        shutil.move(bak, path)

# restore and prove the suite is green again
clean_results()
proc = run_tests()
tot, f, e = results()
print(f"RESTORED: core tests={tot} failures={f} errors={e} (must be match baseline 137/0/0)")
if f or e:
    print("FAIL baseline not green after run")
    sys.exit(2)

score = len(killed) / len(MUTANTS) * 100
print(f"\nMUTANTS: {len(MUTANTS)} applied, killed={len(killed)}, survived={len(survived)}")
print(f"MUTATION SCORE: {score:.1f}%")
if survived:
    print("SURVIVORS (each becomes a test ticket):")
    for mid, why in survived:
        print(f"  - {mid}: {why}")
sys.exit(1 if survived else 0)
