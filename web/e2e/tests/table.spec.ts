import { expect, test, type Page } from "@playwright/test";
import { expectNoHorizontalOverflow, open, shot, title, watchErrors } from "./helpers";

// The table tools (W3): the phase timer and the draft, as a player uses them at the table on an
// iPhone. Each flow checks what a player would hit (state surviving a reload, the tab bar hidden on
// full-screen tools, no sideways scrolling) and leaves review screenshots (GOA_SHOTS_DIR).

async function addPlayers(page: Page, names: string[]) {
  const field = page.getByLabel("Quick add a player");
  for (const name of names) {
    await field.fill(name);
    await page.getByRole("button", { name: "Add player" }).click();
    await expect(page.getByRole("checkbox", { name: new RegExp(`^${name}`) })).toBeVisible();
  }
}

async function next(page: Page, label: RegExp) {
  await page.getByRole("button", { name: label }).click();
}

test("the timer runs full screen, survives a reload and ends in the match summary", async ({ page }, info) => {
  const check = watchErrors(page);
  await open(page, "#/timer");
  await expect(page.getByRole("heading", { name: "Phase timer" })).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await shot(page, info, "timer-start");

  await page.getByRole("button", { name: "Start timer" }).click();
  await expect(title(page)).toHaveText("Round 1");
  // A full-screen table tool: no tab bar beside Next.
  await expect(page.getByRole("navigation", { name: "Main" })).toHaveCount(0);
  await expectNoHorizontalOverflow(page);
  await shot(page, info, "timer-running");

  // The clock keeps counting from its timestamps across a reload.
  const before = await page.locator(".goa-table-dial-digits").textContent();
  await page.waitForTimeout(1500);
  await page.reload();
  await expect(title(page)).toHaveText("Round 1");
  const after = await page.locator(".goa-table-dial-digits").textContent();
  expect(after).not.toEqual(before);

  await page.getByRole("button", { name: "Pause" }).click();
  await expect(page.locator(".goa-table-dial-paused")).toBeVisible();
  await shot(page, info, "timer-paused");
  await page.getByRole("button", { name: "Resume" }).click();

  // Next walks the round; the round view shows the whole round.
  await page.locator(".goa-table-next").click();
  await page.waitForTimeout(600); // Next is debounced against double taps
  await page.locator(".goa-table-next").click();
  // On a phone-width bar the switch is in More (ADR-043 R3); on a wide one it is in the bar.
  const direct = page.getByRole("button", { name: "Show the whole round" });
  if (await direct.isVisible()) {
    await direct.click();
  } else {
    await page.getByRole("button", { name: "More options" }).click();
    await page.getByRole("button", { name: "Whole-round view" }).click();
  }
  await expect(page.locator(".goa-table-round-list")).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await shot(page, info, "timer-round-view");

  // Other tabs show the "timer running" bar; tapping it returns.
  await page.goto("./#/heroes");
  await expect(page.locator(".goa-table-running-bar")).toBeVisible();
  await shot(page, info, "timer-running-bar");
  await page.locator(".goa-table-running-bar").click();
  await expect(title(page)).toHaveText("Round 1");

  await page.getByRole("button", { name: "End match" }).first().click();
  await page.getByRole("alertdialog").getByRole("button", { name: "End match" }).click();
  await expect(page.getByRole("heading", { name: "Match over" })).toBeVisible();
  await expect(page.getByRole("navigation", { name: "Main" })).toBeVisible();
  await shot(page, info, "timer-summary");
  check();
});

