import { expect, test, type Page } from "@playwright/test";
import { expectNoHorizontalOverflow, open, shot, title, watchErrors } from "./helpers";

// W1: rules reference, the level & defeat ladder, Settings and About (ADR-043). Screenshots go to
// GOA_SHOTS_DIR (D:\.goa-qa\web\shots-w1 for the W1 review).

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
  await page.waitForTimeout(500);
}

for (const variant of variants) {
  test(`rules and settings screens: ${variant.name}`, async ({ page }, info) => {
    const check = watchErrors(page);
    if (variant.viewport) await page.setViewportSize(variant.viewport);
    await page.addInitScript((theme) => localStorage.setItem("goa.theme", theme), variant.theme);
    for (const [name, hash] of [
      ["rules", "#/rules"],
      ["topic", "#/rules/turn-sequence"],
      ["topic-table", "#/rules/double-lane"],
      ["settings", "#/settings"],
      ["about", "#/settings/about"],
    ]) {
      await open(page, hash);
      await settle(page, variant.text);
      await shot(page, info, `w1-${name}-${variant.name}`);
      await expectNoHorizontalOverflow(page);
    }
    // The ladder sheet with a level picked, scrolled to show the table.
    await open(page, "#/rules");
    await settle(page, variant.text);
    await page.getByRole("button", { name: /Levels & defeats/ }).click();
    const sheet = page.getByRole("dialog", { name: "Levels & defeats" });
    await sheet.getByRole("radio", { name: "Level 3" }).click();
    await page.waitForTimeout(400);
    await shot(page, info, `w1-ladder-${variant.name}`);
    await sheet.locator(".goa-rules-ladder-table-wrap").scrollIntoViewIfNeeded();
    await shot(page, info, `w1-ladder-table-${variant.name}`);
    await expectNoHorizontalOverflow(page);
    check();
  });
}

test("the ladder gives the picked level's numbers from the p.13 table", async ({ page }) => {
  const check = watchErrors(page);
  await open(page, "#/rules");
  await page.getByRole("button", { name: /Levels & defeats/ }).click();
  const sheet = page.getByRole("dialog", { name: "Levels & defeats" });
  await sheet.getByRole("radio", { name: "Level 1" }).click();
  await expect(sheet.getByRole("radio", { name: "Level 1" })).toHaveAttribute("aria-checked", "true");
  const result = sheet.getByRole("group", { name: /^Level 1 hero defeated/ });
  await expect(result).toBeVisible();
  await expect(result).toHaveAttribute("aria-label", /spends 1 Life counter\./);
  // Picking it again clears it, as on the phone.
  await sheet.getByRole("radio", { name: "Level 1" }).click();
  await expect(result).toBeHidden();
  // A table row's button names the whole row and picks its level.
  await sheet.getByRole("button", { name: /^Level 8\. / }).click();
  await expect(sheet.getByRole("radio", { name: "Level 8" })).toHaveAttribute("aria-checked", "true");
  // The cards that change it, quoted from the catalog.
  await expect(sheet.getByText("Bain · Dead or Alive")).toBeVisible();
  // Opened from Rules: no "All rules" link back to where it came from.
  await expect(sheet.getByRole("button", { name: "All rules" })).toHaveCount(0);
  await page.keyboard.press("Escape");
  await expect(sheet).toBeHidden();
  check();
});

test("the timer's ladder page (#/levels) quotes only the match's heroes' cards and links to all rules", async ({ page }, info) => {
  const check = watchErrors(page);
  await open(page, "#/levels?heroes=bain,arien");
  await expect(title(page)).toHaveText("Levels & defeats");
  await expect(page.getByText("Cards in this match that change this")).toBeVisible();
  await expect(page.getByText("Bain · Dead or Alive")).toBeVisible();
  await page.getByRole("radio", { name: "Level 1" }).click();
  await expect(page.getByRole("group", { name: /^Level 1 hero defeated/ })).toHaveAttribute(
    "aria-label",
    /spends 1 Life counter\./,
  );
  await shot(page, info, "w1-levels-page");
  await expectNoHorizontalOverflow(page);
  await page.getByRole("button", { name: "All rules" }).click();
  await expect(title(page)).toHaveText("Rules");
  check();
});

