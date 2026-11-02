import { expect, test, type Browser, type Page } from "@playwright/test";
import { expectNoHorizontalOverflow, open, shot, title } from "./helpers";
import { blockProduction, readStorage } from "./group-helpers";
import { clock, createAccount, FakeGameServer, GAME_ID, mapSvg, useServer, watchPolicy } from "./online-helpers";

// Online games on the web (ADR-056): the account, the lobby, the table, the result, the leaderboard, against a game
// server on this machine (online-helpers.ts). Its socket is a real WebSocket, so CORS, the page's policy and the
// browser's own socket are in play. The rules are the recording's: see online-real.spec.ts for the real server.

// A page a service worker controls sends its requests past Playwright's routes in WebKit; these servers are local,
// and one test below runs with the worker on.
test.use({ serviceWorkers: "block" });

let server: FakeGameServer;

/** Console errors fail a test, but for how a browser reports a server that says no or isn't there. */
function watchAppErrors(page: Page): () => void {
  const errors: string[] = [];
  const expected = /Service Worker|.waiting|reading 'waiting'|Failed to load resource|WebSocket connection to|Connection terminated unexpectedly|Fetch API cannot load|access control checks|WebSocket network error|Server returned nothing|Could not connect|ERR_BLOCKED_BY_CLIENT|oldboi/;
  const add = (text: string) => {
    if (!expected.test(text)) errors.push(text);
  };
  page.on("console", (msg) => {
    if (msg.type() === "error") add(`console: ${msg.text()}`);
  });
  page.on("pageerror", (err) => add(`page: ${err.message}`));
  return () => expect(errors, errors.join("; ")).toEqual([]);
}

test.beforeEach(async ({ page }) => {
  server = await new FakeGameServer().start();
  await blockProduction(page);
});

test.afterEach(async () => {
  await server.stop();
});

/** A second person's phone. */
async function secondPhone(browser: Browser, baseURL: string | undefined, browserName: string) {
  const context = await browser.newContext({
    baseURL,
    serviceWorkers: "block",
    hasTouch: true,
    isMobile: browserName !== "webkit" ? true : undefined,
    viewport: { width: 390, height: 844 },
  });
  const page = await context.newPage();
  await blockProduction(page);
  return page;
}

/** A game running: this page plays Wasp (Red), [guest] Arien (Blue); the table is on screen. */
async function atTheTable(page: Page, name = "Ann") {
  const host = server.addAccount(name);
  const guest = server.addAccount("Bob");
  server.seedGame(host, guest);
  await useServer(page, server, host);
  await page.goto(`./#/online/game/${GAME_ID}`);
  await expect(mapSvg(page)).toBeVisible({ timeout: 30_000 });
  return { host, guest };
}

