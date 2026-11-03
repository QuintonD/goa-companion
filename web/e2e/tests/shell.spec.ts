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
  ["#/play/home", "Play"],
  ["#/boards", "Boards"],
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

test("the tab bar walks the five tabs and each remembers its screen", async ({ page }) => {
  const check = watchErrors(page);
  await open(page, "#/heroes");
  await page.getByRole("link", { name: /^Arien/ }).click();
  await expect(title(page)).toHaveText("Arien");
  const tabs = page.getByRole("navigation", { name: "Main" });
  for (const [tab, name] of [["Timer", "Timer"], ["Draft", "Draft"], ["Play", "Play"], ["Players", "Players"]]) {
    await tabs.getByRole("button", { name: tab, exact: true }).click();
    await expect(title(page)).toHaveText(name);
    await expect(tabs.getByRole("button", { name: tab, exact: true })).toHaveAttribute("aria-current", "page");
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

test("Play holds Board and Card trivia, and Back returns to it", async ({ page }, info) => {
  const check = watchErrors(page);
  await open(page, "#/heroes");
  const tabs = page.getByRole("navigation", { name: "Main" });
  // Off the Heroes bar: they live under Play now.
  await expect(page.getByRole("link", { name: "Board", exact: true })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Card trivia" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "More options" })).toBeHidden();
  await tabs.getByRole("button", { name: "Play", exact: true }).click();
  await expect(title(page)).toHaveText("Play");
  await expect(page).toHaveURL(/#\/play\/home$/);
  await expect(page.getByRole("button", { name: "Back" })).toHaveCount(0);
  await shot(page, info, "play-home-dark");
  await page.getByRole("button", { name: /^Board/ }).click();
  await expect(title(page)).toHaveText("Boards");
  await expect(tabs.getByRole("button", { name: "Play", exact: true })).toHaveAttribute("aria-current", "page");
  await page.getByRole("button", { name: "Back" }).click();
  await expect(title(page)).toHaveText("Play");
  await page.getByRole("button", { name: /^Card trivia/ }).click();
  await expect(title(page)).toHaveText("Card trivia");
  await page.getByRole("button", { name: "Back" }).click();
  await expect(title(page)).toHaveText("Play");
  check();
});

test("a hero's Learn the numbers stays on Heroes, and Back returns to the hero", async ({ page }) => {
  await open(page, "#/heroes/arien");
  await page.locator(".goa-learn-numbers").click();
  await expect(title(page)).toHaveText("Card trivia");
  const tabs = page.getByRole("navigation", { name: "Main" });
  await expect(tabs.getByRole("button", { name: "Heroes" })).toHaveAttribute("aria-current", "page");
  await page.getByRole("button", { name: "Back" }).click();
  await expect(title(page)).toHaveText("Arien");
});

test("at the largest text every tab's label shows whole, and the Heroes bar keeps its title", async ({ page }) => {
  await open(page, "#/heroes");
  await page.evaluate(() => (document.documentElement.style.fontSize = "200%"));
  const cut = await page.locator(".goa-tab-label").evaluateAll((els) =>
    els.filter((e) => e.scrollWidth > e.clientWidth).map((e) => e.textContent),
  );
  expect(cut).toEqual([]);
  await expect(page.getByRole("button", { name: "Show as list" })).toBeVisible();
  const [text, room] = await title(page).evaluate((el) => {
    const range = document.createRange();
    range.selectNodeContents(el);
    const style = getComputedStyle(el);
    return [range.getBoundingClientRect().width, el.getBoundingClientRect().width - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight)];
  });
  expect(text).toBeLessThanOrEqual(room + 0.01);
});

test("the More sheet opens, and closes with its button and with Escape", async ({ page }, info) => {
  await open(page, "#/trivia");
  await page.evaluate(() => (document.documentElement.style.fontSize = "200%"));
  const more = page.getByRole("button", { name: "More options" });
  await expect(more).toBeVisible();
  await expect(page.getByRole("link", { name: "Leaderboard" })).toBeHidden();
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
  await sheet.getByRole("link", { name: "Leaderboard" }).click();
  await expect(title(page)).toHaveText("Leaderboard");
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

test("About and Settings link to the maker's Ko-fi in a new tab", async ({ page }) => {
  const check = watchErrors(page);
  for (const hash of ["#/settings/about", "#/settings"]) {
    await open(page, hash);
    const donate = page.getByRole("link", { name: /^Support the app/ });
    await expect(donate).toHaveAttribute("href", "https://ko-fi.com/quintonde");
    await expect(donate).toHaveAttribute("target", "_blank");
    await expect(donate).toHaveAttribute("rel", "noopener");
  }
  check();
});
