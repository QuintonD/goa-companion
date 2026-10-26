import { expect, test, type Page } from "@playwright/test";
import { expectNoHorizontalOverflow, open, shot, title } from "./helpers";
import { blockProduction, FakeRelay, groupPrefs, readStorage, seedDocument, seedStorage, watchAppErrors } from "./group-helpers";

// Group stream (W2): players, match log, meta, the match editor and group sync, against a fake relay
// on this machine (the production relay is blocked). Screenshots go to GOA_SHOTS_DIR
// (D:/.goa-qa/web/shots-w2 for review).

// Requests from a page a service worker controls bypass Playwright's routes in WebKit, so the block
// on production wouldn't hold: these tests run without the service worker (offline.spec.ts covers it).
test.use({ serviceWorkers: "block" });

let relay: FakeRelay;

test.beforeEach(async () => {
  relay = new FakeRelay();
  await relay.start();
});

test.afterEach(async () => {
  await relay.stop();
});

/** Opens the app with the seeded match log and, unless [inGroup] is false, in a group syncing with [relay]. */
async function seeded(page: Page, inGroup = true, extra: Record<string, unknown> = {}) {
  await blockProduction(page);
  // Seed from a page of the site that isn't the app, so no app is running while the data changes.
  await page.goto("./manifest.webmanifest");
  await seedStorage(page, {
    "group/document": seedDocument(),
    ...(inGroup ? { "group/prefs": groupPrefs(relay.url, extra) } : {}),
  });
  await page.goto("./#/players");
  await expect(title(page)).toBeVisible();
}

const screens: [name: string, hash: string, prepare?: (page: Page) => Promise<void>][] = [
  ["players", "#/players"],
  ["matches", "#/matches"],
  ["meta", "#/meta"],
  ["meta-cards", "#/meta", async (page) => page.getByRole("radio", { name: "Cards" }).click()],
  ["player", "#/players/p-ann"],
  ["player-edit", "#/players/p-bob/edit"],
  ["match-new", "#/matches/new"],
  ["match-edit", "#/matches/m-03"],
  ["who-won", "#/matches/m-pending?result=1"],
  ["ratings", "#/ratings"],
  ["group", "#/group"],
  ["invite", "#/group", async (page) => page.getByRole("button", { name: "Invite a friend" }).click()],
  ["who-are-you", "#/group/me"],
];

const variants: { name: string; viewport?: { width: number; height: number }; theme: string; text?: string }[] = [
  { name: "dark", theme: "dark" },
  { name: "light", theme: "light" },
  { name: "se-dark", viewport: { width: 375, height: 667 }, theme: "dark" },
  { name: "landscape-light", viewport: { width: 852, height: 393 }, theme: "light" },
  { name: "text200-dark", theme: "dark", text: "200%" },
];

for (const variant of variants) {
  test(`group screens: ${variant.name}`, async ({ page }, info) => {
    test.setTimeout(240_000);
    if (variant.viewport) await page.setViewportSize(variant.viewport);
    await page.addInitScript((theme) => localStorage.setItem("goa.theme", theme), variant.theme);
    await seeded(page);
    for (const [name, hash, prepare] of screens) {
      await open(page, hash);
      if (variant.text) await page.evaluate((size) => (document.documentElement.style.fontSize = size), variant.text);
      await page.evaluate(() => document.fonts.ready);
      if (prepare) await prepare(page);
      await page.waitForTimeout(700);
      await shot(page, info, `${name}-${variant.name}`);
      await expectNoHorizontalOverflow(page);
    }
  });
}

test("no group yet: a broken invite, starting a group, a server address", async ({ page }, info) => {
  const errors = watchAppErrors(page);
  await seeded(page, false);
  await open(page, "#/group");
  await expect(page.getByRole("heading", { name: "Play on every phone" })).toBeVisible();
  await shot(page, info, "group-none-dark");

  // A broken invite says why, under the field.
  await page.getByLabel("Or paste an invite").fill("goa-sync:1?g=nope");
  await page.getByRole("button", { name: "Join", exact: true }).click();
  await expect(page.getByText("This invite is incomplete. Scan the QR code again.")).toBeVisible();

  // Start a group; then point it at the group's server (here the fake relay) under Advanced.
  await page.getByLabel("Group name").fill("Tuesday crew");
  await page.getByRole("button", { name: "Start group" }).click();
  await expect(page.getByRole("heading", { name: "Tuesday crew" })).toBeVisible();
  await page.getByRole("button", { name: "Advanced" }).click();
  await page.getByLabel("Server address").fill(relay.url);
  await page.getByRole("button", { name: "Save address" }).click();
  await expect.poll(() => relay.rev, { timeout: 30_000 }).toBe(1);
  await expect(page.getByText(/^Synced/)).toBeVisible({ timeout: 30_000 });
  await shot(page, info, "group-started-dark");
  errors();
});

