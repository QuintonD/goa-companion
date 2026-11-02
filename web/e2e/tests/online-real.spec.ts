import { expect, test, type Browser, type Page } from "@playwright/test";
import { title } from "./helpers";
import { blockProduction, seedStorage } from "./group-helpers";
import { createAccount, mapSvg, watchPolicy } from "./online-helpers";

// Online games against the real server (ADR-056): two browsers, the real rules, through to a result. It needs
// `:server:game:run` on this machine and says where in GOA_GAME_SERVER_URL; without it the suite skips this:
//   GOA_GAME_DATA=<empty dir> GOA_GAME_ASSETS=app/src/main/assets GOA_GAME_PORT=8799
//   GOA_GAME_ORIGINS=http://localhost:<GOA_PORT>
const SERVER = process.env.GOA_GAME_SERVER_URL;
test.skip(!SERVER, "needs GOA_GAME_SERVER_URL: a local :server:game:run");
test.use({ serviceWorkers: "block" });

const OVER = /^(You won|Your team lost)$/;
const ANSWER_MS = 8_000;

async function point(page: Page) {
  await page.goto("./manifest.webmanifest");
  await seedStorage(page, { "online/testServer": SERVER });
}

/** Keeps every socket the page opens, so a test can drop them as a suspended phone's are dropped. */
async function keepSockets(page: Page) {
  await page.addInitScript(() => {
    const seen: WebSocket[] = [];
    (window as unknown as { goaSockets: WebSocket[] }).goaSockets = seen;
    const Real = window.WebSocket;
    window.WebSocket = new Proxy(Real, {
      construct(target, args: [string]) {
        const socket = new target(...args);
        seen.push(socket);
        return socket;
      },
    });
  });
}

async function secondPhone(browser: Browser, baseURL: string | undefined) {
  const context = await browser.newContext({ baseURL, serviceWorkers: "block", hasTouch: true, viewport: { width: 390, height: 844 } });
  const page = await context.newPage();
  await blockProduction(page);
  return page;
}

async function kind(page: Page): Promise<string> {
  if (await page.locator(".goa-attack").count()) return "defend";
  if (await page.locator(".goa-offered-card").count()) return "cards";
  if (await page.locator(".goa-action-chip").count()) return "action";
  if (await page.locator(".goa-decision .goa-tap-hint").count()) return "board";
  return "buttons";
}

/** Answers the question in front of the person, the simplest way a player would (as play-game.spec.ts). */
async function answer(page: Page) {
  const decision = page.locator(".goa-decision");
  const click = { timeout: ANSWER_MS };
  const k = await kind(page);
  if (k === "defend") {
    await page.locator(".goa-decline").first().click(click);
    await page.locator(".goa-confirm").click(click);
  } else if (k === "cards") {
    if (!(await page.locator(".goa-focus").count())) {
      await page.locator(".goa-offered-card:not(.dimmed)").first().click(click);
    }
    await page.locator(".goa-focus-confirm .goa-button").click(click);
  } else if (k === "action") {
    await decision.locator(".goa-action-chip:not(:disabled)").first().click(click);
  } else if (k === "board") {
    const chip = decision.locator(".goa-option-chip");
    if (await chip.count()) {
      await chip.first().click(click);
    } else {
      await page.locator(".goa-map .goa-visually-hidden button", { hasText: /^Choose/ }).first().dispatchEvent("click", {}, click);
    }
    await page.locator(".goa-confirm").click(click);
  } else {
    await decision.locator(".goa-choice, .goa-number, .goa-button.outlined").first().click(click);
  }
}

/** Plays this seat until the game has a result: whenever a question is up and not on its way. */
async function playToTheEnd(page: Page, onMove: (moves: number) => Promise<void>) {
  let moves = 0;
  for (;;) {
    if (await page.getByRole("heading", { name: OVER }).count()) return moves;
    if (await title(page).filter({ hasText: "Game closed" }).count()) throw new Error("the game was closed");
    const asked = await page.locator(".goa-decision").count();
    const busy = await page.locator(".goa-decision button:disabled").count();
    if (asked === 0 || busy > 0) {
      await page.waitForTimeout(120);
      continue;
    }
    try {
      await answer(page);
      moves++;
      await onMove(moves);
    } catch (e) {
      // The question moved on under the click, or the page was closing: look at it again.
      if (String(e).includes("Target page")) throw e;
    }
  }
}

