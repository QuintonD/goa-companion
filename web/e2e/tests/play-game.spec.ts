import { expect, type Page, test } from "@playwright/test";
import { mkdirSync } from "node:fs";
import { join } from "node:path";
import { open, watchErrors } from "./helpers";

// Play against the computer (W4): New game → the table → card play through the card focus viewer,
// decisions of every kind the game reaches, take back, the game state, reload resumes. Review
// screenshots go to D:\.goa-qa\web\shots-w4 (<project>-game-<what>-<variant>.png).

const shots = process.env.GOA_W4_SHOTS ?? (process.platform === "win32" ? "D:/.goa-qa/web/shots-w4" : "test-results/shots-w4");
mkdirSync(shots, { recursive: true });

async function snap(page: Page, project: string, name: string) {
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(450);
  await page.screenshot({ path: join(shots, `${project}-game-${name}.png`) });
}

/** New game: Brogan + Arien (you, the whole Red team) against Wasp + Tigerclaw on Easy, Coach off. */
async function newGame(page: Page, coach = false) {
  await open(page, "#/play/new");
  await expect(page.locator(".goa-hero-grid")).toBeVisible({ timeout: 30_000 });
  for (const name of ["Brogan", "Wasp", "Arien", "Tigerclaw"]) {
    await page.locator(".goa-hero-grid").getByRole("button", { name, exact: true }).click();
  }
  await page.getByRole("button", { name: "Next" }).click();
  await page.getByRole("radio", { name: /Easy/ }).first().click();
  if (!coach) await page.locator(".goa-switch-row", { hasText: "A gentle nudge" }).locator("input").uncheck();
  await page.getByRole("button", { name: "Flip the Tie Breaker coin" }).click();
  await page.getByRole("button", { name: "Start the game" }).click();
  await expect(page.locator(".goa-map-svg")).toBeVisible();
  // Fast pace, so the computer's moves don't hold the test up.
  await page.getByRole("button", { name: "More options" }).click();
  await page.getByRole("radio", { name: "Fast" }).click();
  await page.keyboard.press("Escape");
}

let lastDecision: string | null = null;

/** Waits until the panel asks the person a new question (or the game is over). */
async function yourTurn(page: Page) {
  await expect
    .poll(async () => {
      if (await page.locator(".goa-game-over").count()) return "over";
      const id = await page.locator(".goa-decision").first().getAttribute("data-decision", { timeout: 1000 }).catch(() => null);
      const busy = await page.locator(".goa-decision button:disabled").count();
      return id !== null && id !== lastDecision && busy === 0 ? "ready" : "waiting";
    }, { timeout: 90_000, intervals: [200] })
    .not.toBe("waiting");
  lastDecision = await page.locator(".goa-decision").first().getAttribute("data-decision").catch(() => null);
}

/** Lets the next [yourTurn] accept the question on screen again (after a take back or a reload). */
function sameQuestionAgain() {
  lastDecision = null;
}

/** What the person is asked now, by the panel's look. */
async function kind(page: Page): Promise<string> {
  if (await page.locator(".goa-game-over").count()) return "over";
  if (await page.locator(".goa-attack").count()) return "defend";
  if (await page.locator(".goa-offered-card").count()) return "cards";
  if (await page.locator(".goa-action-chip").count()) return "action";
  if (await page.locator(".goa-decision .goa-tap-hint").count()) return "board";
  return "buttons";
}

/** Answers the question in front of the person, the simplest way a player would. */
async function answer(page: Page, k: string) {
  const decision = page.locator(".goa-decision");
  if (k === "defend") {
    await page.locator(".goa-decline").first().click();
    await page.locator(".goa-confirm").click();
  } else if (k === "cards") {
    await page.locator(".goa-offered-card:not(.dimmed)").first().click();
    await expect(page.locator(".goa-focus")).toBeVisible();
    await page.locator(".goa-focus-confirm .goa-button").click();
  } else if (k === "action") {
    await decision.locator(".goa-action-chip:not(:disabled)").first().click();
  } else if (k === "board") {
    const chip = decision.locator(".goa-option-chip");
    if (await chip.count()) {
      await chip.first().click();
    } else {
      await page.locator(".goa-map .goa-visually-hidden button", { hasText: /^Choose/ }).first().dispatchEvent("click");
    }
    await page.locator(".goa-confirm").click();
  } else {
    await decision.locator(".goa-choice, .goa-number, .goa-button.outlined").first().click();
  }
}

