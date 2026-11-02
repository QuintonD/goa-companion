import { expect, test, type Page } from "@playwright/test";
import { seedDocument, seedStorage } from "./group-helpers";
import { open, shot, title, watchErrors } from "./helpers";
import { expectNoSidewaysScroll, textSize } from "./r3-helpers";

// The game's own look (ADR-053), the shell and the screens around the game: the banner's medallion,
// the tab bar, plates in the light theme and the board style setting.
const SE = { width: 375, height: 667 };
const LANDSCAPE = { width: 852, height: 393 };
const MEDALLION = 60;

async function useTheme(page: Page, theme: "dark" | "light") {
  await page.evaluate((t) => document.documentElement.setAttribute("data-theme", t), theme);
}

/** The right edge of the top bar's last control: it must end before the medallion starts. */
async function actionsEnd(page: Page): Promise<{ end: number; width: number }> {
  return page.evaluate(() => {
    const buttons = [...document.querySelectorAll<HTMLElement>(".goa-topbar-actions > *")].filter(
      (el) => getComputedStyle(el).display !== "none",
    );
    const end = Math.max(0, ...buttons.map((el) => el.getBoundingClientRect().right));
    return { end, width: window.innerWidth };
  });
}

test("the board style is a radio choice that survives a reload", async ({ page }, info) => {
  const check = watchErrors(page);
  await open(page, "#/settings");
  const group = page.getByRole("radiogroup", { name: "Board style" });
  await expect(group.getByRole("radio", { name: "Miniatures" })).toHaveAttribute("aria-checked", "true");
  await group.getByRole("radio", { name: "Grid and discs" }).click();
  await expect(group.getByRole("radio", { name: "Grid and discs" })).toHaveAttribute("aria-checked", "true");
  await page.evaluate(() => document.fonts.ready);
  await group.scrollIntoViewIfNeeded();
  await shot(page, info, "look-board-style");

  await page.reload();
  const again = page.getByRole("radiogroup", { name: "Board style" });
  await expect(again.getByRole("radio", { name: "Grid and discs" })).toHaveAttribute("aria-checked", "true");
  // Tall enough to tap, the whole tile being the button.
  const box = await again.getByRole("radio", { name: "Miniatures" }).boundingBox();
  expect(box!.height).toBeGreaterThanOrEqual(44);
  check();
});

test("on a phone the title fits and the actions clear the medallion", async ({ page }, info) => {
  const check = watchErrors(page);
  await open(page, "#/heroes");
  const { end, width } = await actionsEnd(page);
  expect(end).toBeLessThanOrEqual(width - MEDALLION);
  // Five icons beside the medallion cut this to "HE...".
  const [text, room] = await page.locator("h1.goa-topbar-title").evaluate((el) => {
    const range = document.createRange();
    range.selectNodeContents(el);
    const style = getComputedStyle(el);
    const padding = parseFloat(style.paddingLeft) + parseFloat(style.paddingRight);
    return [range.getBoundingClientRect().width, el.getBoundingClientRect().width - padding];
  });
  expect(text).toBeLessThanOrEqual(room + 0.01);
  await shot(page, info, "look-topbar-heroes");

  // The banner is really painted (WebKit too): its ::before carries the art, with the height of the bar.
  const art = await page.evaluate(() => {
    const style = getComputedStyle(document.querySelector(".goa-topbar")!, "::before");
    return { image: style.borderImageSource, width: style.borderRightWidth, height: parseFloat(style.height) };
  });
  expect(art.image).toContain("official/ui/banner.webp");
  expect(art.width).toBe("76px");
  expect(art.height).toBeGreaterThanOrEqual(56);

  // The status bar's height (a home-screen app) doesn't stretch the medallion.
  await page.evaluate(() => document.documentElement.style.setProperty("--safe-top", "59px"));
  const tall = await page.evaluate(() => {
    const bar = document.querySelector<HTMLElement>(".goa-topbar")!;
    return { bar: bar.getBoundingClientRect().height, top: getComputedStyle(bar, "::before").top };
  });
  expect(tall.bar).toBeGreaterThanOrEqual(56 + 59);
  expect(tall.top).toBe("59px");
  await shot(page, info, "look-topbar-safe-top");
  check();
});