test("two browsers play a real game through to a result", async ({ page, browser, baseURL, browserName }) => {
  test.setTimeout(25 * 60_000);
  await blockProduction(page);
  await keepSockets(page);
  const violations = await watchPolicy(page);
  await point(page);
  await page.goto("./#/online");
  await createAccount(page, `Ann ${browserName}`);
  await page.getByText("Ranked: the result counts").click();
  await page.getByRole("button", { name: "Create room" }).click();
  await expect(title(page)).toHaveText(/^Room /);
  const code = (await title(page).textContent())!.replace("Room ", "");

  // An invite link, cold, with no account yet; a reload at the invite and in the room.
  const guest = await secondPhone(browser, baseURL);
  await keepSockets(guest);
  const guestViolations = await watchPolicy(guest);
  await point(guest);
  await guest.goto(`./#play=${code}`);
  await expect.poll(() => guest.url()).toContain(`#/online/join/${code}`);
  await guest.reload();
  await createAccount(guest, `Bob ${browserName}`);
  await expect(title(guest)).toHaveText(`Room ${code}`);
  await guest.reload();
  await expect(title(guest)).toHaveText(`Room ${code}`);

  // Seats and heroes as the real lobby gives them.
  await expect(page.locator(".goa-online-seat", { hasText: "Bob" })).toBeVisible();
  await page.getByRole("button", { name: "Wasp" }).click();
  await expect(page.locator(".goa-online-seat.mine")).toContainText("Wasp");
  await expect(guest.getByRole("button", { name: "Wasp", exact: true })).toHaveCount(0);
  await guest.getByRole("button", { name: "Arien" }).click();
  await expect(guest.locator(".goa-online-seat.mine")).toContainText("Arien");
  await page.getByRole("button", { name: "I'm ready" }).click();
  await guest.getByRole("button", { name: "I'm ready" }).click();
  await page.getByRole("button", { name: "Start the game" }).click();
  await expect(mapSvg(page)).toBeVisible({ timeout: 30_000 });
  await expect(mapSvg(guest)).toBeVisible({ timeout: 30_000 });
  await expect(page.locator(".goa-online-player", { hasText: "Bob" })).toContainText("Arien");
  await expect(page.locator(".goa-online-clock")).toContainText(/\d+ s/);
  await expect(page.getByRole("button", { name: "Take back" })).toHaveCount(0);

  // What happens on the way, at set moves of the game.
  const hostMoves = playToTheEnd(page, async (moves) => {
    if (moves === 3) {
      // Suspended and back: the socket is gone with no warning, and the app comes back to the foreground.
      await page.evaluate(() => {
        (window as unknown as { goaSockets: WebSocket[] }).goaSockets.forEach((s) => s.close());
        document.dispatchEvent(new Event("visibilitychange"));
      });
      await expect(page.locator(".goa-online-reconnecting")).toHaveCount(0, { timeout: 20_000 });
      await expect(mapSvg(page)).toBeVisible();
    }
    if (moves === 6) {
      // A reload in the middle of a real game finds the same game.
      await page.reload();
      await expect(mapSvg(page)).toBeVisible({ timeout: 30_000 });
    }
  });
  const guestMoves = playToTheEnd(guest, async () => undefined);
  const [hostPlayed, guestPlayed] = await Promise.all([hostMoves, guestMoves]);
  console.log(`the game took ${hostPlayed} + ${guestPlayed} answers`);

  // One won, one lost, and it counted.
  const results = await Promise.all([page, guest].map((p) => p.getByRole("heading", { name: OVER }).textContent()));
  expect(results.sort()).toEqual(["You won", "Your team lost"]);
  await expect(page.getByText("Rated: it counts on the leaderboard")).toBeVisible();
  await expect(guest.getByText("Rated: it counts on the leaderboard")).toBeVisible();
  await expect(page.locator(".goa-result-row", { hasText: "Bob" })).toBeVisible();

  // The finished game is no longer among the ones to go back to.
  await page.getByRole("button", { name: "Back to Online" }).click();
  await expect(title(page)).toHaveText("Online");
  await expect(page.getByRole("button", { name: "Create room" })).toBeVisible();
  await expect(page.getByRole("link", { name: /Forgotten Island/ })).toHaveCount(0);

  expect(await violations()).toEqual([]);
  expect(await guestViolations()).toEqual([]);
  await guest.context().close();
});

test("a wrong recovery code, a rename and a deleted account, as the real server answers them", async ({ page, browserName }) => {
  await blockProduction(page);
  const violations = await watchPolicy(page);
  await point(page);
  await page.goto("./#/online");
  await page.getByLabel("Recovery code").fill("not-a-code");
  await page.getByRole("button", { name: "Use this code" }).click();
  await expect(page.getByRole("alert")).toContainText("recovery code isn't right");

  const code = await createAccount(page, `Cid ${browserName}`);
  expect(code.length).toBeGreaterThan(8);
  await page.getByRole("button", { name: new RegExp(`^Cid ${browserName}`) }).click();
  await page.getByRole("dialog").getByLabel("Your name").fill(`Dee ${browserName}`);
  await page.getByRole("button", { name: "Save" }).click();
  await expect(page.getByRole("button", { name: new RegExp(`^Dee ${browserName}`) })).toBeVisible();

  await page.getByRole("button", { name: /^Delete account/ }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Delete" }).click();
  await expect(page.getByRole("button", { name: "Create account" })).toBeVisible();
  // The code died with the account.
  await page.getByLabel("Recovery code").fill(code);
  await page.getByRole("button", { name: "Use this code" }).click();
  await expect(page.getByRole("alert")).toContainText("recovery code isn't right");
  expect(await violations()).toEqual([]);
});
