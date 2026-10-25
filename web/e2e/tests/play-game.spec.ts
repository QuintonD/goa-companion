import { expect, type Page, test } from "@playwright/test";
import { mkdirSync } from "node:fs";
import { join } from "node:path";
import { open, watchErrors } from "./helpers";

// Play against the computer (W4): New game → the table → card play through the card focus viewer,
// a few decisions, take back, reload resumes. Review screenshots go to D:\.goa-qa\web\shots-w4.

const shots = process.env.GOA_W4_SHOTS ?? (process.platform === "win32" ? "D:/.goa-qa/web/shots-w4" : "test-results/shots-w4");
mkdirSync(shots, { recursive: true });

async function snap(page: Page, project: string, name: string) {
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(500);
  await page.screenshot({ path: join(shots, `${project}-game-${name}.png`) });
}

/** New game: Brogan + Arien (you) against Wasp + Tigerclaw on Easy, Fast pace, Coach off. */
async function newGame(page: Page, coach = false) {
  await open(page, "#/play/new");
  for (const name of ["Brogan", "Wasp", "Arien", "Tigerclaw"]) {
    await page.locator(".goa-hero-grid").getByRole("button", { name, exact: true }).click();
  }
  await page.getByRole("button", { name: "Next" }).click();
  await page.getByRole("radio", { name: /Easy/ }).first().click();
  if (!coach) await page.locator(".goa-switch-row", { hasText: "A gentle nudge" }).locator("input").uncheck();
  await page.getByRole("button", { name: "Flip the Tie Breaker coin" }).click();
  await page.getByRole("button", { name: "Start the game" }).click();
  await expect(page.locator(".goa-map-svg")).toBeVisible();
}

/** Waits until the panel asks the person something (or the game is over). */
async function yourTurn(page: Page) {
  await expect(page.locator(".goa-decision, .goa-game-over").first()).toBeVisible({ timeout: 60_000 });
}

test("play: new game, play cards through the card viewer, take back, reload resumes", async ({ page }, info) => {
  const errors = watchErrors(page);
  const p = info.project.name;
  await page.addInitScript(() => localStorage.setItem("goa.theme", "dark"));
  await open(page, "#/play/new");
  await snap(page, p, "new-lineup");
  await newGame(page);
  await yourTurn(page);
  await snap(page, p, "play-card");
  // Card play: the first tap zooms the card, the second answers.
  await page.locator(".goa-offered-card").first().click();
  await expect(page.locator(".goa-focus")).toBeVisible();
  await snap(page, p, "focus");
  await page.locator(".goa-focus-slide").first().locator("button").click();
  await expect(page.locator(".goa-focus")).toHaveCount(0);
  await snap(page, p, "after-play");
  await yourTurn(page);
  await snap(page, p, "decision");
  // Take back.
  const takeBack = page.getByRole("button", { name: "Take back" });
  await expect(takeBack).toBeEnabled();
  await takeBack.click();
  await yourTurn(page);
  // The game state sheet.
  await page.getByRole("button", { name: "Game state" }).click();
  await expect(page.locator(".goa-sheet")).toBeVisible();
  await snap(page, p, "state-now");
  await page.getByRole("radio", { name: "Turns" }).click();
  await snap(page, p, "state-turns");
  await page.keyboard.press("Escape");
  // The panel hidden.
  await page.getByRole("button", { name: /Hide the panel/ }).click();
  await snap(page, p, "panel-hidden");
  await page.getByRole("button", { name: "Show" }).click();
  // Reload: the game resumes from the save.
  await page.reload();
  await yourTurn(page);
  await expect(page.locator(".goa-map-svg")).toBeVisible();
  errors();
});
