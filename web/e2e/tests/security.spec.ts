import { expect, test, type Page } from "@playwright/test";
import { readFileSync } from "node:fs";
import { open, title, waitForServiceWorker } from "./helpers";
import { RELAY } from "./group-helpers";

// Web review R2 (security and data safety): the network guard, the Content-Security-Policy, invite
// links and markup built from names. Runs with the service worker, as on an iPhone.

const KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"; // gitleaks:allow (the test vector key 0..31, not a secret)
const invite = (name: string) => `goa-sync:1?g=8c4f2b1e-3a5d-4c6e-9f70-1a2b3c4d5e6f&k=${KEY}&n=${encodeURIComponent(name)}`;

/** Collects the page's CSP violations (from the moment the document starts). */
async function watchPolicy(page: Page): Promise<() => Promise<string[]>> {
  await page.addInitScript(() => {
    const seen: string[] = [];
    (window as unknown as { goaViolations: string[] }).goaViolations = seen;
    document.addEventListener("securitypolicyviolation", (e) =>
      seen.push(`${e.effectiveDirective} ${e.blockedURI} ${e.sourceFile}:${e.lineNumber}`),
    );
  });
  return () => page.evaluate(() => (window as unknown as { goaViolations: string[] }).goaViolations ?? []);
}

test("no test can reach the production relay, even through the service worker", async ({ page }) => {
  const log = process.env.GOA_GUARD_LOG!;
  await open(page, "#/settings");
  await waitForServiceWorker(page);
  // A page the service worker controls: WebKit sends its requests past Playwright's routes, so only
  // the browser-wide guard stands between a test and oldboi.
  const outcome = await page.evaluate(async (url) => {
    try {
      const res = await fetch(`${url}/v1/health`, { cache: "no-store" });
      return `answered ${res.status}`;
    } catch (e) {
      return `failed ${(e as Error).name}`;
    }
  }, RELAY);
  expect(outcome).toMatch(/^failed/);
  const refused = readFileSync(log, "utf8");
  expect(refused).toContain("oldboi.taila4d38d.ts.net");
});

test("the policy is strict and nothing the app does breaks it", async ({ page }) => {
  test.setTimeout(180_000);
  const violations = await watchPolicy(page);
  await page.goto("./#/heroes");
  await expect(title(page)).toBeVisible();
  const policy = await page.locator('meta[http-equiv="Content-Security-Policy"]').getAttribute("content");
  expect(policy).toBeTruthy();
  const directives = Object.fromEntries(policy!.split(";").map((d) => d.trim().split(/\s+/)).map(([k, ...v]) => [k, v]));
  expect(directives["script-src"]).toContain("'self'");
  expect(directives["script-src"].join(" ")).not.toMatch(/unsafe-inline|unsafe-eval|\*/);
  expect(directives["connect-src"]).toContain(RELAY);
  expect(directives["object-src"]).toEqual(["'none'"]);
  expect(directives["base-uri"]).toEqual(["'none'"]);
  expect(await page.locator('meta[name="referrer"]').getAttribute("content")).toBe("no-referrer");

  // The theme script before the first paint is inline: allowed by its hash, so a saved theme applies.
  await page.evaluate(() => localStorage.setItem("goa.theme", "light"));
  await page.reload();
  await expect(title(page)).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.getAttribute("data-theme"))).toBe("light");

  // Walk the app: catalog and card art, rules, timer, draft, players and group, trivia, the board,
  // a game against the computer (the engine worker) and a puzzle.
  for (const hash of [
    "#/heroes/arien", "#/heroes/arien/cards/arien-red-1", "#/rules", "#/levels", "#/timer", "#/draft", "#/players",
    "#/matches", "#/meta", "#/group", "#/trivia", "#/boards", "#/play/new", "#/puzzles", "#/settings",
    "#/settings/about",
  ]) {
    await open(page, hash);
  }
  await open(page, "#/play/new");
  await expect(page.locator(".goa-hero-grid")).toBeVisible({ timeout: 30_000 });
  for (const name of ["Brogan", "Wasp", "Arien", "Tigerclaw"]) {
    await page.locator(".goa-hero-grid").getByRole("button", { name, exact: true }).click();
  }
  await page.getByRole("button", { name: "Next" }).click();
  await page.getByRole("button", { name: "Flip the Tie Breaker coin" }).click();
  await page.getByRole("button", { name: "Start the game" }).click();
  await expect(page.locator(".goa-map-svg")).toBeVisible();
  await expect(page.locator(".goa-decision").first()).toBeVisible({ timeout: 60_000 }); // the engine worker answered
  expect(await violations()).toEqual([]);
});

test("an invite link's key leaves the address before the app has loaded", async ({ page }) => {
  // app.js never arrives: only index.html's own script runs.
  await page.route("**/app.js", (route) => route.abort());
  await page.goto(`./#join=${invite("Friday")}`);
  await expect.poll(() => page.url()).toContain("#/group");
  expect(page.url()).not.toContain("goa-sync");
  expect(page.url()).not.toContain(KEY);
});

