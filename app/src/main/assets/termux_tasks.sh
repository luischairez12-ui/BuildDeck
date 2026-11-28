#!/data/data/com.termux/files/usr/bin/bash
# Only inspect the private registry created by the bundled BuildDeck builder.
set -u
root="$HOME/builddeck/tasks"
action="${1:-list}"
id="${2:-}"
if [[ "$action" != list && "$action" != cancel && "$action" != clean ]]; then exit 2; fi
nonce="${3:-}"
uri="${4:-}"
[[ "$nonce" =~ ^[a-f0-9-]{36}$ ]] || exit 2
mkdir -p "$root" || exit 3
report="$(mktemp "$root/.snapshot.XXXXXXXX")" || exit 3
trap 'rm -f -- "$report"' EXIT
exec 3>&1
exec > "$report"

if [[ "$action" == cancel ]]; then
  [[ "$id" =~ ^[a-f0-9-]{36}$ ]] || exit 2
  dir="$root/$id"
  phase="$(cat "$dir/state" 2>/dev/null || true)"
  case "$phase" in
    STARTING|PREPARING|EXTRACTING|COMPILING)
      if [ -d "$dir" ]; then touch "$dir/cancel"; echo "NOTICE|Cancellation requested; wait for the build to acknowledge it."; fi ;;
    PACKAGING|DELIVERING) echo "NOTICE|APK is being saved; cancellation skipped to protect output." ;;
    *) echo "NOTICE|No cancellable managed job found for this request." ;;
  esac
fi
shopt -s nullglob
for dir in "$root"/*; do
  [ -d "$dir" ] || continue
  key="${dir##*/}"
  [[ "$key" =~ ^[a-f0-9-]{36}$ ]] || continue
  project="$(cat "$dir/project" 2>/dev/null || true)"
  phase="$(cat "$dir/state" 2>/dev/null || true)"
  [[ "$project" =~ ^[a-f0-9-]{36}$ ]] || continue
  live=UNKNOWN
  if command -v flock >/dev/null 2>&1 && [ -f "$dir/lock" ]; then
    if flock -n "$dir/lock" true; then live=STOPPED; else live=ACTIVE; fi
  fi
  case "$phase" in DONE|FAILED|CANCELLED) ;; *) if [[ "$live" == STOPPED ]]; then phase=ABANDONED; fi ;; esac
  if [[ "$action" == clean && "$live" == STOPPED ]]; then
    case "$phase" in
      DONE|FAILED|CANCELLED)
        # Registry metadata only. Never touch source, logs, APKs or Gradle caches.
        rm -f -- "$dir/project" "$dir/pid" "$dir/state" "$dir/state.tmp" "$dir/cancel" "$dir/lock"
        rmdir -- "$dir" 2>/dev/null || true
        continue ;;
    esac
  fi
  [ ! -f "$dir/cancel" ] || { case "$phase" in STARTING|PREPARING|EXTRACTING|COMPILING) phase=CANCEL_REQUESTED ;; esac; }
  printf 'JOB|%s|%s|%s|%s\n' "$key" "$phase" "$live" "$project"
done
echo "NOTICE|Only BuildDeck-managed jobs created by v0.5.1+ are shown."
printf "END|%s\n" "$nonce"
if [[ "$uri" == content://com.builddeck.mobile.handoff/task/* ]] && command -v timeout >/dev/null 2>&1; then
  timeout -k 2s 12s /system/bin/content write --uri "$uri" < "$report" >/dev/null 2>&1 || true
fi
cat "$report" >&3
