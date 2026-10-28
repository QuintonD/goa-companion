import { expect, test, type Page } from "@playwright/test";
import { open, watchErrors } from "./helpers";
import { r3Shot } from "./r3-helpers";

// R3 hands-on: the Tie Breaker coin is one coin everywhere. The draft's coin step and the timer's
// Match setup showed ◆ / ● glyphs while the game table and New game show the printed face (Red's
// gear, Blue's star, rulebook p.5; ADR-042). Both now draw the same TieBreakerCoin.

/** The coin shows the printed face of the side it announces, and no glyph stands in for it. */
async function expectPrintedFace(page: Page) {
  const coin = page.locator(".goa-table-coin");
  await expect(coin).toHaveAttribute("aria-label", /showing (Red|Blue)/);
  const label = (await coin.getAttribute("aria-label")) ?? "";
  const side = /showing (Red|Blue)/.exec(label)?.[1]?.toLowerCase();
  expect(side, label).toBeTruthy();
  await expect(coin.locator(`img[src$="official/board/tie-breaker-${side}.webp"]`)).toHaveCount(1);
  // Loaded, not the fallback ring: the face is what the player sees.
  await expect(coin.locator(".goa-tb-coin")).not.toHaveClass(/ring/);
  expect(await coin.innerText()).not.toMatch(/[◆●]/);
}

test("the draft's coin step shows the printed Tie Breaker face", async ({ page }, info) => {
  const check = watchErrors(page);
  await open(page, "#/draft");
  const field = page.getByLabel("Quick add a player");
  for (const name of ["Ann", "Ben", "Cleo", "Dev"]) {
    await field.fill(name);
    await page.getByRole("button", { name: "Add player" }).click();
  }
  await page.getByRole("button", { name: /^Next: Teams$/ }).click();
  await page.getByRole("button", { name: /^Next: Heroes$/ }).click();
  await page.getByRole("button", { name: /^Next: Tie Breaker$/ }).click();
  // Before the flip: a neutral coin.
  await expect(page.locator(".goa-table-coin")).toHaveAttribute("aria-label", "Coin not flipped");
  await page.getByRole("button", { name: "Flip the coin" }).click();
  await expectPrintedFace(page);
  // Saying which side the real coin shows turns the face too.
  await page.getByRole("button", { name: /Blue shows$/ }).click();
  await expect(page.locator(".goa-table-coin")).toHaveAttribute("aria-label", "Tie Breaker coin showing Blue");
  await expectPrintedFace(page);
  await r3Shot(page, info, "coin-draft");
  check();
});

test("Match setup's coin shows the printed Tie Breaker face", async ({ page }, info) => {
  const check = watchErrors(page);
  await open(page, "#/timer/setup");
  await page.getByRole("button", { name: "Flip coin" }).click();
  await expectPrintedFace(page);
  await page.locator(".goa-table-coin-card").scrollIntoViewIfNeeded();
  await r3Shot(page, info, "coin-match-setup");
  check();
});
