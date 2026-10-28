import { expect, test } from "@playwright/test";
import { open, title, waitForServiceWorker, watchErrors } from "./helpers";
import { blockProduction, FakeRelay, groupPrefs, seedDocument, seedStorage, watchAppErrors } from "./group-helpers";

// First load on a phone (ADR-043 "Addendum (R3 first load)"): what the first visit downloads, in which
// order, and what an update downloads again. `node scripts/first-load.mjs` measures the timings.

test.beforeEach(async ({ page }) => {
  await page.route(/oldboi\.taila4d38d\.ts\.net/, (route) => route.abort("blockedbyclient"));
});

test("the first visit draws the first screen before it installs the offline copy", async ({ page }) => {
  const check = watchErrors(page);
  // When the app asks for the service worker (the precache of the whole shell starts then).
  await page.addInitScript(() => {
    const register = ServiceWorkerContainer.prototype.register;
    ServiceWorkerContainer.prototype.register = function (...args: Parameters<typeof register>) {
      (window as unknown as { swAt: number }).swAt = performance.now();
      return register.apply(this, args);
    };
  });
  const requested: string[] = [];
  page.on("request", (r) => requested.push(new URL(r.url()).pathname));
  await open(page, "#/heroes");
  await expect(page.getByRole("link", { name: /^Arien/ })).toBeVisible();
  await waitForServiceWorker(page);

  const order = await page.evaluate(() => {
    const swAt = (window as unknown as { swAt?: number }).swAt ?? -1;
    const onScreen = [...document.querySelectorAll("img")]
      .filter((img) => {
        const box = img.getBoundingClientRect();
        return box.bottom > 0 && box.top < innerHeight && box.width > 0;
      })
      .map((img) => img.currentSrc);
    const finished = (src: string) =>
      (performance.getEntriesByName(src)[0] as PerformanceResourceTiming | undefined)?.responseEnd ?? Infinity;
    return { swAt, images: onScreen.map((src) => ({ src, end: finished(src) })) };
  });
  expect(order.swAt, "the service worker was registered").toBeGreaterThan(0);
  expect(order.images.length, "portraits on the first screen").toBeGreaterThan(2);
  for (const image of order.images) {
    expect(image.end, `${image.src} loaded before the precache started`).toBeLessThan(order.swAt);
  }
  // The page itself fetched WOFF2 fonts and no QR library (the precache may fetch the chunks later).
  const beforeWorker = requested.slice(0, requested.indexOf("/goa-companion/sw.js"));
  expect(beforeWorker.filter((p) => p.endsWith(".ttf"))).toEqual([]);
  expect(beforeWorker.filter((p) => p.endsWith(".chunk.js"))).toEqual([]);
  expect(beforeWorker.some((p) => p.endsWith("/fonts/inter_regular.woff2"))).toBe(true);
  expect(await page.evaluate(() => document.fonts.check("16px Inter") && document.fonts.check("700 16px Cinzel"))).toBe(true);
  check();
});

test("an update copies the files that didn't change instead of downloading them again", async ({ page, context, baseURL, browserName }) => {
  // Only Chromium lets a test look inside the service worker (its own resource timings).
  test.skip(browserName !== "chromium", "service worker introspection is Chromium-only");
  await open(page, "#/heroes");
  await waitForServiceWorker(page);
  await context.addCookies([{ name: "goa-deploy", value: "r3", url: baseURL! }]);
  await page.evaluate(async () => (await navigator.serviceWorker.getRegistration())?.update());
  await expect(page.getByText("A new version of the app is ready.")).toBeVisible({ timeout: 30_000 });
  const worker = context.serviceWorkers().find((w) => w.url().endsWith("/sw.js"));
  expect(worker).toBeDefined();
  // The waiting worker's install fetched only the manifest; app.js, the engine, fonts and catalog came
  // from the previous build's cache (same content hash).
  const fetched = await page.evaluate(async () => {
    const names = await caches.keys();
    const latest = names.filter((n) => n.startsWith("goa-shell-")).sort((a, b) => b.length - a.length)[0];
    const cache = await caches.open(latest);
    return { latest, count: (await cache.keys()).length };
  });
  expect(fetched.latest).toMatch(/-dr3$/);
  expect(fetched.count).toBeGreaterThan(50);
  const network = await Promise.all(
    context.serviceWorkers().map((w) =>
      w.evaluate(() => performance.getEntriesByType("resource").map((e) => new URL(e.name).pathname)),
    ),
  );
  const newest = network.flat();
  for (const big of ["/goa-companion/app.js", "/goa-companion/engine-worker.js", "/goa-companion/fonts/inter_regular.woff2"]) {
    expect(newest.filter((p) => p === big).length, `${big} downloaded by the first install only`).toBeLessThanOrEqual(1);
  }
});