test("the Play tab offers Online, and an account starts with a name and a recovery code", async ({ page }) => {
  const check = watchAppErrors(page);
  await useServer(page, server);
  await open(page, "#/play/home");
  await page.getByRole("button", { name: /^Online/ }).click();
  await expect(title(page)).toHaveText("Online");
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page.getByRole("alert")).toHaveText("Pick a name for the others to see.");

  await page.getByLabel("Your name").fill("Ann");
  await page.getByRole("button", { name: "Create account" }).click();
  const code = page.locator(".goa-online-code");
  await expect(code).toHaveText(/^CODE-/);
  await expect(page.getByText("This code is the only way back")).toBeVisible();
  await expect(page.getByText("your recovery code is how you get back")).toBeVisible();
  await shot(page, test.info(), "online-recovery-code");
  // Nothing else on this screen until it's saved, and no way off it by accident.
  await expect(page.getByRole("button", { name: "Back" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Create room" })).toHaveCount(0);
  // The account isn't saved until the code is: reloading here starts again, with nothing lost.
  expect(await readStorage(page, "online/identity")).toBeUndefined();
  await page.reload();
  await expect(page.getByRole("button", { name: "Create account" })).toBeVisible();

  await createAccount(page, "Ann");
  await expect(page.getByRole("button", { name: "Create room" })).toBeVisible();
  const stored = JSON.parse((await readStorage(page, "online/identity"))!);
  expect(stored.name).toBe("Ann");
  // Across a reload the person is still in; the token went in a header and in no address.
  await page.reload();
  await expect(page.getByRole("button", { name: "Create room" })).toBeVisible();
  expect(server.bearers).toContain(`Bearer ${stored.token}`);
  expect(server.addresses.join(" ")).not.toContain(stored.token);
  check();
});

test("a recovery code brings the account to another device, and has to be saved again", async ({ page }) => {
  const check = watchAppErrors(page);
  const account = server.addAccount("Ann");
  const old = account.recoveryCode;
  await useServer(page, server);
  await open(page, "#/online");
  await page.getByLabel("Recovery code").fill("wrong");
  await page.getByRole("button", { name: "Use this code" }).click();
  await expect(page.getByRole("alert")).toContainText("recovery code doesn't work");

  await page.getByLabel("Recovery code").fill(old);
  await page.getByRole("button", { name: "Use this code" }).click();
  await expect(page.locator(".goa-online-code")).toBeVisible();
  expect(account.recoveryCode).not.toBe(old);
  await expect(page.locator(".goa-online-code")).toHaveText(account.recoveryCode);
  // The name came back from the server, and this device holds it at once: the old code is already burnt.
  expect(JSON.parse((await readStorage(page, "online/identity"))!).token).toBe(account.token);
  await page.getByRole("button", { name: "I've saved it" }).click();
  await expect(page.getByRole("button", { name: /^Ann/ })).toBeVisible();
  check();
});

test("renaming and deleting an account, with a name that is hostile markup", async ({ page }) => {
  const check = watchAppErrors(page);
  const name = `<img src=x onerror=a()>`;
  const account = server.addAccount("Ann");
  await useServer(page, server, account);
  await open(page, "#/online");
  await page.getByRole("button", { name: /^Ann/ }).click();
  await page.getByRole("dialog").getByLabel("Your name").fill(name);
  await page.getByRole("button", { name: "Save" }).click();
  // Text, as typed: nothing parsed it.
  await expect(page.getByRole("button", { name: /^<img/ })).toBeVisible();
  expect(account.name).toBe(name);
  expect(await page.locator("main img[src='x']").count()).toBe(0);

  await page.getByRole("button", { name: /^Delete account/ }).click();
  await expect(page.getByRole("alertdialog")).toContainText("“Former player”");
  await page.getByRole("alertdialog").getByRole("button", { name: "Cancel" }).click();
  expect(server.accounts).toHaveLength(1);
  await page.getByRole("button", { name: /^Delete account/ }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Delete" }).click();
  await expect(page.getByRole("button", { name: "Create account" })).toBeVisible();
  expect(server.accounts).toHaveLength(0);
  expect(await readStorage(page, "online/identity")).toBeUndefined();
  check();
});

test("the room form offers what each map allows", async ({ page }) => {
  const check = watchAppErrors(page);
  await useServer(page, server, server.addAccount("Ann"));
  await open(page, "#/online");
  const sizes = page.getByRole("radiogroup", { name: "Team size" }).getByRole("radio");
  await expect(sizes).toHaveText(["1v1", "2v2", "3v3"]);
  await page.getByRole("radiogroup", { name: "Team size" }).getByRole("radio", { name: "3v3" }).click();
  await expect(page.getByRole("radiogroup", { name: "Length" }).getByRole("radio")).toHaveText(["Quick", "Long"]);

  await page.getByRole("radiogroup", { name: "Map" }).getByRole("radio", { name: "Across the River" }).click();
  await expect(sizes).toHaveText(["4v4", "5v5"]);
  await expect(page.getByRole("radiogroup", { name: "Team size" }).getByRole("radio", { name: "4v4" })).toHaveAttribute("aria-checked", "true");
  await expect(page.getByRole("radiogroup", { name: "Length" }).getByRole("radio")).toHaveText(["Quick"]);
  await shot(page, test.info(), "online-home");
  await expectNoHorizontalOverflow(page);

  await page.getByText("Ranked: the result counts").click();
  await page.getByRole("button", { name: "Create room" }).click();
  await expect(title(page)).toHaveText(/^Room /);
  const room = [...server.rooms.values()][0];
  expect(room).toMatchObject({ mapId: "across-the-river", teamSize: 4, length: "quick", ranked: true });
  check();
});

test("joining by a code in any spelling, and a code that isn't one", async ({ page }) => {
  const check = watchAppErrors(page);
  const owner = server.addAccount("Host");
  const room = server.seedGameless(owner);
  await useServer(page, server, server.addAccount("Ann"));
  await open(page, "#/online");
  await page.getByLabel("Room code or invite link").fill("hello");
  await page.getByRole("button", { name: "Join", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("doesn't look like a room code");
  await page.getByLabel("Room code or invite link").fill("ZZZZ2222");
  await page.getByRole("button", { name: "Join", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("no room with that code");
  await page.getByLabel("Room code or invite link").fill(`https://quintond.github.io/goa-companion/#play=${room.code.toLowerCase()}`);
  await page.getByRole("button", { name: "Join", exact: true }).click();
  await expect(title(page)).toHaveText(`Room ${room.code}`);
  check();
});

test("two people meet in a room through an invite link, cold and across a reload, and start", async ({ page, browser, baseURL, browserName }) => {
  const violations = await watchPolicy(page);
  const check = watchAppErrors(page);
  await useServer(page, server, server.addAccount("Ann"));
  await open(page, "#/online");
  await page.getByRole("button", { name: "Create room" }).click();
  await expect(title(page)).toHaveText(/^Room /);
  const code = (await title(page).textContent())!.replace("Room ", "");

  // The invite: QR code, link, code.
  await page.getByRole("button", { name: "Invite" }).click();
  await expect(page.locator(".goa-w2-qr svg")).toHaveAttribute("aria-label", `Invite QR code for room ${code}`);
  await expect(page.getByText(`quintond.github.io/goa-companion/#play=${code}`)).toBeVisible();
  await shot(page, test.info(), "online-invite");
  await page.getByRole("button", { name: "Done" }).click();

  // Someone follows the link, with no account yet: a name first, then the room.
  const guest = await secondPhone(browser, baseURL, browserName);
  const guestViolations = await watchPolicy(guest);
  await useServer(guest, server);
  await guest.goto(`./#play=${code}`);
  await expect.poll(() => guest.url()).toContain(`#/online/join/${code}`);
  await expect(guest.getByText(`Room ${code}`)).toBeVisible();
  // A reload at the invite stays at the invite.
  await guest.reload();
  await expect(guest.getByRole("button", { name: "Create account" })).toBeVisible();
  await createAccount(guest, "Bob");
  await expect(title(guest)).toHaveText(`Room ${code}`);
  // Back goes home, not to the invite, and a reload stays in the room.
  await guest.reload();
  await expect(title(guest)).toHaveText(`Room ${code}`);

  // Seats by team, live on both phones.
  await expect(page.locator(".goa-online-seat", { hasText: "Bob" })).toBeVisible();
  await expect(guest.locator(".goa-online-seat", { hasText: "Ann" })).toBeVisible();
  await expect(guest.locator(".goa-online-seat.mine")).toContainText("Bob");

  // A hero needs choosing before ready; a hero taken is gone from the other's list.
  await expect(page.getByRole("button", { name: "I'm ready" })).toBeDisabled();
  await page.getByRole("button", { name: "Wasp" }).click();
  await expect(page.locator(".goa-online-seat.mine")).toContainText("Wasp");
  await expect(guest.getByRole("button", { name: "Wasp", exact: true })).toHaveCount(0);
  // The grid folds away once picked, and comes back to change it.
  await expect(page.locator(".goa-online-heroes")).toHaveCount(0);
  await page.getByRole("button", { name: "Change hero" }).click();
  await expect(page.getByRole("button", { name: "Wasp" })).toHaveAttribute("aria-pressed", "true");
  await page.getByRole("button", { name: "Change hero" }).waitFor({ state: "detached" });
  await guest.getByRole("button", { name: "Arien" }).click();
  await expect(guest.locator(".goa-online-seat.mine")).toContainText("Arien");

  await page.getByRole("button", { name: "I'm ready" }).click();
  await expect(page.getByRole("button", { name: "Start the game" })).toBeDisabled();
  await guest.getByRole("button", { name: "I'm ready" }).click();
  await expect(guest.getByRole("button", { name: "Start the game" })).toHaveCount(0);
  await shot(page, test.info(), "online-lobby");
  await expectNoHorizontalOverflow(page);

  // The host starts: both go to the table, Back no longer the lobby.
  await page.getByRole("button", { name: "Start the game" }).click();
  await expect(mapSvg(page)).toBeVisible({ timeout: 30_000 });
  await expect(mapSvg(guest)).toBeVisible({ timeout: 30_000 });
  await expect.poll(() => page.url()).toContain(`#/online/game/${GAME_ID}`);
  await expect.poll(() => guest.url()).toContain(`#/online/game/${GAME_ID}`);

  // Hello opens every socket, with the token inside it and never in an address.
  for (const frame of server.firstFrames) expect(JSON.parse(frame).type).toBe("hello");
  for (const account of server.accounts) expect(server.addresses.join(" ")).not.toContain(account.token);

  expect(await violations()).toEqual([]);
  expect(await guestViolations()).toEqual([]);
  check();
  await guest.context().close();
});

test("the game: the question, the wait, the other person's turn, what they did, the result", async ({ page }) => {
  const violations = await watchPolicy(page);
  const check = watchAppErrors(page);
  await atTheTable(page);

  // The table of a game with people: names and a clock, and nothing that needs a whole game.
  await expect(clock(page)).toContainText("You");
  await expect(clock(page)).toContainText(/\d+ s/);
  await expect(page.locator(".goa-online-player", { hasText: "Bob" })).toContainText("Arien");
  await expect(page.getByRole("button", { name: "Take back" })).toHaveCount(0);
  await page.getByRole("button", { name: "More options" }).click();
  await expect(page.getByRole("dialog").getByText("Coach")).toHaveCount(0);
  await expect(page.getByRole("radiogroup", { name: "Computer speed" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Leave this game" })).toBeVisible();
  await page.keyboard.press("Escape");
  await shot(page, test.info(), "online-table");

  // Playing a card goes out with the seat's ticket, then the person waits: the same p.7 as ever.
  await page.locator(".goa-offered-card:not(.dimmed)").first().click();
  await expect(page.locator(".goa-focus")).toBeVisible();
  await page.locator(".goa-focus").getByRole("button", { name: /^Play / }).click();
  await expect.poll(() => server.answers.length).toBe(1);
  expect(server.answers[0]).toMatchObject({ ticket: 1000, seat: "wasp" });
  await expect(page.getByText("Your card is down")).toBeVisible();
  await expect(page.getByText("Waiting for Arien to play facedown")).toBeVisible();

  // The other phone's frames: who is being asked, never what.
  server.advance();
  server.advance();
  await expect(page.getByText("Your card is down")).toHaveCount(0);

  // Arien's turn (the recording's step 4): only who.
  server.advance(2);
  await expect(page.getByText("Bob is choosing")).toBeVisible();
  await expect(clock(page)).toContainText("Bob");

  // What several people just did: the resolution flow, opening to its steps.
  const entry = page.locator(".goa-flow-entry").first();
  await expect(entry).toBeVisible();
  const head = entry.locator(".goa-flow-head");
  await expect(head).toHaveAttribute("aria-expanded", "false");
  await head.click();
  await expect(head).toHaveAttribute("aria-expanded", "true");
  await expect(entry.locator(".goa-flow-steps")).toBeVisible();
  await shot(page, test.info(), "online-flow");

  // Presence: the table says who has gone and who answers for them.
  server.setPresence("arien", "computer");
  await expect(page.locator(".goa-online-player", { hasText: "Bob" })).toContainText("the computer answers");
  await expect(page.getByText("the computer answers until they're back").first()).toBeVisible();

  // A reload finds the same table, with the log from the server.
  await page.reload();
  await expect(mapSvg(page)).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText("Bob is choosing")).toBeVisible();

  // The result, and whether it counted.
  server.finish(true);
  await expect(title(page)).toHaveText("Game over");
  await expect(page.getByRole("heading", { name: "You won" })).toBeVisible();
  await expect(page.getByText("Rated: it counts on the leaderboard")).toBeVisible();
  await expect(page.locator(".goa-result-row", { hasText: "Bob" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Rematch" })).toHaveCount(0);
  await shot(page, test.info(), "online-result");
  await page.getByRole("button", { name: "Back to Online" }).click();
  await expect(title(page)).toHaveText("Online");

  expect(await violations()).toEqual([]);
  check();
});

test("a result that doesn't count says so, and the losing side hears it", async ({ page }) => {
  const check = watchAppErrors(page);
  const host = server.addAccount("Ann");
  const guest = server.addAccount("Bob");
  server.seedGame(host, guest);
  await useServer(page, server, guest);
  await page.goto(`./#/online/game/${GAME_ID}`);
  await expect(mapSvg(page)).toBeVisible({ timeout: 30_000 });
  // Blue's seat sees Red asked first: Ann is choosing.
  await expect(page.getByText("Ann is choosing")).toBeVisible();
  server.finish(false);
  await expect(page.getByRole("heading", { name: "Your team lost" })).toBeVisible();
  await expect(page.getByText("Not rated: this one doesn't count")).toBeVisible();
  check();
});

test("leaving a game asks first, and says the computer plays on", async ({ page }) => {
  const check = watchAppErrors(page);
  await atTheTable(page);
  await page.getByRole("button", { name: "More options" }).click();
  await page.getByRole("button", { name: "Leave this game" }).click();
  const dialog = page.getByRole("alertdialog");
  await expect(dialog).toContainText("The computer plays your seat to the end; your team still wins or loses.");
  await dialog.getByRole("button", { name: "Stay" }).click();
  await expect(mapSvg(page)).toBeVisible();
  expect(server.frames.filter((f) => f.type === "game.leave")).toHaveLength(0);

  await page.getByRole("button", { name: "More options" }).click();
  await page.getByRole("button", { name: "Leave this game" }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Leave" }).click();
  await expect(title(page)).toHaveText("Online");
  await expect.poll(() => server.frames.filter((f) => f.type === "game.leave").length).toBe(1);
  check();
});

test("a dropped connection keeps the table, says so, and comes back by itself", async ({ page }) => {
  const check = watchAppErrors(page);
  await atTheTable(page);
  await expect(clock(page)).toBeVisible();
  server.down = true;
  server.dropSockets();
  await expect(page.getByText("Connection lost. Getting back to your game…")).toBeVisible();
  // The table stays where it was.
  await expect(mapSvg(page)).toBeVisible();
  await shot(page, test.info(), "online-reconnecting");
  server.down = false;
  await expect(page.getByText("Connection lost. Getting back to your game…")).toHaveCount(0, { timeout: 45_000 });
  await expect(mapSvg(page)).toBeVisible();
  check();
});

test("coming back to the foreground reconnects at once, even from a silent socket", async ({ page }) => {
  // What a suspended phone looks like: the socket says nothing and no one is told it's gone. Playwright can't
  // suspend a page, so these are the events a real resume sends.
  test.setTimeout(120_000);
  await atTheTable(page);
  const before = server.socketsOpened;
  server.silent = true;
  await page.waitForTimeout(10_500); // past RESUME_SILENT_MS
  server.silent = false;
  await page.evaluate(() => document.dispatchEvent(new Event("visibilitychange")));
  await expect.poll(() => server.socketsOpened, { timeout: 5_000 }).toBeGreaterThan(before);
  await expect(page.getByText("Connection lost. Getting back to your game…")).toHaveCount(0, { timeout: 10_000 });

  // With the server away the backoff grows to many seconds; the network coming back cuts it short.
  server.down = true;
  server.dropSockets();
  await expect(page.getByText("Connection lost. Getting back to your game…")).toBeVisible();
  await page.waitForTimeout(16_000);
  server.down = false;
  const started = Date.now();
  await page.evaluate(() => window.dispatchEvent(new Event("online")));
  await expect(page.getByText("Connection lost. Getting back to your game…")).toHaveCount(0, { timeout: 5_000 });
  expect(Date.now() - started).toBeLessThan(5_000);
});

test("a game that was voided says why, in words", async ({ page }) => {
  const check = watchAppErrors(page);
  await atTheTable(page);
  server.voidGame("rules_changed");
  await expect(title(page)).toHaveText("Game closed");
  await expect(page.getByText("A rules fix changed this game's past, so it can't go on. It won't count.")).toBeVisible();
  await page.getByRole("button", { name: "Back to Online" }).click();
  await expect(title(page)).toHaveText("Online");
  check();
});

test("a frame this build can't read asks for an update, and a dead token for the recovery code", async ({ page }) => {
  const check = watchAppErrors(page);
  const { host } = await atTheTable(page);
  server.sendRaw(JSON.stringify({ type: "game.teleport", to: "mars" }));
  await expect(page.getByText("This needs a newer version of the app.")).toBeVisible();

  await useServer(page, server, { ...host, token: "not-a-token" });
  await page.goto(`./#/online/game/${GAME_ID}`);
  await page.reload();
  await expect(page.getByText("The server doesn't know this device's account any more.")).toBeVisible();
  await page.getByRole("button", { name: "Use my recovery code" }).click();
  await expect(page.getByLabel("Recovery code")).toBeVisible({ timeout: 15_000 });
  check();
});

test("my games come back on the home screen", async ({ page }) => {
  const check = watchAppErrors(page);
  const host = server.addAccount("Ann");
  server.seedGame(host, server.addAccount("Bob"));
  await useServer(page, server, host);
  await open(page, "#/online");
  const row = page.getByRole("link", { name: /Forgotten Island/ });
  await expect(row).toContainText("Ann, Bob");
  await row.click();
  await expect(mapSvg(page)).toBeVisible({ timeout: 30_000 });
  check();
});

test("with the server down the screens say so, and the rest of the app works", async ({ page }) => {
  const check = watchAppErrors(page);
  const account = server.addAccount("Ann");
  server.down = true;
  await useServer(page, server, account);
  await open(page, "#/online");
  await expect(page.getByRole("alert")).toContainText("The server can't be reached; your games wait for it.");
  await page.getByRole("button", { name: "Create room" }).click();
  await expect(page.getByRole("alert")).toContainText("Playing against the computer still works.");
  await open(page, "#/boards");
  check();
});

test.describe("the leaderboard", () => {
  const board = (name: string, generatedAt: number) => ({
    schema: 1,
    generatedAt,
    logSha256: "abc",
    matches: 12,
    eloConfig: 1,
    players: [
      { id: "acc-1", name, rating: 1612, games: 9, wins: 7 },
      { id: "acc-2", name: "<b>Bob</b>", rating: 1490, games: 5, wins: 1 },
    ],
    heroes: [{ id: "wasp", name: "Wasp", rating: 1533, games: 11, wins: 6 }],
  });
  const PAGES = "https://quintond.github.io/goa-leaderboards";

  test("Players and Heroes from the Pages file, your own row marked, its age said", async ({ page }) => {
    const violations = await watchPolicy(page);
    const check = watchAppErrors(page);
    const account = server.addAccount("Ann");
    await useServer(page, server, account);
    const cors = { "access-control-allow-origin": "*" };
    await page.route(`${PAGES}/leaderboard.json`, (route) =>
      route.fulfill({ json: board("Ann", Date.now() - 12 * 60_000), headers: cors }));
    await open(page, "#/online/leaderboard");
    await expect(title(page)).toHaveText("Leaderboard");
    await expect(page.getByRole("status")).toHaveText("Updated 12 min ago");
    const rows = page.locator(".goa-online-rank");
    await expect(rows).toHaveCount(2);
    await expect(rows.nth(0)).toContainText("1612");
    await expect(rows.nth(0)).toHaveAttribute("aria-current", "true");
    await expect(rows.nth(0)).toContainText("You");
    await expect(rows.nth(1)).toContainText("<b>Bob</b>"); // text, as typed
    expect(await rows.nth(1).locator("b").count()).toBe(0);
    await shot(page, test.info(), "online-leaderboard");
    await page.getByRole("tab", { name: "Heroes" }).click();
    await expect(rows).toHaveCount(1);
    await expect(rows.first()).toContainText("Wasp");
    await expect(rows.first().getByText("You")).toHaveCount(0);
    expect(server.addresses, "a healthy Pages file sends the game host nothing").toEqual([]);
    expect(await violations()).toEqual([]);
    check();
  });

  test("Pages down: the game host's board; everything down: the last copy, said to be old", async ({ page }) => {
    await useServer(page, server, server.addAccount("Ann"));
    await page.route(`${PAGES}/**`, (route) => route.abort("connectionfailed"));
    server.leaderboard = board("Ann", Date.now() - 3 * 86_400_000);
    await open(page, "#/online/leaderboard");
    await expect(page.locator(".goa-online-rank")).toHaveCount(2);
    await expect(page.getByRole("status")).toHaveText("Updated 3 days ago");

    server.down = true;
    await page.getByRole("button", { name: "Read it again" }).click();
    await expect(page.getByRole("status")).toContainText("Last copy, from 3 days ago: the server can't be reached");
    await expect(page.locator(".goa-online-status.stale")).toBeVisible();
    await expect(page.locator(".goa-online-rank")).toHaveCount(2);
  });

  test("with nothing anywhere it says so", async ({ page }) => {
    await useServer(page, server);
    server.down = true;
    await page.route(`${PAGES}/**`, (route) => route.abort("connectionfailed"));
    await open(page, "#/online/leaderboard");
    await expect(page.getByText("No leaderboard yet: the server can't be reached")).toBeVisible();
  });
});

test("a room's address with no account here asks for a name, then opens the room", async ({ page }) => {
  // What an iPhone that has cleared this site's data finds behind a bookmark.
  const room = server.seedGameless(server.addAccount("Host"));
  await useServer(page, server);
  await page.goto(`./#/online/room/${room.code}`);
  await expect.poll(() => page.url()).toContain(`#/online/join/${room.code}`);
  await createAccount(page, "Ann");
  await expect(title(page)).toHaveText(`Room ${room.code}`);
});

test("a hero can't be picked while the room's socket is still opening: the tap would be lost", async ({ page }) => {
  // Found on the live server: the socket takes a second through Funnel, and a tap in that second went nowhere.
  const room = server.seedGameless(server.addAccount("Host"));
  await useServer(page, server);
  await page.addInitScript(() => {
    window.WebSocket = class {
      readyState = 0;
      send() {}
      close() {}
      addEventListener() {}
      removeEventListener() {}
    } as unknown as typeof WebSocket;
  });
  await page.goto(`./#/online/room/${room.code}`);
  await createAccount(page, "Ann");
  await expect(title(page)).toHaveText(`Room ${room.code}`);
  await expect(page.getByText("Connecting to the room…")).toBeVisible();
  await expect(page.getByRole("button", { name: "Wasp", exact: true })).toBeDisabled();
});

test("an invite to a room that's gone says so where the room would be", async ({ page }) => {
  await useServer(page, server, server.addAccount("Ann"));
  await page.goto("./#play=ABCD2345");
  await expect(page.getByRole("alert")).toContainText("no room with that code");
  await expect(page.getByText("It couldn't be opened from here.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Create room" })).toBeVisible();
});

test("an invite with no code in it isn't a room", async ({ page }) => {
  await useServer(page, server);
  await page.goto("./#play=nope");
  await expect(title(page)).toHaveText("Not found");
});

test("the Android browser is offered the app; an iPhone isn't", async ({ page, browserName }) => {
  await useServer(page, server);
  await page.goto("./#play=ABCD2345");
  await expect(page.getByText("Room ABCD2345")).toBeVisible();
  const link = page.getByRole("link", { name: "Open in the app" });
  if (browserName === "chromium") {
    await expect(link).toHaveAttribute("href", "goa-companion://play/ABCD2345");
  } else {
    await expect(link).toHaveCount(0);
  }
});

test.describe("with the service worker on, as on an iPhone", () => {
  test.use({ serviceWorkers: "allow" });

  test("an invite link opens cold, survives a reload, and the socket passes the worker by", async ({ page }) => {
    const violations = await watchPolicy(page);
    const host = server.addAccount("Host");
    const room = server.seedGameless(host);
    await useServer(page, server, server.addAccount("Ann"));
    await page.goto(`./#play=${room.code}`);
    await expect(title(page)).toHaveText(`Room ${room.code}`, { timeout: 30_000 });
    await page.waitForFunction(() => !!navigator.serviceWorker?.controller, null, { timeout: 60_000 });
    await page.reload();
    await expect(title(page)).toHaveText(`Room ${room.code}`);
    await expect(page.locator(".goa-online-seat", { hasText: "Host" })).toBeVisible();
    // The socket is up behind the worker: a pick goes through.
    await page.getByRole("button", { name: "Wasp" }).click();
    await expect(page.locator(".goa-online-seat.mine")).toContainText("Wasp");
    expect(await violations()).toEqual([]);
  });
});

