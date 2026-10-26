import { expect, type Page, test } from "@playwright/test";
import { open, shot, title, watchErrors } from "./helpers";

// Review R1 (parity and rules, ADR-043): places where the web said or did something other than the
// phone, each found by reading both and fixed; these walk them the way a player would hit them.

/** Taps [name]'s tile in the line-up grid. */
async function pickHero(page: Page, name: string) {
  await page
    .locator(".goa-board-tile")
    .filter({ has: page.locator(".goa-board-tile-name", { hasText: new RegExp(`^${name}$`) }) })
    .click();
}

/** Red: Brogan, Arien (you); Blue: Wasp, Tigerclaw at strength 2. Picks alternate, as on the phone. */
async function lineUp(page: Page) {
  await open(page, "#/play/new");
  await expect(page.locator(".goa-board-grid")).toBeVisible({ timeout: 30_000 });
  for (const name of ["Brogan", "Wasp", "Arien", "Tigerclaw"]) await pickHero(page, name);
}

test("a match being edited asks before the top bar's Back drops it", async ({ page }) => {
  const errors = watchErrors(page);
  await open(page, "#/matches/new");
  await expect(title(page)).toHaveText("Log match");
  await page.locator("#match-notes").fill("Close one");
  await page.locator(".goa-topbar").getByRole("button", { name: "Back" }).click();
  const dialog = page.getByRole("alertdialog");
  await expect(dialog).toContainText("Discard changes?");
  await expect(dialog).toContainText("Your edits to this match haven't been saved.");
  await dialog.getByRole("button", { name: "Keep editing" }).click();
  await expect(page.locator("#match-notes")).toHaveValue("Close one");
  await page.locator(".goa-topbar").getByRole("button", { name: "Back" }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Discard" }).click();
  await expect(title(page)).not.toHaveText("Log match");
  errors();
});

test("Settings → Ratings opens the rating settings, as on the phone", async ({ page }) => {
  const errors = watchErrors(page);
  await open(page, "#/settings");
  await page.getByRole("link", { name: /^Ratings/ }).click();
  await expect(title(page)).toHaveText("Ratings");
  errors();
});

test("a phase that runs out on another screen still says time's up", async ({ page }, info) => {
  const errors = watchErrors(page);
  await page.clock.install();
  await open(page, "#/timer");
  await page.getByRole("button", { name: "Start timer" }).click();
  await expect(title(page)).toHaveText("Round 1");
  // The timer's own buttons lead away from it (Levels and defeats, Rules); so do the tabs.
  await page.goto("./#/rules");
  await expect(page.locator(".goa-table-running-bar")).toBeVisible();
  await page.clock.fastForward("03:00");
  const message = page.locator(".goa-snackbar-text");
  // The phone's alert: "Time's up: <phase> · Round 1", and overtime is counting (no auto-advance).
  await expect(message).toContainText(/Time's up: .+ · Round 1\. Overtime is counting · Next: /);
  await page.waitForTimeout(800); // the message has slid in, the flash has faded
  await shot(page, info, "r1-times-up-away");
  await page.getByRole("button", { name: "Open timer" }).click();
  await expect(title(page)).toHaveText("Round 1");
  errors();
});

test("new game: the line-up is New board's, with pick order the player can change", async ({ page }, info) => {
  const errors = watchErrors(page);
  await lineUp(page);
  await expect(page.getByRole("button", { name: "Pick 1: Brogan. Change" })).toBeVisible();
  await page.getByRole("button", { name: "Pick 1: Brogan. Change" }).click();
  await page.getByRole("button", { name: "Move later" }).click();
  await expect(page.getByRole("button", { name: "Pick 1: Arien. Change" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Pick 2: Brogan. Change" })).toBeVisible();
  await expect(page.locator(".goa-lineup-size p").filter({ visible: true }).first()).toHaveText("2 v 2");
  await shot(page, info, "r1-new-game-lineup");
  // Back from the options returns to the line-up, as on the phone.
  await page.getByRole("button", { name: "Next", exact: true }).click();
  await expect(title(page)).toHaveText("Game options");
  await page.locator(".goa-topbar").getByRole("button", { name: "Back" }).click();
  await expect(title(page)).toHaveText("New game");
  errors();
});

test("the table reads card text and the game state as the phone does", async ({ page }, info) => {
  test.setTimeout(180_000);
  const errors = watchErrors(page);
  await lineUp(page);
  await page.getByRole("button", { name: "Next", exact: true }).click();
  await page.getByRole("slider", { name: "Computer strength" }).fill("2");
  await page.getByRole("button", { name: "Flip the Tie Breaker coin" }).click();
  await page.getByRole("button", { name: "Start the game" }).click();
  await expect(page.locator(".goa-offered-card").first()).toBeVisible({ timeout: 60_000 });

  // The card's words as text: the catalog's markup drawn ({icons}, **bold**, ~(reminders)), never shown raw.
  for (let i = 0; i < (await page.locator(".goa-offered-card").count()); i++) {
    await page.locator(".goa-offered-card").nth(i).click();
    await page.getByRole("button", { name: "Read the card as text" }).filter({ visible: true }).first().click();
    const words = page.locator(".goa-card-as-text").filter({ visible: true }).first();
    await expect(words.locator(".goa-cardtext")).toBeVisible();
    const text = (await words.innerText()) ?? "";
    expect(text, "card text markup shown raw").not.toMatch(/\*\*|\{[a-z_]+\}|^~|\n~/);
    if (i === 0) await shot(page, info, "r1-card-as-text");
    await page.keyboard.press("Escape");
    await expect(page.locator(".goa-focus")).toHaveCount(0);
  }

  // Game state before any card is down: every hero says so, and no Turn slot is filled yet.
  await page.getByRole("button", { name: "Game state" }).click();
  const sheet = page.locator(".goa-sheet");
  await expect(sheet).toBeVisible();
  await expect(sheet.getByText("No card played yet.").first()).toBeVisible();
  await expect(sheet.getByText("None yet").first()).toBeVisible();
  await expect(sheet.getByText("Red team · yours")).toBeVisible();
  await shot(page, info, "r1-game-state");
  errors();
});
