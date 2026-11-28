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
project_name="${7:-AndroidApp}"
if [[ ! "$project_name" =~ ^[A-Za-z0-9._-]{1,40}$ ]]; then
  echo "Invalid project label" >&2
  exit 2
fi
if [[ ! "$record_id" =~ ^[a-f0-9-]{36}$ ]]; then echo "Invalid build record id"; exit 2; fi
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
workspace="$HOME/builddeck/managed_work/$project_id"
mkdir -p "$out" "$HOME/builddeck/managed_work"
log="$out/${record_id:-build_$(date +%Y%m%d_%H%M%S)}.log"
# Use a durable log rather than a tee process holding the Termux callback pipe.
exec 3>&1
exec >> "$log" 2>&1
# Every managed build owns a private control directory, never shared storage.
control="$HOME/builddeck/tasks/$record_id"
mkdir -p "$HOME/builddeck/tasks" "$HOME/builddeck/locks"
mkdir "$control" || exit 10
printf "%s\n" "$project_id" > "$control/project"
state() { printf "%s\n" "$1" > "$control/state.tmp"; mv -f "$control/state.tmp" "$control/state"; }
state STARTING
# flock is optional on minimal Termux installations; normal builds still work.
if command -v flock >/dev/null 2>&1; then
  exec 9>"$control/lock"
  flock -n 9 || exit 10
fi
check_cancel() { if [ -f "$control/cancel" ]; then echo "BUILDDECK_CANCELLED=YES"; exit 130; fi; }

