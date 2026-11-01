import { expect, test } from "@playwright/test";
import { seedDocument, seedStorage } from "./group-helpers";
import { open, shot, watchErrors } from "./helpers";
import { expectNoSidewaysScroll, textSize } from "./r3-helpers";

// The look round's review of the screens around the game: what a player would have hit.
const SE = { width: 375, height: 667 };
const LANDSCAPE = { width: 852, height: 393 };

test("the p.8 diagrams carry their printed captions, both of them", async ({ page }, info) => {
  const check = watchErrors(page);
  await open(page, "#/rules/pushing-the-lane");
  const figures = page.locator(".goa-rules-figure");
  await expect(figures).toHaveCount(2);
  await expect(figures.nth(0).locator("img")).toHaveAttribute("src", /push-1/);
  await expect(figures.nth(0)).toContainText("the Blue team must remove the difference");
  await expect(figures.nth(1).locator("img")).toHaveAttribute("src", /push-2/);
  await expect(figures.nth(1)).toContainText("One Red minion will not be respawned.");
  await figures.nth(0).scrollIntoViewIfNeeded();
  await page.waitForTimeout(300);
  await shot(page, info, "fix-rest-push-diagrams");
  check();
});

test("the ladder keeps every number on screen at large text", async ({ page }, info) => {
  const check = watchErrors(page);
  await open(page, "#/levels");
  // Normal text: the table.
  await expect(page.locator(".goa-rules-ladder-table")).toBeVisible();
  await expect(page.locator(".goa-rules-ladder-blocks")).toBeHidden();
  await shot(page, info, "fix-rest-ladder");
  await textSize(page, "200%");
  await expect(page.locator(".goa-rules-ladder-table")).toBeHidden();
  const blocks = page.locator(".goa-rules-ladder-block");
  await expect(blocks).toHaveCount(8);
  await expect(blocks.first()).toContainText("Assist Coins");
  await expectNoSidewaysScroll(page, "ladder at 200%");
  await blocks.first().scrollIntoViewIfNeeded();
  await shot(page, info, "fix-rest-ladder-200");
  check();
});

test("the rules foot is a band upright, a plain line sideways and at large text", async ({ page }, info) => {
  const check = watchErrors(page);
  await open(page, "#/rules/turn-sequence");
  await expect(page.locator(".goa-rules-foot")).toBeVisible();
  await expect(page.locator(".goa-rules-foot-plain")).toBeHidden();
  await shot(page, info, "fix-rest-topic");
  await page.setViewportSize(LANDSCAPE);
  await expect(page.locator(".goa-rules-foot")).toBeHidden();
  await expect(page.locator(".goa-rules-foot-plain")).toBeVisible();
  await shot(page, info, "fix-rest-topic-landscape");
  await page.setViewportSize(SE);
  await textSize(page, "200%");
  await expect(page.locator(".goa-rules-foot")).toBeHidden();
  await expect(page.locator(".goa-rules-foot-plain")).toBeVisible();
  await expectNoSidewaysScroll(page, "topic at 200%");
  await shot(page, info, "fix-rest-topic-200");
  check();
});

test("a long title takes two smaller lines rather than losing its end", async ({ page }, info) => {
  const check = watchErrors(page);
  await page.setViewportSize(SE);
  await open(page, "#/rules/turn-sequence");
  const box = page.locator("h1.goa-topbar-title");
  await expect(box).toHaveClass(/long/);
  const cut = await box.evaluate((el) => el.scrollHeight - el.clientHeight);
  expect(cut, "the title is cut").toBeLessThanOrEqual(1);
  await shot(page, info, "fix-rest-long-title");
  check();
});

test("hero tiles are one height, without the product chip", async ({ page }, info) => {
  const check = watchErrors(page);
  await open(page, "#/heroes");
  const tiles = page.locator(".goa-hero-tile");
  await expect(tiles.first()).toBeVisible();
  await expect(page.locator(".goa-hero-tile-product")).toHaveCount(0);
  // The tiles of a grid line: another line may need one more line of title.
  const line = await tiles.evaluateAll((els) => {
    const top = els[0].getBoundingClientRect().top;
    return els.filter((el) => el.getBoundingClientRect().top === top).map((el) => el.getBoundingClientRect().height);
  });
  expect(line.length).toBeGreaterThan(1);
  expect(Math.max(...line) - Math.min(...line)).toBeLessThanOrEqual(1);
  await shot(page, info, "fix-rest-heroes");
  check();
});

test("one plate per list, bronze for what's selected", async ({ page }, info) => {
  // It reloads several screens, cancelling the catalog's fetches: only those errors are expected.
  const check = watchErrors(page, [/catalog\/.*access control checks/, /Expected start of the object .* but had 'EOF'/]);
  await open(page, "#/rules");
  await expect(page.locator(".goa-rules-list.goa-plate")).toHaveCount(1);
  await expect(page.locator(".goa-rules-list .goa-row.goa-plate")).toHaveCount(0);
  await shot(page, info, "fix-rest-rules-list");

  // Let the catalog finish loading first: a reload cancels its fetches, which WebKit reports as errors.
  await page.waitForLoadState("networkidle");
  await seedStorage(page, { "group/document": seedDocument() });
  await page.goto("./#/matches");
  await page.waitForLoadState("networkidle");
  await page.reload();
  await expect(page.getByRole("button", { name: "Log a match" })).toBeVisible();
  await shot(page, info, "fix-rest-matches");
  await page.getByRole("button", { name: "Log a match" }).click();
  await expect(page.locator(".goa-w2-team-editor.goa-plate")).toHaveCount(2);
  await page.locator(".goa-w2-team-editor").first().scrollIntoViewIfNeeded();
  await shot(page, info, "fix-rest-match-edit");

  await page.goto("./#/timer/presets/new");
  await page.waitForLoadState("networkidle");
  await page.reload();
  const group = page.locator(".goa-table-phase-group").first();
  await expect(group).toBeVisible();
  await group.scrollIntoViewIfNeeded();
  await shot(page, info, "fix-rest-preset");

  await page.goto("./#/trivia/leaderboard");
  await page.waitForLoadState("networkidle");
  await page.reload();
  await page.waitForTimeout(400);
  await shot(page, info, "fix-rest-trivia-board");
  check();
});

test("at large text the floating button is its icon, and About tells the truth about sync", async ({ page }, info) => {
  const check = watchErrors(page);
  await open(page, "#/players");
  await seedStorage(page, { "group/document": seedDocument() });
  await page.reload();
  await expect(page.getByRole("button", { name: "Add player" })).toBeVisible();
  await page.evaluate(() => (document.documentElement.style.fontSize = "200%"));
  await page.goto("./#/matches");
  await expect(page.getByRole("button", { name: "Log a match" })).toHaveClass(/icon-only/);
  await shot(page, info, "fix-rest-matches-200");
  await page.evaluate(() => (document.documentElement.style.fontSize = ""));

  await page.goto("./#/settings/about");
  await expect(page.getByText("sync through the group's sync server")).toBeVisible();
  await expect(page.getByText("never goes online")).toHaveCount(0);
  await shot(page, info, "fix-rest-about");

  await page.goto("./#/settings");
  const tile = page.getByRole("radio", { name: "Grid and discs" });
  await tile.scrollIntoViewIfNeeded();
  const lineHeight = await tile.locator(".goa-board-style-label").evaluate((el) => el.getBoundingClientRect().height);
  expect(lineHeight, "the label wraps").toBeLessThanOrEqual(52);
  await shot(page, info, "fix-rest-settings");
  check();
});
