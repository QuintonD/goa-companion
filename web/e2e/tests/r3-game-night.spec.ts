import { expect, test, type BrowserContext, type Page } from "@playwright/test";
import { hkdfSync } from "node:crypto";
import { readFileSync, writeFileSync } from "node:fs";
import { open, title, waitForServiceWorker } from "./helpers";
import { blockProductionContext, r3Shot } from "./r3-helpers";

// R3 hands-on game night (ADR-043 Addendum R3), run by scripts/r3-game-night.mjs: a real relay on
// this machine (port 8833) and the phone's real sync code on the JVM (LiveRelayInteropTest) before
// and after. Two iPhones: A runs from the Home Screen (standalone, notch insets), B is a 375 × 667 SE
// in Safari. A joins from the phone's invite link, sees the phone's ratings, drafts, runs the timer
// with the screen off for two minutes mid-phase, logs the result; B joins and shows the same
// ratings; the Android phone then checks them too. On the side: trivia challenge, the line-up on the
// board, three turns against the computer at strengths 5 and 10, puzzle 1 solved with nudges, the
// app offline and the group syncing when it's back. Skipped unless the script runs it.
const configFile = process.env.GOA_R3_LIVE;
test.skip(!configFile, "run by scripts/r3-game-night.mjs");
test.describe.configure({ mode: "serial" });

type Live = { url: string; groupId: string; keyHex: string; jvmRatings: string; browserRatings: string };
const live: Live = configFile ? JSON.parse(readFileSync(configFile, "utf8")) : ({} as Live);
const SE = { width: 375, height: 667 };

/** The invite the phone's QR code carries (GroupInvite.format), as the link an iPhone's Camera opens. */
function inviteHash(): string {
  const key = Buffer.from(live.keyHex, "hex").toString("base64url");
  return `#join=goa-sync:1?g=${live.groupId}&k=${key}&n=Friday%20nights&s=${encodeURIComponent(live.url)}`;
}

function token(): string {
  const key = Buffer.from(live.keyHex, "hex");
  return Buffer.from(hkdfSync("sha256", key, Buffer.from(live.groupId), "goa-relay/token/v1", 32)).toString("base64url");
}

