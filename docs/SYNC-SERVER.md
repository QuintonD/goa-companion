# Sync server — protocol and roles (ADR-036)

The group's sync server is a **dumb, encrypted mailbox** on the owner's machine (`oldboi`). Phones
do all the work: they read, merge, validate and seal the group's data; the server stores one sealed
blob per group and hands it back. It can't read the data (it never has the group key), never merges,
never validates content and runs none of the app's rules. Nearby sync (ADR-025) and sync files stay
for tables without internet.

## Address

- Production: `https://oldboi.taila4d38d.ts.net:8443` (Tailscale Funnel on oldboi → `127.0.0.1:8787`,
  public since 2026-10-25; runbook in [deploy/relay/README.md](../deploy/relay/README.md)).
- The app's default comes from the Gradle property `goa.syncServer` (BuildConfig
  `SYNC_SERVER_URL`); empty = no default. A group can override it (Group screen, or an invite's
  `s=` field). Release builds speak HTTPS only; debug builds may use `http://10.0.2.2` / `localhost`.

## Protocol v1

All paths under the server origin. `{groupId}` is the group's UUID (`GroupCredentials.groupId`);
anything not matching `[A-Za-z0-9-]{8,64}` gets `400`.

**Token.** `Authorization: Bearer <token>`, where `token` = base64url, no padding, of
`SyncCrypto.hkdf(ikm = group key, salt = groupId UTF-8, info = "goa-relay/token/v1", length = 32)`.
The server stores only `SHA-256(token)` (lowercase hex, over the token string's ASCII bytes) per
group, set by the group's first upload (trust on first use: the group id is a random UUID shared only
inside the invite, beside the key). Comparison is constant time. Tokens and bodies are never logged.
The server accepts tokens matching `[A-Za-z0-9_-]{16,256}`; anything else counts as missing.

Test vector (asserted by `server/relay`'s `RelayTokenTest`; the Android side should assert the same):

| | |
|---|---|
| group key (hex) | `000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f` |
| groupId | `8c4f2b1e-3a5d-4c6e-9f70-1a2b3c4d5e6f` |
| token | `dFROVzWTpa17AzhaeXje8rW5l1okK5Q9nuEeVerIQTI` |
| stored `tokenHash` | `cfb0311773dcc2650c1160044e1f82ed615b49d23428d9b9c935d82d01399e25` |

| Request | Responses |
|---|---|
| `GET /v1/health` | `200` `{"ok":true,"protocol":1}` (no auth) |
| `GET /v1/groups/{groupId}/document` | `200` body = sealed bytes, `ETag: "<rev>"`, `Content-Type: application/vnd.dev.quinton.goa.sync` · `404` no document for this group (also when the group was never registered, whatever the token) · `401` registered group, wrong or missing token · `304` (optional) when the request carries `If-None-Match: "<rev>"` naming the current rev |
| `PUT /v1/groups/{groupId}/document` with `If-None-Match: *` (first upload) or `If-Match: "<rev>"` | `201` (first) / `200`, `ETag: "<newRev>"` · `412` the stored rev isn't the one named (or a document exists for `If-None-Match: *`) · `428` neither header · `401` wrong or missing token · `413` body over 8 MiB · `400` empty body · `503` + `Retry-After: 3600` a first upload while the relay is full (`GOA_RELAY_MAX_GROUPS`) |

**Browsers** (the web app, ADR-043) call the relay from `https://quintond.github.io`
(`GOA_RELAY_CORS_ORIGINS`): answers to an allowed `Origin` carry `Access-Control-Allow-Origin` and
expose `ETag` and `Retry-After`; `OPTIONS` preflights for `GET`/`PUT` with `Authorization`,
`If-Match`, `If-None-Match`, `Content-Type` get `204` (cached 10 min), others `403`. Headers only:
the protocol is the same for phones and browsers.

Any route: `429` when rate limited (`Retry-After` seconds), `503` while the store is unavailable
(`Retry-After: 30`). Error bodies are small JSON (`{"error":"precondition_failed"}`); clients go by
the status code. `If-Match: *` (any existing document) is accepted too. Checks run in this order:
group id (400), token present (401), precondition header (428), declared size (413), then under the
group's lock token and revision (401/412), then the body as received (413/400).
`rev` is a positive integer, `1` for the first document, +1 per accepted PUT. Bodies are opaque to
the server: today they are `SyncCodec.encodeFile(doc, group, createdAt, withGroupName = false)` (a JSON
envelope sealed with a key derived from the group key; in clear only the group id, document version,
time, salt and an empty group name), and the server must not depend on that.

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
`document.bin`, and the last 20 revisions (the current one included) in `history/<rev>.bin` for
recovery. Writes are atomic (temp file, fsync, rename) under a per-group lock, `meta.json` last.
Listens on `127.0.0.1:8787` by default; deployed with Docker (`deploy/relay/`; inside the container it
binds `0.0.0.0` and is published on host loopback only), published by Tailscale Funnel on 8443.
CORS preflights are answered before the rate limiter. Rate limits: token buckets of 60 requests/min per client (the last `X-Forwarded-For` hop, else the
peer) and 600/min overall. At most 200 groups (`GOA_RELAY_MAX_GROUPS`) may register. Build, run and
operations: [server/relay/README.md](../server/relay/README.md).
