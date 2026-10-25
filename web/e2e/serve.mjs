// Serves the built site (web/app/build/site, or GOA_SITE_DIR) under /goa-companion/ like GitHub
// Pages does, for Playwright and for trying the production build locally:
//   node serve.mjs            → http://localhost:4173/goa-companion/
// Test hook: POST /__bump makes sw.js differ (a comment), as a new deploy would, so the update
// prompt can be tested without building twice.
import { createServer } from "node:http";
import { createReadStream, statSync } from "node:fs";
import { extname, join, normalize, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { readFile } from "node:fs/promises";

const here = fileURLToPath(new URL(".", import.meta.url));
const root = resolve(process.env.GOA_SITE_DIR ?? join(here, "..", "app", "build", "site"));
const port = Number(process.env.GOA_PORT ?? 4173);
const base = "/goa-companion/";
let bump = 0;

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
  ".txt": "text/plain; charset=utf-8",
  ".md": "text/markdown; charset=utf-8",
};

createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${port}`);
  if (req.method === "POST" && url.pathname === "/__bump") {
    bump++;
    res.writeHead(204).end();
    return;
  }
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
  // GitHub Pages: max-age=600 on everything; the service worker must cope with it.
  const headers = { "content-type": types[extname(file)] ?? "application/octet-stream", "cache-control": "max-age=600" };
  if (rel === "sw.js" && bump > 0) {
    const body = (await readFile(file, "utf8")) + `\n// test deploy ${bump}\n`;
    res.writeHead(200, headers).end(body);
    return;
  }
  res.writeHead(200, { ...headers, "content-length": stat.size });
  createReadStream(file).pipe(res);
}).listen(port, () => console.log(`Serving ${root} at http://localhost:${port}${base}`));