test.describe("invite QR code", () => {
  // Requests from a page a service worker controls bypass routes in WebKit; the relay is local.
  test.use({ serviceWorkers: "block" });

  test("Invite a friend draws the QR code with the library loaded on first use", async ({ page }) => {
    const relay = new FakeRelay();
    await relay.start();
    try {
      const check = watchAppErrors(page);
      await blockProduction(page);
      const chunks: string[] = [];
      page.on("request", (r) => {
        if (r.url().endsWith(".chunk.js")) chunks.push(r.url());
      });
      await page.goto("./manifest.webmanifest");
      await seedStorage(page, { "group/document": seedDocument(), "group/prefs": groupPrefs(relay.url) });
      await open(page, "#/group");
      await expect(title(page)).toHaveText("Group sync");
      expect(chunks, "no QR library before it is needed").toEqual([]);
      await page.getByRole("button", { name: "Invite a friend" }).click();
      const qr = page.locator(".goa-w2-qr svg");
      await expect(qr).toBeVisible();
      await expect(qr).toHaveAttribute("aria-label", /Invite QR code for Friday nights/);
      expect(chunks.length).toBeGreaterThan(0);
      check();
    } finally {
      await relay.stop();
    }
  });
});

test.describe("list thumbnails", () => {
  test.use({ serviceWorkers: "block" });

  test("a hero page's card grid loads the small copies, and the full scans when they can't load", async ({ page }) => {
    const check = watchErrors(page);
    // The hero grid's deck symbols (30 px): the 96-pixel copies.
    await open(page, "#/heroes");
    const symbol = page.locator(".goa-hero-symbol.tile").first();
    await expect.poll(() => symbol.evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth)).toBeGreaterThan(0);
    expect(await symbol.evaluate((img: HTMLImageElement) => img.currentSrc)).toContain("/official/thumbs/heroes/");
    // A hero page's card grid (about 120 px a card): the 384-pixel copies.
    await open(page, "#/heroes/arien");
    const scans = page.locator(".goa-deck-grid img");
    await scans.first().scrollIntoViewIfNeeded();
    await expect.poll(() => scans.first().evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth)).toBeGreaterThan(0);
    expect(await scans.first().evaluate((img: HTMLImageElement) => img.currentSrc)).toContain("/official/thumbs/cards/");
    check();
  });

  test("without the small copies (offline, only the full images downloaded) the full scans show", async ({ page }) => {
    await page.route("**/official/thumbs/**", (route) => route.abort("internetdisconnected"));
    await open(page, "#/heroes/arien");
    const scan = page.locator(".goa-deck-grid img").first();
    await scan.scrollIntoViewIfNeeded();
    await expect
      .poll(() => scan.evaluate((img: HTMLImageElement) => (img.complete && img.naturalWidth > 0 ? img.currentSrc : "")))
      .toMatch(/\/official\/cards\/arien-[^/]+\.webp$/);
    await expect(page.locator(".goa-deck-grid .goa-card-tile")).toHaveCount(0);
  });
});
