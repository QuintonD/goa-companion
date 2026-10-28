import { expect, test, type Page } from "@playwright/test";
import { seedDocument, seedStorage } from "./group-helpers";
import { open, title, watchErrors } from "./helpers";
import { expectNoSidewaysScroll, r3Shot, textSize } from "./r3-helpers";

// R3 hands-on (ADR-043 Addendum R3): what an iPhone player hit at the table, each with the check
// that would have caught it. iPhone SE here is the 375 × 667 one iOS 17 runs on.
const SE = { width: 375, height: 667 };
const LANDSCAPE = { width: 852, height: 393 };

async function addDraftPlayers(page: Page, names: string[]) {
  const field = page.getByLabel("Quick add a player");
  for (const name of names) {
    await field.fill(name);
    await page.getByRole("button", { name: "Add player" }).click();
  }
}

/** The text in [locator] isn't cut with an ellipsis. */
async function expectNotCut(page: Page, selector: string) {
  const sizes = await page.locator(selector).first().evaluate((el) => [el.scrollWidth, el.clientWidth]);
  expect(sizes[0], `${selector} is cut`).toBeLessThanOrEqual(sizes[1]);
}

for (const [name, viewport] of [["iPhone 15", null], ["iPhone SE", SE]] as const) {
  test(`the running timer's bar shows the round, coin tracked (${name})`, async ({ page }, info) => {
    const check = watchErrors(page);
    if (viewport) await page.setViewportSize(viewport);
    await open(page, "#/timer/setup");
    await page.getByRole("button", { name: "Flip coin" }).click();
    await page.getByRole("button", { name: "Start timer" }).click();
    await expect(title(page)).toHaveText("Round 1");
    await expect(page.locator(".goa-table-coin-chip")).toBeVisible();
    // "Roun…" before: the coin chip and four icons left the title no room.
    await expectNotCut(page, ".goa-table-timer-header .goa-topbar-title");
    // The chip's Undo takes room too.
    await page.locator(".goa-table-coin-chip").click();
    await expect(page.getByRole("button", { name: "Undo" })).toBeVisible();
    await expectNotCut(page, ".goa-table-timer-header .goa-topbar-title");
    // What left the bar is in its More menu.
    await page.getByRole("button", { name: "More options" }).click();
    await expect(page.getByRole("button", { name: "Levels & defeats" })).toBeVisible();
    await expect(page.getByRole("button", { name: /Whole-round view|Step-by-step view/ })).toBeVisible();
    await r3Shot(page, info, `timer-bar-${viewport ? "se" : "15"}`);
    check();
  });
}

test("New game's options start at the top, not where the line-up was scrolled", async ({ page }) => {
  const check = watchErrors(page);
  await open(page, "#/play/new");
  await expect(page.locator(".goa-board-grid")).toBeVisible({ timeout: 30_000 });
  for (const hero of ["Brogan", "Wasp", "Arien", "Tigerclaw"]) {
    await page
      .locator(".goa-board-tile")
      .filter({ has: page.locator(".goa-board-tile-name", { hasText: new RegExp(`^${hero}$`) }) })
      .click();
  }
  await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
  await page.getByRole("button", { name: "Next", exact: true }).click();
  await expect(title(page)).toHaveText("Game options");
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(0);
  await expect(page.getByRole("radio", { name: /^All of Red/ })).toBeInViewport();
  check();
});

test("New game's choices look like choices (no class shared with the catalog's sort list)", async ({ page }, info) => {
  const check = watchErrors(page);
  await open(page, "#/play/new");
  await expect(page.locator(".goa-board-grid")).toBeVisible({ timeout: 30_000 });
  for (const hero of ["Brogan", "Wasp", "Arien", "Tigerclaw"]) {
    await page
      .locator(".goa-board-tile")
      .filter({ has: page.locator(".goa-board-tile-name", { hasText: new RegExp(`^${hero}$`) }) })
      .click();
  }
  await page.getByRole("button", { name: "Next", exact: true }).click();
  // Every option of "You play" and "Map" is a framed card, its title over its line, text from the left.
  for (const option of await page.locator(".goa-play-radio-list [role=radio]").all()) {
    const style = await option.evaluate((el) => {
      const cs = getComputedStyle(el);
      return { border: parseFloat(cs.borderTopWidth), direction: cs.flexDirection, align: cs.alignItems };
    });
    expect(style).toEqual({ border: expect.any(Number), direction: "column", align: "flex-start" });
    expect(style.border).toBeGreaterThanOrEqual(1);
  }
  await r3Shot(page, info, "new-game-options");

  // The catalog's Sort sheet: each check mark on its option's row, not under it.
  await open(page, "#/heroes");
  await page.getByRole("button", { name: /^Sort/ }).click();
  const selected = page.getByRole("dialog").locator(".goa-radio-row[aria-checked=true]");
  await expect(selected).toBeVisible();
  const [label, mark] = await selected.evaluate((row) =>
    [...row.children].map((c) => (c as HTMLElement).getBoundingClientRect().top + (c as HTMLElement).getBoundingClientRect().height / 2),
  );
  expect(Math.abs(label - mark), "the check mark sits beside its label").toBeLessThan(4);
  await r3Shot(page, info, "catalog-sort");
  check();
});

