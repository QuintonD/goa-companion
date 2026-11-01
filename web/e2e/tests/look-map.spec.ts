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
  await expect(page.getByRole("group", { name: /^Goal:/ })).toBeVisible({ timeout: 30_000 });
  await painted(page);
  await shot(page, info, "puzzle");
  errors();
});

/** Where each piece stands now, in px on the page, by its node: a re-inserted node is the same object. */
async function pieceCentres(page: Page) {
  return page.evaluate(() =>
    [...document.querySelectorAll(".goa-piece")].map((g) => {
      const m = new DOMMatrix(getComputedStyle(g).transform);
      return { x: m.e, y: m.f, figure: !!g.querySelector(".p-figure") };
    }),
  );
}

test("a piece moving up the screen past others glides: it isn't re-inserted mid-way", async ({ page }) => {
  const errors = watchErrors(page);
  await board(page, "forgotten-island");
  await painted(page);
  // A selected piece is drawn on top, so it never changes place among the others: an unselected one has to move.
  // A hero from the top of the screen goes to the last empty space listed, in the bottom row in view; deselected,
  // Undo sends it back up past every piece between.
  await node(page, /^Wasp, Blue/).first().evaluate((b: HTMLElement) => b.click());
  await expect(page.locator(".goa-board-panel h2")).toContainText("Wasp");
  await page.waitForTimeout(600);
  await page.locator(".goa-map .goa-visually-hidden button").filter({ hasText: /^Empty space/ }).last().evaluate((b: HTMLElement) => b.click());
  await page.waitForTimeout(600);
  await page.getByRole("button", { name: "Deselect" }).click();
  await page.waitForTimeout(600);
  // A transition that runs its course ends with an event; a node re-inserted mid-way loses it and never does.
  const glide = await page.getByRole("button", { name: "Undo" }).evaluate(
    (button: HTMLElement) =>
      new Promise<{ from: number; to: number; ended: boolean }>((resolve) => {
        const pieces = [...document.querySelectorAll(".goa-piece")] as HTMLElement[];
        const before = pieces.map((g) => g.style.transform);
        const y = (g: Element) => new DOMMatrix(getComputedStyle(g).transform).f;
        const from = pieces.map(y);
        const ended = new Set<Element>();
        pieces.forEach((g) => g.addEventListener("transitionend", () => ended.add(g)));
        button.click();
        setTimeout(() => {
          const moved = pieces.findIndex((g, i) => g.style.transform !== before[i]);
          resolve({ from: from[moved], to: y(pieces[moved]), ended: ended.has(pieces[moved]) });
        }, 900);
      }),
  );
  expect(glide.to).toBeLessThan(glide.from - 2);
  expect(glide.ended).toBe(true);
  // The depth order follows once it has landed: figures nearer the bottom of the screen stand in front.
  const order = await pieceCentres(page);
  for (let i = 1; i < order.length; i++) expect(order[i].y).toBeGreaterThanOrEqual(order[i - 1].y - 0.01);
  errors();
});

test("a tap on a Heavy's head, over an empty space, selects the Heavy", async ({ page }) => {
  await board(page, "forgotten-island");
  await painted(page);
  // Each figure: its base's centre gives the space's on screen, its picture's height tells a Heavy.
  const heads = await page.evaluate(() => {
    const group = document.querySelector(".goa-map-svg > g:not(.goa-map-defs)") as SVGGElement;
    const scale = Number(group.getAttribute("transform")!.match(/scale\(([\d.]+)\)/)![1]);
    const pieces = [...document.querySelectorAll(".goa-piece")].map((g) => {
      const box = g.querySelector(".p-base, .p-coin, .p-token")?.getBoundingClientRect();
      const figure = g.querySelector(".p-figure");
      const y = box ? box.top + box.height / 2 - (figure ? 0.45 * scale : 0) : 0;
      return { x: box ? box.left + box.width / 2 : 0, y, heavy: figure ? Number(figure.getAttribute("height")) > 2 : false };
    });
    return pieces
      .filter((p) => p.heavy)
      .map((p) => ({ x: p.x, y: p.y - 1.25 * scale }))
      .filter((head) => pieces.every((o) => Math.hypot(o.x - head.x, o.y - head.y) > 1.1 * scale));
  });
  expect(heads.length).toBeGreaterThan(0);
  await page.mouse.click(heads[0].x, heads[0].y);
  await expect(page.locator(".goa-board-panel h2")).toContainText(/Heavy/);
});

