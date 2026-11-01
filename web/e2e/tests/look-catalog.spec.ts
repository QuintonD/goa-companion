import { expect, test, type Page } from "@playwright/test";
import { expectNoHorizontalOverflow, open, shot, watchErrors } from "./helpers";

// The catalog and rules in the game's own look (ADR-053): review screenshots of every screen in each
// mode, and the things the look must not lose: art that loads, text that stays readable on it, and
// the same names for VoiceOver.

const variants: { name: string; viewport?: { width: number; height: number }; theme: string; text?: string }[] = [
  { name: "dark", theme: "dark" },
  { name: "light", theme: "light" },
  { name: "se-dark", viewport: { width: 375, height: 667 }, theme: "dark" },
  { name: "landscape-dark", viewport: { width: 852, height: 393 }, theme: "dark" },
  { name: "text200-dark", theme: "dark", text: "200%" },
];

async function settle(page: Page, text?: string) {
  if (text) await page.evaluate((size) => (document.documentElement.style.fontSize = size), text);
  await page.evaluate(() => document.fonts.ready);
  await page.waitForFunction(() => [...document.images].filter((i) => i.loading !== "lazy" || i.getBoundingClientRect().top < innerHeight).every((i) => i.complete));
  await page.waitForTimeout(400);
}

const screens: [string, string][] = [
  ["heroes", "#/heroes"],
  ["hero-tigerclaw", "#/heroes/tigerclaw"],
  ["hero-arien", "#/heroes/arien"],
  ["hero-min", "#/heroes/min"],
  ["card", "#/heroes/arien/cards/arien-blue-2b"],
  ["rules", "#/rules"],
  ["rules-push", "#/rules/pushing-the-lane"],
  ["rules-talk", "#/rules/table-talk"],
  ["levels", "#/levels"],
];

for (const variant of variants) {
  test(`look: catalog and rules, ${variant.name}`, async ({ page }, info) => {
    const check = watchErrors(page);
    if (variant.viewport) await page.setViewportSize(variant.viewport);
    await page.addInitScript((theme) => localStorage.setItem("goa.theme", theme), variant.theme);
    for (const [name, hash] of screens) {
      await open(page, hash);
      await settle(page, variant.text);
      await shot(page, info, `look-${name}-${variant.name}`);
      await expectNoHorizontalOverflow(page);
    }
    check();
  });
}

test("a hero tile: the name on the plate, one link read as the whole hero", async ({ page }) => {
  await open(page, "#/heroes");
  const tile = page.getByRole("link", { name: /^Arien, the Tidemaster\. Complexity 1 of 4/ });
  await expect(tile).toBeVisible();
  await expect(tile.locator(".goa-title-plate")).toHaveText("Arien");
  // Every touch target stays big enough on the small plate.
  const box = (await tile.boundingBox())!;
  expect(box.height).toBeGreaterThanOrEqual(44);
});

test("the hero page says Name the Title, and the stats their spoken values", async ({ page }) => {
  await open(page, "#/heroes/tigerclaw");
  await expect(page.locator(".goa-hero-plate")).toHaveText("Tigerclaw the Cutpurse");
  await expect(page.getByRole("img", { name: "Tigerclaw artwork" })).toBeVisible();
  const art = page.locator(".goa-hero-art.header img");
  await expect.poll(() => art.evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth)).toBeGreaterThan(0);
  expect(await art.getAttribute("src")).toContain("official/ui/dashboard-tigerclaw.webp");
  await expect(page.getByRole("img", { name: /^Attack: \d of 8, up to \d conditionally$/ })).toBeVisible();
});

test("a rules page: parchment, the book's boxes, the p.8 diagram multiplied into the page", async ({ page }) => {
  const check = watchErrors(page);
  await open(page, "#/rules/pushing-the-lane");
  const main = page.locator("main.goa-content");
  const parchment = await main.evaluate((el) => getComputedStyle(el).backgroundColor);
  expect(parchment).toBe("rgb(217, 210, 193)");
  await expect(page.locator(".goa-rules-excerpt.box-blue")).toHaveCount(2);
  await expect(page.locator(".goa-rules-diagram")).toHaveCount(2);
  const diagram = page.locator(".goa-rules-diagram").first();
  await diagram.scrollIntoViewIfNeeded();
  await expect.poll(() => diagram.evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth)).toBeGreaterThan(0);
  expect(await diagram.evaluate((img) => getComputedStyle(img).mixBlendMode)).toBe("multiply");
  // Only this topic has the diagram.
  await open(page, "#/rules/table-talk");
  await expect(page.locator(".goa-rules-diagram")).toHaveCount(0);
  await expect(page.locator(".goa-rules-excerpt.box-green").first()).toBeVisible();
  check();
});