test("presets and match setup", async ({ page }, info) => {
  const check = watchErrors(page);
  await open(page, "#/timer/presets");
  await expect(page.getByRole("radio", { name: /Table rules/ })).toBeVisible();
  await shot(page, info, "timer-presets");
  await page.getByRole("button", { name: "Edit Table rules" }).click();
  await expect(title(page)).toHaveText("Edit preset");
  await expectNoHorizontalOverflow(page);
  await shot(page, info, "timer-preset-edit");
  await page.getByRole("button", { name: /^Play a card: .*Change$/ }).first().click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await shot(page, info, "timer-duration-picker");
  await page.getByRole("button", { name: "Done" }).click();

  await open(page, "#/timer/setup");
  await expect(page.getByText("Wave counters", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Flip coin" }).click();
  await expect(page.getByText(/is showing\. Place the coin by the board\./)).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await shot(page, info, "timer-match-setup");
  check();
});

test("a draft from players to the finished line-up, surviving a reload", async ({ page }, info) => {
  const check = watchErrors(page);
  await open(page, "#/draft");
  await addPlayers(page, ["Ann", "Ben", "Cleo", "Dev"]);
  await expectNoHorizontalOverflow(page);
  await shot(page, info, "draft-players");
  await next(page, /^Next: Teams$/);
  await expect(page.getByText("Tap a player to switch teams")).toBeVisible();
  await shot(page, info, "draft-teams");
  await next(page, /^Next: Heroes$/);
  await page.getByRole("radio", { name: /^All Pick/ }).click();
  await page.getByRole("radio", { name: "All", exact: true }).click();
  await shot(page, info, "draft-heroes");
  await next(page, /^Next: Tie Breaker$/);
  await page.getByRole("button", { name: "Flip the coin" }).click();
  await shot(page, info, "draft-coin");
  await page.getByRole("button", { name: "Start draft" }).click();

  // The board: a full-screen tool. Pick the first available hero, confirm, and again.
  await expect(page.locator(".goa-draft-banner")).toBeVisible();
  await expect(page.getByRole("navigation", { name: "Main" })).toHaveCount(0);
  await expectNoHorizontalOverflow(page);
  await shot(page, info, "draft-board");
  await pickNext(page);
  await shot(page, info, "draft-board-after-pick");

  // A reload resumes the same step (whose turn, and which step of the sequence).
  const turn = await page.locator(".goa-draft-banner-title").textContent();
  const step = /Step \d+ of \d+/.exec((await page.locator(".goa-draft-banner").textContent()) ?? "")?.[0] ?? "";
  await page.reload();
  await expect(page.locator(".goa-draft-banner-title")).toHaveText(turn ?? "");
  await expect(page.locator(".goa-draft-banner")).toContainText(step);

  while (await page.locator(".goa-draft-banner").count()) await pickNext(page);
  await expect(page.getByRole("heading", { name: "Draft complete" })).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await shot(page, info, "draft-complete");
  check();
});

async function pickNext(page: Page) {
  await page.locator(".goa-draft-tile:not(.inactive):not(.banned):not(.picked)").first().click();
  const dialog = page.getByRole("alertdialog");
  await expect(dialog).toBeVisible();
  await dialog.getByRole("button", { name: /^(◆|●) (Pick|Ban)$/ }).click();
  await expect(dialog).toHaveCount(0);
}

test("draft methods: the list, a duplicate and its editor", async ({ page }, info) => {
  const check = watchErrors(page);
  await open(page, "#/draft/methods");
  await expect(page.getByRole("heading", { name: "Built in" })).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await shot(page, info, "draft-methods");
  await page.getByRole("button", { name: "Duplicate" }).first().click();
  await expect(title(page)).toHaveText("Edit method");
  await expectNoHorizontalOverflow(page);
  await shot(page, info, "draft-method-edit");
  check();
});

// The running timer and the draft board at the sizes and settings players use: iPhone SE, landscape
// (the phone lying on the table), light, and 200 % text (iOS Larger Text). Nothing may scroll
// sideways, and Next must stay on screen.
const variants: { name: string; viewport?: { width: number; height: number }; theme: string; text?: string }[] = [
  { name: "dark", theme: "dark" },
  { name: "light", theme: "light" },
  { name: "se-dark", viewport: { width: 375, height: 667 }, theme: "dark" },
  { name: "landscape-light", viewport: { width: 852, height: 393 }, theme: "light" },
  { name: "text200-dark", theme: "dark", text: "200%" },
];

for (const variant of variants) {
  test(`table tools: ${variant.name}`, async ({ page }, info) => {
    const check = watchErrors(page);
    if (variant.viewport) await page.setViewportSize(variant.viewport);
    await page.addInitScript((theme) => localStorage.setItem("goa.theme", theme), variant.theme);
    const text = async () => {
      if (variant.text) await page.evaluate((size) => (document.documentElement.style.fontSize = size), variant.text);
      await page.evaluate(() => document.fonts.ready);
      await page.waitForTimeout(400);
    };

    await open(page, "#/timer");
    await text();
    await shot(page, info, `timer-start-${variant.name}`);
    await expectNoHorizontalOverflow(page);
    await page.getByRole("button", { name: "Start timer" }).click();
    await expect(title(page)).toHaveText("Round 1");
    await text();
    await expect(page.locator(".goa-table-next")).toBeInViewport();
    await shot(page, info, `timer-running-${variant.name}`);
    await expectNoHorizontalOverflow(page);
    // The Action step with the hero chooser, then the whole-round view.
    await page.locator(".goa-table-next").click();
    await page.waitForTimeout(600);
    await page.locator(".goa-table-next").click();
    await page.getByRole("button", { name: "More options" }).click();
    await page.getByRole("button", { name: "Whole-round view" }).click();
    await text();
    await expect(page.locator(".goa-table-next")).toBeInViewport();
    await shot(page, info, `timer-round-${variant.name}`);
    await expectNoHorizontalOverflow(page);

    await open(page, "#/draft");
    await addPlayers(page, ["Ann", "Ben", "Cleo", "Dev", "Eve"]);
    await next(page, /^Next: Teams$/);
    await text();
    await shot(page, info, `draft-teams-${variant.name}`);
    await expectNoHorizontalOverflow(page);
    await next(page, /^Next: Heroes$/);
    await next(page, /^Next: Tie Breaker$/);
    await page.getByRole("button", { name: "Flip the coin" }).click();
    await page.getByRole("button", { name: "Start draft" }).click();
    await expect(page.locator(".goa-draft-banner")).toBeVisible();
    await text();
    await shot(page, info, `draft-board-${variant.name}`);
    await expectNoHorizontalOverflow(page);
    check();
  });
}
