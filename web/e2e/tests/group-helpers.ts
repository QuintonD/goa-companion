import { expect, type Page } from "@playwright/test";
import { createHash } from "node:crypto";
import { createServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";
import type { AddressInfo } from "node:net";

/**
 * Group stream (W2) test helpers: a seeded match log, a fake sync relay behind Playwright routes
 * (the production relay is never contacted from tests), and IndexedDB access.
 */

export const RELAY = "https://oldboi.taila4d38d.ts.net:8443";

const DAY = 86_400_000;
const HEROES = ["arien", "brogan", "tigerclaw", "wasp", "sabina", "xargatha", "dodger", "misa", "whisper", "ursafar"];
const PALETTE = [0xff3fc1b0, 0xffd9b44a, 0xffee7f6f, 0xff62a8f2, 0xffb892e6, 0xff6cc27a];

/** Six friends and fourteen matches over three evenings, the latest today. */
export function seedDocument(now = Date.now()) {
  const names = ["Ann", "Bob", "Cid", "Dee", "Eve", "Finn"];
  const players = names.map((name, i) => ({
    id: `p-${name.toLowerCase()}`,
    name,
    color: PALETTE[i],
    avatarId: i * 3,
    createdAt: now - 30 * DAY + i,
    archived: false,
    updatedAt: now - 30 * DAY + i,
  }));
  const ids = players.map((p) => p.id);
  const matches = [];
  let n = 0;
  for (const [daysAgo, games] of [[14, 4], [7, 5], [0, 5]] as const) {
    for (let g = 0; g < games; g++) {
      const playedAt = now - daysAgo * DAY - (games - g) * 50 * 60_000;
      const red = [ids[(n + g) % 6], ids[(n + g + 1) % 6]];
      const blue = [ids[(n + g + 2) % 6], ids[(n + g + 3) % 6]];
      const heroes = [0, 1, 2, 3].map((k) => HEROES[(n * 3 + k * 2 + g) % HEROES.length]);
      const winner = (n + g) % 3 === 0 ? "blue" : "red";
      matches.push({
        id: `m-${String(n).padStart(2, "0")}`,
        createdAt: playedAt,
        playedAt,
        status: "completed",
        mode: g % 4 === 3 ? "long" : "quick",
        winner,
        winCondition: ["throne", "last_push", "life_counters"][n % 3],
        durationMin: 35 + ((n * 7) % 30),
        notes: n === 3 ? "Close one: Bob's last push on the final wave." : "",
        participants: [
          { playerId: red[0], heroId: heroes[0], team: "red" },
          { playerId: red[1], heroId: heroes[1], team: "red", handicap: false },
          { playerId: blue[0], heroId: heroes[2], team: "blue" },
          { playerId: blue[1], heroId: heroes[3], team: "blue" },
        ],
        updatedAt: playedAt,
      });
      n++;
    }
  }
  // One pending match from a draft.
  matches.push({
    id: "m-pending",
    createdAt: now - 10 * 60_000,
    playedAt: now - 10 * 60_000,
    status: "pending",
    mode: "quick",
    participants: [
      { playerId: ids[0], heroId: "gydion", team: "red" },
      { playerId: ids[3], heroId: "snorri", team: "blue" },
    ],
    updatedAt: now - 10 * 60_000,
  });
  return { format: "goa-sync", version: 2, players, matches };
}

/** Writes values into the app's IndexedDB (`goa`/`kv`) before the app reads them. */
export async function seedStorage(page: Page, values: Record<string, unknown>) {
  await page.evaluate(async (entries) => {
    const db: IDBDatabase = await new Promise((resolve, reject) => {
      const request = indexedDB.open("goa", 1);
      request.onupgradeneeded = () => request.result.createObjectStore("kv");
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction("kv", "readwrite");
      for (const [key, value] of Object.entries(entries)) {
        tx.objectStore("kv").put(typeof value === "string" ? value : JSON.stringify(value), key);
      }
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
    db.close();
  }, values);
}

export async function readStorage(page: Page, key: string): Promise<string | undefined> {
  return page.evaluate(async (k) => {
    const db: IDBDatabase = await new Promise((resolve, reject) => {
      const request = indexedDB.open("goa", 1);
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
    const value = await new Promise<string | undefined>((resolve) => {
      const get = db.transaction("kv").objectStore("kv").get(k);
      get.onsuccess = () => resolve(get.result as string | undefined);
    });
    db.close();
    return value;
  }, key);
}

/** A group this browser is in, syncing through [serverUrl] (the fake relay). */
export function groupPrefs(serverUrl: string, extra: Record<string, unknown> = {}) {
  return {
    serverUrl,
    groupId: "8c4f2b1e-3a5d-4c6e-9f70-1a2b3c4d5e6f",
    groupKey: "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
    groupName: "Friday nights",
    deviceId: "web-test",
    mePlayerId: "p-ann",
    ...extra,
  };
}

/**
 * The relay (docs/SYNC-SERVER.md) as a real HTTP server on this machine, so the app's fetches go
 * through the browser's real CORS and conditional requests (304) as with oldboi: preflights, one
 * opaque blob per group with its rev, `If-Match` / `If-None-Match`, the token's hash. [mode] "ok"
 * behaves; "wrong-key" answers 401; "down" closes connections. The app reaches it through the
 * group's server address (plain HTTP is allowed because the page itself is on localhost).
 * Production is never contacted: [blockProduction] makes every request to oldboi fail.
 */
export class FakeRelay {
  blob: Buffer | null = null;
  rev = 0;
  tokenHash: string | null = null;
  mode: "ok" | "down" | "wrong-key" = "ok";
  requests: { method: string; headers: Record<string, string | string[] | undefined> }[] = [];
  private server: Server | null = null;
  url = "";

  async start(): Promise<string> {
    this.server = createServer((req, res) => this.handle(req, res));
    await new Promise<void>((resolve) => this.server!.listen(0, "127.0.0.1", () => resolve()));
    this.url = `http://127.0.0.1:${(this.server.address() as AddressInfo).port}`;
    return this.url;
  }

  async stop() {
    await new Promise<void>((resolve) => this.server?.close(() => resolve()));
  }

  private cors(origin: string | undefined, extra: Record<string, string> = {}) {
    return {
      ...(origin ? { "Access-Control-Allow-Origin": origin, "Access-Control-Expose-Headers": "ETag, Retry-After", Vary: "Origin" } : {}),
      "Cache-Control": "no-store",
      ...extra,
    };
  }

  private handle(req: IncomingMessage, res: ServerResponse) {
    const origin = req.headers.origin;
    if (this.mode === "down") {
      req.socket.destroy();
      return;
    }
    if (req.method === "OPTIONS") {
      res.writeHead(204, this.cors(origin, {
        "Access-Control-Allow-Methods": "GET, PUT",
        "Access-Control-Allow-Headers": "Authorization, If-Match, If-None-Match, Content-Type",
        "Access-Control-Max-Age": "600",
      }));
      res.end();
      return;
    }
    const chunks: Buffer[] = [];
    req.on("data", (c: Buffer) => chunks.push(c));
    req.on("end", () => {
      this.requests.push({ method: req.method ?? "", headers: req.headers });
      const send = (status: number, extra: Record<string, string> = {}, body: Buffer | string = "") => {
        res.writeHead(status, this.cors(origin, extra));
        res.end(body);
      };
      const token = req.headers.authorization?.replace(/^Bearer /, "") ?? null;
      const tokenHash = token ? createHash("sha256").update(token).digest("hex") : null;
      if (this.mode === "wrong-key") return send(401, {}, '{"error":"unauthorized"}');
      if (req.method === "GET") {
        if (!this.blob) return send(404, {}, '{"error":"no_document"}');
        if (tokenHash !== this.tokenHash) return send(401);
        if (req.headers["if-none-match"] === `"${this.rev}"`) return send(304, { ETag: `"${this.rev}"` });
        return send(200, { ETag: `"${this.rev}"`, "Content-Type": "application/vnd.dev.quinton.goa.sync" }, this.blob);
      }
      if (req.method !== "PUT") return send(405);
      if (!token) return send(401);
      if (this.tokenHash && tokenHash !== this.tokenHash) return send(401);
      const ifMatch = req.headers["if-match"];
      const ifNoneMatch = req.headers["if-none-match"];
      if ((ifNoneMatch === "*" && this.blob) || (ifMatch && ifMatch !== `"${this.rev}"`)) return send(412);
      this.blob = Buffer.concat(chunks);
      this.rev++;
      this.tokenHash ??= tokenHash;
      return send(this.rev === 1 ? 201 : 200, { ETag: `"${this.rev}"` }, `{"rev":${this.rev}}`);
    });
  }
}

/** Every request to the production relay fails: tests never touch it. */
export async function blockProduction(page: Page) {
  await page.route(`${RELAY}/**`, (route) => route.abort("blockedbyclient"));
}

/**
 * Fails on console errors or uncaught exceptions, except what comes from the service worker being
 * blocked for these tests (Playwright hands `register()` back undefined) and the relay's expected
 * 404 for a group that has no document yet.
 */
export function watchAppErrors(page: Page): () => void {
  const errors: string[] = [];
  const expected = [/Service Worker/, /\.waiting/, /reading 'waiting'/, /status of 404/, /ERR_BLOCKED_BY_CLIENT/, /oldboi/];
  page.on("console", (msg) => {
    if (msg.type() === "error" && !expected.some((e) => e.test(msg.text()))) errors.push(`console: ${msg.text()}`);
  });
  page.on("pageerror", (err) => {
    if (!expected.some((e) => e.test(err.message))) errors.push(`page: ${err.message}`);
  });
  return () => expect(errors, errors.join("; ")).toEqual([]);
}