test("rules search filters topics, keeps the ladder for ladder words, and survives Back", async ({ page }) => {
  const check = watchErrors(page);
  await open(page, "#/rules");
  const search = page.getByRole("searchbox", { name: "Search rules" });
  await search.fill("respawn");
  await expect(page.getByRole("button", { name: /Levels & defeats/ })).toHaveCount(0);
  await expect(page.getByText(/^\d+ of 11 topics$/)).toBeVisible();
  await page.getByRole("link", { name: /^Hero respawn, rulebook pages/ }).click();
  await expect(title(page)).toHaveText("Hero respawn");
  await expect(page.getByText("quoted word for word")).toBeVisible();
  await page.getByRole("button", { name: "Back" }).click();
  await expect(search).toHaveValue("respawn");
  await search.fill("assist coins");
  await expect(page.getByRole("button", { name: /Levels & defeats/ })).toBeVisible();
  await search.fill("zzzz");
  await expect(page.getByText("No rules match")).toBeVisible();
  await page.getByRole("button", { name: "Clear search" }).click();
  await expect(page.getByText("Quick reference")).toBeVisible();
  check();
});

test("an unknown topic says so and offers all rules", async ({ page }) => {
  await open(page, "#/rules/movement");
  await expect(page.getByText("Topic not found")).toBeVisible();
  await page.getByRole("button", { name: "All rules" }).click();
  await expect(title(page)).toHaveText("Rules");
});

test("the collection: untick a product, it is kept across a reload, everything again clears it", async (
  { page },
  info,
) => {
  const check = watchErrors(page);
  await open(page, "#/settings");
  const everything = page.getByRole("switch", { name: /I own every product/ });
  await expect(everything).toBeChecked();
  await everything.click();
  await expect(everything).not.toBeChecked();
  const products = page.locator(".goa-settings-check");
  const count = await products.count();
  expect(count).toBeGreaterThan(1);
  await products.nth(1).click();
  await expect(products.nth(1)).not.toBeChecked();
  await expect(page.getByText(`${count - 1} of ${count} products owned`)).toBeVisible();
  // The last owned product can't be unticked.
  for (let i = 0; i < count; i++) if (i !== 1) await products.nth(i).click();
  await expect(products.nth(count - 1)).toBeChecked();
  await expect(page.locator(".goa-settings-check:checked")).toHaveCount(1);
  await page.reload();
  await expect(page.getByText(`1 of ${count} products owned`)).toBeVisible();
  await page.evaluate(() => document.fonts.ready);
  await shot(page, info, "w1-settings-products-dark");
  await page.getByRole("switch", { name: /I own every product/ }).click();
  await expect(page.getByText("All heroes are available in drafts and filters")).toBeVisible();
  await expect(page.locator(".goa-settings-check:disabled")).toHaveCount(count);
  check();
});

test("settings switches and the running timer choice are saved", async ({ page }) => {
  await open(page, "#/settings");
  const sounds = page.getByRole("switch", { name: /Sounds/ });
  await expect(sounds).toBeChecked();
  await sounds.click();
  await page.getByRole("radio", { name: "Whole round" }).click();
  await page.getByRole("switch", { name: /Puzzle nudges/ }).click();
  await expect(page.getByText("Only when you ask for one from the puzzle's menu")).toBeVisible();
  await page.reload();
  await expect(page.getByRole("switch", { name: /Sounds/ })).not.toBeChecked();
  await expect(page.getByRole("radio", { name: "Whole round" })).toHaveAttribute("aria-checked", "true");
  await expect(page.getByRole("switch", { name: /Puzzle nudges/ })).not.toBeChecked();
});

test("Haptics shows only where the browser can vibrate", async ({ page }, info) => {
  await open(page, "#/settings");
  const canVibrate = await page.evaluate(() => typeof navigator.vibrate === "function");
  await expect(page.getByRole("switch", { name: /Haptics/ })).toHaveCount(canVibrate ? 1 : 0);
  if (info.project.name.startsWith("webkit")) expect(canVibrate).toBe(false);
});

test("About: the fan-companion notice and the sources sheet", async ({ page }, info) => {
  const check = watchErrors(page);
  await open(page, "#/settings/about");
  await expect(page.getByText("A free fan companion")).toBeVisible();
  await expect(page.getByText(/who keep all rights to them/)).toBeVisible();
  await page.getByRole("button", { name: /Official images and their sources/ }).click();
  const sheet = page.getByRole("dialog", { name: "Official images" });
  await expect(sheet.getByText(/free, non-commercial fan companion/)).toBeVisible();
  await expect(sheet.getByText(/private, non-commercial build/)).toHaveCount(0);
  await page.waitForTimeout(400);
  await shot(page, info, "w1-about-sources-dark");
  check();
});
