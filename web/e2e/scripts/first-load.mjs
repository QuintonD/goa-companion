// First-load measurement (ADR-043, R3): how long a phone with an empty cache waits for the first
// usable screen, and until the app works offline, on a throttled link.
//
//   node scripts/first-load.mjs [runs]      (from web/e2e, after :web:app:site)
//
// The site is served like GitHub Pages does (gzip for text types, nothing for fonts and images) through
// one shared, throttled link per run: every response waits one round trip, then its bytes share the
// link's bandwidth with every other response in flight. Profiles (Chrome DevTools' presets):
//   Fast 3G   1.44 Mbit/s down, 562.5 ms round trip     4G   9 Mbit/s down, 170 ms round trip
// Browsers: WebKit with the iPhone 15 profile (iOS Safari's stand-in) and Chromium with the Pixel 7.
// The production relay is never contacted (any request to it fails the run).
//
// Reported per browser and profile (median of [runs], default 3):
//   usable   navigation start → the Heroes list shows its first hero (title + a tappable row)
//   images   … → the hero portraits in the first screenful have loaded
//   offline  … → the service worker controls the page, i.e. the shell and catalog are precached
//   KB       bytes on the wire until "usable" and until "offline"
import { createServer } from "node:http";
import { createReadStream, readFileSync, statSync } from "node:fs";
import { extname, join, normalize, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { gzipSync } from "node:zlib";
import { chromium, devices, webkit } from "@playwright/test";

const here = fileURLToPath(new URL(".", import.meta.url));
const root = resolve(process.env.GOA_SITE_DIR ?? join(here, "..", "..", "app", "build", "site"));
const port = Number(process.env.GOA_PORT ?? 4193);
const runs = Number(process.argv[2] ?? 3);
const offlineLimitS = Number(process.env.GOA_OFFLINE_LIMIT_S ?? 300);
const base = "/goa-companion/";

const profiles = {
  "Fast 3G": { bytesPerSecond: 1_440_000 / 8, rttMs: 562.5 },
  "4G": { bytesPerSecond: 9_000_000 / 8, rttMs: 170 },
};

const types = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".webmanifest": "application/manifest+json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".webp": "image/webp",
  ".ttf": "font/ttf",
  ".woff2": "font/woff2",
  ".txt": "text/plain; charset=utf-8",
  ".md": "text/markdown; charset=utf-8",
};
// What GitHub Pages compresses (text); fonts and images go as they are.
const gzipped = new Set([".html", ".js", ".css", ".json", ".webmanifest", ".svg", ".txt", ".md"]);
const gzCache = new Map();

/** The link: one queue of bytes at [bytesPerSecond], shared by every response. */
let link = null;
let traceZero = 0;
function newLink(profile) {
  return { ...profile, freeAt: 0, sent: 0 };
}

