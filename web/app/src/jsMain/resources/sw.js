/*
 * Service worker of the Atlantis Companion web app (ADR-043, "Web shell (F2)").
 *
 * - The shell (HTML, JS, CSS, fonts, icons) and all catalog/rules JSON are precached on install
 *   into a cache named after the build, so a deploy is atomic: the old build keeps serving until
 *   the person taps "Reload" on the update prompt (no skipWaiting on its own).
 * - Images (everything under official/) are cached on demand in one long-lived cache, keyed by
 *   path plus content hash (`official/cards/x.webp?v=<hash>`): a changed image is fetched again,
 *   stale entries are dropped on activation, and "Download everything for offline" survives
 *   deploys that don't touch the images. The page reads the same keys (OfflineAssets.kt).
 * - Everything is resolved relative to this file, so the site works under /goa-companion/.
 *
 * BUILD is stamped with the build id (a hash of every file) when the site is assembled, so every
 * deploy changes this file's bytes, which is what makes the browser install the new worker.
 *
 * Integrity (web review R2): MANIFEST_SHA256 is the SHA-256 of this build's asset-manifest.json,
 * stamped after the manifest is written; the manifest names each file's hash. Install takes only
 * files whose bytes match (so a deploy half-way through on the CDN can't leave app.js of one build
 * beside the worker of another: the install fails and the browser tries again later), and a cached
 * shell file is checked again each time it is served: every site on quintond.github.io shares this
 * origin and so its Cache Storage, and a changed copy is dropped and fetched afresh, never run.
 */
const BUILD = "__GOA_BUILD__";
const MANIFEST_SHA256 = "__GOA_MANIFEST_SHA256__";
const HASH_LENGTH = 16; // hex digits of SHA-256 in the manifest (AssembleSite.HASH_LENGTH)
const INSTALL_PARALLEL = 6;
const SHELL_CACHE = "goa-shell-" + BUILD;
const IMAGE_CACHE = "goa-images";
const MANIFEST = "asset-manifest.json";
const SCOPE = new URL("./", self.location.href);

function url(path) {
  return new URL(path, SCOPE).href;
}

function isImage(path) {
  return path.startsWith("official/") && !path.endsWith(".md");
}

/** Shell files: everything except images, the manifest itself and the worker. */
function isShell(path) {
  return !isImage(path) && path !== MANIFEST && path !== "sw.js";
}

/** The path below the scope (`official/cards/x.webp`), or null for another origin or outside the app. */
function relativePath(requestUrl) {
  const u = new URL(requestUrl);
  if (u.origin !== SCOPE.origin || !u.pathname.startsWith(SCOPE.pathname)) return null;
  return decodeURIComponent(u.pathname.slice(SCOPE.pathname.length));
}

/** The image cache's key for [path] at content [hash]. */
function imageKey(path, hash) {
  return url(path) + "?v=" + hash;
}

/** Lowercase hex SHA-256 of [bytes] (an ArrayBuffer). */
async function sha256(bytes) {
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", bytes));
  return Array.from(digest, (b) => b.toString(16).padStart(2, "0")).join("");
}

/** Whether [bytes] are the manifest this worker was built with (always, if it wasn't stamped). */
async function isOurManifest(bytes) {
  return MANIFEST_SHA256.startsWith("__") || (await sha256(bytes)) === MANIFEST_SHA256;
}

/** Whether [bytes] are the file the manifest lists with [hash]. */
async function matches(bytes, hash) {
  return !!hash && (await sha256(bytes)).slice(0, HASH_LENGTH) === hash;
}

/** A response with [bytes] as its body and [from]'s status and headers (a body can be read once). */
function rebuilt(bytes, from) {
  return new Response(bytes, { status: from.status, statusText: from.statusText, headers: from.headers });
}

let hashes = null;

/**
 * path -> content hash of this build's files, from the cached manifest if it is this build's
 * (checked once per worker start); empty when it isn't, so nothing cached is trusted.
 */
function buildHashes() {
  if (!hashes) {
    hashes = caches.open(SHELL_CACHE)
      .then((cache) => cache.match(url(MANIFEST)))
      .then(async (res) => {
        if (!res) return { files: [] };
        const bytes = await res.arrayBuffer();
        if (!(await isOurManifest(bytes))) return { files: [] };
        return JSON.parse(new TextDecoder().decode(bytes));
      })
      .then((m) => new Map(m.files.map((f) => [f.p, f.h])))
      .catch((e) => {
        hashes = null;
        throw e;
      });
  }
  return hashes;
}

