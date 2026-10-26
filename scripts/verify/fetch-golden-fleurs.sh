#!/usr/bin/env bash
# V6 multilingual golden fixtures: fetch a few short public FLEURS clips per language
# (dataset google/fleurs, CC-BY 4.0) through the Hugging Face datasets-server /rows API,
# convert them to 16 kHz mono PCM16 WAV (datasets-server serves 32-bit IEEE float),
# write them to recordings/golden-fleurs/ (GITIGNORED - audio never enters git) and
# emit the expected-transcript manifest app/src/test/resources/golden/fleurs-manifest.json
# (COMMITTED, contains source ids + transcripts only, no audio).
#
# Usage: scripts/verify/fetch-golden-fleurs.sh [--langs cs,en,...] [--rows 6] [--per-lang 3]
# Env:   FLEURS_DIR (default recordings/golden-fleurs), PYTHON (a python3 binary)
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."
ROOT="$PWD"
FLEURS_DIR="${FLEURS_DIR:-$ROOT/recordings/golden-fleurs}"
MANIFEST="$ROOT/app/src/test/resources/golden/fleurs-manifest.json"
ROWS="${ROWS:-6}"
PER_LANG="${PER_LANG:-3}"

die() { echo "fetch-golden-fleurs: FAIL: $*" >&2; exit 1; }
PYTHON="${PYTHON:-$(command -v python3 || true)}"
[[ -n "$PYTHON" && -x "$PYTHON" ]] || die "python3 is required (nix run nixpkgs#python3)"
command -v jq >/dev/null || die "jq is required"
command -v curl >/dev/null || die "curl is required"

# language: whisper code + FLEURS config name
declare -A FLEURS_CONFIG=(
    [cs]=cs_cz [en]=en_us [de]=de_de [es]=es_es [fr]=fr_fr [ru]=ru_ru
    [uk]=uk_ua [pl]=pl_pl [ar]=ar_eg [he]=he_il [hi]=hi_in [th]=th_th
    [ja]=ja_jp [zh]=cmn_hans_cn [ko]=ko_kr [tr]=tr_tr
)
# CER languages (CJK/Thai, no word spacing)
declare -A CER_LANG=(
    [ja]=1 [zh]=1 [th]=1
)
DATASET=google/fleurs
SPLIT=validation

LANGS_ALL=(cs en de es fr ru uk pl ar he hi th ja zh ko tr)
if [[ "${1:-}" == "--langs" ]]; then
    IFS=',' read -r -a LANGS_ALL <<< "${2// /}"
    shift 2
fi

mkdir -p "$FLEURS_DIR" "$ROOT/app/src/test/resources/golden"
TMPROOT="$(mktemp -d /tmp/fleurs.XXXXXX)"
trap 'rm -rf "$TMPROOT"' EXIT

fetch_config() {
    local cfg="$1" out="$TMPROOT/$cfg.json" try
    for try in 1 2 3; do
        curl -s --max-time 120 -o "$out" \
            "https://datasets-server.huggingface.co/rows?dataset=$DATASET&config=$cfg&split=$SPLIT&offset=0&length=$ROWS" \
            || true
        # "server is busier" responses are retried after a short wait
        if ! grep -q '"error"' "$out"; then echo "$out"; return 0; fi
        jq -r '.error' "$out" | head -1 | sed "s/^/  $cfg: /" >&2
        sleep $((try * 20))
        if ! grep -q 'Scan size limit' "$out"; then continue; fi
        return 1 # parquet scan limit -> unrecoverable here
    done
    return 1
}

declare -a FAILS=() FETCHED_ROWS=()
for lang in "${LANGS_ALL[@]}"; do
    cfg="${FLEURS_CONFIG[$lang]}"
    out="$(fetch_config "$cfg")" || { FAILS+=("$lang"); echo "!! $lang ($cfg): datasets-server gave no rows, skipping" >&2; continue; }
    mkdir -p "$FLEURS_DIR/$lang"

    # select the first PER_LANG sane-length rows (2.5-12.5 s of speech)
    n=0
    while IFS=$'\t' read -r id nsamples dur transcript jsonrow; do
        n=$((n + 1))
        base="${lang}_${id}"
        url="$(echo "$jsonrow" | jq -r '.row.audio[0].src')"
        wav_src="$TMPROOT/$base.src.wav"
        curl -s --max-time 120 -o "$wav_src" "$url" \
            || { echo "  !! audio download failed: $lang id=$id" >&2; continue; }
        "$PYTHON" "$ROOT/scripts/verify/wav_pcm16.py" "$wav_src" "$FLEURS_DIR/$lang/$base.wav"
        FETCHED_ROWS+=("$lang|$id|$cfg|$SPLIT|$nsamples|$dur|$transcript|$FLEURS_DIR/$lang/$base.wav")
    done < <("$PYTHON" - "$out" "$PER_LANG" "$lang" <<'PYEOF'
import json, sys
path, per, lang = sys.argv[1], int(sys.argv[2]), sys.argv[3]
data = json.load(open(path))
picked = 0
for r in data["rows"]:
    row = r["row"]
    if not (40000 <= row["num_samples"] <= 200000):
        continue
    picked += 1
    text = row["raw_transcription"].replace("\t", " ").replace("\n", " ")
    print(f'{row["id"]}\t{row["num_samples"]}\t{row["num_samples"]/16000:.2f}\t{text}\t{json.dumps(r)}')
    if picked >= per:
        break
PYEOF
)
    echo "  $lang: $n clips"
done

# manifest (committed): json + a human-readable summary of what was fetched
"$PYTHON" - "$MANIFEST" "${FETCHED_ROWS[@]}" <<'PYEOF'
import json, os, sys
out_path = sys.argv[1]
entries = []
for chunk in sys.argv[2:]:
    lang, rid, cfg, split, nsamples, dur, transcript, af = chunk.split("|", 7)
    entries.append({
        "dataset": "google/fleurs",
        "config": cfg,
        "split": split,
        "sourceId": f'{cfg}/{split}#id={rid}',
        "rowId": int(rid),
        "language": lang,
        "cer": lang in ("ja", "zh", "th"),
        "numSamples": int(nsamples),
        "durationSeconds": float(dur),
        "audioFile": "FLEURS_DIR/" + af.split("recordings/golden-fleurs/")[-1],
        "expectedTranscript": transcript,
    })
manifest = {
    "note": "CC-BY 4.0 clips from the google/fleurs dataset via the Hugging Face datasets-server; audio itself is not committed (recordings/golden-fleurs is gitignored); re-fetch with scripts/verify/fetch-golden-fleurs.sh",
    "license": "CC-BY-4.0",
    "source": "https://datasets-server.huggingface.co/datasets?dataset=google/fleurs",
    "clips": entries,
}
import os
this_run = {e["language"] for e in entries}
try:
    old = json.load(open(out_path))
    for c in old.get("clips", []):
        if c.get("language") not in this_run:
            entries.append(c)
except Exception:
    pass
import os
os.makedirs(os.path.dirname(out_path), exist_ok=True)
json.dump(manifest, open(out_path, "w"), ensure_ascii=False, indent=1)
print(f"manifest written: {out_path} ({len(entries)} clips)")
PYEOF

if [[ ${#FAILS[@]} -gt 0 ]]; then
    echo "SKIPPED languages (datasets-server unavailable): ${FAILS[*]}" >&2
    exit 3
fi
echo "fetch-golden-fleurs: OK"
