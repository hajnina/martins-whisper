#!/usr/bin/env bash
set -u

if [ "$#" -lt 4 ]; then
  echo "usage: $0 ID WORKTREE PROMPT_FILE MODEL..." >&2
  exit 2
fi

id=$1
worktree=$2
prompt_file=$3
shift 3

log="/work/martins-whisper/agents/logs/${id}.log"
pid_file="/work/martins-whisper/agents/logs/${id}.pid"
prompt=$(cat "$prompt_file") || exit 1
base=$(git -C "$worktree" rev-parse HEAD) || exit 1

echo $$ >"$pid_file"
{
  echo "=== ${id} launch $(date -u +%FT%TZ) base=${base}"
  for model in "$@"; do
    echo "=== ${id} model ${model} start $(date -u +%FT%TZ)"
    ( cd "$worktree" && timeout 900 opencode run --agent general --auto -m "opencode/${model}" "$prompt" )
    rc=$?
    echo "=== ${id} model ${model} exit ${rc} $(date -u +%FT%TZ)"

    if [ -n "$(git -C "$worktree" status --porcelain)" ]; then
      git -C "$worktree" add -A
      git -C "$worktree" commit -m "wip(${id}): checkpoint after ${model}" >/dev/null 2>&1 || true
    fi

    head=$(git -C "$worktree" rev-parse HEAD)
    if [ "$head" != "$base" ]; then
      echo "=== ${id} SUCCESS model=${model} head=${head} $(date -u +%FT%TZ)"
      exit 0
    fi
  done
  echo "=== ${id} FAILED no commit $(date -u +%FT%TZ)"
  exit 1
} >>"$log" 2>&1
