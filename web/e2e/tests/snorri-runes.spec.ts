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
