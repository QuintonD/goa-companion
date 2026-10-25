import { expect, test, type Page, type TestInfo } from "@playwright/test";
import { mkdirSync } from "node:fs";
import { join } from "node:path";
import { expectNoHorizontalOverflow, open, watchErrors } from "./helpers";

// Puzzles (PRODUCT §10) on the web: the list, a puzzle with its goal, nudges and the solution
// replayed. Review screenshots go to D:\.goa-qa\web\shots-w4 (GOA_SHOTS_DIR overrides).
function dir(): string {
  const d = process.env.GOA_SHOTS_DIR ?? (process.platform === "win32" ? "D:/.goa-qa/web/shots-w4" : "test-results/shots-w4");
  mkdirSync(d, { recursive: true });
  return d;
}

async function shot(page: Page, info: TestInfo, name: string) {
  await page.screenshot({ path: join(dir(), `${info.project.name}-puzzle-${name}.png`) });
}

const variants: { name: string; viewport?: { width: number; height: number }; theme: string; text?: string }[] = [
  { name: "dark", theme: "dark" },
  { name: "light", theme: "light" },
  { name: "se-dark", viewport: { width: 375, height: 667 }, theme: "dark" },
  { name: "landscape-light", viewport: { width: 852, height: 393 }, theme: "light" },
  { name: "text200-dark", theme: "dark", text: "200%" },
];

for (const variant of variants) {
  test(`puzzles: ${variant.name}`, async ({ page }, info) => {
    const errors = watchErrors(page);
    if (variant.viewport) await page.setViewportSize(variant.viewport);
    await page.addInitScript((theme) => localStorage.setItem("goa.theme", theme), variant.theme);
    await open(page, "#/puzzles");
    if (variant.text) await page.evaluate((size) => (document.documentElement.style.fontSize = size), variant.text);
    await expect(page.getByText(/of \d+ solved/i)).toBeVisible({ timeout: 30_000 });
    await shot(page, info, `list-${variant.name}`);
    await expectNoHorizontalOverflow(page);
    await page.getByRole("link", { name: /Start|Continue/ }).click();
    await expect(page.getByRole("button", { name: /^Goal:/ })).toBeVisible({ timeout: 30_000 });
    await page.waitForTimeout(800);
    await shot(page, info, `table-${variant.name}`);
    await expectNoHorizontalOverflow(page);
    errors();
  });
}

test("puzzles: nudges, the panel hidden, the solution replayed", async ({ page }, info) => {
  const errors = watchErrors(page);
  await open(page, "#/puzzles/p01");
  await expect(page.getByRole("button", { name: /^Goal:/ })).toBeVisible({ timeout: 30_000 });
  // Nudge me from the menu; the nudge card says which of how many.
  await page.getByRole("button", { name: "More options" }).click();
  await page.getByRole("button", { name: /Nudge me/ }).click();
  await expect(page.getByText(/Nudge 1 of \d/i)).toBeVisible();
  await shot(page, info, "nudge");
  // The panel hides to the peek bar and comes back.
  await page.getByRole("button", { name: /Hide the panel/ }).click();
  await expect(page.getByRole("button", { name: "Show" })).toBeVisible();
  await shot(page, info, "hidden");
  await page.getByRole("button", { name: "Show" }).click();
  // Show solution: step through to the goal met.
  await page.getByRole("button", { name: "More options" }).click();
  await page.getByRole("button", { name: /Show solution/ }).click();
  await expect(page.getByText(/Solution · step 1 of/i)).toBeVisible({ timeout: 30_000 });
  await shot(page, info, "replay-first");
  const goal = page.getByText(/^Goal met/);
  for (let i = 0; i < 30 && !(await goal.isVisible()); i++) {
    await page.getByRole("button", { name: "Next step" }).last().click();
  }
  await expect(goal).toBeVisible();
  await shot(page, info, "replay-last");
  await page.getByRole("button", { name: "Back to the puzzle" }).click();
  await expect(page.getByText(/Solution · step/i)).toHaveCount(0);
  errors();
});

test("puzzles: progress is remembered across a reload", async ({ page }) => {
  await open(page, "#/puzzles/p01");
  await expect(page.getByRole("button", { name: /^Goal:/ })).toBeVisible({ timeout: 30_000 });
  await page.getByRole("button", { name: "More options" }).click();
  await page.getByRole("button", { name: /Show solution/ }).click();
  await expect(page.getByText(/Solution · step 1 of/i)).toBeVisible({ timeout: 30_000 });
  await open(page, "#/puzzles");
  await expect(page.getByText("Solution seen").first()).toBeVisible({ timeout: 30_000 });
  await page.reload();
  await expect(page.getByText("Solution seen").first()).toBeVisible({ timeout: 30_000 });
});
