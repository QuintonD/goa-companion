import { expect, test, type Page, type TestInfo } from "@playwright/test";
import { mkdirSync } from "node:fs";
import { join } from "node:path";
import { open, watchErrors } from "./helpers";

// The painted map (ADR-053): the picture's layers under the SVG, the light, both board styles, and the
// drawn map whenever the painting isn't there. Review screenshots go to GOA_LOOK_SHOTS_DIR.

function shotsDir(): string {
  const dir = process.env.GOA_LOOK_SHOTS_DIR ?? (process.platform === "win32" ? "D:/.goa-qa/web/shots-look-map" : "test-results/shots-look-map");
  mkdirSync(dir, { recursive: true });
  return dir;
}

async function shot(page: Page, info: TestInfo, name: string) {
  await page.waitForTimeout(500);
  await page.screenshot({ path: join(shotsDir(), `${info.project.name}-${name}.png`) });
}

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

/** Opens a board of [red] v [blue] on [map]. */
async function board(page: Page, map: string, red = "brogan,arien", blue = "wasp,tigerclaw") {
  await open(page, `#/boards/open?red=${red}&blue=${blue}&map=${map}`);
  await hideUpdatePrompt(page);
  await expect(page).toHaveURL(/#\/boards\/[0-9a-f-]{36}$/, { timeout: 30_000 });
  await expect(page.locator(".goa-piece")).not.toHaveCount(0, { timeout: 30_000 });
  await page.waitForTimeout(300);
}

async function painted(page: Page) {
  await expect(page.locator(".goa-map.painted")).toBeVisible({ timeout: 30_000 });
}

/** The button VoiceOver gets for the piece or space whose label matches. */
function node(page: Page, label: RegExp) {
  return page.locator(".goa-map .goa-visually-hidden button").filter({ hasText: label });
}

/** The picture's matrix and the SVG group's translate and scale, as numbers. */
async function transforms(page: Page) {
  return page.evaluate(() => {
    const img = document.querySelector(".goa-map-art.dim") as HTMLElement;
    const lit = document.querySelector(".goa-map-art.lit") as HTMLElement;
    const group = document.querySelector(".goa-map-svg > g:not(.goa-map-defs)") as SVGGElement;
    const parse = (s: string) => s.match(/-?[\d.]+(e-?\d+)?/g)!.map(Number);
    return {
      img: parse(img.style.transform),
      lit: parse(lit.style.transform),
      group: parse(group.getAttribute("transform")!),
    };
  });
}

/** The board style, as Settings would store it (AppSettings in IndexedDB), before the app starts. */
async function setBoardStyle(page: Page, style: string) {
  await page.goto("./");
  await page.evaluate(
    async (value) =>
      new Promise<void>((resolve, reject) => {
        const request = indexedDB.open("goa");
        request.onerror = () => reject(request.error);
        request.onsuccess = () => {
          const db = request.result;
          const name = db.objectStoreNames[0];
          const tx = db.transaction(name, "readwrite");
          const store = tx.objectStore(name);
          const key = "content/settings/app";
          const get = store.get(key);
          get.onsuccess = () => {
            const current = get.result ? JSON.parse(get.result) : {};
            store.put(JSON.stringify({ ...current, boardStyle: value }), key);
          };
          tx.oncomplete = () => {
            db.close();
            resolve();
          };
          tx.onerror = () => reject(tx.error);
        };
      }),
    style,
  );
  // The app read its settings when the page loaded.
  await page.reload();
}

for (const [map, red, blue] of [
  ["forgotten-island", "brogan,arien", "wasp,tigerclaw"],
  ["narrow-passages", "brogan,arien", "wasp,tigerclaw"],
  ["across-the-river", "brogan,arien,xargatha", "wasp,tigerclaw,sabina"],
  ["magma-and-ice", "brogan,arien,xargatha", "wasp,tigerclaw,sabina"],
] as const) {
  test(`painted map: ${map}, the light and the hero's overlays`, async ({ page }, info) => {
    const errors = watchErrors(page);
    await board(page, map, red, blue);
    await painted(page);
    await expect(page.locator(".m-lit")).toHaveCount(1);
    await expect(page.locator(".m-water")).toHaveCount(0);
    await expect(page.locator(".p-figure").first()).toBeVisible();
    await shot(page, info, `${map}-opening`);
    // VoiceOver's buttons are for what is in view: a narrow map's Spawn point may be cropped.
    const arien = node(page, /^Arien, Red/).first();
    if (await arien.count()) {
      await arien.evaluate((b: HTMLElement) => b.click());
      await expect(page.locator(".goa-board-panel h2")).toContainText("Arien");
      await shot(page, info, `${map}-hero`);
    }
    errors();
  });
}

test("painted map: a gesture moves the picture and the SVG by the same transform", async ({ page }, info) => {
  const errors = watchErrors(page);
  await board(page, "forgotten-island");
  await painted(page);
  const before = await transforms(page);
  expect(before.lit).toEqual(before.img);
  const box = (await page.locator(".goa-map").boundingBox())!;
  await page.mouse.move(box.x + box.width / 2, box.y + 200);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width / 2 + 40, box.y + 260, { steps: 6 });
  await page.mouse.up();
  await page.waitForTimeout(100);
  const after = await transforms(page);
  expect(after.lit).toEqual(after.img);
  // Both move by the pan: the matrix's translation and the group's translate, same delta.
  const dxGroup = after.group[0] - before.group[0];
  const dxArt = after.img[4] - before.img[4];
  expect(Math.abs(dxGroup)).toBeGreaterThan(5);
  expect(Math.abs(dxGroup - dxArt)).toBeLessThan(0.1);
  // A pinch-sized change: the wheel zooms both alike.
  // (Dispatched: mobile WebKit has no mouse wheel; the handler is the same pinch path.)
  await page.locator(".goa-map").evaluate((el, at) => {
    el.dispatchEvent(new WheelEvent("wheel", { deltaY: -300, clientX: at.x, clientY: at.y, bubbles: true, cancelable: true }));
  }, { x: box.x + box.width / 2, y: box.y + 300 });
  await page.waitForTimeout(100);
  const zoomed = await transforms(page);
  const scaleGroup = zoomed.group[2] / after.group[2];
  const scaleArt = Math.hypot(zoomed.img[0], zoomed.img[1]) / Math.hypot(after.img[0], after.img[1]);
  expect(scaleGroup).toBeGreaterThan(1.05);
  expect(Math.abs(scaleGroup - scaleArt)).toBeLessThan(0.01);
  await shot(page, info, "zoomed");
  errors();
});

