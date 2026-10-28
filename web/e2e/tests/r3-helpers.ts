import { expect, type Page, type TestInfo } from "@playwright/test";
import { mkdirSync } from "node:fs";
import { join } from "node:path";
import { RELAY } from "./group-helpers";

/** R3 hands-on review screenshots: GOA_R3_SHOTS, else D:/.goa-qa/r3-shots on the owner's PC, else test-results. */
export function r3ShotsDir(): string {
  const dir = process.env.GOA_R3_SHOTS ?? (process.platform === "win32" ? "D:/.goa-qa/r3-shots" : "test-results/r3-shots");
  mkdirSync(dir, { recursive: true });
  return dir;
}

export async function r3Shot(page: Page, info: TestInfo, name: string) {
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: join(r3ShotsDir(), `${info.project.name}-${name}.png`) });
}

/** iOS Larger Text: the root font size the app's rem sizes follow (`font: -apple-system-body`). */
export async function textSize(page: Page, size: string) {
  await page.evaluate((s) => (document.documentElement.style.fontSize = s), size);
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(300);
}

/** Every request to the production relay fails, for the whole browser context. */
export async function blockProductionContext(page: Page) {
  await page.context().route(`${RELAY}/**`, (route) => route.abort("blockedbyclient"));
  await page.context().route(/oldboi\.taila4d38d\.ts\.net/, (route) => route.abort("blockedbyclient"));
}

/** Nothing on the page is wider than the window (no sideways scrolling), naming the culprits if so. */
export async function expectNoSidewaysScroll(page: Page, where: string) {
  const culprits = await page.evaluate(() => {
    const w = window.innerWidth;
    if (document.documentElement.scrollWidth <= w) return [];
    const out: string[] = [];
    for (const el of document.querySelectorAll<HTMLElement>("body *")) {
      const r = el.getBoundingClientRect();
      if (r.width === 0 || r.right <= w + 0.5) continue;
      let clipped = false;
      for (let p = el.parentElement; p; p = p.parentElement) {
        if (getComputedStyle(p).overflowX !== "visible") {
          clipped = true;
          break;
        }
      }
      if (!clipped) out.push(`${el.tagName.toLowerCase()}.${el.className} "${(el.textContent ?? "").trim().slice(0, 30)}"`);
    }
    return out.length ? out.slice(0, 5) : ["(page wider than the window)"];
  });
  expect(culprits, `${where} scrolls sideways`).toEqual([]);
}
