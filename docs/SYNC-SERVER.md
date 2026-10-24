# Sync server — protocol and roles (ADR-036)

The group's sync server is a **dumb, encrypted mailbox** on the owner's machine (`oldboi`). Phones
do all the work: they read, merge, validate and seal the group's data; the server stores one sealed
blob per group and hands it back. It can't read the data (it never has the group key), never merges,
never validates content and runs none of the app's rules. Nearby sync (ADR-025) and sync files stay
for tables without internet.

## Address

- Production: `https://oldboi.taila4d38d.ts.net:8443` (Tailscale Funnel on oldboi → `127.0.0.1:8787`).
  Until Funnel is enabled on the tailnet, the same URL works only for devices on the tailnet
  (`tailscale serve`).
- The app's default comes from the Gradle property `goa.syncServer` (BuildConfig
  `SYNC_SERVER_URL`); empty = no default. A group can override it (Group screen, or an invite's
  `s=` field). Release builds speak HTTPS only; debug builds may use `http://10.0.2.2` / `localhost`.

## Protocol v1

All paths under the server origin. `{groupId}` is the group's UUID (`GroupCredentials.groupId`);
anything not matching `[A-Za-z0-9-]{8,64}` gets `400`.

**Token.** `Authorization: Bearer <token>`, where `token` = base64url, no padding, of
`SyncCrypto.hkdf(ikm = group key, salt = groupId UTF-8, info = "goa-relay/token/v1", length = 32)`.
The server stores only `SHA-256(token)` (lowercase hex) per group, set by the group's first upload
(trust on first use: the group id is a random UUID shared only inside the invite, beside the key).
Comparison is constant time. Tokens and bodies are never logged.

| Request | Responses |
|---|---|
| `GET /v1/health` | `200` `{"ok":true,"protocol":1}` (no auth) |
| `GET /v1/groups/{groupId}/document` | `200` body = sealed bytes, `ETag: "<rev>"`, `Content-Type: application/vnd.dev.quinton.goa.sync` · `404` no document for this group (also when the group was never registered, whatever the token) · `401` registered group, wrong or missing token |
| `PUT /v1/groups/{groupId}/document` with `If-None-Match: *` (first upload) or `If-Match: "<rev>"` | `201` (first) / `200`, `ETag: "<newRev>"` · `412` the stored rev isn't the one named (or a document exists for `If-None-Match: *`) · `428` neither header · `401` wrong token · `413` body over 8 MiB · `400` empty body |

Any route: `429` when rate limited (`Retry-After` seconds), `503` while the store is unavailable.
`rev` is a positive integer, `1` for the first document, +1 per accepted PUT. Bodies are opaque to
the server: today they are `SyncCodec.encodeFile(doc, group, createdAt)` (a JSON envelope sealed with
a key derived from the group key), and the server must not depend on that.

## Phone algorithm (one sync)

1. `local = LocalSyncData.read()`.
2. `GET` the document. `404` → `remote = null`. `200` → `remote = SyncCodec.decodeFile(bytes, group)`;
   another `SyncDocument.VERSION` → stop with "update the app" (which side is older is in the error).
3. `merged = remote?.let { SyncMerge.merge(local, it) } ?: SyncMerge.normalize(local)`; apply it
   locally through the same path as an imported sync file (`LocalSyncData.write(local, merged)`).
4. If `remote == null` or `merged` differs from `remote` (content, not envelope): `PUT` the sealed
   `merged` with `If-Match` (or `If-None-Match: *`). `412` → back to 1, at most 3 times.
5. Record the outcome (time, rev, error) in the `group_sync` DataStore for the Group screen.

Triggers: the activity coming on screen, 15 s after the last write to synced data (debounced),
hourly with a network constraint (WorkManager), and "Sync now". Only transient failures (no
connection, timeout, 5xx, 429) retry with backoff; 401/412-exhausted/version/413 wait for a person.

## Server

`server/relay/` — Kotlin/JVM, Ktor (CIO), its own Gradle build (not part of the Android build, so it
builds on a machine without the Android SDK). Storage under `GOA_RELAY_DATA` (default
`/var/lib/goa-relay`): `groups/<groupId>/meta.json` (`tokenHash`, `rev`, `updatedAt`, `size`),
`document.bin`, and the last 20 revisions in `history/<rev>.bin` for recovery. Writes are atomic
(temp file, fsync, rename) under a per-group lock. Listens on `127.0.0.1:8787` only; deployed with
Docker (`deploy/relay/`), published by Tailscale Funnel on 8443.
