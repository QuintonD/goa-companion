import { expect, test, type Page } from "@playwright/test";
import { join } from "node:path";
import { expectNoHorizontalOverflow, open, shot, shotsDir, title, watchErrors } from "./helpers";

// The hero catalog on the web (W1, ADR-043): Heroes tab, hero page, card viewer. Review screenshots
// go to GOA_SHOTS_DIR (D:\.goa-qa\web\shots-w1 for this stream); each also checks nothing scrolls
// sideways. The numbers and words are the phone's (core:domain catalog), so the checks here are
// about what a player does on an iPhone: search, filter, open a hero, swipe the cards, zoom.

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
  // Lazy images in view finish loading.
  await page.waitForFunction(() => [...document.images].filter((i) => i.loading !== "lazy" || i.getBoundingClientRect().top < innerHeight).every((i) => i.complete));
  await page.waitForTimeout(400);
}

for (const variant of variants) {
  test(`catalog screens: ${variant.name}`, async ({ page }, info) => {
    const check = watchErrors(page);
    if (variant.viewport) await page.setViewportSize(variant.viewport);
    await page.addInitScript((theme) => localStorage.setItem("goa.theme", theme), variant.theme);
    const screens: [string, string][] = [
      ["w1-heroes", "#/heroes"],
      ["w1-hero-arien", "#/heroes/arien"],
      ["w1-card-arien", "#/heroes/arien/cards/arien-blue-2b"],
      ["w1-hero-gydion", "#/heroes/gydion"],
    ];
    for (const [name, hash] of screens) {
      await open(page, hash);
      await settle(page, variant.text);
      await shot(page, info, `${name}-${variant.name}`);
      await expectNoHorizontalOverflow(page);
    }
    check();
  });
}

// Whole hero pages, top to bottom: the deck grid, the Handicap card, Gydion's spellbook, rulings.
for (const hero of ["arien", "gydion", "takahide"]) {
  test(`hero page in full: ${hero}`, async ({ page }, info) => {
    const check = watchErrors(page);
    await open(page, `#/heroes/${hero}`);
    // Lazy scans load once scrolled to.
    await page.evaluate(async () => {
      for (let y = 0; y < document.body.scrollHeight; y += 400) {
        window.scrollTo(0, y);
        await new Promise((r) => setTimeout(r, 60));
      }
      window.scrollTo(0, 0);
    });
    await settle(page);
    await page.waitForFunction(() => [...document.images].every((i) => i.complete));
    await page.screenshot({ path: join(shotsDir(), `${info.project.name}-w1-hero-${hero}-full-dark.png`), fullPage: true });
    await expectNoHorizontalOverflow(page);
    check();
  });
}

test("search finds heroes and cards, and a card result opens the card", async ({ page }, info) => {
  const check = watchErrors(page);
  await open(page, "#/heroes");
  await page.getByRole("searchbox", { name: "Search heroes & cards" }).fill("fast travel");
  await expect(page.getByRole("heading", { name: /^Cards/ })).toBeVisible();
  await expect(page.locator(".goa-catalog-summary")).toHaveText(/ of \d+ heroes/);
  await settle(page);
  await shot(page, info, "w1-search-fast-travel-dark");
  await expectNoHorizontalOverflow(page);
  const first = page.locator(".goa-card-result").first();
  const name = (await first.locator(".goa-card-result-name").textContent())!;
  await first.click();
  await expect(title(page)).toHaveText(name);
  // Back keeps the search.
  await page.goBack();
  await expect(page.getByRole("searchbox")).toHaveValue("fast travel");
  check();
});

test("a search with no answer says so and clears", async ({ page }) => {
  await open(page, "#/heroes");
  await page.getByRole("searchbox").fill("zzqx");
  await expect(page.getByRole("heading", { name: "No heroes match" })).toBeVisible();
  await page.getByRole("button", { name: "Clear search" }).last().click();
  await expect(page.locator(".goa-hero-tile").first()).toBeVisible();
});

