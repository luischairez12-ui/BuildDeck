# BuildDeck Roadmap

## Batch 1 — Private Android Development Studio (source ready)
- ZIP import, source viewer/editor, source snapshot and restore, release vault.
- Experimental Termux Gradle build handoff.

## Batch 2 — AI Developer Control Center (source ready, uncompiled)
- Per-project permission switches with one-touch grant/revoke.
- Manual AI patch inbox: JSON import, abbreviated diff preview, select files, apply with snapshot.
- Source file create/edit/delete from validated patch bundles with SHA-256 preconditions.
- Persistent build submission history and manual log import.
- Optional local stdio MCP handoff prototype for exported projects.
- GitHub Actions debug APK build on a future public BuildDeck repo.

## Batch 3 — Remote AI Connector + Reliable Local Builds (planned)
- Authenticated HTTPS MCP service or supported connector that ChatGPT can actually access.
- Remote project browsing with scoped permissions, revocable tokens and audit logs.
- Review-before-apply of all AI patches, never expose signing keys to AI.
- Reliable on-device status and log transport / optional trusted desktop build worker.
- Auto-import validated APKs, install prompts, device-specific setup wizard.
- End-to-end tests, Android UI test devices and production signing workflow.

## Product rules
- No pretending that a build has passed without compiler success and a real APK.
- Public release must not include credentials/keystores.
- GitHub optional long-term, but a good bootstrap builder.
- Deletion, dangerous operations and installation remain user-controlled.
