import { expect, type Page, type TestInfo } from "@playwright/test";
import { mkdirSync } from "node:fs";
import { join } from "node:path";

/** Where review screenshots go: GOA_SHOTS_DIR, else D:/.goa-qa/web/shots on the owner's PC, else test-results. */
export function shotsDir(): string {
  const dir = process.env.GOA_SHOTS_DIR ?? (process.platform === "win32" ? "D:/.goa-qa/web/shots" : "test-results/shots");
  mkdirSync(dir, { recursive: true });
  return dir;
}

export async function shot(page: Page, info: TestInfo, name: string) {
  await page.screenshot({ path: join(shotsDir(), `${info.project.name}-${name}.png`) });
}

/** Fails the test on any console error or uncaught exception in the page. */
export function watchErrors(page: Page): () => void {
  const errors: string[] = [];
  page.on("console", (msg) => {
    if (msg.type() === "error") errors.push(`console: ${msg.text()}`);
  });
  page.on("pageerror", (err) => errors.push(`page: ${err.message}`));
  return () => expect(errors, errors.join("\n")).toEqual([]);
}

/** Opens [hash] (e.g. "#/heroes") and waits for the app to render its title. */
export async function open(page: Page, hash = "#/heroes") {
  await page.goto(`./${hash}`);
  await expect(page.locator("h1.goa-topbar-title")).toBeVisible();
}

export function title(page: Page) {
  return page.locator("h1.goa-topbar-title");
}

/** Waits until a service worker controls the page (shell + catalog precached). */
export async function waitForServiceWorker(page: Page) {
  await page.waitForFunction(() => !!navigator.serviceWorker?.controller, null, { timeout: 60_000 });
}

/** No sideways scrolling: nothing wider than the viewport. */
export async function expectNoHorizontalOverflow(page: Page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow, "page scrolls sideways").toBeLessThanOrEqual(0);
}
