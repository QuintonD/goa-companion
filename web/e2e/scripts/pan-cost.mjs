// What one step of a pan costs the page on the board (ADR-057): script, style and layout time per
// pointer move, from Chromium's own counters, with the CPU slowed to about a phone's.
//
//   node scripts/pan-cost.mjs [runs]      (from web/e2e, after :web:app:site)
//
// Reported per state of the board (median of [runs], default 3): nothing selected, and a hero selected
// (its movement marked). Chromium only: WebKit has no such counters; its pan is the same Kotlin.
import { createServer } from "node:http";
import { readFileSync, statSync } from "node:fs";
import { extname, join, normalize, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { chromium, devices } from "@playwright/test";

const here = fileURLToPath(new URL(".", import.meta.url));
const root = resolve(process.env.GOA_SITE_DIR ?? join(here, "..", "..", "app", "build", "site"));
const port = Number(process.env.GOA_PORT ?? 4194);
const runs = Number(process.argv[2] ?? 3);
const base = "/goa-companion/";
const STEPS = 120;
const CPU_SLOWDOWN = 4;
const types = {
  ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".json": "application/json",
  ".svg": "image/svg+xml", ".webp": "image/webp", ".png": "image/png", ".woff2": "font/woff2",
  ".webmanifest": "application/manifest+json", ".md": "text/markdown",
};

const server = createServer((req, res) => {
  const path = decodeURIComponent(new URL(req.url, "http://x").pathname);
  if (!path.startsWith(base)) return void res.writeHead(404).end();
  const file = normalize(join(root, path.slice(base.length) || "index.html"));
  if (!file.startsWith(root + sep) || !statSync(file, { throwIfNoEntry: false })?.isFile()) return void res.writeHead(404).end();
  res.writeHead(200, { "content-type": types[extname(file)] ?? "application/octet-stream" }).end(readFileSync(file));
});
await new Promise((ok) => server.listen(port, ok));

const median = (xs) => [...xs].sort((a, b) => a - b)[xs.length >> 1];

async function panCost(page, cdp, box) {
  const read = async () => Object.fromEntries((await cdp.send("Performance.getMetrics")).metrics.map((m) => [m.name, m.value]));
  const x = box.x + box.width / 2;
  const top = box.y + box.height * 0.35;
  await page.mouse.move(x, top);
  await page.mouse.down();
  const before = await read();
  for (let i = 1; i <= STEPS; i++) {
    const phase = (i % 40) / 40;
    const y = top + 160 * (phase < 0.5 ? phase * 2 : 2 - phase * 2);
    await page.mouse.move(x, y);
    await page.evaluate(() => new Promise(requestAnimationFrame));
  }
  const after = await read();
  await page.mouse.up();
  const per = (name) => ((after[name] - before[name]) * 1000) / STEPS;
  return { script: per("ScriptDuration"), style: per("RecalcStyleDuration"), layout: per("LayoutDuration") };
}

const browser = await chromium.launch();
const results = { idle: [], selected: [] };
for (let run = 0; run < runs; run++) {
  const context = await browser.newContext({ ...devices["Pixel 7"], serviceWorkers: "block" });
  const page = await context.newPage();
  await page.goto(`http://localhost:${port}${base}#/boards/open?red=brogan,arien&blue=wasp,tigerclaw&map=forgotten-island`);
  await page.locator(".goa-map.painted").waitFor({ timeout: 60_000 });
  await page.locator(".goa-piece").first().waitFor({ timeout: 60_000 });
  await page.waitForTimeout(1500);
  const cdp = await context.newCDPSession(page);
  await cdp.send("Performance.enable");
  await cdp.send("Emulation.setCPUThrottlingRate", { rate: CPU_SLOWDOWN });
  const box = await page.locator(".goa-map").boundingBox();
  await panCost(page, cdp, box); // warm the JIT
  results.idle.push(await panCost(page, cdp, box));
  // Select a hero through its hidden node, as a screen reader does: its movement is marked.
  await page.locator(".goa-map .goa-visually-hidden button", { hasText: "Arien" }).first().evaluate((b) => b.click());
  await page.waitForTimeout(800);
  console.log("marks on the board:", await page.locator(".goa-map-marks polygon").count());
  await panCost(page, cdp, box);
  results.selected.push(await panCost(page, cdp, box));
  await context.close();
}
await browser.close();
server.close();
for (const [state, rows] of Object.entries(results)) {
  const m = (k) => median(rows.map((r) => r[k])).toFixed(2);
  console.log(`${state}: per pan step at ${CPU_SLOWDOWN}x slowdown: script ${m("script")} ms, style ${m("style")} ms, layout ${m("layout")} ms`);
}
