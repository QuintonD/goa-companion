// Serves the built site (web/app/build/site, or GOA_SITE_DIR) under /goa-companion/ like GitHub
// Pages does, for Playwright and for trying the production build locally:
//   node serve.mjs            → http://localhost:4173/goa-companion/
// Test hook: a `goa-deploy=<n>` cookie makes sw.js differ (its build id), as a new deploy would, so
// the update prompt can be tested without building twice and without touching other tests.
// Test hook: a `goa-stale=<path>` cookie serves that file with other bytes (a deploy half-way
// through on the CDN), so the service worker's all-or-nothing install can be tested.
// Test relays: index.html's Content-Security-Policy lets the page reach only the site and the group
// relay; served from here it may also reach a relay on this machine (the suite's fake relay and
// server/relay), which the app allows only on a localhost page anyway. The manifest and sw.js are
// re-stamped to match, as the site task would (the service worker installs only files whose bytes
// its manifest names). Nothing else is changed.
import { createServer } from "node:http";
import { createReadStream, statSync } from "node:fs";
import { extname, join, normalize, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { readFile } from "node:fs/promises";
import { createHash } from "node:crypto";

const here = fileURLToPath(new URL(".", import.meta.url));
const root = resolve(process.env.GOA_SITE_DIR ?? join(here, "..", "app", "build", "site"));
const port = Number(process.env.GOA_PORT ?? 4173);
const base = "/goa-companion/";

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

const sha256 = (text) => createHash("sha256").update(text).digest("hex");

/** index.html allowing local relays, and the asset-manifest.json and sw.js that vouch for it. */
async function localRelaySite() {
  const index = (await readFile(join(root, "index.html"), "utf8")).replace(
    /(Content-Security-Policy" content="[^"]*connect-src 'self')/,
    "$1 http://127.0.0.1:* http://localhost:*",
  );
  const built = await readFile(join(root, "asset-manifest.json"), "utf8");
  const manifest = built.replace(
    /(\{"p": "index\.html", "s": )\d+(, "h": ")[0-9a-f]+/,
    (_, head, mid) => `${head}${Buffer.byteLength(index)}${mid}${sha256(index).slice(0, 16)}`,
  );
  const sw = (await readFile(join(root, "sw.js"), "utf8")).replace(sha256(built), sha256(manifest));
  return { "index.html": index, "asset-manifest.json": manifest, "sw.js": sw };
}

createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${port}`);
  if (url.pathname === base.slice(0, -1)) {
    res.writeHead(301, { location: base }).end();
    return;
  }
  if (!url.pathname.startsWith(base)) {
    res.writeHead(404, { "content-type": "text/plain" }).end("Not found");
    return;
  }
  let rel = decodeURIComponent(url.pathname.slice(base.length)) || "index.html";
  if (rel.endsWith("/")) rel += "index.html";
  const file = normalize(join(root, rel));
  if (!file.startsWith(root + sep)) {
    res.writeHead(403).end();
    return;
  }
  let stat;
  try {
    stat = statSync(file);
  } catch {
    res.writeHead(404, { "content-type": "text/plain" }).end("Not found");
    return;
  }
  // GitHub Pages: max-age=600 and an ETag on everything; the service worker must cope with both.
  const etag = `"${stat.size.toString(16)}-${Math.floor(stat.mtimeMs).toString(16)}"`;
  const headers = { "content-type": types[extname(file)] ?? "application/octet-stream", "cache-control": "max-age=600", etag };
  const cookie = req.headers.cookie ?? "";
  const deploy = /(?:^|;\s*)goa-deploy=(\w+)/.exec(cookie)?.[1];
  const stale = /(?:^|;\s*)goa-stale=([\w./-]+)/.exec(cookie)?.[1];
  if (rel === "index.html" || rel === "asset-manifest.json" || rel === "sw.js") {
    // Re-stamped bodies aren't the bytes on disk, so they carry no ETag.
    delete headers.etag;
    let body = (await localRelaySite())[rel];
    if (rel === "sw.js" && deploy) {
      // A new build id too, as a real deploy has: the new worker installs into a cache of its own.
      body = body.replace(/const BUILD = "([^"]*)";/, `const BUILD = "$1-d${deploy}";`) + `\n// test deploy ${deploy}\n`;
    }
    res.writeHead(200, headers).end(body);
    return;
  }
  if (rel === stale) {
    delete headers.etag;
    res.writeHead(200, headers).end((await readFile(file, "utf8")) + "\n/* another build */\n");
    return;
  }
  if (req.headers["if-none-match"] === etag) {
    res.writeHead(304, headers).end();
    return;
  }
  res.writeHead(200, { ...headers, "content-length": stat.size });
  createReadStream(file).pipe(res);
}).listen(port, () => console.log(`Serving ${root} at http://localhost:${port}${base}`));
