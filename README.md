# Guards of Atlantis Companion

An Android companion for the Guards of Atlantis II board game: hero catalog, phase
timer, friends and Elo tracking, a pick/ban drafting system, a rules reference and phone-to-phone
group sync.

Non-commercial. Not affiliated with Wolff Designa. Official game content and art belong to
them; a public release with them bundled needs their permission (ADR-034).

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

## Sharing with the play group

Share the signed release APK with your group through a link (Google Drive works).
`D:\GoA-share\` holds the APK and a `HOW-TO-INSTALL.txt` for friends. For an update,
bump `versionCode`/`versionName` in `app/build.gradle.kts`, rebuild, and replace the file in the
same Drive folder (Drive "Manage versions" keeps the link). Friends install over the old version
and keep their data.

## Group sync: every friend's phone, the same data (ADR-025)

Phones sync face to face at the table, over Bluetooth and Wi-Fi Direct (Google Nearby
Connections), and through the group's sync server whenever they have internet (see "The sync
server" below). No accounts.

**Start the group (once, on one phone):** Settings → Group sync → Start a group → name it.

**A friend joins:**
1. Install the APK from the Drive link (above) and open the app.
2. On a phone already in the group: Group sync → **Invite a friend**. A QR code appears.
3. On the friend's phone: Settings → Group sync (or the sync icon on the Players tab) →
   **Scan invite QR code**. The scanner is Google Play services' (no camera permission for the
   app); the first scan needs Play services to fetch it once, so the phone needs a connection
   that one time. No scanner? "Share as text" on the invite, then paste the whole message into
   **Or paste an invite**.
4. The first sync starts right away: allow **Nearby devices** (Android 12+; "Location" on older
   phones) when asked. On the inviting phone, tap Sync now → **Host**; the friend's phone joins
   by itself.
5. **Which player are you?** Pick yourself from the list, or type your name to add yourself.

**At game night:** one phone taps Sync now → Host, everyone else Sync now → Join. The host shows
each phone; tap **Sync** when they're all in. Every phone ends with the same matches, players,
deletions and rating constants. The later edit of a match or player wins; a deleted match is
deleted everywhere.

**Without Nearby:** Group sync → **Share sync file** sends this phone's data, locked with the
group key, through Quick Share, a message or Drive; the other phone opens it (or uses
**Import sync file**) and merges it the same way. A file from another group can't be opened.

**Same friend twice** (added on two phones before they ever synced): open either player →
**Merge into…** → pick the other. Sync again so the other phones follow.

**Sync server (ADR-036):** the group can also sync over the internet through a small relay on the
owner's home server (`oldboi`, `https://oldboi.taila4d38d.ts.net:8443`). It only stores the
group's data sealed with the group key and can't read it; the phones still do all the merging.
Server: [server/relay/README.md](server/relay/README.md) · runbook:
[deploy/relay/README.md](deploy/relay/README.md) · protocol: [docs/SYNC-SERVER.md](docs/SYNC-SERVER.md).

The invite QR code is the group's key: show it only at the table. Release and debug builds
don't see each other (different app ids). Leaving a group keeps all data on the phone.

### The sync server (ADR-036)

A small relay on the owner's machine (`server/relay/`, deployed from `deploy/relay/`, protocol in
[docs/SYNC-SERVER.md](docs/SYNC-SERVER.md)) keeps one copy of each group's data, locked with the
group key it never sees. Phones do the work: they fetch the copy, merge it like a sync file and
upload the result when it changed. With a server, a friend who scans the invite gets the group's
data at once, and phones stay in step between game nights without meeting.

- **Built-in address:** the Gradle property `goa.syncServer` (default in `gradle.properties`:
  `https://oldboi.taila4d38d.ts.net:8443`). Override it per machine in
  `~/.gradle/gradle.properties` or with `-Pgoa.syncServer=https://host:port`; set it empty for a
  build without a server. It must be an `https://` origin (debug builds also accept
  `http://10.0.2.2:<port>` for a server on the PC running the emulator).
- **Per group:** Group sync → Sync server → **Advanced** sets another address for this group
  (https only in release builds) or goes back to the built-in one. New invites from that phone
  carry the address, so friends who scan them use the same server.
- **Off switch:** "Sync through the server" on the same card; off = face to face and sync files only.
- **Status:** the card shows when this phone last synced and, when something is wrong, what to do;
  a problem that needs a person also puts a dot on the Players tab's sync icon.

## Release signing key — back it up

Release builds are signed with a private key kept **outside** the repo:

- Keystore: `D:\.goa-signing\goa-release.jks`
- Passwords: `D:\.goa-signing\keystore.properties` (a copy sits in the repo root as
  `keystore.properties`, which git ignores)

Keep a copy of both files somewhere safe. Android only installs an update over the existing
app if it is signed with the same key. Without it you would have to uninstall first, which
deletes the match history on the phone (export a backup from Players → Backup first).

## Your data

Everything is stored on the phone. The only place the app connects to is the group's sync server
(above), which holds a copy locked with the group key and can't read it; switch it off on the
Group screen and the app stays offline. Group sync (above) keeps the group's phones in step. Use Players → Backup to export a JSON file (and to move data
to a new phone; a sync with the group afterwards brings it up to date). Android Auto Backup
also covers the database and settings.

## Refreshing hero data

Hero data and images come from pinned community sources via `tools/catalog-import` (see the
`import-catalog` skill). Re-run with `.\gradlew.bat :tools:catalog-import:run`
(`--args="--offline"` reuses the local download cache).