test("painted map: Flip and landscape keep the spaces on the painting", async ({ page }, info) => {
  const errors = watchErrors(page);
  await board(page, "forgotten-island");
  await painted(page);
  const before = await transforms(page);
  await page.getByRole("button", { name: /Flip/ }).first().click();
  await page.waitForTimeout(500);
  const flipped = await transforms(page);
  expect(Math.sign(flipped.img[0] * before.img[0] + flipped.img[1] * before.img[1])).toBe(-1);
  await shot(page, info, "flipped");
  await page.setViewportSize({ width: 852, height: 393 });
  await page.waitForTimeout(500);
  await shot(page, info, "landscape");
  errors();
});

test("painted map: figures on lava and on ice, zoomed in", async ({ page }, info) => {
  await board(page, "magma-and-ice", "brogan,arien,xargatha", "wasp,tigerclaw,sabina");
  await painted(page);
  const box = (await page.locator(".goa-map").boundingBox())!;
  const zoomAt = (x: number) =>
    page.locator(".goa-map").evaluate((el, at) => {
      for (let i = 0; i < 4; i++) {
        el.dispatchEvent(new WheelEvent("wheel", { deltaY: -240, clientX: at.x, clientY: at.y, bubbles: true, cancelable: true }));
      }
    }, { x, y: box.y + box.height * 0.45 });
  await zoomAt(box.x + box.width * 0.18);
  await shot(page, info, "lava-zoomed");
  await page.reload();
  await painted(page);
  await zoomAt(box.x + box.width * 0.82);
  await shot(page, info, "ice-zoomed");
});

test("no painting: the drawn map works as before, the board stays readable", async ({ page }, info) => {
  await page.route("**/official/maps/**", (route) => route.abort());
  await board(page, "forgotten-island");
  await page.waitForTimeout(800);
  await expect(page.locator(".goa-map.painted")).toHaveCount(0);
  await expect(page.locator(".m-water")).not.toHaveCount(0);
  await expect(page.locator(".m-border-battle")).toHaveCount(1);
  await expect(page.locator(".p-glyph").first()).toBeVisible();
  await shot(page, info, "no-painting");
});