test("filters and sort", async ({ page }, info) => {
  const check = watchErrors(page);
  await open(page, "#/heroes");
  const before = await page.locator(".goa-hero-tile").count();
  await page.getByRole("button", { name: "Filters" }).click();
  const sheet = page.getByRole("dialog", { name: "Filters" });
  await sheet.getByRole("button", { name: /^Complexity 1/ }).click();
  await sheet.getByRole("button", { name: "Ranged attacks", exact: true }).click();
  await settle(page);
  await shot(page, info, "w1-filters-dark");
  const show = sheet.getByRole("button", { name: /^Show \d+ hero|No heroes match/ });
  const label = (await show.textContent())!;
  await show.click();
  await expect(sheet).toBeHidden();
  const after = await page.locator(".goa-hero-tile").count();
  expect(after).toBeLessThan(before);
  if (after > 0) expect(label).toContain(`${after}`);
  // The applied filters are removable chips.
  await page.getByRole("button", { name: "Remove filter Ranged attacks" }).click();
  await page.getByRole("button", { name: /^Sort: Name/ }).click();
  await page.getByRole("radio", { name: "Complexity" }).click();
  await expect(page.getByRole("button", { name: /^Sort: Complexity/ })).toBeVisible();
  // List view persists (settings).
  await page.getByRole("button", { name: "Show as list" }).click();
  await expect(page.locator(".goa-hero-row").first()).toBeVisible();
  await settle(page);
  await shot(page, info, "w1-heroes-list-dark");
  await page.reload();
  await expect(page.locator(".goa-hero-row").first()).toBeVisible();
  await page.getByRole("button", { name: "Show as grid" }).click();
  check();
});

test("hero page: FAQ opens, Learn the numbers goes to trivia, a card opens the viewer", async ({ page }, info) => {
  const check = watchErrors(page);
  await open(page, "#/heroes/takahide");
  await expect(page.getByRole("heading", { name: "Handicap" })).toBeVisible();
  const faq = page.locator(".goa-faq-question").first();
  if (await faq.count()) {
    await faq.click();
    await expect(faq).toHaveAttribute("aria-expanded", "true");
  }
  await page.getByRole("heading", { name: "Handicap" }).scrollIntoViewIfNeeded();
  await settle(page);
  await shot(page, info, "w1-hero-takahide-handicap-dark");
  await page.getByRole("link", { name: /Learn Takahide's card numbers/ }).click();
  await expect(page).toHaveURL(/#\/trivia\?hero=takahide/);
  await page.goBack();
  await page.locator(".goa-scan").first().click();
  await expect(page.locator(".goa-pager")).toBeVisible();
  check();
});

test("card viewer: next and previous, swipe, text only, double-tap zoom", async ({ page }, info) => {
  const check = watchErrors(page);
  await open(page, "#/heroes/arien/cards/arien-gold");
  const caption = page.locator(".goa-pager-caption");
  await expect(caption).toContainText("1 of");
  await page.getByRole("button", { name: "Next card" }).click();
  await expect(caption).toContainText("2 of");
  // A swipe is a native scroll of the pager: scroll one page left.
  await page.locator(".goa-pager").evaluate((el) => el.scrollBy({ left: el.clientWidth }));
  await expect(caption).toContainText("3 of");
  await page.getByRole("button", { name: "Previous card" }).click();
  await expect(caption).toContainText("2 of");
  const cardTitle = await title(page).textContent();
  await page.getByRole("button", { name: "Show text only" }).click();
  await expect(page.locator(".goa-parchment").first()).toBeVisible();
  await settle(page);
  await shot(page, info, "w1-card-text-dark");
  await page.getByRole("button", { name: "Show card image" }).click();
  await expect(title(page)).toHaveText(cardTitle!);
  if (info.project.use.hasTouch) {
    const img = page.locator(".goa-pager-page:not([aria-hidden]) .goa-zoom img");
    const box = (await img.boundingBox())!;
    await page.touchscreen.tap(box.x + box.width / 2, box.y + box.height / 3);
    await page.touchscreen.tap(box.x + box.width / 2, box.y + box.height / 3);
    await expect(page.locator(".goa-pager.zoomed")).toBeVisible();
    await settle(page);
    await shot(page, info, "w1-card-zoomed-dark");
    await page.touchscreen.tap(box.x + box.width / 2, box.y + box.height / 3);
    await page.touchscreen.tap(box.x + box.width / 2, box.y + box.height / 3);
    await expect(page.locator(".goa-pager.zoomed")).toHaveCount(0);
  }
  check();
});

test("a card whose scan can't load shows the designed card", async ({ page }, info) => {
  await page.route("**/official/cards/arien-red-1.webp", (route) => route.abort());
  await open(page, "#/heroes/arien/cards/arien-red-1");
  await expect(page.locator(".goa-designed-card")).toBeVisible();
  await settle(page);
  await shot(page, info, "w1-card-designed-dark");
  await expectNoHorizontalOverflow(page);
});

test("an unknown hero and card say so", async ({ page }) => {
  await open(page, "#/heroes/nobody");
  await expect(page.getByRole("heading", { name: "Unknown hero" })).toBeVisible();
  await open(page, "#/heroes/arien/cards/nope");
  // An unknown card id opens the hero's first card (as on the phone).
  await expect(page.locator(".goa-pager-caption")).toContainText("1 of");
});
