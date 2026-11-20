# BuildDeck v0.3.3 — APK collection with Android file manager fallback

## Diagnosis

The OnePlus successfully built BuildDeck at 18:03 and the Termux `find` command confirmed the resulting APK at `Downloads/BuildDeck/Out/<project-UUID>/<build-UUID>_app-debug.apk`. Android's Storage Access Framework selected the `Out` folder but returned **no child directories**, even after reconnecting. Reconnecting again is not a useful remedy. The previous v0.3.2 scan labeled this situation "folder is empty" although the file exists at the filesystem level.

## Changes

1. **Exact-document fallback**: After a confirmed successful Termux callback, BuildDeck attempts a scoped `DocumentsContract.buildDocumentUriUsingTree` lookup for that specific UUID-named APK within the already user-granted Out tree. This does not use storage-wide permissions and does not assume the folder can enumerate children. It also reads the exact APK filename from the recorded build log when available. Whether the external storage provider supports direct-document lookup must be tested on the phone.
2. **Accurate scanner text**: Instead of repeatedly suggesting "Reconnect Out", the scan now says when Android's document provider can't see Termux's files, and recommends the manual alternative.
3. **Easy manual action**: **Choose a finished APK manually** appears next to **Scan Out folder and collect APKs** in the project.
4. **File Manager → Share**: When Android's system document picker cannot show the APK but a regular file manager can, open the APK's **Share** menu and select **BuildDeck**. BuildDeck prompts for which existing project should receive it, then copies the shared APK into the APK Vault. This relies on the file manager's temporary content URI grant; it is a user-initiated action and does not install any APK automatically.
5. **No impact on signing**: Updates are built in the same Termux installation and continue to use the original local debug key. A GitHub CI APK cannot update an existing differently-signed local install.

## Update safely in Termux

```bash
cd ~/builddeck-test &&
git pull --ff-only &&
grep -E 'versionCode|versionName' app/build.gradle.kts &&
grep -q 'versionName = "0.3.3"' app/build.gradle.kts &&
"$HOME/opt/gradle-8.9/bin/gradle" :app:assembleDebug --no-daemon --max-workers=2 "-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)" &&
cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/BuildDeck-v0.3.3-local.apk
```

Update the currently installed BuildDeck from Downloads; do not uninstall or clear app data.

## Test plan

1. After installing 0.3.3, open the previously successful linked `BuildDeck` project and tap **Scan Out folder and collect APKs**. If it imports the prior build, Build Requests changes from SUCCESS NEEDS IMPORT to READY and the APK appears under Releases.
2. If Android still won't read the selected tree, **do not rename directories or reconfigure repeatedly**. In the regular file manager—which can display the APK—navigate to the actual current `Out` folder, find the existing `<build-UUID>_app-debug.apk`, choose **Share → BuildDeck**, and then choose the correct project to put the APK in its Vault.
3. If the regular file manager cannot expose Share to BuildDeck, copy the APK to main Downloads with Termux and open BuildDeck's manual APK picker.
4. Once an APK is in Vault, verify package name, version, and signing message. Do not install an older APK over a newer version.

No further Gradle build is required to test collection of the already-compiled APK after the **BuildDeck app update** itself. Do not claim automatic collection has been proven until the user verifies it on-device.
