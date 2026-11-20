#!/data/data/com.termux/files/usr/bin/bash
# This script is supplied from BuildDeck directly via the Termux intent stdin.
# Never execute a script from the world-writable Downloads directory.
set -Eeuo pipefail
id="${1:-}"
zip_name="${2:-}"
if [[ ! "$id" =~ ^[a-f0-9-]{36}$ ]] || [[ ! "$zip_name" =~ ^[a-f0-9-]{36}_[0-9]+\.zip$ ]] || [[ "$zip_name" != "$id"_* ]]; then
  echo "Invalid project identifier/zip name" >&2
  exit 2
fi
inbox="$HOME/storage/downloads/BuildDeck/Inbox/$zip_name"
out="$HOME/storage/downloads/BuildDeck/Out/$id"
workspace="$HOME/builddeck/work/$id"
mkdir -p "$out" "$HOME/builddeck/work"
log="$out/build_$(date +%Y%m%d_%H%M%S).log"
exec > >(tee -a "$log") 2>&1
# Result marker is consumed by BuildDeck's manually imported log viewer.
# Never mark success just because a command was started.
trap 'code=$?; if [ "$code" -eq 0 ]; then echo "BUILDDECK_RESULT=SUCCESS"; else echo "BUILDDECK_RESULT=FAILED (exit $code)"; fi' EXIT
printf 'BuildDeck local build for %s\n' "$id"
printf 'Input: %s\n' "$inbox"
for tool in unzip java find; do
  if ! command -v "$tool" >/dev/null 2>&1; then echo "Missing Termux tool: $tool"; exit 3; fi
done
if [ ! -f "$inbox" ]; then echo 'Cannot read source ZIP; grant Termux shared storage access and run termux-setup-storage.'; exit 4; fi
rm -rf -- "$workspace"
mkdir -p "$workspace"
# Validate archive member names before extraction; reject absolute, parent, Windows paths.
if unzip -Z1 "$inbox" | grep -Eq '(^/|(^|/)\.\.(/|$)|\\)'; then
  echo 'Unsafe ZIP paths detected; aborting.'; exit 5
fi
unzip -q "$inbox" -d "$workspace"
root="$workspace"
if [ ! -f "$root/settings.gradle" ] && [ ! -f "$root/settings.gradle.kts" ]; then
  candidate="$(find "$workspace" -mindepth 1 -maxdepth 3 -type f \( -name settings.gradle -o -name settings.gradle.kts \) -print -quit)"
  if [ -n "$candidate" ]; then root="$(dirname "$candidate")"; fi
fi
if [ ! -f "$root/settings.gradle" ] && [ ! -f "$root/settings.gradle.kts" ]; then
  echo 'Project is missing settings.gradle or settings.gradle.kts; import a complete Android Gradle project.'; exit 6
fi
cd "$root"
if [ -z "${ANDROID_HOME:-}" ]; then export ANDROID_HOME="$HOME/android-sdk"; fi
if [ ! -d "$ANDROID_HOME/platforms" ]; then
  echo "Android SDK not configured at $ANDROID_HOME; set ANDROID_HOME in Termux."; exit 7
fi
if [ -f ./gradlew ]; then
  chmod +x ./gradlew
  cmd=(./gradlew)
elif command -v gradle >/dev/null 2>&1; then
  cmd=(gradle)
else
  echo 'Neither a Gradle wrapper nor a system Gradle executable was found.'; exit 8
fi
extra=()
if command -v aapt2 >/dev/null 2>&1; then
  extra+=("-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)")
else
  echo 'Warning: No native Termux aapt2 found; Android builds may fail on ARM devices.'
fi
echo "Building from $root"
"${cmd[@]}" --no-daemon assembleDebug "${extra[@]}"
found=0
while IFS= read -r -d '' apk; do
  found=$((found + 1))
  cp -f -- "$apk" "$out/$(date +%Y%m%d_%H%M%S)_$(basename "$apk")"
  echo "APK ready in: $out"
done < <(find "$root" -type f -path '*/build/outputs/apk/*' -name '*.apk' -print0)
if [ "$found" -eq 0 ]; then echo 'Gradle completed but no APK was found; check build configuration.'; exit 9; fi
echo "Build successful. Import APK from Downloads/BuildDeck/Out/$id into BuildDeck Releases."
