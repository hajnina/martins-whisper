#!/bin/bash
set -u
m="$1"; t="$2"
base="/home/node/bench"
out="$base/runs/${m}-${t}"
mkdir -p "$base/runs/$m"
case "$t" in
  A)
    wd="$base/repos/$m"
    prompt="The Kotlin unit test BenchFixTest is failing because of a planted bug. Failing output: BenchFixTest.addsTwoNumbers: expected:<7> but was:<-1>; BenchFixTest.handlesNegative: expected:<1> but was:<7>. Fix the bug in core/src/main/kotlin/com/martins/whisper/core/BenchFix.kt. Edit only that file. Do not run gradle or any build; the harness verifies. Reply DONE when finished."
    ;;
  B)
    wd="$base/runs/$m/B"; mkdir -p "$wd"; prompt="$(cat "$base/prompts/B.txt")"
    ;;
  C)
    wd="$base/runs/$m/C"; mkdir -p "$wd"; prompt="$(cat "$base/prompts/C.txt")"
    ;;
  D)
    wd="$base/runs/$m/D"; mkdir -p "$wd"; prompt="$(cat "$base/prompts/D.txt")"
    ;;
  *) echo "bad task $t" >&2; exit 2;;
esac
start=$(date +%s)
( cd "$wd" && timeout 900 env PATH="$base/bin:$PATH" GRADLE_USER_HOME=/home/node/gh-pm \
    opencode run -m "opencode/$m" --format json --auto "$prompt" > "$out.jsonl" 2> "$out.err" )
code=$?
end=$(date +%s)
wall=$((end-start))
timeout_flag=false; [ "$code" -eq 124 ] && timeout_flag=true
printf '{"model":"%s","task":"%s","start":%s,"end":%s,"wall_s":%s,"exit":%s,"timeout":%s,"workdir":"%s"}\n' \
  "$m" "$t" "$start" "$end" "$wall" "$code" "$timeout_flag" "$wd" > "$out.meta.json"
echo "$m $t exit=$code wall=${wall}s"
