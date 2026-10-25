import { test } from "@playwright/test";
import { expectNoHorizontalOverflow, open, shot } from "./helpers";

// Review screenshots (D:\.goa-qa\web\shots) at the sizes and settings players use; each also checks
// that nothing scrolls sideways. Look at them: they are the shell's visual check.
const screens: [name: string, hash: string][] = [
  ["heroes", "#/heroes"],
  ["hero", "#/heroes/arien"],
  ["players", "#/players"],
  ["settings", "#/settings"],
  ["about", "#/settings/about"],
  ["notfound", "#/nope"],
];

const variants: { name: string; viewport?: { width: number; height: number }; theme: string; text?: string }[] = [
  { name: "dark", theme: "dark" },
  { name: "light", theme: "light" },
  { name: "se-dark", viewport: { width: 375, height: 667 }, theme: "dark" },
  { name: "landscape-light", viewport: { width: 852, height: 393 }, theme: "light" },
  { name: "text200-dark", theme: "dark", text: "200%" },
];

for (const variant of variants) {
  test(`screens: ${variant.name}`, async ({ page }, info) => {
    if (variant.viewport) await page.setViewportSize(variant.viewport);
    await page.addInitScript((theme) => localStorage.setItem("goa.theme", theme), variant.theme);
    for (const [name, hash] of screens) {
      await open(page, hash);
      if (variant.text) await page.evaluate((size) => (document.documentElement.style.fontSize = size), variant.text);
      await page.evaluate(() => document.fonts.ready);
      await page.waitForTimeout(600); // icon masks and images settle
      await shot(page, info, `${name}-${variant.name}`);
      await expectNoHorizontalOverflow(page);
    }
  });
}

test("screens: sheet and dialog", async ({ page }, info) => {
  await open(page, "#/settings/about");
  await page.getByRole("button", { name: /^Cinzel/ }).click();
  await page.waitForTimeout(400);
  await shot(page, info, "sheet-licence-dark");
});
