#!/data/data/com.termux/files/usr/bin/bash
# Only recover verified APKs from previously approved build records.
set -u
downloads="$HOME/storage/downloads"
tmp="$(mktemp -d)" || exit 1
trap 'rm -rf -- "$tmp"' EXIT
shopt -s nullglob
while (( $# >= 4 )); do
  project="$1"; id="$2"; apk_uri="$3"; log_uri="$4"; shift 4
  [[ "$project" =~ ^[a-f0-9-]{36}$ && "$id" =~ ^[a-f0-9-]{36}$ ]] || continue
  [[ "$apk_uri" == content://com.builddeck.mobile.handoff/v1/"$id"/*/apk ]] || continue
  [[ "$log_uri" == content://com.builddeck.mobile.handoff/v1/"$id"/*/log ]] || continue
  out="$downloads/BuildDeck/Out/$project"
  log="$out/$id.log"
  [ -s "$log" ] || continue
  # Never promote a failed build. Require a completed Gradle/package checksum or success marker.
  terminal="$(grep '^BUILDDECK_RESULT=' "$log" | tail -1)"
  [[ "$terminal" != BUILDDECK_RESULT=FAILED* ]] || continue
  if [[ "$terminal" != BUILDDECK_RESULT=SUCCESS ]]; then
    grep -q '^BUILD SUCCESSFUL' "$log" || continue
  fi
  expected="$(grep '^BUILDDECK_APK_SHA256=' "$log" | tail -1 | cut -d= -f2)"
  [[ "$expected" =~ ^[a-f0-9]{64}$ ]] || continue
  candidates=("$out"/"$id"_*.apk "$downloads"/*-"${id:0:8}"-debug.apk)
  for file in "${candidates[@]}"; do
    [ -s "$file" ] || continue
    [ "$(wc -c < "$file")" -le 524288000 ] || continue
    actual="$(sha256sum "$file" | awk '{print $1}')"
    [[ "$actual" == "$expected" ]] || continue
    unzip -tq "$file" >/dev/null 2>&1 || continue
    # The original Downloads APK and log are untouched.
    tail -c 110000 "$log" > "$tmp/result.log"
    printf '\nBUILDDECK_APK_SHA256=%s\nBUILDDECK_STAGE=Complete\nBUILDDECK_RESULT=SUCCESS\nBUILDDECK_RECOVERED_FROM_DOWNLOADS=YES\n' "$expected" >> "$tmp/result.log"
    if timeout -k 3s 90s /system/bin/content write --uri "$apk_uri" < "$file" >/dev/null 2>&1; then
      timeout -k 3s 20s /system/bin/content write --uri "$log_uri" < "$tmp/result.log" >/dev/null 2>&1 || true
    fi
    break
  done
done
