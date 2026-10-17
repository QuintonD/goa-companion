# Guards of Atlantis Companion

A private Android companion for the Guards of Atlantis II board game: hero catalog, phase
timer, friends and Elo tracking, and a pick/ban drafting system.

Personal, non-commercial use only. Not affiliated with Wolff Designa. Official game content
and art belong to them and are included only for this private build.

- Agents: start at [AGENTS.md](AGENTS.md).
- Humans: start at [docs/PRODUCT.md](docs/PRODUCT.md) and [docs/ROADMAP.md](docs/ROADMAP.md).

## Build and install (Windows, PowerShell)

C: on the build PC is nearly full, so Gradle's caches live on D: (ADR-010):

```powershell
$env:GRADLE_USER_HOME = "D:\.gradle-home"; $env:TMP = "D:\.tmp-build"; $env:TEMP = "D:\.tmp-build"
.\gradlew.bat :app:assembleRelease
# APK: app\build\outputs\apk\release\app-release.apk
adb install -r app\build\outputs\apk\release\app-release.apk
```

To sideload on the phone: enable Developer options → USB debugging, connect it, accept the
prompt, then run the `adb install` line above (or copy the APK to the phone and open it).

The debug build (`.\gradlew.bat :app:installDebug`) installs side by side as
`dev.quinton.goa.debug` with its own separate data.

## Release signing key — back it up

Release builds are signed with a private key kept **outside** the repo:

- Keystore: `D:\.goa-signing\goa-release.jks`
- Passwords: `D:\.goa-signing\keystore.properties` (a copy sits in the repo root as
  `keystore.properties`, which git ignores)

Keep a copy of both files somewhere safe. Android only installs an update over the existing
app if it is signed with the same key. Without it you would have to uninstall first, which
deletes the match history on the phone (export a backup from Players → Backup first).

## Your data

Everything is stored on the phone; the app has no internet permission. Use
Players → Backup to export a JSON file (and to move data to a new phone). Android Auto Backup
also covers the database and settings.

## Refreshing hero data

Hero data and images come from pinned community sources via `tools/catalog-import` (see the
`import-catalog` skill). Re-run with `.\gradlew.bat :tools:catalog-import:run`
(`--args="--offline"` reuses the local download cache).