test("grid and discs: hex hairlines over the painting and discs for minions", async ({ page }, info) => {
  const errors = watchErrors(page);
  await setBoardStyle(page, "GRID_AND_DISCS");
  await board(page, "forgotten-island");
  await painted(page);
  await expect(page.locator(".m-grid")).toHaveCount(1);
  await expect(page.locator(".p-figure")).toHaveCount(0);
  await expect(page.locator(".p-disc-red").first()).toBeVisible();
  await shot(page, info, "grid-opening");
  await node(page, /^Arien, Red/).first().evaluate((b: HTMLElement) => b.click());
  await expect(page.locator(".goa-board-panel h2")).toContainText("Arien");
  await shot(page, info, "grid-hero");
  errors();
});

test("light theme: what lies on the painting doesn't change", async ({ page }, info) => {
  await page.addInitScript(() => localStorage.setItem("goa.theme", "light"));
  await board(page, "forgotten-island");
  await painted(page);
  await shot(page, info, "light-opening");
});

test("the table: a game's board, a move question in the light", async ({ page }, info) => {
  test.setTimeout(240_000);
  const errors = watchErrors(page);
  await open(page, "#/play/new");
  await expect(page.locator(".goa-board-grid")).toBeVisible({ timeout: 30_000 });
  for (const name of ["Brogan", "Wasp", "Arien", "Tigerclaw"]) {
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
  await hideUpdatePrompt(page);
  await painted(page);
  await page.waitForTimeout(1500);
  await shot(page, info, "table-opening");
  // Play on until a Movement question: the spaces in the light with their numbers, the chosen one bronze.
  const decision = page.locator(".goa-decision");
  for (let i = 0; i < 40 && (await page.locator(".o-chip").count()) === 0; i++) {
    await expect(decision.locator("button:not(:disabled)").first()).toBeVisible({ timeout: 90_000 });
    const movement = decision.locator(".goa-action-chip:not(:disabled)", { hasText: /Movement/ });
    if (await page.locator(".goa-attack").count()) {
      await page.locator(".goa-decline").first().click();
      await page.locator(".goa-confirm").click();
    } else if (await page.locator(".goa-offered-card").count()) {
      await page.locator(".goa-offered-card:not(.dimmed)").first().click();
      await page.locator(".goa-focus-confirm .goa-button").click();
    } else if (await movement.count()) {
      await movement.first().click();
    } else if (await page.locator(".goa-action-chip:not(:disabled)").count()) {
      await page.locator(".goa-action-chip:not(:disabled)").first().click();
    } else if (await decision.locator(".goa-tap-hint").count()) {
      await page.locator(".goa-map .goa-visually-hidden button", { hasText: /^Choose/ }).first().dispatchEvent("click");
      await page.locator(".goa-confirm").click();
    } else {
      await decision.locator(".goa-choice, .goa-number, .goa-button.outlined").first().click();
    }
    await page.waitForTimeout(700);
  }
  await expect(page.locator(".o-chip").first()).toBeAttached();
  await expect(page.locator(".m-lit")).toHaveCount(1);
  await expect(page.locator(".m-zone-line")).toHaveCount(1);
  await shot(page, info, "table-move");
  await page.locator(".goa-map .goa-visually-hidden button", { hasText: /^Choose/ }).first().dispatchEvent("click");
  await expect(page.locator(".o-chip.chosen").first()).toBeAttached();
  await shot(page, info, "table-move-chosen");
  errors();
});

test("puzzles: the board on a painting, nudges and all", async ({ page }, info) => {
  const errors = watchErrors(page);
  await open(page, "#/puzzles");
  await hideUpdatePrompt(page);
  await expect(page.getByText(/of \d+ solved/i)).toBeVisible({ timeout: 30_000 });
  await page.getByRole("link", { name: /Start|Continue/ }).click();
  await expect(page.getByRole("button", { name: /^Goal:/ })).toBeVisible({ timeout: 30_000 });
  await painted(page);
  await shot(page, info, "puzzle");
  errors();
});