test("long titles, large text and landscape don't push anything off the bar", async ({ page }, info) => {
  const check = watchErrors(page);
  await open(page, "#/heroes");
  await seedStorage(page, { "group/document": seedDocument() });
  await page.evaluate(() => {
    document.querySelector(".goa-topbar-title")!.textContent = "A very long title that can never fit on one small phone";
  });
  const { end, width } = await actionsEnd(page);
  expect(end).toBeLessThanOrEqual(width - MEDALLION);
  await shot(page, info, "look-topbar-long-title");

  await page.setViewportSize(SE);
  await open(page, "#/settings");
  await textSize(page, "200%");
  await expectNoSidewaysScroll(page, "settings at 200%");
  await shot(page, info, "look-settings-200");

  await page.setViewportSize(LANDSCAPE);
  await open(page, "#/heroes");
  await expect(title(page)).toHaveText("Heroes");
  await expectNoSidewaysScroll(page, "heroes in landscape");
  await shot(page, info, "look-heroes-landscape");
  check();
});

test("on a plate the light theme keeps the dark tokens", async ({ page }, info) => {
  const check = watchErrors(page);
  await open(page, "#/players");
  await seedStorage(page, { "group/document": seedDocument() });
  await page.reload();
  await useTheme(page, "light");
  const onPlate = await page.evaluate(() => {
    const probe = document.createElement("span");
    probe.className = "goa-pc c-team-red";
    probe.style.setProperty("--pc-d", "#010203");
    probe.style.setProperty("--pc-l", "#040506");
    const card = document.createElement("div");
    card.className = "goa-card";
    card.appendChild(probe);
    document.body.appendChild(card);
    const style = getComputedStyle(probe);
    const out = { team: style.getPropertyValue("--team-red").trim(), pc: style.getPropertyValue("--pc").trim() };
    card.remove();
    return out;
  });
  expect(onPlate.team).toBe("#E76C61");
  expect(onPlate.pc).toBe("#010203");
  await shot(page, info, "look-players-light");
  check();
});

test("the running timer's actions clear the medallion on a phone on its side", async ({ page }, info) => {
  const check = watchErrors(page);
  await page.setViewportSize(LANDSCAPE);
  await open(page, "#/timer/setup");
  await page.getByRole("button", { name: "Flip coin" }).click();
  await page.getByRole("button", { name: "Start timer" }).click();
  await expect(title(page)).toHaveText("Round 1");
  const { end, width } = await actionsEnd(page);
  expect(end).toBeLessThanOrEqual(width - MEDALLION);
  await shot(page, info, "look-timer-landscape");
  check();
});

// A walk over the screens this stream restyled, for looking at; each must render without an error.
for (const theme of ["dark", "light"] as const) {
  test(`the screens render on their plates, ${theme}`, async ({ page }, info) => {
    const check = watchErrors(page);
    await open(page);
    await seedStorage(page, { "group/document": seedDocument() });
    await page.reload();
    const screens: [string, string][] = [
      ["players", "#/players"],
      ["matches", "#/matches"],
      ["meta", "#/meta"],
      ["group", "#/group"],
      ["settings", "#/settings"],
      ["about", "#/settings/about"],
      ["trivia", "#/trivia"],
      ["trivia-leaderboard", "#/trivia/leaderboard"],
    ];
    for (const [name, hash] of screens) {
      await open(page, hash);
      await useTheme(page, theme);
      await page.waitForTimeout(500);
      await page.evaluate(() => document.fonts.ready);
      await shot(page, info, `look-${theme}-${name}`);
      await expectNoSidewaysScroll(page, hash);
    }
    check();
  });
}