test("the app's cards keep their own style (the playing card's rules no longer reach them)", async ({ page }) => {
  const check = watchErrors(page);
  // Every GoaCard got the playing card's `overflow: hidden` and flat surface (same class name).
  await open(page, "#/settings");
  const cards = await page.locator(".goa-card:not(.flush)").all();
  expect(cards.length).toBeGreaterThan(0);
  for (const card of cards) {
    const style = await card.evaluate((el) => [getComputedStyle(el).overflow, getComputedStyle(el).backgroundImage]);
    expect(style[0]).toBe("visible");
    expect(style[1]).toContain("gradient");
  }
  // The playing cards keep theirs.
  await open(page, "#/puzzles/p01");
  await expect(page.getByRole("button", { name: /^Goal:/ })).toBeVisible({ timeout: 30_000 });
  const playing = page.locator(".goa-play-card").first();
  await expect(playing).toBeAttached();
  expect(await playing.evaluate((el) => getComputedStyle(el).overflow)).toBe("hidden");
  check();
});

test("a draft's finished line-up opens at its top", async ({ page }) => {
  const check = watchErrors(page);
  await open(page, "#/draft");
  await addDraftPlayers(page, ["Ann", "Ben", "Cleo", "Dev"]);
  await page.getByRole("button", { name: /^Next: Teams$/ }).click();
  await page.getByRole("button", { name: /^Next: Heroes$/ }).click();
  await page.getByRole("radio", { name: /^All Pick/ }).click();
  await page.getByRole("button", { name: /^Next: Tie Breaker$/ }).click();
  await page.getByRole("button", { name: "Flip the coin" }).click();
  await page.getByRole("button", { name: "Start draft" }).click();
  await expect(page.locator(".goa-draft-banner")).toBeVisible();
  while (await page.locator(".goa-draft-banner").count()) {
    await page.locator(".goa-draft-tile:not(.inactive):not(.banned):not(.picked)").last().click();
    const dialog = page.getByRole("alertdialog");
    await dialog.getByRole("button", { name: /^(◆|●) (Pick|Ban)$/ }).click();
    await expect(dialog).toHaveCount(0);
  }
  await expect(page.getByRole("heading", { name: "Draft complete" })).toBeInViewport();
  check();
});

test("trivia's results open at the score, not where the last question was scrolled", async ({ page }, info) => {
  const check = watchErrors(page);
  await page.setViewportSize(SE);
  await open(page, "#/trivia");
  await page.getByRole("button", { name: "10", exact: true }).click();
  await page.getByRole("button", { name: "Start quiz" }).click();
  for (let i = 0; i < 40; i++) {
    if ((await title(page).textContent())?.includes("Round over")) break;
    const next = page.locator("main button", { hasText: /^Next$|^See results$/ });
    // A right answer moves on by itself: a button counted a moment ago may be gone.
    if (await next.count()) {
      await next.first().click({ timeout: 2000 }).catch(() => {});
      continue;
    }
    await page.locator("main button:not([disabled])").first().click({ timeout: 2000 }).catch(() => {});
    await page.waitForTimeout(150);
  }
  await expect(title(page)).toHaveText("Round over");
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(0);
  await expect(page.getByText(/of 10 right/)).toBeInViewport();
  await r3Shot(page, info, "trivia-results-se");
  check();
});

test("large text on an iPhone SE: nothing scrolls sideways", async ({ page }, info) => {
  test.setTimeout(150_000);
  const check = watchErrors(page);
  await page.setViewportSize(SE);
  await page.goto("./manifest.webmanifest");
  await seedStorage(page, { "group/document": seedDocument() });
  for (const hash of ["#/timer/setup", "#/players", "#/matches", "#/meta", "#/draft", "#/trivia", "#/heroes", "#/rules"]) {
    await open(page, hash);
    await textSize(page, "200%");
    await page.waitForTimeout(400);
    await expectNoSidewaysScroll(page, hash);
  }
  await open(page, "#/timer/setup");
  await textSize(page, "200%");
  await page.getByRole("button", { name: /Untimed/ }).scrollIntoViewIfNeeded();
  await r3Shot(page, info, "se-200-match-setup");
  check();
});

