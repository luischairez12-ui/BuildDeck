# BuildDeck v0.3.1 — Fix for “Missing Gradle settings file” (exit 6)

## Why this happened

The screenshot of the failed Build Requests log showed:

- `BUILDDECK_STAGE=Extracting source`
- `Missing Gradle settings file`
- `BUILDDECK_RESULT=FAILED (exit 6)`
- Source file cards starting with `res/color/vector_tint_theme_color.xml` and `res/drawable-v21/notification_action_background.xml`.

Android APK files are ZIP archives that contain compiled Android resources such as `res/...`, `classes.dex` and `resources.arsc`. Earlier BuildDeck versions accepted any ZIP as a “source project,” even without `settings.gradle` or `settings.gradle.kts`. When the embedded Termux builder extracted the ZIP, it discovered that no Gradle source project was present and stopped before compilation.

The real BuildDeck GitHub repository *does* contain `settings.gradle.kts` at its root and `app/build.gradle.kts`. An APK is not equivalent to the Gradle source repository from which it was built.

## Immediate recovery in the currently installed app

1. Exit the build log and return to **Projects**.
2. In **GitHub Workspace**, enter `luischairez12-ui/BuildDeck` and leave the branch blank (default branch).
3. Tap **Import GitHub source**, then open the newly imported project.
4. Check that the source files include `settings.gradle.kts` and `app/build.gradle.kts`.
5. Tap **BUILD APK ON THIS PHONE** on that project.
6. The previously failed project can remain for inspection. Its prior build log will remain intact.

Don't uninstall BuildDeck or delete existing projects. The original `Out` folder with already compiled APKs is independent of this source-import error.

## Changes in v0.3.1

- Source import explicitly checks that the ZIP contains `settings.gradle` or `settings.gradle.kts` near the project root; if not, import is rejected with an actionable message.
- ZIPs with `classes.dex` or `resources.arsc` get a specific “compiled APK, not source code” message.
- Existing projects with no Gradle settings are marked **SOURCE IMPORT NEEDS REPAIR** in their project workspace, with their build button disabled and a shortcut to **GitHub source import**.
- Local build handoff checks source readiness before creating another failed request.
- Termux fallback logs now identify APK-looking archives and print a short list of archive entries when the Gradle project cannot be found.

## Build and install safely

Build in the *same* Termux that signed the previous BuildDeck APK:

```bash
cd ~/builddeck-test &&
git pull --ff-only &&
"$HOME/opt/gradle-8.9/bin/gradle" :app:assembleDebug --no-daemon --max-workers=2 "-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)" &&
cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/BuildDeck-v0.3.1-local.apk
```

When Gradle says `BUILD SUCCESSFUL`, tap `Downloads/BuildDeck-v0.3.1-local.apk` to update. Do not uninstall the current Termux-signed app; GitHub CI artifacts use a different signing key.

This is a source-validation fix. It does not add full APK decompilation or convert an APK into editable Gradle source. Real-device verification is still necessary after GitHub Actions compilation passes.