test("the dimmed picture survives leaving the screen, and a lost canvas comes back", async ({ page }) => {
  await board(page, "forgotten-island");
  await painted(page);
  await page.locator(".goa-map-art.dim canvas").evaluate((c) => ((c as HTMLCanvasElement & { goaMark?: boolean }).goaMark = true));
  const url = page.url();
  await page.goto(url.replace(/#.*$/, "#/boards"));
  await expect(page.locator(".goa-map")).toHaveCount(0);
  await page.evaluate((to) => {
    location.hash = to;
  }, new URL(url).hash);
  await painted(page);
  expect(await page.locator(".goa-map-art.dim canvas").evaluate((c) => (c as HTMLCanvasElement & { goaMark?: boolean }).goaMark)).toBe(true);
  // Lost: the drawn map stands in. Restored: baked again, and the picture is back.
  await page.locator(".goa-map-art.dim canvas").evaluate((c) => c.dispatchEvent(new Event("contextlost")));
  await expect(page.locator(".goa-map.painted")).toHaveCount(0);
  await expect(page.locator(".m-water")).not.toHaveCount(0);
  await page.locator(".goa-map-art.dim canvas").evaluate((c) => {
    (c as HTMLCanvasElement).getContext("2d")!.clearRect(0, 0, 4, 4);
    c.dispatchEvent(new Event("contextrestored"));
  });
  await painted(page);
  const alpha = await page.locator(".goa-map-art.dim canvas").evaluate((c) => (c as HTMLCanvasElement).getContext("2d")!.getImageData(0, 0, 1, 1).data[3]);
  expect(alpha).toBe(255);
});

test.describe("without the service worker", () => {
  test.use({ serviceWorkers: "block" });

  test("a miniature that fails to load is a disc", async ({ page }) => {
    await page.route("**/official/pieces/**", (route) => route.abort());
    await board(page, "forgotten-island");
    await painted(page);
    await page.waitForTimeout(500);
    await expect(page.locator(".p-figure")).toHaveCount(0);
    await expect(page.locator(".p-disc-red").first()).toBeVisible();
    await expect(page.locator(".p-disc-blue").first()).toBeVisible();
  });
});

test("the sea past the picture is one colour: the picture has no edge on the canvas", async ({ page }, info) => {
  await board(page, "across-the-river", "brogan,arien,xargatha", "wasp,tigerclaw,sabina");
  await painted(page);
  const pixels = await page.locator(".goa-map-art.dim canvas").evaluate((c) => {
    const canvas = c as HTMLCanvasElement;
    const context = canvas.getContext("2d")!;
    const at = (x: number, y: number) => [...context.getImageData(x, y, 1, 1).data].slice(0, 3);
    const background = getComputedStyle(canvas.closest(".goa-map")!).backgroundColor.match(/\d+/g)!.map(Number);
    return { background, corners: [at(0, 0), at(canvas.width - 1, 0), at(0, canvas.height - 1), at(canvas.width - 1, canvas.height - 1)] };
  });
  for (const corner of pixels.corners) {
    corner.forEach((v, i) => expect(Math.abs(v - pixels.background[i])).toBeLessThanOrEqual(3));
  }
  await page.getByRole("button", { name: /Fit/ }).first().click();
  await page.waitForTimeout(500);
  await shot(page, info, "across-fit");
});