test("an invite link asks before joining and drops the key from the address", async ({ page }, info) => {
  await seeded(page, false);
  const invite =
    "goa-sync:1?g=8c4f2b1e-3a5d-4c6e-9f70-1a2b3c4d5e6f&k=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8" +
    `&n=Friday%20nights&s=${encodeURIComponent(relay.url)}`;
  await page.goto(`./#join=${invite}`);
  const dialog = page.getByRole("alertdialog", { name: "Join “Friday nights”?" });
  await expect(dialog).toBeVisible();
  expect(page.url()).not.toContain("goa-sync");
  await shot(page, info, "join-link-dark");
  await dialog.getByRole("button", { name: "Join", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Friday nights" })).toBeVisible();
  // The relay had nothing: this browser's data went up as the group's first document.
  await expect.poll(() => relay.rev, { timeout: 30_000 }).toBe(1);
});

test("a match logged here reaches the relay, sealed, and a second browser gets it", async ({ page, browser }, info) => {
  test.setTimeout(150_000);
  const first = page;
  await seeded(first);
  await expect.poll(() => relay.rev, { timeout: 30_000 }).toBe(1);
  const sealed = relay.blob!.toString("utf8");
  expect(sealed).toContain('"format":"goa-sync-file"');
  expect(sealed).toContain('"groupName":""'); // uploads never carry the group's name in clear
  expect(sealed).not.toContain("Ann"); // nor any data

  // Log a match: Ann + Bob (Red) beat Cid + Dee.
  await open(first, "#/matches/new");
  const seats = first.locator(".goa-w2-seat-editor");
  for (const [i, pilot] of ["Ann", "Bob", "Cid", "Dee"].entries()) {
    await seats.nth(i).getByRole("button", { name: /^Pilot:/ }).click();
    await first.getByRole("dialog").getByRole("button", { name: new RegExp(`^${pilot}`) }).click();
  }
  await first.getByRole("radio", { name: /^Red won/ }).click();
  await first.getByRole("button", { name: "Save match" }).click();
  await expect(title(first)).toHaveText("Result saved");
  await shot(first, info, "result-saved-dark");
  await expect.poll(() => relay.rev, { timeout: 40_000 }).toBe(2); // 15 s after the change

  // A fresh browser joins the same group and gets everything; both show the same ratings.
  const context = await browser.newContext({ ...info.project.use, serviceWorkers: "block" });
  const second = await context.newPage();
  await blockProduction(second);
  await second.goto("./manifest.webmanifest");
  await seedStorage(second, { "group/prefs": groupPrefs(relay.url, { deviceId: "web-test-2" }) });
  await open(second, "#/matches");
  await expect(second.locator(".goa-w2-match")).toHaveCount(16, { timeout: 30_000 });
  const elo = async (p: Page) => (await p.locator(".goa-w2-leader .goa-w2-elo .t-title-large").allTextContents()).join(",");
  await open(first, "#/players");
  await open(second, "#/players");
  await expect.poll(() => elo(second)).toEqual(await elo(first));
  // Nothing new on either side: the next sync is one GET answered 304.
  relay.requests.length = 0;
  await open(second, "#/group");
  await second.getByRole("button", { name: "Sync now" }).click();
  await expect.poll(() => relay.requests.map((r) => r.method).join(",")).toContain("GET");
  expect(relay.requests.some((r) => r.method === "PUT")).toBe(false);
  await context.close();
});

test("server problems read as the Group screen words them", async ({ page }, info) => {
  await seeded(page);
  relay.mode = "wrong-key";
  await open(page, "#/group");
  await page.getByRole("button", { name: "Sync now" }).click();
  await expect(page.getByText(/holds this group under another key/)).toBeVisible({ timeout: 30_000 });
  await shot(page, info, "group-wrong-key-dark");
  // The Players tab's sync icon carries a dot and opens the explanation.
  await open(page, "#/players");
  await expect(page.locator(".goa-w2-dot")).toBeVisible();
  relay.mode = "down";
  await open(page, "#/group");
  await page.getByRole("button", { name: "Sync now" }).click();
  await expect(page.getByText("Can't reach the sync server. Check the internet connection.")).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText("Trying again on its own.", { exact: false })).toBeVisible();
});

test("deleting a match offers undo and leaves a tombstone", async ({ page }) => {
  await seeded(page);
  await open(page, "#/matches");
  await page.locator("#match-m-13").getByRole("button", { name: "More options" }).click();
  await page.getByRole("button", { name: "Delete" }).click();
  await expect(page.getByText("Match deleted. Ratings updated.")).toBeVisible();
  await expect(page.locator("#match-m-13")).toHaveCount(0);
  const doc = JSON.parse((await readStorage(page, "group/document"))!);
  expect(doc.deletions.map((d: { id: string }) => d.id)).toContain("m-13");
  await page.getByRole("button", { name: "Undo" }).click();
  await expect(page.locator("#match-m-13")).toHaveCount(1);
});
