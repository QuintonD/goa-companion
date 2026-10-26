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
 */
const BUILD = "__GOA_BUILD__";
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

/**
 * Shell files: everything except images, the manifest itself and the worker. The PNG app icons are
 * only read by the browser when the app is installed, so they aren't precached either.
 */
function isShell(path) {
  return !isImage(path) && path !== MANIFEST && path !== "sw.js" && !(path.startsWith("icons/") && path.endsWith(".png"));
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

let hashes = null;

/** path -> content hash of this build's files (parsed once per worker start). */
function buildHashes() {
  if (!hashes) {
    hashes = caches.open(SHELL_CACHE)
      .then((cache) => cache.match(url(MANIFEST)))
      .then((res) => (res ? res.json() : { files: [] }))
      .then((m) => new Map(m.files.map((f) => [f.p, f.h])))
      .catch((e) => {
        hashes = null;
        throw e;
      });
  }
  return hashes;
}

self.addEventListener("install", (event) => {
  event.waitUntil((async () => {
    const res = await fetch(url(MANIFEST), { cache: "no-store" });
    if (!res.ok) throw new Error("asset manifest " + res.status);
    const manifest = await res.clone().json();
    const cache = await caches.open(SHELL_CACHE);
    // A file whose content hash is unchanged since the build this one replaces is copied from that
    // build's cache: an update downloads only what changed, not the whole shell again. A file already
    // in this build's cache (an earlier attempt that a slow link cut short) is kept.
    const previous = await previousShell();
    const download = [];
    for (const file of manifest.files.filter((f) => isShell(f.p))) {
      if (await cache.match(url(file.p))) continue;
      const old = previous.get(file.p + "#" + file.h);
      const hit = old && (await old.match(url(file.p)));
      if (hit) await cache.put(url(file.p), hit);
      else download.push(file);
    }
    // File by file, a few at a time, each kept as it arrives: on a slow phone link one all-or-nothing
    // addAll of every file didn't finish in WebKit and left nothing; now the next attempt resumes.
    // cache: "no-cache" revalidates with the server (ETag), so a stale copy from the HTTP cache can't
    // slip in, while a file the browser already holds can come back as a 304. Each file must match
    // the manifest's content hash, so a deploy landing mid-install can't mix two builds.
    const queue = download.slice();
    const worker = async () => {
      for (let file = queue.shift(); file; file = queue.shift()) {
        const response = await fetch(new Request(url(file.p), { cache: "no-cache" }));
        if (!response.ok) throw new Error(file.p + " " + response.status);
        if ((await contentHash(response.clone())) !== file.h) throw new Error(file.p + " changed during install");
        await cache.put(url(file.p), response);
      }
    };
    await Promise.all(Array.from({ length: PRECACHE_PARALLEL }, worker));
    await cache.put(url(MANIFEST), res);
  })());
});

const PRECACHE_PARALLEL = 4;

/** The manifest's hash of a file: the first 16 hex digits of its SHA-256. */
async function contentHash(response) {
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", await response.arrayBuffer()));
  return Array.from(digest.slice(0, 8), (b) => b.toString(16).padStart(2, "0")).join("");
}

/** The shell caches of earlier builds, by "path#hash" of the files they hold. */
async function previousShell() {
  const found = new Map();
  for (const name of await caches.keys()) {
    if (!name.startsWith("goa-shell-") || name === SHELL_CACHE) continue;
    const cache = await caches.open(name);
    const res = await cache.match(url(MANIFEST));
    if (!res) continue;
    try {
      for (const f of (await res.json()).files) found.set(f.p + "#" + f.h, cache);
    } catch (e) {
      // an unreadable manifest: nothing to reuse from that build
    }
  }
  return found;
}

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
  return hit || fetch(request);
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
