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
    const shell = manifest.files.map((f) => f.p).filter(isShell);
    // cache: "reload" skips the HTTP cache, so a stale file can't slip into a new build's cache.
    await cache.addAll(shell.map((p) => new Request(url(p), { cache: "reload" })));
    await cache.put(url(MANIFEST), res);
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