function delay(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function sendThrottled(res, body) {
  const l = link;
  await delay(l.rttMs);
  const chunk = 16 * 1024;
  for (let i = 0; i < body.length; i += chunk) {
    const piece = body.subarray(i, i + chunk);
    const now = performance.now();
    l.freeAt = Math.max(now, l.freeAt) + (piece.length / l.bytesPerSecond) * 1000;
    l.sent += piece.length;
    await delay(l.freeAt - now);
    if (!res.write(piece)) await new Promise((r) => res.once("drain", r));
  }
  res.end();
}

const server = createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${port}`);
  if (!url.pathname.startsWith(base)) return res.writeHead(404).end();
  let rel = decodeURIComponent(url.pathname.slice(base.length)) || "index.html";
  if (rel.endsWith("/")) rel += "index.html";
  const file = normalize(join(root, rel));
  if (!file.startsWith(root + sep)) return res.writeHead(403).end();
  let stat;
  try {
    stat = statSync(file);
  } catch {
    return res.writeHead(404).end();
  }
  const ext = extname(file);
  // GitHub Pages: max-age=600 and an ETag on everything (a revalidation is a 304 without a body).
  const etag = `"${stat.size.toString(16)}-${Math.floor(stat.mtimeMs).toString(16)}"`;
  const headers = { "content-type": types[ext] ?? "application/octet-stream", "cache-control": "max-age=600", etag };
  if (req.headers["if-none-match"] === etag) {
    res.writeHead(304, headers);
    await sendThrottled(res, Buffer.alloc(0));
    if (process.env.GOA_TRACE) console.log(`  ${((performance.now() - traceZero) / 1000).toFixed(1)}s 304 ${rel}`);
    return;
  }
  let body;
  if (gzipped.has(ext) && /\bgzip\b/.test(req.headers["accept-encoding"] ?? "")) {
    body = gzCache.get(file) ?? gzipSync(readFileSync(file), { level: 6 });
    gzCache.set(file, body);
    headers["content-encoding"] = "gzip";
  } else {
    body = readFileSync(file);
  }
  headers["content-length"] = body.length;
  res.writeHead(200, headers);
  const start = performance.now();
  await sendThrottled(res, body);
  if (process.env.GOA_TRACE) console.log(`  ${((start - traceZero) / 1000).toFixed(1)}s → ${((performance.now() - traceZero) / 1000).toFixed(1)}s ${Math.round(body.length / 1024)} KB ${rel}`);
});
await new Promise((r) => server.listen(port, r));

function median(values) {
  const s = [...values].sort((a, b) => a - b);
  return s[Math.floor(s.length / 2)];
}

async function measure(browserType, device, profileName) {
  link = newLink(profiles[profileName]);
  const browser = await browserType.launch();
  const context = await browser.newContext({ ...device, serviceWorkers: "allow" });
  await context.route(/oldboi\.taila4d38d\.ts\.net/, (route) => {
    relayHits.push(route.request().url());
    return route.abort();
  });
  const page = await context.newPage();
  const t0 = performance.now();
  traceZero = t0;
  await page.goto(`http://localhost:${port}${base}#/heroes`, { waitUntil: "commit" });
  await page.locator("h1.goa-topbar-title", { hasText: "Heroes" }).waitFor({ timeout: 300_000 });
  await page.getByRole("link", { name: /^Arien/ }).waitFor({ timeout: 300_000 });
  const usable = performance.now() - t0;
  const usableBytes = link.sent;
  if (process.env.GOA_TRACE) console.log(`  usable at ${(usable / 1000).toFixed(1)}s`);
  await page.waitForFunction(
    () => {
      const imgs = [...document.querySelectorAll("main img, .goa-app img")].filter((i) => {
        const r = i.getBoundingClientRect();
        return r.bottom > 0 && r.top < innerHeight && r.width > 0;
      });
      return imgs.length > 0 && imgs.every((i) => i.complete && i.naturalWidth > 0);
    },
    null,
    { timeout: 300_000, polling: 50 },
  );
  const images = performance.now() - t0;
  // The worker claims the page when it activates, i.e. after the shell is precached. (WebKit reports
  // registration.active as "activated" while the first install is still running.) Not within
  // GOA_OFFLINE_LIMIT_S (default 300) counts as never.
  const offline = await page
    .waitForFunction(() => !!navigator.serviceWorker?.controller, null, { timeout: offlineLimitS * 1000, polling: 100 })
    .then(() => performance.now() - t0, () => Infinity);
  const offlineBytes = link.sent;
  if (process.env.GOA_LINGER) {
    console.log(`  activated at ${(offline / 1000).toFixed(1)}s`, await page.evaluate(async () => (await caches.keys()).join(",")));
    await delay(Number(process.env.GOA_LINGER));
    console.log(await page.evaluate(async () => { const out = []; for (const k of await caches.keys()) out.push(k + ":" + (await (await caches.open(k)).keys()).length); return out.join(" "); }));
  }
  await browser.close();
  return { usable, images, offline, usableBytes, offlineBytes };
}

const only = process.env.GOA_ONLY;
const browsers = [
  ["WebKit iPhone 15", webkit, devices["iPhone 15"]],
  ["Chromium Pixel 7", chromium, devices["Pixel 7"]],
];
const rows = [];
const relayHits = [];
try {
  for (const profileName of Object.keys(profiles).filter((p) => !process.env.GOA_PROFILE || p === process.env.GOA_PROFILE)) {
    for (const [name, type, device] of browsers.filter(([n]) => !only || n.startsWith(only))) {
      const results = [];
      for (let i = 0; i < runs; i++) results.push(await measure(type, device, profileName));
      const m = (k) => median(results.map((r) => r[k]));
      rows.push({
        browser: name,
        profile: profileName,
        "usable s": (m("usable") / 1000).toFixed(1),
        "images s": (m("images") / 1000).toFixed(1),
        "offline s": (m("offline") / 1000).toFixed(1),
        "KB to usable": Math.round(m("usableBytes") / 1024),
        "KB to offline": Math.round(m("offlineBytes") / 1024),
      });
      console.log(JSON.stringify(rows.at(-1)));
    }
  }
  console.table(rows);
  if (relayHits.length) throw new Error(`production relay contacted: ${relayHits.join(", ")}`);
} finally {
  server.close();
}
