import { expect, test } from "@playwright/test";
import { mkdirSync } from "node:fs";
import { join } from "node:path";
import { expectNoHorizontalOverflow, open, watchErrors } from "./helpers";

// Snorri's runes on a real position (full-round puzzle r17, Snorri on the other side): the tokens below the dashboard's Turn slots, the
// active one named on the chip, on its slot and in the sheet. Review screenshots go to
// D:\.goa-qa\web\shots-runes (GOA_SHOTS_DIR overrides).
function dir(): string {
  const d = process.env.GOA_SHOTS_DIR ?? (process.platform === "win32" ? "D:/.goa-qa/web/shots-runes" : "test-results/shots-runes");
  mkdirSync(d, { recursive: true });
  return d;
}

const variants: { name: string; viewport?: { width: number; height: number } }[] = [
  { name: "phone" },
  { name: "small", viewport: { width: 375, height: 667 } },
];

for (const variant of variants) {
  test(`Snorri's runes lie below his Turn slots, the active one named: ${variant.name}`, async ({ page }, info) => {
    const errors = watchErrors(page);
    if (variant.viewport) await page.setViewportSize(variant.viewport);
    await open(page, "#/puzzles/r17");
    const chip = page.getByRole("button", { name: /^Snorri, .*(Axe|Bird|Anvil|Horn) rune active/ });
    await expect(chip).toBeVisible({ timeout: 30_000 });
    await expect(chip.locator(".goa-chip-marker")).toBeVisible();
    await page.screenshot({ path: join(dir(), `${info.project.name}-${variant.name}-table.png`) });
    await chip.click();

    const tokens = page.locator(".goa-dashboard-marker .token");
    await expect(tokens).toHaveCount(4);
    await expect(page.locator(".goa-dashboard-marker.active")).toHaveCount(1);
    await expect(page.locator(".goa-dashboard-marker.active .label")).toHaveText("ACTIVE");
    await expect
      .poll(() => tokens.evaluateAll((els) => els.every((e) => (e as HTMLImageElement).naturalWidth > 0)))
      .toBe(true);
    // Each token inside the dashboard, centred on its slot.
    const frame = await page.locator(".goa-dashboard").boundingBox();
    for (const turn of [1, 2, 3, 4]) {
      const token = await page.locator(`.goa-dashboard-marker.t${turn} .token`).boundingBox();
      const slot = await page.locator(`.goa-dashboard-slot.t${turn}`).boundingBox();
      expect(token!.y + token!.height).toBeLessThanOrEqual(frame!.y + frame!.height);
      expect(Math.abs(token!.x + token!.width / 2 - (slot!.x + slot!.width / 2))).toBeLessThan(2);
    }
    await expect(page.getByRole("button", { name: /^Turn \d.*rune, active/ })).toHaveCount(1);
    await expect(page.getByText(/rune active this turn$/)).toBeVisible();
    await expect(page.getByText("Rune markers are not removed at the end of round, nor if you are defeated.")).toBeVisible();
    // The raw kind never shows.
    await expect(page.getByText(/marker_/i)).toHaveCount(0);
    await page.screenshot({ path: join(dir(), `${info.project.name}-${variant.name}-sheet.png`) });
    await expectNoHorizontalOverflow(page);
    errors();
  });
}