test("an invite link never stays in the history, and the app forgets where it read it", async ({ page }) => {
  await open(page, "#/rules");
  await page.goto(`./#join=${invite("Friday")}`);
  const dialog = page.getByRole("alertdialog", { name: "Join “Friday”?" });
  await expect(dialog).toBeVisible();
  expect(page.url()).not.toContain(KEY);
  expect(await page.evaluate(() => "goaInvite" in window)).toBe(false);
  await dialog.getByRole("button", { name: "Cancel" }).click();
  // Back walks to the screen before, never to an address with the key in it.
  await page.goBack();
  expect(page.url()).not.toContain(KEY);
  await page.goForward();
  expect(page.url()).not.toContain(KEY);
  await expect(page.getByRole("alertdialog")).toHaveCount(0);
});

test("a group name from a crafted invite stays text in the invite's QR code", async ({ page }) => {
  // A fresh browser joins; its first sync goes nowhere (the guard refuses the production relay).
  // Before the fix this name became an onclick on the QR code: tapping the code ran it.
  const name = `x" onclick="window.goaPwned=1`;
  await page.goto(`./#join=${invite(name)}`);
  await page.getByRole("alertdialog").getByRole("button", { name: "Join", exact: true }).click();
  await expect(page.getByRole("heading", { name })).toBeVisible();
  await page.getByRole("button", { name: "Invite a friend" }).click();
  const qr = page.locator(".goa-w2-qr svg");
  await expect(qr).toBeVisible();
  expect(await qr.getAttribute("aria-label")).toBe(`Invite QR code for ${name}`);
  expect(await qr.getAttribute("onclick")).toBeNull();
  await qr.click();
  expect(await page.evaluate(() => (window as unknown as { goaPwned?: number }).goaPwned)).toBeUndefined();
});

test("the app doesn't run inside another site's frame", async ({ page, baseURL }) => {
  await page.goto("./manifest.webmanifest");
  await page.setContent(`<iframe src="${baseURL}#/group" style="width:390px;height:600px"></iframe>`);
  const frame = page.frameLocator("iframe");
  await expect(frame.getByText("only runs in its own tab")).toBeVisible();
  await expect(frame.locator("h1.goa-topbar-title")).toHaveCount(0);
});

test("a shell file changed in the cache by another site is never run", async ({ page }) => {
  await open(page, "#/heroes");
  await waitForServiceWorker(page);
  // Every site on quintond.github.io shares this origin's Cache Storage: plant a script in ours.
  await page.evaluate(async () => {
    const name = (await caches.keys()).find((n) => n.startsWith("goa-shell-"))!;
    const cache = await caches.open(name);
    const planted = { headers: { "content-type": "text/javascript" } };
    await cache.put(new URL("app.js", document.baseURI).href, new Response("window.goaPwned = 1;", planted));
    await cache.put(
      new URL("index.html", document.baseURI).href,
      new Response("<!DOCTYPE html><script>window.goaPwned = 1</script><p>planted</p>", { headers: { "content-type": "text/html" } }),
    );
  });
  await page.reload();
  await expect(title(page)).toHaveText("Heroes");
  expect(await page.evaluate(() => (window as unknown as { goaPwned?: number }).goaPwned)).toBeUndefined();
  // The planted copies are gone; the next visit fetches the real files again.
  const left = await page.evaluate(async () => {
    const name = (await caches.keys()).find((n) => n.startsWith("goa-shell-"))!;
    const hit = await (await caches.open(name)).match(new URL("app.js", document.baseURI).href);
    return hit ? (await hit.text()).includes("goaPwned") : false;
  });
  expect(left).toBe(false);
});

test("a deploy half-way through on the CDN is not installed", async ({ page, context, baseURL }) => {
  await open(page, "#/heroes");
  await waitForServiceWorker(page);
  // A new sw.js, but the engine worker still of another build: the install must fail as a whole.
  await context.addCookies([
    { name: "goa-deploy", value: "3", url: baseURL! },
    { name: "goa-stale", value: "engine-worker.js", url: baseURL! },
  ]);
  const failed = await page.evaluate(async () => {
    const reg = (await navigator.serviceWorker.getRegistration())!;
    await reg.update().catch(() => undefined);
    const worker = reg.installing;
    if (!worker) return "no install";
    return new Promise<string>((resolve) => {
      if (worker.state === "redundant" || worker.state === "installed") resolve(worker.state);
      worker.addEventListener("statechange", () => {
        if (worker.state === "redundant" || worker.state === "installed") resolve(worker.state);
      });
    });
  });
  expect(failed).toBe("redundant");
  await expect(page.getByText("A new version of the app is ready.")).toBeHidden();
  // The CDN catches up: the same deploy now installs.
  await context.clearCookies({ name: "goa-stale" });
  await page.evaluate(async () => (await navigator.serviceWorker.getRegistration())?.update());
  await expect(page.getByText("A new version of the app is ready.")).toBeVisible({ timeout: 30_000 });
});
