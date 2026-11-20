# BuildDeck — Android Developer Hub

**Batch 2: AI Control Center** — Android/Kotlin project source, experimental local patch review and APK building. The Android app has not yet been compiled or tested on a device.

## First-time source import

1. Download [BuildDeck Batch 2 source ZIP](https://github.com/luischairez12-ui/BuildDeck) from the original ChatGPT conversation (the source package is `BuildDeck_Batch2_AI_Control_Source.zip`).
2. In the repository, use **Add file → Upload files** and select the ZIP **without renaming it**. Shortcut: [Upload on main](https://github.com/luischairez12-ui/BuildDeck/upload/main).
3. GitHub Actions workflow `.github/workflows/bootstrap.yml` detects the ZIP and unpacks it. It attempts to commit the readable project source into `main`, then compiles a debug APK.
4. Check [Actions](https://github.com/luischairez12-ui/BuildDeck/actions). If successful, open the workflow run and download the artifact `builddeck-batch2-debug-apk`.
5. Unzip the artifact and install the APK on Android (with permission to install from the chosen file app).

If the source publication step is blocked by GitHub repository permissions, the workflow still attempts to build the APK from extracted source. A separate source commit can be made by uploading the extracted contents of the original ZIP.

**Safety:** Don't publish personal project source, API tokens, Android signing keys, or keystores in a public repository. BuildDeck permissions are local and require explicit patch review. Remote ChatGPT integration is **not yet connected**.

For implementation details, see `docs/BATCH2_AI_CONTROL.md` after source import.