test("a game as Snorri: the notes before and after Inscribe the Runes, its tokens and picks", async ({ page }, info) => {
  test.setTimeout(300_000);
  const errors = watchErrors(page);
  await open(page, "#/play/new");
  await expect(page.locator(".goa-board-grid")).toBeVisible({ timeout: 30_000 });
  for (const name of ["Snorri", "Wasp"]) {
    await page
      .locator(".goa-board-tile")
      .filter({ has: page.locator(".goa-board-tile-name", { hasText: new RegExp(`^${name}$`) }) })
      .click();
  }
  await page.getByRole("button", { name: "Next", exact: true }).click();
  await page.getByRole("slider", { name: "Computer strength" }).fill("2");
  await page.locator(".goa-switch-row", { hasText: "A gentle nudge" }).locator("input").uncheck();
  await page.getByRole("button", { name: "Flip the Tie Breaker coin" }).click();
  await page.getByRole("button", { name: "Start the game" }).click();
  await expect(page.locator(".goa-map-svg")).toBeVisible();
  await page.getByRole("button", { name: "More options" }).click();
  await page.getByRole("radio", { name: "Fast" }).click();
  await page.keyboard.press("Escape");

  // Before the silver: a rune card says its lines do nothing yet, and the hand has no rune line.
  const card = (name: string) => page.locator(`.goa-offered-card[aria-label^="${name}"]`);
  const focus = page.locator(".goa-focus");
  await expect(card("Rune Sigils")).toBeVisible({ timeout: 90_000 });
  await expect(page.locator(".goa-decision .goa-marker-line")).toHaveCount(0);
  await card("Rune Sigils").click();
  await expect(focus.getByText("No rune is active yet: this card's rune lines do nothing.").first()).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(focus).toHaveCount(0);

  await card("Inscribe the Runes").click();
  await expect(focus).toBeVisible();
  await page.locator(".goa-focus-confirm .goa-button").click();

  // Its primary action; anything the computer's turn asks first is declined.
  const runeButtons = page.locator(".goa-decision .goa-choice:has(.goa-marker-token)");
  await expect
    .poll(
      async () => {
        const primary = page.locator(".goa-action-chip.primary:not(:disabled)");
        if (await primary.count()) await primary.first().click({ timeout: 5_000 }).catch(() => {});
        const decline = page.locator(".goa-decline");
        if (await decline.count()) {
          await decline.first().click({ timeout: 5_000 }).catch(() => {});
          await page.locator(".goa-confirm").click({ timeout: 5_000 }).catch(() => {});
        }
        return runeButtons.count();
      },
      { timeout: 120_000, intervals: [500] },
    )
    .toBe(4);
  const decision = page.locator(".goa-decision");
  await expect(decision.getByText("Which rune goes below turn slot 1?")).toBeVisible();
  await page.screenshot({ path: join(dir(), `${info.project.name}-game-rune-question.png`) });
  const first = await runeButtons.first().innerText();
  await runeButtons.first().click();
  await expect(runeButtons).toHaveCount(3);
  await expect(decision.getByText(new RegExp(`slot 2\\? Turn 1: ${first.trim()}\\.`))).toBeVisible();
  await page.screenshot({ path: join(dir(), `${info.project.name}-game-rune-picks.png`) });
  await runeButtons.first().click();
  await expect(runeButtons).toHaveCount(2);
  await runeButtons.first().click();

  // The next turn's hand: which rune it reads, and a card that has a line for it (Rune Sigils has all four).
  const line = page.locator(".goa-decision .goa-marker-line");
  await expect(line).toHaveText(/\w+ rune active this turn · Next turn: \w+ rune/, { timeout: 90_000 });
  await page.screenshot({ path: join(dir(), `${info.project.name}-game-hand-line.png`) });
  await card("Rune Sigils").click();
  await expect(focus.getByText(/\w+ rune active: this card has a line for it\./).first()).toBeVisible();
  await page.waitForTimeout(600); // the viewer's opening
  await page.screenshot({ path: join(dir(), `${info.project.name}-game-card-note.png`) });
  await page.keyboard.press("Escape");
  await expect(focus).toHaveCount(0);

  await page.getByRole("button", { name: /^Snorri, .*rune active/ }).click();
  await expect(page.locator(".goa-dashboard-marker .token")).toHaveCount(4);
  await expect(page.locator(".goa-dashboard-marker.active")).toHaveCount(1);
  await page.screenshot({ path: join(dir(), `${info.project.name}-game-sheet.png`) });
  errors();
});
