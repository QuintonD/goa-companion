// Renders the web app's PNG icons from the app icon's SVG layers (design/brand, the Android
// adaptive icon on a 108-unit grid) with Chromium. Run after changing design/brand:
//   cd web/e2e && npm run icons
// "any" icons and the apple-touch-icon show what an Android launcher shows (the centre 72 units);
// maskable icons keep the full 108-unit square (the foreground sits inside the r33 safe zone).
import { chromium } from "@playwright/test";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { join } from "node:path";

const repo = fileURLToPath(new URL("../../../", import.meta.url));
const out = join(repo, "web/app/src/jsMain/resources/icons");
const layer = async (name) => (await readFile(join(repo, "design/brand", name), "utf8"))
  .replace(/<\?xml[^>]*>/, "")
  .replace(/<title>[\s\S]*?<\/title>/, "");

const background = await layer("ic_launcher_background.svg");
const foreground = (await layer("ic_launcher_foreground.svg")).replaceAll('id="bronze"', 'id="bronze-fg"').replaceAll("url(#bronze)", "url(#bronze-fg)");

function svg(size, viewBox) {
  const inner = (s) => s.replace(/<svg[^>]*>/, "").replace("</svg>", "");
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${viewBox}" width="${size}" height="${size}">${inner(background)}${inner(foreground)}</svg>`;
}

const icons = [
  ["apple-touch-icon.png", 180, "18 18 72 72"],
  ["app-192.png", 192, "18 18 72 72"],
  ["app-512.png", 512, "18 18 72 72"],
  ["app-maskable-192.png", 192, "0 0 108 108"],
  ["app-maskable-512.png", 512, "0 0 108 108"],
];

const browser = await chromium.launch();
const page = await browser.newPage({ deviceScaleFactor: 1 });
for (const [file, size, viewBox] of icons) {
  await page.setViewportSize({ width: size, height: size });
  await page.setContent(`<html><body style="margin:0">${svg(size, viewBox)}</body></html>`);
  await page.screenshot({ path: join(out, file), clip: { x: 0, y: 0, width: size, height: size }, omitBackground: true });
  console.log(`icons/${file} (${size} px)`);
}
await browser.close();
