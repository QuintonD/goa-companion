import { expect, test, type Page } from "@playwright/test";
import { open, title } from "./helpers";
import { blockProduction, readStorage, seedDocument, seedStorage, watchAppErrors } from "./group-helpers";

// Where the group's match log (W2) and the table tools (W3) hand over to each other, as on the
// phone: Resume and Rematch open Match setup, the timer's Log result opens the match editor
// pre-filled, Draft again fills the draft. No group is joined, and production is blocked anyway.
test.use({ serviceWorkers: "block" });

async function seeded(page: Page) {
  await blockProduction(page);
  await page.goto("./manifest.webmanifest");
  await seedStorage(page, { "group/document": seedDocument() });
}

async function endTimer(page: Page) {
  await expect(title(page)).toHaveText("Round 1");
  await page.getByRole("button", { name: "End match" }).first().click();
  await page.getByRole("alertdialog").getByRole("button", { name: "End match" }).click();
  await expect(page.getByRole("heading", { name: "Match over" })).toBeVisible();
}

test("a pending match resumes in Match setup, then its timer's result is the same match", async ({ page }) => {
  const check = watchAppErrors(page);
  await seeded(page);
  await open(page, "#/matches");
  const pending = page.locator("#match-m-pending");
  await pending.getByRole("button", { name: "Resume: setup & timer" }).click();
  await expect(title(page)).toHaveText("Match setup");
  await expect(page).toHaveURL(/#\/timer\/setup\?match=m-pending$/);

  await page.getByRole("button", { name: "Start timer" }).click();
  await endTimer(page);
  await page.getByRole("button", { name: "Log result" }).click();
  await expect(title(page)).toHaveText("Who won?");
  await expect(page).toHaveURL(/#\/matches\/m-pending\?result=1/);
  check();
});

test("a rematch opens Match setup and Draft again fills the draft with the same players", async ({ page }) => {
  const check = watchAppErrors(page);
  await seeded(page);
  await open(page, "#/matches");
  const latest = page.locator("#match-m-13");
  await latest.getByRole("button", { name: "More options" }).click();
  await page.getByRole("button", { name: "Rematch" }).click();
  await page.getByRole("button", { name: /^Same seats/ }).click();
  await expect(title(page)).toHaveText("Match setup");
  await expect(page).toHaveURL(/#\/timer\/setup\?rematch=m-13&kind=SAME_SEATS$/);
  // Nothing is saved until the timer starts: the log still has its 15 matches.
  const doc = JSON.parse((await readStorage(page, "group/document")) ?? "{}");
  expect(doc.matches).toHaveLength(15);

  await open(page, "#/matches");
  await latest.getByRole("button", { name: "More options" }).click();
  await page.getByRole("button", { name: "Rematch" }).click();
  await page.getByRole("button", { name: /^Draft again/ }).click();
  await expect(title(page)).toHaveText("Draft");
  // m-13: Finn and Ann (Red) against Bob and Cid (Blue), seedDocument; all four are in the draft.
  for (const name of ["Finn", "Ann", "Bob", "Cid"]) {
    await expect(page.getByRole("checkbox", { name: new RegExp(`^${name}`) })).toBeChecked();
  }
  check();
});

test("Log result after a timer without a match opens a new match with its mode and duration", async ({ page }) => {
  const check = watchAppErrors(page);
  await seeded(page);
  await open(page, "#/timer");
  await page.getByRole("button", { name: "Start timer" }).click();
  await endTimer(page);
  await page.getByRole("button", { name: "Log result" }).click();
  await expect(title(page)).toHaveText("Who won?");
  const url = page.url();
  expect(url).toMatch(/#\/matches\/new\?/);
  expect(url).toMatch(/[?&]newId=/);
  expect(url).toMatch(/[?&]duration=\d+/);
  const duration = new URL(url.replace("#/", "")).searchParams.get("duration");
  await expect(page.getByLabel(/Duration/)).toHaveValue(duration!);
  check();
});
