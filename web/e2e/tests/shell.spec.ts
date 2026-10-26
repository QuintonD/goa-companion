import { expect, test } from "@playwright/test";
import { expectNoHorizontalOverflow, open, shot, title, watchErrors } from "./helpers";

// Every destination of the shell (ADR-043): its title, no console errors, no sideways scrolling.
const destinations: [hash: string, title: string][] = [
  ["#/heroes", "Heroes"],
  ["#/heroes/arien", "Arien"],
  ["#/heroes/arien/cards/arien-gold", "Noble Blade"],
  ["#/rules", "Rules"],
  ["#/rules/movement", "Rules"],
  ["#/trivia", "Card trivia"],
  ["#/boards", "Board"],
  ["#/play", "Play"],
  ["#/puzzles", "Puzzles"],
  ["#/puzzles/p1", "Puzzle"],
  ["#/timer", "Timer"],
  ["#/draft", "Draft"],
  ["#/players", "Players"],
  // Players, Matches and Meta are tabs of one Players screen, as on the phone (W2).
  ["#/matches", "Players"],
  ["#/meta", "Players"],
  ["#/players/new", "New player"],
  ["#/matches/new", "Log match"],
  ["#/ratings", "Ratings"],
  ["#/group", "Group sync"],
  ["#/group/me", "Which player are you?"],
  ["#/settings", "Settings"],
  ["#/settings/about", "About"],
  ["#/no/such/page", "Not found"],
];

test("every destination opens without errors", async ({ page }) => {
  const check = watchErrors(page);
  for (const [hash, name] of destinations) {
    await open(page, hash);
    await expect(title(page)).toHaveText(name);
    await expectNoHorizontalOverflow(page);
  }
  check();
});

test("the tab bar walks the four tabs and each remembers its screen", async ({ page }) => {
  const check = watchErrors(page);
  await open(page, "#/heroes");
  await page.getByRole("link", { name: /^Arien/ }).click();
  await expect(title(page)).toHaveText("Arien");
  const tabs = page.getByRole("navigation", { name: "Main" });
  for (const [tab, name] of [["Timer", "Timer"], ["Draft", "Draft"], ["Players", "Players"]]) {
    await tabs.getByRole("button", { name: tab }).click();
    await expect(title(page)).toHaveText(name);
    await expect(tabs.getByRole("button", { name: tab })).toHaveAttribute("aria-current", "page");
  }
  // Back on Heroes: the hero page it was on; tapping Heroes again goes to the list.
  await tabs.getByRole("button", { name: "Heroes" }).click();
  await expect(title(page)).toHaveText("Arien");
  await tabs.getByRole("button", { name: "Heroes" }).click();
  await expect(title(page)).toHaveText("Heroes");
  await expect(page).toHaveURL(/#\/heroes$/);
  check();
});

test("a deep link survives reload, and Back goes up to its list", async ({ page }) => {
  await open(page, "#/heroes/brogan");
  await expect(page.getByRole("heading", { name: "Brogan" })).toBeVisible();
  await page.reload();
  await expect(page.getByRole("heading", { name: "Brogan" })).toBeVisible();
  await expect(page).toHaveURL(/#\/heroes\/brogan$/);
  await page.getByRole("button", { name: "Back" }).click();
  await expect(title(page)).toHaveText("Heroes");
  await expect(page).toHaveURL(/#\/heroes$/);
});

test("browser Back and Forward walk the app's history", async ({ page }) => {
  await open(page, "#/heroes");
  await page.getByRole("link", { name: "Settings" }).click();
  await expect(title(page)).toHaveText("Settings");
  await page.getByRole("link", { name: /^About/ }).click();
  await expect(title(page)).toHaveText("About");
  await page.goBack();
  await expect(title(page)).toHaveText("Settings");
  await page.goBack();
  await expect(title(page)).toHaveText("Heroes");
  await page.goForward();
  await expect(title(page)).toHaveText("Settings");
});

test("a list keeps its scroll position when the person comes back", async ({ page }) => {
  await open(page, "#/heroes");
  const last = page.getByRole("link", { name: /^Widget/ });
  await last.scrollIntoViewIfNeeded();
  const before = await page.evaluate(() => window.scrollY);
  expect(before).toBeGreaterThan(100);
  await last.click();
  await expect(title(page)).toHaveText("Widget");
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeLessThan(5);
  // Up with the app's Back arrow, then the browser's Back from a second visit.
  await page.getByRole("button", { name: "Back" }).click();
  await expect(title(page)).toHaveText("Heroes");
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(before - 5);
  await last.click();
  await page.goBack();
  await expect(title(page)).toHaveText("Heroes");
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(before - 5);
});

test("Settings and Rules stay on the tab that opened them", async ({ page }) => {
  await open(page, "#/players");
  await page.getByRole("link", { name: "Settings" }).click();
  await expect(title(page)).toHaveText("Settings");
  const tabs = page.getByRole("navigation", { name: "Main" });
  await expect(tabs.getByRole("button", { name: "Players" })).toHaveAttribute("aria-current", "page");
  await page.getByRole("button", { name: "Back" }).click();
  await expect(title(page)).toHaveText("Players");
});

test("the theme choice is kept across a reload", async ({ page }) => {
  await open(page, "#/settings");
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await page.getByRole("radio", { name: "Light" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
  await expect(page.getByRole("radio", { name: "Light" })).toHaveAttribute("aria-checked", "true");
});

test("the More sheet opens, and closes with its button and with Escape", async ({ page }, info) => {
  // Large text: Board and Card trivia move from the top bar into More (as on Android).
  await open(page, "#/heroes");
  await page.evaluate(() => (document.documentElement.style.fontSize = "200%"));
  const more = page.getByRole("button", { name: "More options" });
  await expect(more).toBeVisible();
  await expect(page.getByRole("link", { name: "Card trivia" })).toBeHidden();
  await more.click();
  const sheet = page.getByRole("dialog", { name: "More" });
  await expect(sheet).toBeVisible();
  await page.waitForTimeout(400);
  await shot(page, info, "sheet-more-text200-dark");
  await sheet.getByRole("button", { name: "Close" }).click();
  await expect(sheet).toBeHidden();
  await more.click();
  await expect(sheet).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(sheet).toBeHidden();
  await more.click();
  await sheet.getByRole("link", { name: "Card trivia" }).click();
  await expect(title(page)).toHaveText("Card trivia");
});

test("About shows where every official image comes from", async ({ page }) => {
  const check = watchErrors(page);
  await open(page, "#/settings/about");
  await page.getByRole("button", { name: /Official images and their sources/ }).click();
  const sheet = page.getByRole("dialog", { name: "Official images" });
  await expect(sheet.getByText("official/cards/arien-gold.webp")).toBeVisible();
  await expect(sheet.getByRole("link", { name: /github\.com\/dmrediger\/goa2/ }).first()).toBeVisible();
  await sheet.getByRole("button", { name: "Close" }).click();
  await page.getByRole("button", { name: /^Inter/ }).click();
  await expect(page.getByRole("dialog", { name: "Inter licence" }).getByText("SIL OPEN FONT LICENSE")).toBeVisible();
  check();
});