handoff_write() {
  local uri="$1" limit="$2"
  timeout -k 3s "$limit" /system/bin/content write --uri "$uri"
}
report_stage() {
  local description="$1"
  echo "BUILDDECK_STAGE=$description"
  # Actual stage events, no simulated percent complete.
  if [[ "$stage_uri" == content://com.builddeck.mobile.handoff/v1/* ]] && [ -x /system/bin/content ]; then
    printf "%s\n" "$description" | handoff_write "$stage_uri" 12s >/dev/null 2>&1 || echo "BUILDDECK_HANDOFF_WARNING=stage delivery failed"
  fi
}
finalize_build() {
  code=$?
  trap - EXIT
  # Write the authoritative terminal marker to the durable log.
  if [ "$code" -eq 0 ]; then
    report_stage "Complete"
    echo "BUILDDECK_RESULT=SUCCESS"
  else
    if [ "$code" -eq 130 ] && [ -f "$control/cancel" ]; then
      report_stage "Cancelled by user"
    else
      report_stage "Build failed"
    fi
    echo "BUILDDECK_RESULT=FAILED (exit $code)"
  fi
  # Termux's PendingIntent remains authoritative; the authenticated handoff
  # copies the full log to BuildDeck private storage when Android permits it.
  if [[ "$log_uri" == content://com.builddeck.mobile.handoff/v1/* ]] && [ -s "$log" ]; then
    handoff_write "$log_uri" 20s < "$log" >/dev/null 2>&1 || echo "BUILDDECK_HANDOFF_WARNING=log delivery failed"
  fi
  # Return a bounded summary to the PendingIntent; full output stays on disk.
  if [ "$code" -eq 0 ]; then state DONE; elif [ "$code" -eq 130 ] && [ -f "$control/cancel" ]; then state CANCELLED; else state FAILED; fi
  tail -c 32000 "$log" >&3 || true
  exec 3>&-
  exit "$code"
}
trap finalize_build EXIT

echo "BuildDeck local build pipeline v5.1"
# Protect the existing per-project workspace from concurrent new managed builds.
if command -v flock >/dev/null 2>&1; then
  exec 8>"$HOME/builddeck/locks/$project_id.lock"
  flock -n 8 || { echo "Another managed build owns this project workspace"; exit 10; }
else
  # Isolate new jobs if the device lacks flock; never overwrite another workspace.
  workspace="$HOME/builddeck/managed_work/${project_id}_${record_id}"
fi
state PREPARING
check_cancel
report_stage "Preparing project"
echo "Input: $inbox"
echo "Java: $JAVA_HOME"
echo "SDK: $ANDROID_HOME"
for tool in unzip java find aapt2 timeout; do
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
check_cancel
state EXTRACTING
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
check_cancel
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

state COMPILING
check_cancel
report_stage "Compiling Android app"
echo "Running: ${cmd[0]} --no-daemon --max-workers=2 assembleDebug"
if command -v setsid >/dev/null 2>&1; then
  setsid "${cmd[@]}" --no-daemon --max-workers=2 assembleDebug "-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)" 3>&- 8>&- 9>&- &
  gradle_pid=$!
  while kill -0 "$gradle_pid" 2>/dev/null; do
    if [ -f "$control/cancel" ]; then
      echo "BUILDDECK_CANCELLED=YES"
      # This is the dedicated session started above, never an arbitrary PID.
      kill -TERM -- "-$gradle_pid" 2>/dev/null || kill -TERM "$gradle_pid" 2>/dev/null || true
      for ((i=0; i<5; i++)); do kill -0 "$gradle_pid" 2>/dev/null || break; sleep 1; done
      if kill -0 "$gradle_pid" 2>/dev/null; then kill -KILL -- "-$gradle_pid" 2>/dev/null || kill -KILL "$gradle_pid" 2>/dev/null || true; fi
      wait "$gradle_pid" || true
      exit 130
    fi
    sleep 1
  done
  wait "$gradle_pid"
else
  echo "BUILDDECK_HANDOFF_WARNING=setsid missing; cancellation waits for compilation to return"
  "${cmd[@]}" --no-daemon --max-workers=2 assembleDebug "-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)" 3>&- 8>&- 9>&-
fi
# Once Gradle succeeds, finish packaging and transfer even if a late cancel arrives.
state PACKAGING
apk="$(find "$root" -type f -path '*/build/outputs/apk/debug/*.apk' -name '*.apk' -print -quit)"
[ -n "$apk" ] && [ -s "$apk" ] || { echo "Gradle completed without an APK"; exit 9; }
report_stage "Packaging APK"
dest="$out/${record_id:-$(date +%Y%m%d_%H%M%S)}_$(basename "$apk")"
cp -f -- "$apk" "$dest.partial"
mv -f -- "$dest.partial" "$dest"
echo "BUILDDECK_APK=$dest"
# User-visible convenience copy. The canonical collector backup stays in Out.
# A failed second copy must never turn a real successful build into a failure.
friendly="$HOME/storage/downloads/${project_name}-${record_id:0:8}-debug.apk"
if cp -f -- "$dest" "$friendly"; then
  echo "BUILDDECK_DOWNLOADS_APK=$friendly"
else
  echo "BUILDDECK_DOWNLOADS_COPY=FAILED - use the APK in BuildDeck/Out"
fi
apk_sha="$(sha256sum "$dest" | awk '{print $1}')"
echo "BUILDDECK_APK_SHA256=$apk_sha"
printf 'BUILDDECK_APK_SHA256=%s\n' "$apk_sha" >> "$log"
if [[ "$apk_uri" == content://com.builddeck.mobile.handoff/v1/* ]] && [ -x /system/bin/content ]; then
  if handoff_write "$apk_uri" 90s < "$dest" >/dev/null 2>&1; then
    echo "BUILDDECK_APK_IMPORTED=YES"
  else
    echo "BUILDDECK_APK_IMPORTED=NO - use the manual import from Downloads"
  fi
fi
state DELIVERING
report_stage "APK ready"
echo "APK backup: Downloads/BuildDeck/Out/$project_id"
echo "Easy-to-find copy: Downloads/${project_name}-${record_id:0:8}-debug.apk (if permitted)"
echo "Signing: Gradle debug.keystore in Termux ~/.android/ (keep a private backup)."