test("on its side: a compact tab bar, and New game's grid isn't hidden behind its Next bar", async ({ page }, info) => {
  const check = watchErrors(page);
  await page.setViewportSize(LANDSCAPE);
  await open(page, "#/play/new");
  await expect(page.locator(".goa-board-grid")).toBeVisible({ timeout: 30_000 });
  const tabbar = await page.locator(".goa-tabbar").boundingBox();
  expect(tabbar!.height, "tab bar height on a phone on its side").toBeLessThanOrEqual(52);
  // The first heroes show without scrolling: the line-up's Next bar (New board's, review R1) sits in
  // the side column here, not over the grid.
  await expect(page.locator(".goa-board-tile").first()).toBeInViewport();
  expect(await page.locator(".goa-board-setup-bar.wide").evaluate((el) => getComputedStyle(el).position)).toBe("static");
  // Tab targets stay 44 pt.
  for (const tab of await page.locator(".goa-tab").all()) {
    expect((await tab.boundingBox())!.height).toBeGreaterThanOrEqual(44);
  }
  await r3Shot(page, info, "landscape-new-game");
  check();
});

/**
 * Focuses each control of the screen coming from below and from above (VoiceOver moving on, Tab,
 * the keyboard's next-field arrows) and returns those that end up under the top bar, the tab bar or
 * a pinned footer.
 */
async function hiddenWhenFocused(page: Page): Promise<string[]> {
  return page.evaluate(async () => {
    const frame = () => new Promise((r) => requestAnimationFrame(r));
    const bars = ".goa-topbar, .goa-tabbar, .goa-table-footer, .goa-step-bar, .goa-trivia-startbar, .goa-draft-banner-slot";
    const out: string[] = [];
    const controls = [...document.querySelectorAll<HTMLElement>("main button, main a[href], main select")]
      .filter((el) => el.getBoundingClientRect().height > 0 && !el.closest(".goa-visually-hidden, [inert]"));
    for (const control of controls) {
      for (const from of [document.documentElement.scrollHeight, 0]) {
        (document.activeElement as HTMLElement | null)?.blur?.();
        window.scrollTo(0, from);
        await frame();
        control.focus();
        await frame();
        const r = control.getBoundingClientRect();
        const x = Math.min(window.innerWidth - 2, Math.max(1, r.left + r.width / 2));
        const covered = [r.top + 2, r.bottom - 2].some((y) => {
          const hit = document.elementFromPoint(x, Math.min(window.innerHeight - 1, Math.max(0, y)));
          return !!hit && !control.contains(hit) && !!hit.closest(bars);
        });
        if (covered) {
          out.push(`${(control.getAttribute("aria-label") ?? control.textContent ?? "").trim().slice(0, 40)} (from ${from ? "below" : "above"})`);
          break;
        }
      }
    }
    return out;
  });
}

test("a focused control is never left under the top bar, the tab bar or a pinned footer", async ({ page }) => {
  test.setTimeout(150_000);
  const check = watchErrors(page);
  await page.setViewportSize(SE);
  for (const hash of ["#/settings", "#/timer/setup", "#/trivia"]) {
    await open(page, hash);
    await page.waitForTimeout(500);
    expect(await hiddenWhenFocused(page), hash).toEqual([]);
  }
  check();
});

test("a puzzle's spaces read apart for VoiceOver, and its panel's Hide is a 44 pt target", async ({ page }) => {
  const check = watchErrors(page);
  await open(page, "#/puzzles/p01");
  await expect(page.getByRole("button", { name: /^Goal:/ })).toBeVisible({ timeout: 30_000 });
  // Hide: a 36 px row with a hit area reaching 44.
  const hide = page.locator(".goa-play-hide");
  const height = (await hide.boundingBox())!.height;
  const extra = await hide.evaluate((el) => {
    const after = getComputedStyle(el, "::after");
    return after.position === "absolute" ? -parseFloat(after.top) - parseFloat(after.bottom) : 0;
  });
  expect(height + extra).toBeGreaterThanOrEqual(44);
  // Brogan's Skill: move 1 space. Three spaces, one space from him in Lane Zone 2 each: VoiceOver
  // read three identical buttons.
  await page.locator(".goa-action-chip", { hasText: "Skill" }).first().click();
  const choices = page.locator(".goa-map .goa-visually-hidden button", { hasText: /^Choose/ });
  await expect.poll(() => choices.count(), { timeout: 30_000 }).toBeGreaterThan(1);
  const labels = await choices.allInnerTexts();
  expect(new Set(labels).size, labels.join(" | ")).toBe(labels.length);
  check();
});
