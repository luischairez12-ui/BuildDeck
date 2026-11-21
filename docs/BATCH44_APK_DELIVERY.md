# BuildDeck v0.4.4 — APK Delivery & Downloads

This update keeps the Nova Studio white/cobalt UI and existing projects, APK Vault, build history, GitHub links, and debug signing. It changes how APKs are collected and made visible to the user.

## What changed

- **Verified handoff recovery:** After an approved BuildDeck → Termux build, the result receiver checks the authenticated on-device delivered log **as well as** Termux's callback stdout. Some Termux versions may return an incomplete stdout field. A READY result still requires a real successful exit code, the success marker, and a transferred APK with a SHA-256 digest matching the log.
- **Two user-accessible backups:** The bundled Termux builder always saves its canonical APK to `Downloads/BuildDeck/Out/<project UUID>/<build UUID>_app-debug.apk`; it also **attempts** to create `Downloads/<project-name>-<build ID>-debug.apk` for easy discovery. Failure of the convenience copy cannot falsely fail a successfully compiled APK.
- **One-tap Downloads export:** Every saved Vault APK has **Save APK to Downloads**. This uses Android's MediaStore Downloads collection and gives a readable `<project>-v<version>-<id>.apk` name. It checks the APK file before copying, leaves the Vault original intact, and shows the saved filename.
- **Completed build navigation:** The Build console's completed build card includes **Open APK Vault**. A build needing manual import still offers **Import APK**.
- **Diagnostics:** The Builds screen explains how to import APKs that are already in the main Downloads folder.

## Important distinction

A manual `gradle :app:assembleDebug` command in a Termux terminal is **not** a BuildDeck-controlled build. It does not use the authenticated callback or automatically register a Vault entry. For those builds, open **Vault → + Import APK**, choose the APK in Downloads, and select it.

A BuildDeck-controlled build is started from **Projects → [source project] → Build APK** or **Builds → Start build**. Those pass a tracked build ID and the authenticated handoff token to Termux.

## How to update an already installed Termux-signed BuildDeck

Use the *same Termux installation and debug signing key* as the installed app:

```bash
cd ~/builddeck-test &&
git pull --ff-only &&
"$HOME/opt/gradle-8.9/bin/gradle" :app:assembleDebug --no-daemon --max-workers=2 "-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)" &&
cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/BuildDeck-v0.4.4-local.apk
```

Install **Downloads/BuildDeck-v0.4.4-local.apk** as an update. Do not uninstall BuildDeck or clear its data. v0.4.4 has versionCode 14. GitHub CI APKs can use a different debug key, so they are meant for a **fresh test install**, not for replacing a Termux-signed app without uninstalling.

## Phone test

1. Check **Settings** says v0.4.4.
2. Open a valid source project and start its build from BuildDeck. Wait for the official Termux result.
3. Confirm there is an APK under **Downloads/BuildDeck/Out** and an easy-to-find APK under the main **Downloads** folder (if the convenience copy succeeds).
4. Open **Builds**: a true transferred and verified APK says **READY**; otherwise it should say **NEEDS IMPORT** or **FAILED**, with a readable explanation.
5. Open **Vault**, tap **Save APK to Downloads**, and confirm it appears in the Files app's Downloads folder.
6. Install from Vault using the Android confirmation; don't install an older or differently-signed BuildDeck package over your working version.

## Known limits

- Automatic APK import is limited to BuildDeck-controlled builds. APKs compiled manually elsewhere must be imported explicitly.
- Android may block the authenticated ContentProvider upload on some phones. In that case use the APK in Downloads, **Vault → + Import APK**, or the optional **Scan backup Out folder**.
- Android's system folder picker can sometimes fail to enumerate Termux's shared files; the main Downloads convenience copy and MediaStore export are deliberate fallbacks.
- This change has not yet been compiled or tested on the user's OnePlus. CI and device validation are still required.