/** Fetches [path] past the HTTP cache; its bytes, or an error unless they are the file at [hash]. */
async function fetchVerified(path, hash) {
  // cache: "reload" skips the HTTP cache, so a stale file can't slip into a new build's cache.
  const res = await fetch(new Request(url(path), { cache: "reload" }));
  if (!res.ok) throw new Error(path + " " + res.status);
  const bytes = await res.arrayBuffer();
  if (!(await matches(bytes, hash))) throw new Error(path + " is not this build's (a deploy in progress?)");
  return rebuilt(bytes, res);
}

self.addEventListener("install", (event) => {
  event.waitUntil((async () => {
    const res = await fetch(url(MANIFEST), { cache: "no-store" });
    if (!res.ok) throw new Error("asset manifest " + res.status);
    const bytes = await res.arrayBuffer();
    if (!(await isOurManifest(bytes))) throw new Error("asset manifest is not this build's (a deploy in progress?)");
    const manifest = JSON.parse(new TextDecoder().decode(bytes));
    const cache = await caches.open(SHELL_CACHE);
    const shell = manifest.files.filter((f) => isShell(f.p));
    // All or nothing: one file of another build fails the install, and the browser tries again later.
    const verified = new Array(shell.length);
    let next = 0;
    await Promise.all(Array.from({ length: INSTALL_PARALLEL }, async () => {
      while (next < shell.length) {
        const i = next++;
        verified[i] = await fetchVerified(shell[i].p, shell[i].h);
      }
    }));
    await Promise.all(shell.map((f, i) => cache.put(url(f.p), verified[i])));
    await cache.put(url(MANIFEST), rebuilt(bytes, res));
  })());
});

self.addEventListener("activate", (event) => {
  event.waitUntil((async () => {
    const names = await caches.keys();
    await Promise.all(
      names.filter((n) => n.startsWith("goa-shell-") && n !== SHELL_CACHE).map((n) => caches.delete(n)),
    );
    await pruneImages();
    await self.clients.claim();
  })());
});

/** Drops cached images whose file changed or is gone in this build. */
async function pruneImages() {
  const current = await buildHashes();
  if (current.size === 0) return;
  const images = await caches.open(IMAGE_CACHE);
  for (const request of await images.keys()) {
    const u = new URL(request.url);
    const path = relativePath(request.url);
    if (!path || current.get(path) !== u.searchParams.get("v")) await images.delete(request);
  }
}

self.addEventListener("message", (event) => {
  // Only this app's own pages switch builds (another site on the origin could post this too).
  const from = event.source && event.source.url;
  if (!from || !from.startsWith(SCOPE.href)) return;
  if (event.data && event.data.type === "SKIP_WAITING") self.skipWaiting();
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  if (request.method !== "GET") return;
  const path = relativePath(request.url);
  if (path === null) return; // other origins (the sync relay) go straight to the network
  if (request.mode === "navigate") {
    event.respondWith(shellResponse(url("index.html"), request));
  } else if (isImage(path)) {
    event.respondWith(imageResponse(path, request));
  } else if (path !== "sw.js") {
    event.respondWith(shellResponse(url(path), request));
  }
});

async function shellResponse(key, request) {
  const cache = await caches.open(SHELL_CACHE);
  const hit = await cache.match(key, { ignoreSearch: true });
  if (!hit) return fetch(request);
  const path = relativePath(key);
  const bytes = await hit.arrayBuffer();
  if (await matches(bytes, (await buildHashes()).get(path))) return rebuilt(bytes, hit);
  // Not the file this build installed (changed by something else on the origin): never serve it.
  await cache.delete(key, { ignoreSearch: true });
  return fetch(request);
}

async function imageResponse(path, request) {
  const hash = (await buildHashes()).get(path);
  if (!hash) return fetch(request);
  const images = await caches.open(IMAGE_CACHE);
  const key = imageKey(path, hash);
  const hit = await images.match(key);
  if (hit) return hit;
  const response = await fetch(request);
  if (response.ok && response.status === 200) await images.put(key, response.clone());
  return response;
}
