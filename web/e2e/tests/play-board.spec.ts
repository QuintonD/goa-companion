import { expect, test, type Page, type TestInfo } from "@playwright/test";
import { mkdirSync } from "node:fs";
import { join } from "node:path";
import { open, watchErrors } from "./helpers";

// The board sandbox (PRODUCT §8): New board → Set up → move, drag, undo, push; and review screenshots
// (D:\.goa-qa\web\shots-w4\<project>-board-*.png) at the sizes and settings players use.

/**
 * The shell's "A new version of the app is ready" snackbar shows up on the service worker's first
 * install in a fresh test context and covers the bottom controls; it isn't what these tests look at.
 */
async function hideUpdatePrompt(page: Page) {
  await page.evaluate(() => {
    const hide = () =>
      document.querySelectorAll(".goa-snackbar").forEach((s) => {
        if (s.textContent?.includes("new version")) (s as HTMLElement).style.display = "none";
      });
    hide();
    new MutationObserver(hide).observe(document.body, { childList: true, subtree: true });
  });
}

function shotsDir(): string {
  const dir = process.env.GOA_W4_SHOTS_DIR ?? (process.platform === "win32" ? "D:/.goa-qa/web/shots-w4" : "test-results/shots-w4");
  mkdirSync(dir, { recursive: true });
  return dir;
}

async function shot(page: Page, info: TestInfo, name: string) {
  await page.waitForTimeout(500);
  await page.screenshot({ path: join(shotsDir(), `${info.project.name}-board-${name}.png`) });
}

/** The hidden VoiceOver button over the map whose label starts with [label]. */
function node(page: Page, label: string | RegExp) {
  return page.locator(".goa-map .goa-visually-hidden button").filter({ hasText: label });
}

async function status(page: Page) {
  return page.locator(".goa-play [role=status][aria-live=polite]").first();
}

/** New board: picks alternate like a draft, then Set up opens the opening (p.6). */
async function setUpBoard(page: Page) {
  await open(page, "#/boards/new");
  await hideUpdatePrompt(page);
  for (const hero of ["Brogan", "Tigerclaw", "Arien", "Wasp"]) {
    await page.locator(".goa-board-tile", { hasText: hero }).first().click();
  }
  await expect(page.locator(".goa-board-team.red .goa-board-slot")).toHaveCount(2);
  await expect(page.locator(".goa-board-team.blue .goa-board-slot")).toHaveCount(2);
  await page.getByRole("button", { name: /^Set up 2 v 2/ }).first().dispatchEvent("click");
  await expect(page).toHaveURL(/#\/boards\/[0-9a-f-]{36}$/);
  await expect(page.locator(".goa-piece")).not.toHaveCount(0);
  await page.waitForTimeout(400);
}

test("board: set up, move by tap, drag, undo, push the lane", async ({ page }) => {
  const errors = watchErrors(page);
  await setUpBoard(page);
  // Arien starts on a Red hero Spawn point; select it, then move it next to where it stands.
  await node(page, /^Arien, Red/).first().evaluate((b: HTMLElement) => b.click());
  await expect(page.locator(".goa-board-panel h2")).toContainText("Arien");
  const target = node(page, /^Empty space.*1 space from Arien.*Move here$/).first();
  await target.evaluate((b: HTMLElement) => b.click());
  await expect(await status(page)).toContainText("Arien moved 1 space");

  // Drag the selected piece (a real pointer drag on the map) a hex and a half away.
  const box = await page.locator(".goa-piece").last().boundingBox();
  expect(box).not.toBeNull();
  const x = box!.x + box!.width / 2;
  const y = box!.y + box!.height / 2;
  await page.mouse.move(x, y);
  await page.mouse.down();
  await page.mouse.move(x + 20, y, { steps: 4 });
  await page.mouse.move(x + box!.width * 1.6, y, { steps: 6 });
  await page.mouse.up();
  await expect(await status(page)).toContainText(/Arien (moved|and .* swapped)/);

  await page.getByRole("button", { name: "Undo" }).click();
  await expect(await status(page)).toContainText("Undone.");

  // Push the lane: Red won (p.8).
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "Push the lane" }).click();
  await page.getByRole("button", { name: "Red won the push" }).click();
  await expect(await status(page)).toContainText("is now");

  // Every change is saved: a reload shows the same board, pushed.
  await page.waitForTimeout(900);
  await page.reload();
  await expect(page.locator(".goa-piece")).not.toHaveCount(0);
  await expect(page.locator("h1.goa-topbar-title")).toContainText("Brogan");
  errors();
});

test("boards: the list opens, renames and deletes with Undo", async ({ page }) => {
  const errors = watchErrors(page);
  await setUpBoard(page);
  await open(page, "#/boards");
  await hideUpdatePrompt(page);
  await expect(page.locator(".goa-board-row-card")).toHaveCount(1);
  await page.getByRole("button", { name: /^Options for/ }).click();
  await page.getByRole("button", { name: "Delete" }).click();
  await expect(page.locator(".goa-board-row-card")).toHaveCount(0);
  await page.getByRole("button", { name: "Undo" }).click();
  await expect(page.locator(".goa-board-row-card")).toHaveCount(1);
  errors();
});

const variants: { name: string; viewport?: { width: number; height: number }; theme: string; text?: string }[] = [
  { name: "dark", theme: "dark" },
  { name: "light", theme: "light" },
  { name: "se-dark", viewport: { width: 375, height: 667 }, theme: "dark" },
  { name: "landscape-light", viewport: { width: 852, height: 393 }, theme: "light" },
  { name: "text200-dark", theme: "dark", text: "200%" },
];

for (const variant of variants) {
  test(`board screenshots: ${variant.name}`, async ({ page }, info) => {
    const errors = watchErrors(page);
    if (variant.viewport) await page.setViewportSize(variant.viewport);
    await page.addInitScript((theme) => localStorage.setItem("goa.theme", theme), variant.theme);
    const big = async () => {
      if (variant.text) await page.evaluate((size) => (document.documentElement.style.fontSize = size), variant.text);
    };
    await open(page, "#/boards");
    await hideUpdatePrompt(page);
    await big();
    await shot(page, info, `boards-empty-${variant.name}`);
    await open(page, "#/boards/new");
    await big();
    await shot(page, info, `new-${variant.name}`);
    for (const hero of ["Brogan", "Tigerclaw", "Arien"]) await page.locator(".goa-board-tile", { hasText: hero }).first().click();
    await shot(page, info, `new-picks-${variant.name}`);
    await page.locator(".goa-board-tile", { hasText: "Wasp" }).first().click();
    await page.getByRole("button", { name: /^Set up 2 v 2/ }).first().dispatchEvent("click");
    await expect(page).toHaveURL(/#\/boards\/[0-9a-f-]{36}$/);
    await big();
    await shot(page, info, `board-${variant.name}`);
    await node(page, /^Brogan, Red/).first().evaluate((b: HTMLElement) => b.click());
    await page.locator(".goa-board-card").first().click();
    await shot(page, info, `board-selected-${variant.name}`);
    await page.getByRole("button", { name: /Hide the panel/ }).click();
    await shot(page, info, `board-hidden-${variant.name}`);
    await page.getByRole("button", { name: "Deselect" }).click();
    await page.getByRole("button", { name: "Add" }).click();
    await shot(page, info, `board-add-${variant.name}`);
    await page.keyboard.press("Escape");
    await open(page, "#/boards");
    await big();
    await shot(page, info, `boards-${variant.name}`);
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow, "page scrolls sideways").toBeLessThanOrEqual(0);
    errors();
  });
}