async function storedRev(): Promise<number> {
  const res = await fetch(`${live.url}/v1/groups/${live.groupId}/document`, { headers: { Authorization: `Bearer ${token()}` } });
  return Number((res.headers.get("etag") ?? "0").replace(/"/g, ""));
}

/** A home-screen web app on a notched iPhone: navigator.standalone, display-mode standalone, safe areas. */
async function homeScreen(context: BrowserContext) {
  await context.addInitScript(() => {
    Object.defineProperty(navigator, "standalone", { get: () => true });
    const media = window.matchMedia.bind(window);
    window.matchMedia = (query: string) =>
      query.includes("display-mode: standalone")
        ? ({ matches: true, media: query, addEventListener() {}, removeEventListener() {}, addListener() {}, removeListener() {} } as unknown as MediaQueryList)
        : media(query);
    const insets = () => {
      const style = document.createElement("style");
      style.textContent = ":root{--safe-top:59px!important;--safe-bottom:34px!important}";
      document.documentElement.appendChild(style);
    };
    if (document.documentElement) insets();
    else document.addEventListener("DOMContentLoaded", insets);
  });
}

async function join(page: Page) {
  await page.goto(`./${inviteHash()}`);
  const dialog = page.getByRole("alertdialog", { name: "Join “Friday nights”?" });
  await expect(dialog).toBeVisible();
  expect(page.url()).not.toContain("goa-sync");
  await dialog.getByRole("button", { name: "Join", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Friday nights" })).toBeVisible();
}

async function ratings(page: Page): Promise<Record<string, string>> {
  await open(page, "#/players");
  const rows = page.locator(".goa-w2-leader");
  await expect(rows).toHaveCount(4, { timeout: 30_000 });
  const out: Record<string, string> = {};
  for (const row of await rows.all()) {
    const name = (await row.locator(".goa-row-title").textContent())!.trim();
    out[`live-${name.toLowerCase()}`] = (await row.locator(".goa-w2-elo .t-title-large").textContent())!.trim();
  }
  return out;
}

/** Navigates inside the running app (no page load: works offline without a service worker). */
async function go(page: Page, hash: string) {
  await page.evaluate((h) => (window.location.hash = h), hash);
  await expect(title(page)).toBeVisible();
  await page.waitForTimeout(500);
}

const parse = (line: string) => Object.fromEntries(line.split(",").map((pair) => pair.split("=")));

/** The page hidden (the phone locked) and shown again, as Safari reports it. */
async function setHidden(page: Page, hidden: boolean) {
  await page.evaluate((h) => {
    Object.defineProperty(document, "visibilityState", { configurable: true, get: () => (h ? "hidden" : "visible") });
    Object.defineProperty(document, "hidden", { configurable: true, get: () => h });
    document.dispatchEvent(new Event("visibilitychange"));
    window.dispatchEvent(new Event(h ? "pagehide" : "pageshow"));
  }, hidden);
}

let a: Page;
let b: Page;

test.beforeAll(async ({ browser }, info) => {
  const use = info.project.use;
  const ctxA = await browser.newContext({ ...use, serviceWorkers: "block" });
  await homeScreen(ctxA);
  a = await ctxA.newPage();
  await blockProductionContext(a);
  const ctxB = await browser.newContext({ ...use, viewport: SE, serviceWorkers: "block" });
  b = await ctxB.newPage();
  await blockProductionContext(b);
});

/** Closes the two iPhones (done before the third one's games: headless WebKit renders a third
 * page too rarely for its taps while they keep syncing). */
async function closeGroupPhones() {
  await a?.context().close().catch(() => {});
  await b?.context().close().catch(() => {});
}

test.afterAll(closeGroupPhones);

test("iPhone A joins from the phone's invite and sees the phone's ratings", async ({}, info) => {
  await a.clock.install({ time: Date.now() });
  await join(a);
  expect(await a.evaluate(() => (navigator as Navigator & { standalone?: boolean }).standalone)).toBe(true);
  await r3Shot(a, info, "night-joined");
  expect(await ratings(a)).toEqual(parse(readFileSync(live.jvmRatings, "utf8")));
  await r3Shot(a, info, "night-players");
});

test("a draft, the timer through a locked screen, the result logged and synced", async ({}, info) => {
  test.setTimeout(300_000);
  await open(a, "#/draft");
  for (const name of ["Ann", "Bob", "Cid", "Dee"]) await a.getByRole("checkbox", { name: new RegExp(`^${name}`) }).click();
  await a.getByRole("button", { name: /^Next: Teams$/ }).click();
  await a.getByRole("button", { name: /^Next: Heroes$/ }).click();
  await a.getByRole("radio", { name: /^All Pick/ }).click();
  await a.getByRole("button", { name: /^Next: Tie Breaker$/ }).click();
  await a.getByRole("button", { name: "Flip the coin" }).click();
  await r3Shot(a, info, "night-draft-coin");
  await a.getByRole("button", { name: "Start draft" }).click();
  await expect(a.locator(".goa-draft-banner")).toBeVisible();
  while (await a.locator(".goa-draft-banner").count()) {
    await a.locator(".goa-draft-tile:not(.inactive):not(.banned):not(.picked)").first().click();
    await a.getByRole("alertdialog").getByRole("button", { name: /^(◆|●) (Pick|Ban)$/ }).click();
    await expect(a.getByRole("alertdialog")).toHaveCount(0);
  }
  await expect(a.getByRole("heading", { name: "Draft complete" })).toBeInViewport();
  await a.getByRole("button", { name: "Set up match" }).click();
  await expect(title(a)).toHaveText("Match setup");
  await a.getByRole("button", { name: "Start timer" }).click();
  await expect(title(a)).toHaveText("Round 1");

  // 20 s into the 1:30 strategy phase the phone is locked for two minutes: back on screen, the
  // timer counted from its timestamps (+0:50 overtime), not from ticks that never ran.
  await a.clock.runFor(20_000);
  await setHidden(a, true);
  await a.clock.fastForward(120_000);
  await setHidden(a, false);
  await a.clock.runFor(500);
  await expect(a.locator(".goa-table-dial-digits")).toHaveText(/^\+0:5\d$/);
  await r3Shot(a, info, "night-timer-after-lock");

  await a.getByRole("button", { name: "End match" }).first().click();
  await a.getByRole("alertdialog").getByRole("button", { name: "End match" }).click();
  await a.getByRole("button", { name: "Log result" }).click();
  await expect(title(a)).toHaveText("Who won?");
  const before = await storedRev();
  await a.getByRole("radio", { name: /^Blue won/ }).click();
  await a.getByRole("button", { name: "Save", exact: true }).click();
  // Synced 15 s after the change.
  await a.clock.runFor(20_000);
  await expect.poll(storedRev, { timeout: 60_000 }).toBeGreaterThan(before);
  // Real time again (the fake clock also drives requestAnimationFrame).
  await a.clock.resume();
});

test("iPhone B (an SE in Safari) joins and shows the same ratings", async ({}, info) => {
  await join(b);
  const shownA = await ratings(a);
  await expect.poll(() => ratings(b), { timeout: 60_000 }).toEqual(shownA);
  await r3Shot(b, info, "night-b-players");
  writeFileSync(live.browserRatings, Object.entries(shownA).map(([k, v]) => `${k}=${v}`).join(","));
});

test("the trivia challenge as Bob on iPhone B", async ({}, info) => {
  test.setTimeout(300_000);
  await open(b, "#/trivia");
  await b.getByRole("button", { name: /^Playing as/ }).click();
  await b.getByRole("radio", { name: /^Bob/ }).click();
  await expect(b.getByRole("button", { name: /^Playing as Bob/ })).toBeVisible();
  // The choice closes the sheet (Escape on the page itself would go Back).
  if (await b.getByRole("dialog").isVisible()) await b.getByRole("button", { name: "Close" }).click();
  await b.getByRole("button", { name: "Challenge" }).click();
  await expect(title(b)).toHaveText("Challenge");
  for (let i = 0; i < 80; i++) {
    if ((await title(b).textContent())?.includes("Round over")) break;
    const next = b.locator("main button", { hasText: /^Next$|^See results$/ });
    // A right answer moves on by itself: a button counted a moment ago may be gone.
    // Next smooth-scrolls into view as the card scan loads: tap it without waiting for it to settle.
    if (await next.count()) await next.first().click({ timeout: 2000, force: true }).catch(() => {});
    else await b.locator("main button:not([disabled])").first().click({ timeout: 2000 }).catch(() => {});
    await b.waitForTimeout(100);
  }
  await expect(title(b)).toHaveText("Round over");
  await expect(b.getByText(/of 20 right/)).toBeInViewport();
  await r3Shot(b, info, "night-trivia-results");
});

test("the line-up on the board", async ({}, info) => {
  await open(a, "#/matches");
  await a.locator(".goa-w2-match").first().getByRole("button", { name: "More options" }).click();
  await a.getByRole("dialog").getByRole("button", { name: "Edit" }).click();
  await a.getByRole("button", { name: "Open on the board" }).click();
  await expect(a.locator(".goa-map-svg")).toBeVisible({ timeout: 30_000 });
  await r3Shot(a, info, "night-board");
});

test("offline: A adds a player, the sync waits, and comes through when back online", async ({}, info) => {
  test.setTimeout(180_000);
  await a.context().setOffline(true);
  await go(a, "#/players/new");
  await a.getByLabel("Name").fill("Eve");
  await a.getByRole("button", { name: "Save" }).last().click();
  await go(a, "#/group");
  await a.getByRole("button", { name: "Sync now" }).click();
  await expect(a.getByText("Can't reach the sync server. Check the internet connection.")).toBeVisible({ timeout: 30_000 });
  await r3Shot(a, info, "night-offline-sync");
  await a.context().setOffline(false);
  await a.getByRole("button", { name: "Sync now" }).click();
  await expect(a.getByText(/^Synced/).first()).toBeVisible({ timeout: 30_000 });
  await open(b, "#/group");
  await b.getByRole("button", { name: "Sync now" }).click();
  await open(b, "#/players");
  await expect(b.getByText("Eve")).toBeVisible({ timeout: 30_000 });
});

// The same night on a third iPhone that has the app cached (service worker on, no group, so nothing
// can reach a relay): the computer at strengths 5 and 10, a puzzle with nudges, and airplane mode.
let c: Page;

test("the computer: three turns at strength 5, then 10", async ({ browser }, info) => {
  test.setTimeout(420_000);
  await closeGroupPhones();
  const ctx = await browser.newContext({ ...info.project.use, serviceWorkers: "allow" });
  c = await ctx.newPage();
  await blockProductionContext(c);
  for (const strength of ["5", "10"]) {
    await open(c, "#/play/new");
    await expect(c.locator(".goa-hero-grid")).toBeVisible({ timeout: 30_000 });
    for (const hero of ["Brogan", "Wasp", "Arien", "Tigerclaw"]) {
      await c.locator(".goa-hero-grid").getByRole("button", { name: hero, exact: true }).click();
    }
    await c.getByRole("button", { name: "Next" }).click();
    await c.getByRole("slider", { name: "Computer strength" }).fill(strength);
    await c.locator(".goa-switch-row", { hasText: "A gentle nudge" }).locator("input").uncheck();
    await c.getByRole("button", { name: "Flip the Tie Breaker coin" }).click();
    await c.getByRole("alertdialog").getByRole("button", { name: "Start the game" }).click();
    await expect(c.locator(".goa-map-svg")).toBeVisible({ timeout: 30_000 });
    let last: string | null = null;
    for (let step = 0; step < 80; step++) {
      await expect
        .poll(async () => {
          const id = await c.locator(".goa-decision").first().getAttribute("data-decision", { timeout: 1000 }).catch(() => null);
          return id !== null && id !== last && (await c.locator(".goa-decision button:disabled").count()) === 0;
        }, { timeout: 90_000, intervals: [200] })
        .toBe(true);
      last = await c.locator(".goa-decision").first().getAttribute("data-decision");
      const turn = /R(\d+) · Turn (\d+)/.exec((await title(c).textContent()) ?? "");
      if (turn && (Number(turn[1]) - 1) * 4 + Number(turn[2]) > 3) break;
      const decision = c.locator(".goa-decision");
      if (await c.locator(".goa-attack").count()) {
        await c.locator(".goa-decline").first().click();
        await c.locator(".goa-confirm").click();
      } else if (await c.locator(".goa-offered-card").count()) {
        // A tap while the panel switches heroes can go unseen: tap again until the card opens, as a
        // player would.
        for (let tap = 0; tap < 4 && !(await c.locator(".goa-focus").isVisible()); tap++) {
          await c.locator(".goa-offered-card:not(.dimmed)").first().click();
          await c.waitForTimeout(400);
        }
        await c.locator(".goa-focus .goa-focus-confirm .goa-button").click();
      } else if (await c.locator(".goa-action-chip").count()) {
        await decision.locator(".goa-action-chip:not(:disabled)").first().click();
      } else if (await c.locator(".goa-decision .goa-tap-hint").count()) {
        const chip = decision.locator(".goa-option-chip");
        if (await chip.count()) await chip.first().click();
        else await c.locator(".goa-map .goa-visually-hidden button", { hasText: /^Choose/ }).first().dispatchEvent("click");
        await c.locator(".goa-confirm").click();
      } else {
        await decision.locator(".goa-choice, .goa-number, .goa-button.outlined").first().click();
      }
    }
    await expect(title(c)).toHaveText(/R1 · Turn 4|R2/);
    await r3Shot(c, info, `night-computer-${strength}`);
  }
});

test("puzzle 1 solved with its nudges", async ({}, info) => {
  await open(c, "#/puzzles/p01");
  await expect(c.getByRole("button", { name: /^Goal:/ })).toBeVisible({ timeout: 30_000 });
  await c.getByRole("button", { name: "More options" }).click();
  await c.getByRole("button", { name: /Nudge me/ }).click();
  for (const n of [2, 3]) {
    await c.getByRole("button", { name: "Another nudge" }).first().click();
    await expect(c.getByText(`Nudge ${n} of 3`)).toBeVisible();
  }
  // "Brogan steps to the space north-west of Wasp and punches her one space; then Arien attacks Wasp."
  await c.locator(".goa-action-chip", { hasText: "Skill" }).first().click();
  await c.locator(".goa-map .goa-visually-hidden button", { hasText: /next to Wasp/ }).first().dispatchEvent("click");
  await c.locator(".goa-confirm").click();
  const pick = async (name: RegExp) => {
    await c.locator(".goa-decision .goa-option-chip", { hasText: name }).first().click();
    await c.locator(".goa-confirm").click();
  };
  await pick(/^Wasp/);
  await c.locator(".goa-decision").locator(".goa-choice, .goa-number, .goa-button.outlined", { hasText: /^1$/ }).first().click();
  await c.locator(".goa-action-chip", { hasText: /Attack/ }).first().click();
  await pick(/^Wasp/);
  await pick(/^Xargatha/);
  await expect(c.getByText(/^Solved/).first()).toBeVisible({ timeout: 30_000 });
  await expect(c.getByText(/3 nudges/)).toBeVisible();
  await r3Shot(c, info, "night-puzzle-solved");
});

test("airplane mode: catalog, rules, timer and board keep working", async ({}, info) => {
  await open(c, "#/heroes");
  await waitForServiceWorker(c);
  await c.context().setOffline(true);
  await c.reload();
  for (const [hash, text] of [
    ["#/heroes/brogan", "the Destroyer"],
    ["#/rules", "Levels & defeats"],
    ["#/timer", "Phase timer"],
    ["#/boards", "Play against the computer"],
  ] as const) {
    await open(c, hash);
    await expect(c.getByText(text).first()).toBeVisible({ timeout: 30_000 });
  }
  await open(c, "#/puzzles");
  await expect(c.getByText(/of \d+ solved/i)).toBeVisible({ timeout: 30_000 });
  await r3Shot(c, info, "night-offline-puzzles");
  await c.context().setOffline(false);
  await c.context().close();
});