test("play: a game through the card viewer, every kind of question, take back, game state, reload", async ({ page }, info) => {
  test.setTimeout(420_000);
  const errors = watchErrors(page);
  const p = info.project.name;
  sameQuestionAgain();
  await page.addInitScript(() => localStorage.setItem("goa.theme", "dark"));
  await open(page, "#/play/new");
  await expect(page.locator(".goa-hero-grid")).toBeVisible({ timeout: 30_000 });
  await snap(page, p, "new-lineup-dark");
  await newGame(page);
  await yourTurn(page);
  await snap(page, p, "play-card-dark");
  // Card play: the first tap zooms the card, a second tap on it answers.
  await page.locator(".goa-offered-card").first().click();
  await expect(page.locator(".goa-focus")).toBeVisible();
  await snap(page, p, "focus-dark");
  // Swipe to the next card and back (the strip scrolls), then answer with a tap on the card.
  await page.getByRole("button", { name: "Next card" }).click();
  await page.waitForTimeout(400);
  await page.getByRole("button", { name: "Previous card" }).click();
  await page.waitForTimeout(400);
  await page.locator(".goa-focus-slide").first().locator("button").click();
  await expect(page.locator(".goa-focus")).toHaveCount(0);
  await yourTurn(page);
  // Take back the card just played.
  const takeBack = page.getByRole("button", { name: "Take back" });
  await expect(takeBack).toBeEnabled();
  await takeBack.click();
  sameQuestionAgain();
  await yourTurn(page);

  const seen = new Set<string>();
  for (let step = 0; step < 90; step++) {
    const k = await kind(page);
    if (k === "over") break;
    if (!seen.has(k)) {
      seen.add(k);
      await snap(page, p, `q-${k}-dark`);
      if (k === "defend") {
        await page.locator(".goa-offered-card").first().click();
        await snap(page, p, "focus-defend-dark");
        await page.keyboard.press("Escape");
      }
    }
    if (!seen.has("track") && (await page.locator(".goa-turn-track").count())) {
      seen.add("track");
      await snap(page, p, "turn-track-dark");
      await page.getByRole("button", { name: "Game state" }).click();
      await expect(page.locator(".goa-sheet")).toBeVisible();
      await snap(page, p, "state-now-dark");
      await page.getByRole("radio", { name: "Turns" }).click();
      await page.waitForTimeout(600);
      await snap(page, p, "state-turns-dark");
      const board = page.locator(".goa-review-turn").getByRole("button", { name: "Board" }).first();
      if (await board.count()) {
        await board.click();
        await page.waitForTimeout(800);
        await snap(page, p, "state-rewind-dark");
      }
      await page.keyboard.press("Escape");
      await page.getByRole("button", { name: /Hide the panel/ }).click();
      await snap(page, p, "panel-hidden-dark");
      await page.locator(".goa-play-show").click();
    }
    if (seen.has("action") && seen.has("board") && seen.has("track") && seen.has("defend")) break;
    await answer(page, k);
    await yourTurn(page);
  }
  // A hero's sheet: tap a hero chip or slot.
  await page.locator(".goa-hero-chip, .goa-track-hero").first().click();
  await expect(page.locator(".goa-sheet")).toBeVisible();
  await snap(page, p, "hero-sheet-dark");
  await page.keyboard.press("Escape");
  // Reload: the game resumes from the save.
  await page.reload();
  sameQuestionAgain();
  await yourTurn(page);
  await expect(page.locator(".goa-map-svg")).toBeVisible();
  errors();
});

const variants: { name: string; viewport?: { width: number; height: number }; theme: string; text?: string }[] = [
  { name: "light", theme: "light" },
  { name: "se-dark", viewport: { width: 375, height: 667 }, theme: "dark" },
  { name: "landscape-light", viewport: { width: 852, height: 393 }, theme: "light" },
  { name: "text200-dark", theme: "dark", text: "200%" },
];

for (const v of variants) {
  test(`play screens: ${v.name}`, async ({ page }, info) => {
    test.setTimeout(240_000);
    const errors = watchErrors(page);
    const p = info.project.name;
    sameQuestionAgain();
    if (v.viewport) await page.setViewportSize(v.viewport);
    await page.addInitScript((theme) => localStorage.setItem("goa.theme", theme), v.theme);
    const big = async () => {
      if (v.text) await page.evaluate((size) => (document.documentElement.style.fontSize = size), v.text);
    };
    await open(page, "#/play/new");
    await big();
    await expect(page.locator(".goa-hero-grid")).toBeVisible({ timeout: 30_000 });
    await snap(page, p, `new-lineup-${v.name}`);
    await newGame(page);
    await big();
    await yourTurn(page);
    await snap(page, p, `play-card-${v.name}`);
    await page.locator(".goa-offered-card").first().click();
    await snap(page, p, `focus-${v.name}`);
    await page.locator(".goa-focus-confirm .goa-button").click();
    await yourTurn(page);
    for (let step = 0; step < 30; step++) {
      const k = await kind(page);
      if (k === "over") break;
      if (await page.locator(".goa-turn-track").count()) {
        await snap(page, p, `turn-track-${v.name}`);
        break;
      }
      await answer(page, k);
      await yourTurn(page);
    }
    await page.getByRole("button", { name: "Game state" }).click();
    await snap(page, p, `state-now-${v.name}`);
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow, "page scrolls sideways").toBeLessThanOrEqual(0);
    errors();
  });
}
