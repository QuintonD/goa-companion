import { expect, test, type Page } from "@playwright/test";
import { hkdfSync } from "node:crypto";
import { readFileSync, writeFileSync } from "node:fs";
import { blockProduction, seedStorage } from "./group-helpers";
import { open, title } from "./helpers";

// The browser's part of the live interop proof (scripts/interop-live.mjs): a real relay on this
// machine, the phone's real sync code (LiveRelayInteropTest, JVM) before and after. Skipped unless
// the script runs it (GOA_LIVE_RELAY names its config file).
const configFile = process.env.GOA_LIVE_RELAY;
test.skip(!configFile, "run by scripts/interop-live.mjs");
test.use({ serviceWorkers: "block" });

type Live = { url: string; groupId: string; keyHex: string; jvmRatings: string; browserRatings: string };

/** The relay token the app derives (docs/SYNC-SERVER.md), to read the stored revision. */
function token(live: Live): string {
  const key = Buffer.from(live.keyHex, "hex");
  return Buffer.from(hkdfSync("sha256", key, Buffer.from(live.groupId), "goa-relay/token/v1", 32)).toString("base64url");
}

async function storedRev(live: Live): Promise<number> {
  const res = await fetch(`${live.url}/v1/groups/${live.groupId}/document`, { headers: { Authorization: `Bearer ${token(live)}` } });
  return Number((res.headers.get("etag") ?? "0").replace(/"/g, ""));
}

async function shownRatings(page: Page): Promise<Record<string, string>> {
  await open(page, "#/players");
  const rows = page.locator(".goa-w2-leader");
  await expect(rows).toHaveCount(4, { timeout: 30_000 });
  const out: Record<string, string> = {};
  for (const row of await rows.all()) {
    const name = (await row.locator(".goa-row-title").textContent())!.trim();
    out[`live-${name.toLowerCase()}`] = (await row.locator(".goa-w2-elo .t-title-large").textContent())!.trim();
  }
  return out;
}

function parse(line: string): Record<string, string> {
  return Object.fromEntries(line.split(",").map((pair) => pair.split("=")));
}

test("the browser opens the phone's upload, shows the phone's ratings and adds a match", async ({ page }) => {
  test.setTimeout(180_000);
  const live: Live = JSON.parse(readFileSync(configFile!, "utf8"));
  await blockProduction(page);
  await page.goto("./manifest.webmanifest");
  await seedStorage(page, {
    "group/prefs": {
      groupId: live.groupId,
      groupKey: Buffer.from(live.keyHex, "hex").toString("base64"),
      groupName: "Live",
      deviceId: "web-live",
      serverUrl: live.url,
    },
  });
  await page.goto("./#/players");
  await expect(title(page)).toBeVisible();

  // The phone's three matches arrive, and the leaderboard shows exactly the phone's ratings.
  expect(await shownRatings(page)).toEqual(parse(readFileSync(live.jvmRatings, "utf8")));

  // Ann + Bob (Red) lose to Cid + Dee (Blue).
  const before = await storedRev(live);
  await open(page, "#/matches/new");
  const seats = page.locator(".goa-w2-seat-editor");
  for (const [i, pilot] of ["Ann", "Bob", "Cid", "Dee"].entries()) {
    await seats.nth(i).getByRole("button", { name: /^Pilot:/ }).click();
    await page.getByRole("dialog").getByRole("button", { name: new RegExp(`^${pilot}`) }).click();
  }
  await page.getByRole("radio", { name: /^Blue won/ }).click();
  await page.getByRole("button", { name: "Save match" }).click();
  await expect(title(page)).toHaveText("Result saved");
  await expect.poll(() => storedRev(live), { timeout: 60_000 }).toBeGreaterThan(before);
  writeFileSync(live.browserRatings, Object.entries(await shownRatings(page)).map(([k, v]) => `${k}=${v}`).join(","));
});
