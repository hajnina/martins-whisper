#!/bin/bash
set -u
base="/home/node/bench"
cd "$base"
rm -f "$base/logs/BENCH-DONE"
echo "BENCH START $(date -u +%FT%TZ)"
# Task A sequential (Gradle verification shares one cache)
while read -r m; do
  [ -f "$base/runs/${m}-A.meta.json" ] && continue
  "$base/run_one.sh" "$m" A
  cd "$base/repos/$m"
  export JAVA_HOME=/work/android-toolchain/jdk ANDROID_HOME=/work/android-toolchain/sdk ANDROID_SDK_ROOT=/work/android-toolchain/sdk GRADLE_USER_HOME=/home/node/gh-pm
  export PATH="$JAVA_HOME/bin:/work/android-toolchain/gradle/gradle-8.9/bin:$PATH"
  timeout 600 gradle :core:test --tests '*BenchFixTest' --offline --no-daemon --console=plain > "$base/runs/${m}-A-verify.log" 2>&1
  v=$?
  echo "{\"model\":\"$m\",\"task\":\"A-verify\",\"exit\":$v}" > "$base/runs/${m}-A-verify.meta.json"
  echo "A verify $m exit=$v"
done < "$base/models.txt"
# Tasks B/C/D in parallel, max 4
: > "$base/logs/jobs.txt"
while read -r m; do for t in B C D; do echo "$m $t" >> "$base/logs/jobs.txt"; done; done < "$base/models.txt"
xargs -a "$base/logs/jobs.txt" -n 2 -P 4 "$base/run_one.sh"
echo "BENCH END $(date -u +%FT%TZ)"
touch "$base/logs/BENCH-DONE"
