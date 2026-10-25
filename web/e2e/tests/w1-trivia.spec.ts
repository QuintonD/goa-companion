import { expect, test, type Page, type TestInfo } from "@playwright/test";
import { expectNoHorizontalOverflow, open, shot, title, watchErrors } from "./helpers";

// Card trivia (ADR-028, ADR-032) on an iPhone: the home, a whole round played to its summary, the
// sheets, the leaderboard. Screenshots go to GOA_SHOTS_DIR (D:\.goa-qa\web\shots-w1 for W1).

const variants: { name: string; viewport?: { width: number; height: number }; theme: string; text?: string }[] = [
  { name: "dark", theme: "dark" },
  { name: "light", theme: "light" },
  { name: "se-dark", viewport: { width: 375, height: 667 }, theme: "dark" },
  { name: "landscape-light", viewport: { width: 852, height: 393 }, theme: "light" },
  { name: "text200-dark", theme: "dark", text: "200%" },
];

async function settle(page: Page, text?: string) {
  if (text) await page.evaluate((size) => (document.documentElement.style.fontSize = size), text);
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(400);
}

/** Answers the open question with the first answer button (a number, or the first duel card). */
async function answerFirst(page: Page) {
  const button = page.locator("button.goa-trivia-choice.number, button.goa-trivia-duel-tile").first();
  await button.click();
  await expect(page.locator(".goa-trivia-nextbar")).toBeVisible();
}

async function playRound(page: Page, info: TestInfo, variant: string, questions: number) {
  for (let i = 1; i <= questions; i++) {
    await expect(page.locator(".goa-trivia-quiz-count")).toContainText(`Question ${i} of ${questions}`);
    if (i === 1) await shot(page, info, `trivia-question-${variant}`);
    await answerFirst(page);
    if (i === 1) {
      await page.waitForTimeout(700); // the scan loads and scrolls into view
      await shot(page, info, `trivia-answered-${variant}`);
      await expectNoHorizontalOverflow(page);
    }
    await page.locator(".goa-trivia-nextbar button").click();
  }
  await expect(title(page)).toHaveText("Round over");
}

for (const variant of variants) {
  test(`trivia: ${variant.name}`, async ({ page }, info) => {
    const check = watchErrors(page);
    if (variant.viewport) await page.setViewportSize(variant.viewport);
    await page.addInitScript((theme) => localStorage.setItem("goa.theme", theme), variant.theme);

    await open(page, "#/trivia");
    await expect(title(page)).toHaveText("Card trivia");
    await expect(page.getByText(/numbers learned|of .* numbers learned/).first()).toBeVisible();
    await settle(page, variant.text);
    await shot(page, info, `trivia-home-${variant.name}`);
    await expectNoHorizontalOverflow(page);
    // Start and the Challenge are pinned in reach, however far the list is scrolled.
    await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
    await page.waitForTimeout(300);
    await expect(page.getByRole("button", { name: "Start quiz" })).toBeInViewport();
    await shot(page, info, `trivia-home-scrolled-${variant.name}`);

    // A round of 10 questions (the shortest).
    await page.getByRole("button", { name: "10", exact: true }).click();
    await page.getByRole("button", { name: "Start quiz" }).click();
    await expect(title(page)).toHaveText("Card trivia");
    await expect(page.locator(".goa-tabbar")).toHaveCount(0); // full screen
    await settle(page, variant.text);
    await playRound(page, info, variant.name, 10);
    await settle(page, variant.text);
    await shot(page, info, `trivia-summary-${variant.name}`);
    await expectNoHorizontalOverflow(page);
    check();
  });
}

test("trivia: sheets, leaving a round, the leaderboard", async ({ page }, info) => {
  const check = watchErrors(page);
  await open(page, "#/trivia");
  await page.getByRole("button", { name: /^Playing as Guest/ }).click();
  await expect(page.getByRole("dialog", { name: "Who's playing?" })).toBeVisible();
  await page.waitForTimeout(400);
  await shot(page, info, "trivia-playing-as-dark");
  await page.getByRole("button", { name: "Close" }).click();

  await page.getByRole("button", { name: /^Heroes: / }).click();
  const picker = page.getByRole("dialog", { name: "Heroes to quiz" });
  await expect(picker).toBeVisible();
  await picker.getByRole("checkbox", { name: /^Brogan/ }).click();
  await page.waitForTimeout(300);
  await shot(page, info, "trivia-hero-picker-dark");
  await picker.getByRole("button", { name: "Done" }).click();
  await expect(page.getByRole("button", { name: /^Heroes: Brogan, 1 hero/ })).toBeVisible();

  // A hero page's "Learn the numbers" deep link opens the setup on that hero.
  await open(page, "#/trivia?hero=arien");
  await expect(page.getByRole("button", { name: /^Heroes: Arien, 1 hero/ })).toBeVisible();

  // Back after an answer asks first; See results ends the round.
  await page.getByRole("button", { name: "Start quiz" }).click();
  await answerFirst(page);
  await page.getByRole("button", { name: "Back" }).click();
  await expect(page.getByRole("alertdialog", { name: "Leave this round?" })).toBeVisible();
  await shot(page, info, "trivia-leave-dark");
  await page.getByRole("button", { name: "See results" }).click();
  await expect(title(page)).toHaveText("Round over");

  // The setup and progress survive a reload (IndexedDB).
  await open(page, "#/trivia");
  await expect(page.getByRole("button", { name: /^Heroes: Arien, 1 hero/ })).toBeVisible();
  await page.reload();
  await expect(page.getByRole("button", { name: /^Heroes: Arien, 1 hero/ })).toBeVisible();

  await open(page, "#/trivia/leaderboard");
  await expect(title(page)).toHaveText("Leaderboard");
  await page.waitForTimeout(400);
  await shot(page, info, "trivia-leaderboard-dark");
  await expectNoHorizontalOverflow(page);
  check();
});
