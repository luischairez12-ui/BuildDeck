#!/usr/bin/env bash
set -euo pipefail
sandbox="$(mktemp -d)"
trap 'rm -rf "$sandbox"' EXIT
export HOME="$sandbox"
helper="$(pwd)/app/src/main/assets/termux_tasks.sh"
nonce=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
id=bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb
project=cccccccc-cccc-cccc-cccc-cccccccccccc
dir="$HOME/builddeck/tasks/$id"
mkdir -p "$dir"
echo "$project" > "$dir/project"
echo COMPILING > "$dir/state"
exec 9>"$dir/lock"
flock -n 9
output="$(bash "$helper" list "" "$nonce" 9>&-)"
grep -Fq "JOB|$id|COMPILING|ACTIVE|$project" <<< "$output"
grep -Fq "END|$nonce" <<< "$output"
bash "$helper" cancel "$id" "$nonce" 9>&- | grep -Fq 'Cancellation requested'
test -f "$dir/cancel"
rm "$dir/cancel"
echo PACKAGING > "$dir/state"
bash "$helper" cancel "$id" "$nonce" 9>&- | grep -Fq 'cancellation skipped'
test ! -f "$dir/cancel"
echo DONE > "$dir/state"
bash "$helper" clean "" "$nonce" 9>&- >/dev/null
test -d "$dir"
flock -u 9
exec 9>&-
bash "$helper" clean "" "$nonce" >/dev/null
test ! -d "$dir"
echo 'Scoped task-manager tests passed.'
