#!/bin/bash
set -u
base="/home/node/bench"
cd "$base"
echo "A2 START $(date -u +%FT%TZ)"
while read -r m <&3; do
  [ -f "$base/runs/${m}-A.meta.json" ] && continue
  "$base/run_one.sh" "$m" A < /dev/null
  cd "$base/repos/$m"
  export JAVA_HOME=/work/android-toolchain/jdk ANDROID_HOME=/work/android-toolchain/sdk ANDROID_SDK_ROOT=/work/android-toolchain/sdk GRADLE_USER_HOME=/home/node/gh-pm
  export PATH="$JAVA_HOME/bin:/work/android-toolchain/gradle/gradle-8.9/bin:$PATH"
  timeout 600 gradle :core:test --tests '*BenchFixTest' --offline --no-daemon --console=plain < /dev/null > "$base/runs/${m}-A-verify.log" 2>&1
  v=$?
  echo "{\"model\":\"$m\",\"task\":\"A-verify\",\"exit\":$v}" > "$base/runs/${m}-A-verify.meta.json"
  echo "A verify $m exit=$v"
done 3< "$base/models.txt"
echo "A2 END $(date -u +%FT%TZ)"
touch "$base/logs/A2-DONE"
