#!/data/data/com.termux/files/usr/bin/bash
# BuildDeck 4.3 approved local builder with authenticated on-device output handoff.
# Source is transferred through Downloads, never executed from Downloads.
set -Eeuo pipefail
project_id="${1:-}"
zip_name="${2:-}"
apk_uri="${3:-}"
log_uri="${4:-}"
record_id="${5:-}"
stage_uri="${6:-}"
if [[ -n "$record_id" ]] && [[ ! "$record_id" =~ ^[a-f0-9-]{36}$ ]]; then echo "Invalid build record id"; exit 2; fi
if [[ ! "$project_id" =~ ^[a-f0-9-]{36}$ ]] || [[ ! "$zip_name" =~ ^[a-f0-9-]{36}_[0-9]+\.zip$ ]] || [[ "$zip_name" != "$project_id"_* ]]; then
  echo "Invalid project id or archive name" >&2
  exit 2
fi

# Termux RUN_COMMAND launches non-interactive bash; ~/.bashrc may never be loaded.
# Use verified OnePlus setup paths instead of relying on an interactive shell.
export PREFIX=/data/data/com.termux/files/usr
export JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$PREFIX/bin:/system/bin:/system/xbin:$PATH"
inbox="$HOME/storage/downloads/BuildDeck/Inbox/$zip_name"
out="$HOME/storage/downloads/BuildDeck/Out/$project_id"
workspace="$HOME/builddeck/work/$project_id"
mkdir -p "$out" "$HOME/builddeck/work"
log="$out/${record_id:-build_$(date +%Y%m%d_%H%M%S)}.log"
exec > >(tee -a "$log") 2>&1
report_stage() {
  local description="$1"
  echo "BUILDDECK_STAGE=$description"
  # Actual stage events, no simulated percent complete.
  if [[ "$stage_uri" == content://com.builddeck.mobile.handoff/v1/* ]] && [ -x /system/bin/content ]; then
    printf "%s\n" "$description" | /system/bin/content write --uri "$stage_uri" >/dev/null 2>&1 || true
  fi
}
finalize_build() {
  code=$?
  trap - EXIT
  if [ "$code" -eq 0 ]; then echo "BUILDDECK_RESULT=SUCCESS"; report_stage "Complete"; else echo "BUILDDECK_RESULT=FAILED (exit $code)"; report_stage "Build failed"; fi
  # Termux's PendingIntent remains authoritative; the authenticated handoff
  # copies the full log to BuildDeck private storage when Android permits it.
  if [[ "$log_uri" == content://com.builddeck.mobile.handoff/v1/* ]] && [ -s "$log" ]; then
    /system/bin/content write --uri "$log_uri" < "$log" >/dev/null 2>&1 || true
  fi
  exit "$code"
}
trap finalize_build EXIT

echo "BuildDeck local build pipeline v4.3"
report_stage "Preparing project"
echo "Input: $inbox"
echo "Java: $JAVA_HOME"
echo "SDK: $ANDROID_HOME"
for tool in unzip java find aapt2; do
  command -v "$tool" >/dev/null 2>&1 || { echo "Missing tool: $tool"; exit 3; }
done
[ -x "$JAVA_HOME/bin/java" ] || { echo "Java 17 unavailable"; exit 3; }
[ -f "$ANDROID_HOME/platforms/android-35/android.jar" ] || { echo "Install Android SDK API 35"; exit 7; }
[ -f "$inbox" ] || { echo "Missing source ZIP; verify Termux storage access"; exit 4; }
rm -rf -- "$workspace"
mkdir -p "$workspace"
if unzip -Z1 "$inbox" | grep -Eq '(^/|(^|/)\.\.(/|$)|\\)'; then
  echo "Unsafe ZIP entries"; exit 5
fi
report_stage "Extracting source"
unzip -q "$inbox" -d "$workspace"
root="$workspace"
if [ ! -f "$root/settings.gradle" ] && [ ! -f "$root/settings.gradle.kts" ]; then
  candidate="$(find "$workspace" -mindepth 1 -maxdepth 4 -type f \( -name settings.gradle -o -name settings.gradle.kts \) -print -quit)"
  if [ -z "$candidate" ]; then
    echo "Missing Gradle settings file: no settings.gradle or settings.gradle.kts in the source ZIP."
    if unzip -Z1 "$inbox" | grep -Eq '^(classes[0-9]*\.dex|resources\.arsc)$'; then
      echo "This input looks like a compiled Android APK instead of an Android Gradle source project."
      echo "Use Releases to import compiled APKs; use GitHub Workspace to import complete source."
    else
      echo "First archive entries (to help diagnose the folder layout):"
      unzip -Z1 "$inbox" | sed -n '1,15p'
      echo "Use Projects -> GitHub Workspace -> Import to get full Gradle source."
    fi
    exit 6
  fi
  root="$(dirname "$candidate")"
fi
cd "$root"

# Use a project's own wrapper if present; otherwise use the verified Gradle 8.9.
# A different Android Gradle Plugin may require a different Gradle version.
if [ -f ./gradlew ]; then
  chmod +x ./gradlew
  cmd=(./gradlew)
elif [ -x "$HOME/opt/gradle-8.9/bin/gradle" ]; then
  cmd=("$HOME/opt/gradle-8.9/bin/gradle")
elif command -v gradle >/dev/null 2>&1; then
  cmd=(gradle)
else
  echo "No compatible Gradle executable found"; exit 8
fi

report_stage "Compiling Android app"
echo "Running: ${cmd[0]} --no-daemon --max-workers=2 assembleDebug"
"${cmd[@]}" --no-daemon --max-workers=2 assembleDebug "-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)"
apk="$(find "$root" -type f -path '*/build/outputs/apk/debug/*.apk' -name '*.apk' -print -quit)"
[ -n "$apk" ] && [ -s "$apk" ] || { echo "Gradle completed without an APK"; exit 9; }
report_stage "Packaging APK"
dest="$out/${record_id:-$(date +%Y%m%d_%H%M%S)}_$(basename "$apk")"
cp -f -- "$apk" "$dest"
echo "BUILDDECK_APK=$dest"
echo "BUILDDECK_APK_SHA256=$(sha256sum "$dest" | awk '{print $1}')"
if [[ "$apk_uri" == content://com.builddeck.mobile.handoff/v1/* ]] && [ -x /system/bin/content ]; then
  if /system/bin/content write --uri "$apk_uri" < "$dest" >/dev/null 2>&1; then
    echo "BUILDDECK_APK_IMPORTED=YES"
  else
    echo "BUILDDECK_APK_IMPORTED=NO - use the manual import from Downloads"
  fi
fi
report_stage "APK ready"
echo "APK ready in Downloads/BuildDeck/Out/$project_id"
echo "Signing: Gradle debug.keystore in Termux ~/.android/ (keep a private backup)."
