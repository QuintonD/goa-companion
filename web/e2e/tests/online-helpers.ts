import { expect, type Page } from "@playwright/test";
import { createHash, randomBytes } from "node:crypto";
import { readFileSync } from "node:fs";
import { createServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";
import type { AddressInfo, Socket } from "node:net";
import { fileURLToPath } from "node:url";
import { seedStorage } from "./group-helpers";

// Online games (ADR-056): a game server on this machine, speaking docs/ONLINE.md's REST and socket over real
// HTTP and a real WebSocket, so the browser's own CORS, CSP and socket are what the tests exercise. It holds no
// rules: its game is a recording of the real engine (fixtures/online-game.json, kept honest by jsTest's
// OnlineRecordedGameTest), any answer moves it on, and a test ends it. A test that needs the real rules runs
// against `:server:game:run` (online-real.spec.ts) instead.

const fixture = JSON.parse(
  readFileSync(fileURLToPath(new URL("./fixtures/online-game.json", import.meta.url)), "utf8"),
) as {
  start: Record<string, { players: { seat: string; accountId: string; name: string; team: string }[] }>;
  steps: {
    asked: string;
    ticket: number;
    kind: string;
    events: unknown[];
    views: Record<string, unknown>;
    decisions: Record<string, unknown>;
    locked: string[];
  }[];
  end: Record<string, unknown>;
};

/** The game's seats: the room's first Red seat plays Wasp, its first Blue seat Arien. */
const SEATS = { red: "wasp", blue: "arien" } as const;
export const GAME_ID = "game-1";

type Account = { id: string; token: string; name: string; recoveryCode: string };
type Seat = { team: string; index: number; accountId?: string; name?: string; heroId?: string; ready: boolean };
type Room = {
  code: string; hostAccountId: string; mapId: string; length: string; teamSize: number; ranked: boolean;
  seats: Seat[]; status: string; expiresAt: number; gameId?: string;
};
type Connection = { socket: Socket; accountId?: string; buffer: Buffer };

const WS_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

function wsFrame(text: string, opcode = 1): Buffer {
  const body = Buffer.from(text);
  let head: Buffer;
  if (body.length < 126) head = Buffer.from([0x80 | opcode, body.length]);
  else if (body.length < 65536) {
    head = Buffer.from([0x80 | opcode, 126, 0, 0]);
    head.writeUInt16BE(body.length, 2);
  } else {
    head = Buffer.alloc(10);
    head[0] = 0x80 | opcode;
    head[1] = 127;
    head.writeBigUInt64BE(BigInt(body.length), 2);
  }
  return Buffer.concat([head, body]);
}

export class FakeGameServer {
  url = "";
  accounts: Account[] = [];
  rooms = new Map<string, Room>();
  leaderboard: unknown = null;
  /** Every address asked for, REST and socket: a token must never be in one. */
  addresses: string[] = [];
  /** Every frame the browsers sent, as read. */
  frames: { type: string; [key: string]: unknown }[] = [];
  /** The first frame of each socket. */
  firstFrames: string[] = [];
  bearers: string[] = [];
  socketsOpened = 0;
  /** True: new sockets and REST calls are cut off, as with a server that is down. */
  down = false;
  /** True: an open socket stays open and says nothing, as a phone's suspended socket does. */
  silent = false;
  step = 0;
  answers: { ticket: number; seat: string; choice: unknown }[] = [];
  private seatOf = new Map<string, string>();
  private presence: Record<string, string> = {};
  private over: Record<string, unknown> | null = null;
  private connections = new Set<Connection>();
  private server: Server | null = null;

  async start() {
    this.server = createServer((req, res) => this.rest(req, res));
    this.server.on("upgrade", (req, socket) => this.upgrade(req, socket as Socket));
    await new Promise<void>((resolve) => this.server!.listen(0, "127.0.0.1", () => resolve()));
    this.url = `http://127.0.0.1:${(this.server.address() as AddressInfo).port}`;
    return this;
  }

  async stop() {
    this.connections.forEach((c) => c.socket.destroy());
    await new Promise<void>((resolve) => this.server?.close(() => resolve()));
  }

  /** Cuts every open socket, as a dropped connection or a restart does. */
  dropSockets() {
    this.connections.forEach((c) => c.socket.destroy());
  }

  // ---- What a test does to the game ------------------------------------------------------------

  /** Moves the recording on, as if the seat being asked had answered. */
  advance(steps = 1) {
    this.step = Math.min(this.step + steps, fixture.steps.length - 1);
    this.broadcastTables();
  }

  /** Ends the game by the rules; [winner] per the fixture unless given. */
  finish(rated = true) {
    this.over = { ...fixture.end, rated };
    this.broadcast(() => this.over!);
  }

  voidGame(reason: "rules_changed" | "abandoned") {
    this.over = { type: "game.end", gameId: GAME_ID, winner: null, reason: null, rated: false, voided: reason };
    this.broadcast(() => this.over!);
  }

  setPresence(seat: string, presence: "here" | "away" | "computer") {
    this.presence[seat] = presence;
    this.broadcastTables();
  }

  /** A frame of a kind this client has never heard of: what a newer server would send. */
  sendRaw(text: string) {
    this.connections.forEach((c) => c.socket.write(wsFrame(text)));
  }

  /** Seats a room and starts its game without any browser, for tests about the table itself. */
  seedGame(host: Account, guest: Account) {
    const room = this.makeRoom(host, { mapId: "forgotten-island", length: "quick", teamSize: 1, ranked: true });
    this.seat(room, guest);
    room.seats[0] = { ...room.seats[0], heroId: "wasp", ready: true };
    room.seats[1] = { ...room.seats[1], heroId: "arien", ready: true };
    this.startGame(room);
    return room;
  }

  /** A room waiting for people, made by [host]. */
  seedGameless(host: Account) {
    return this.makeRoom(host, { mapId: "forgotten-island", length: "quick", teamSize: 1, ranked: false });
  }

  addAccount(name: string): Account {
    const account = { id: `acc-${this.accounts.length + 1}`, token: `token-${randomBytes(8).toString("hex")}`, name, recoveryCode: `CODE-${randomBytes(6).toString("hex")}` };
    this.accounts.push(account);
    return account;
  }

  // ---- REST ----------------------------------------------------------------------------------------

  private rest(req: IncomingMessage, res: ServerResponse) {
    this.addresses.push(req.url ?? "");
    const origin = req.headers.origin;
    const cors: Record<string, string> = origin ? { "Access-Control-Allow-Origin": origin, "Access-Control-Expose-Headers": "ETag, Retry-After", Vary: "Origin" } : {};
    if (this.down) {
      req.socket.destroy();
      return;
    }
    if (req.method === "OPTIONS") {
      res.writeHead(204, {
        ...cors,
        "Access-Control-Allow-Methods": "GET, POST, PATCH, DELETE",
        "Access-Control-Allow-Headers": "Authorization, If-None-Match, Content-Type",
        "Access-Control-Max-Age": "600",
      }).end();
      return;
    }
    const chunks: Buffer[] = [];
    req.on("data", (c: Buffer) => chunks.push(c));
    req.on("end", () => {
      const send = (status: number, body?: unknown, headers: Record<string, string> = {}) => {
        res.writeHead(status, { ...cors, "Content-Type": "application/json", "Cache-Control": "no-store", ...headers });
        res.end(body === undefined ? "" : JSON.stringify(body));
      };
      const body = chunks.length ? JSON.parse(Buffer.concat(chunks).toString()) : {};
      const header = req.headers.authorization;
      if (header) this.bearers.push(header);
      const me = this.accounts.find((a) => header === `Bearer ${a.token}`);
      const path = (req.url ?? "").split("?")[0];
      const needMe = () => (me ? me : (send(401, { error: "unauthorized" }), null));
      const route = `${req.method} ${path}`;
      if (route === "POST /v1/accounts") {
        const account = this.addAccount(body.name);
        return send(201, { accountId: account.id, token: account.token, recoveryCode: account.recoveryCode });
      }
      if (route === "POST /v1/accounts/link") {
        const account = this.accounts.find((a) => a.recoveryCode === body.recoveryCode);
        if (!account) return send(401, { error: "bad_recovery_code" });
        account.recoveryCode = `CODE-${randomBytes(6).toString("hex")}`;
        account.token = `token-${randomBytes(8).toString("hex")}`;
        return send(200, { accountId: account.id, token: account.token, recoveryCode: account.recoveryCode });
      }
      if (route === "GET /v1/leaderboard") {
        return this.leaderboard ? send(200, this.leaderboard) : send(404, { error: "no_board" });
      }
      const account = needMe();
      if (!account) return;
      if (route === "GET /v1/me") return send(200, { accountId: account.id, name: account.name });
      if (route === "PATCH /v1/me") {
        account.name = body.name;
        return send(200, { accountId: account.id, name: account.name });
      }
      if (route === "DELETE /v1/me") {
        this.accounts = this.accounts.filter((a) => a !== account);
        return send(204);
      }
      if (route === "GET /v1/me/games") {
        const seated = this.rooms.size && [...this.rooms.values()].some((r) => r.gameId && r.seats.some((s) => s.accountId === account.id));
        return send(200, { games: seated && !this.over ? [this.myGame()] : [] });
      }
      if (route === "POST /v1/rooms") return send(201, { code: this.makeRoom(account, body).code });
      const match = /^POST \/v1\/rooms\/([^/]+)\/(join|leave)$/.exec(route);
      if (match) {
        const room = this.rooms.get(match[1]);
        if (!room) return send(404, { error: "no_such_room" });
        if (match[2] === "join") this.seat(room, account);
        else this.unseat(room, account);
        this.broadcastRoom(room);
        return send(200, room);
      }
      return send(404, { error: "bad_request" });
    });
  }

  private myGame() {
    const room = [...this.rooms.values()].find((r) => r.gameId)!;
    return { gameId: GAME_ID, roomCode: room.code, mapId: room.mapId, seats: this.players() };
  }

  private makeRoom(host: Account, form: { mapId: string; length: string; teamSize: number; ranked: boolean }) {
    const code = randomCode();
    const seats: Seat[] = [];
    for (const team of ["red", "blue"]) {
      for (let index = 0; index < form.teamSize; index++) seats.push({ team, index, ready: false });
    }
    const room: Room = { code, hostAccountId: host.id, ...form, seats, status: "open", expiresAt: Date.now() + 7_200_000 };
    this.rooms.set(code, room);
    this.seat(room, host);
    return room;
  }

  private seat(room: Room, account: Account) {
    if (room.seats.some((s) => s.accountId === account.id)) return;
    const free = room.seats.find((s) => !s.accountId);
    if (free) Object.assign(free, { accountId: account.id, name: account.name });
  }

  private unseat(room: Room, account: Account) {
    const seat = room.seats.find((s) => s.accountId === account.id);
    if (seat) Object.assign(seat, { accountId: undefined, name: undefined, heroId: undefined, ready: false });
  }

  private startGame(room: Room) {
    room.status = "started";
    room.gameId = GAME_ID;
    for (const seat of room.seats) {
      if (seat.index === 0 && seat.accountId) this.seatOf.set(seat.accountId, SEATS[seat.team as "red" | "blue"]);
    }
  }

  private players() {
    const room = [...this.rooms.values()].find((r) => r.gameId);
    return fixture.start.wasp.players.map((p) => {
      const seat = room?.seats.find((s) => s.index === 0 && SEATS[s.team as "red" | "blue"] === p.seat);
      return { ...p, accountId: seat?.accountId ?? p.accountId, name: seat?.name ?? p.name };
    });
  }

  // ---- The socket ---------------------------------------------------------------------------------

  private upgrade(req: IncomingMessage, socket: Socket) {
    this.addresses.push(req.url ?? "");
    if (this.down || !(req.url ?? "").endsWith("/v1/play")) {
      socket.destroy();
      return;
    }
    const accept = createHash("sha1").update(`${req.headers["sec-websocket-key"]}${WS_MAGIC}`).digest("base64");
    socket.write(`HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ${accept}\r\n\r\n`);
    this.socketsOpened++;
    const connection: Connection = { socket, buffer: Buffer.alloc(0) };
    this.connections.add(connection);
    socket.on("data", (data) => this.read(connection, data));
    socket.on("close", () => {
      this.connections.delete(connection);
      const seat = connection.accountId && this.seatOf.get(connection.accountId);
      if (seat && !this.connectionsOf(connection.accountId!).length) this.presence[seat] = "away";
    });
    socket.on("error", () => undefined);
  }

  private connectionsOf(accountId: string) {
    return [...this.connections].filter((c) => c.accountId === accountId);
  }

  private read(connection: Connection, data: Buffer) {
    connection.buffer = Buffer.concat([connection.buffer, data]);
    for (;;) {
      const b = connection.buffer;
      if (b.length < 2) return;
      const opcode = b[0] & 0x0f;
      let length = b[1] & 0x7f;
      let at = 2;
      if (length === 126) {
        if (b.length < 4) return;
        length = b.readUInt16BE(2);
        at = 4;
      } else if (length === 127) {
        if (b.length < 10) return;
        length = Number(b.readBigUInt64BE(2));
        at = 10;
      }
      if (b.length < at + 4 + length) return;
      const mask = b.subarray(at, at + 4);
      const payload = Buffer.from(b.subarray(at + 4, at + 4 + length));
      for (let i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
      connection.buffer = b.subarray(at + 4 + length);
      if (opcode === 8) {
        connection.socket.end();
        return;
      }
      if (opcode === 1) this.frame(connection, payload.toString());
    }
  }

  private send(connection: Connection, frame: unknown) {
    connection.socket.write(wsFrame(JSON.stringify(frame)));
  }

  private frame(connection: Connection, text: string) {
    const frame = JSON.parse(text) as { type: string; [key: string]: never };
    this.frames.push(frame);
    if (!connection.accountId && !this.firstFrames.includes(text)) this.firstFrames.push(text);
    if (this.silent) return;
    if (frame.type === "hello") return this.hello(connection, frame);
    const account = this.accounts.find((a) => a.id === connection.accountId);
    if (!account) return;
    const room = this.rooms.get(frame.code);
    switch (frame.type) {
      case "ping":
        return this.send(connection, { type: "pong", nonce: frame.nonce, serverTime: Date.now() });
      case "room.watch":
        if (room) this.send(connection, { type: "room", room });
        return;
      case "room.seat": {
        if (!room) return;
        const wanted = room.seats.find((s) => s.team === frame.team && s.index === frame.index);
        const own = room.seats.find((s) => s.accountId === account.id);
        const heroTaken = frame.heroId && room.seats.some((s) => s !== own && s.heroId === frame.heroId);
        if (!wanted || (wanted.accountId && wanted.accountId !== account.id) || heroTaken) {
          this.send(connection, { type: "refused", reason: "room_taken" });
          return this.send(connection, { type: "room", room });
        }
        if (own && own !== wanted) Object.assign(own, { accountId: undefined, name: undefined, heroId: undefined, ready: false });
        Object.assign(wanted, { accountId: account.id, name: account.name, heroId: frame.heroId ?? undefined, ready: false });
        return this.broadcastRoom(room);
      }
      case "room.ready": {
        const seat = room?.seats.find((s) => s.accountId === account.id);
        if (seat && room) {
          seat.ready = frame.ready;
          this.broadcastRoom(room);
        }
        return;
      }
      case "room.start":
        if (!room) return;
        if (room.hostAccountId !== account.id) {
          this.send(connection, { type: "refused", reason: "not_host" });
          return this.send(connection, { type: "room", room });
        }
        this.startGame(room);
        return this.broadcastRoom(room);
      case "game.answer":
        return this.answer(connection, account, frame);
      case "game.review":
        return this.send(connection, { type: "game.reviewed", gameId: GAME_ID, reviews: [] });
      default:
        return undefined;
    }
  }

  private hello(connection: Connection, frame: { protocol: number; token: string; resume?: { gameId: string; haveEvents: number }[] }) {
    const account = this.accounts.find((a) => a.token === frame.token);
    if (!account) {
      this.send(connection, { type: "refused", reason: "bad_token" });
      connection.socket.end();
      return;
    }
    connection.accountId = account.id;
    const seat = this.seatOf.get(account.id);
    if (seat) this.presence[seat] = "here";
    const active = seat && !this.over ? [GAME_ID] : [];
    this.send(connection, { type: "welcome", protocol: frame.protocol, accountId: account.id, name: account.name, serverTime: Date.now(), activeGames: active });
    const resume = frame.resume?.find((r) => r.gameId === GAME_ID);
    if (resume && seat) {
      this.send(connection, this.gameStart(seat));
      this.send(connection, this.table(seat, resume.haveEvents));
      if (this.over) this.send(connection, this.over);
    }
  }

  private gameStart(seat: string) {
    return { ...fixture.start[seat], players: this.players() };
  }

  private table(seat: string, have: number) {
    const at = fixture.steps[this.step];
    const all = fixture.steps.slice(0, this.step + 1).flatMap((s) => s.events);
    return {
      type: "game.table",
      gameId: GAME_ID,
      seq: this.step + 1,
      view: at.views[seat],
      decision: at.decisions[seat],
      eventsFrom: have,
      events: all.slice(have),
      locked: at.locked,
      clock: { seat: at.asked, deadlineAt: Date.now() + 45_000 },
      presence: this.presence,
    };
  }

  private answer(connection: Connection, account: Account, frame: { ticket: number; choice: unknown }) {
    const at = fixture.steps[this.step];
    const seat = this.seatOf.get(account.id) ?? "";
    this.answers.push({ ticket: frame.ticket, seat, choice: frame.choice });
    const accepted = frame.ticket === at.ticket && seat === at.asked;
    this.send(connection, { type: "game.ack", gameId: GAME_ID, ticket: frame.ticket, accepted });
    if (accepted) this.advance();
  }

  private broadcastTables() {
    if (this.over) return;
    const have = fixture.steps.slice(0, Math.max(this.step, 0)).flatMap((s) => s.events).length;
    this.broadcast((seat) => this.table(seat, have));
  }

  private broadcast(frameOf: (seat: string) => unknown) {
    for (const connection of this.connections) {
      const seat = connection.accountId && this.seatOf.get(connection.accountId);
      if (seat) this.send(connection, frameOf(seat));
    }
  }

  private broadcastRoom(room: Room) {
    for (const connection of this.connections) {
      if (room.seats.some((s) => s.accountId === connection.accountId)) this.send(connection, { type: "room", room });
    }
  }
}

function randomCode() {
  const alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
  return Array.from(randomBytes(8), (b) => alphabet[b % alphabet.length]).join("");
}

/** Points this browser at the [server] before the app starts; only a localhost page reads this key. */
export async function useServer(page: Page, server: { url: string }, account?: Account) {
  await page.goto("./manifest.webmanifest");
  const values: Record<string, unknown> = { "online/testServer": server.url };
  if (account) values["online/identity"] = { accountId: account.id, token: account.token, name: account.name };
  await seedStorage(page, values);
}

/** Collects the page's CSP violations, from the moment the document starts. */
export async function watchPolicy(page: Page): Promise<() => Promise<string[]>> {
  await page.addInitScript(() => {
    const seen: string[] = [];
    (window as unknown as { goaViolations: string[] }).goaViolations = seen;
    document.addEventListener("securitypolicyviolation", (e) => seen.push(`${e.effectiveDirective} ${e.blockedURI}`));
  });
  return () => page.evaluate(() => (window as unknown as { goaViolations: string[] }).goaViolations ?? []);
}

/** Creates an account through the screens, past "I've saved it". */
export async function createAccount(page: Page, name: string) {
  await page.getByLabel("Your name").fill(name);
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page.getByRole("heading", { name: "Your recovery code" })).toBeVisible();
  const code = await page.locator(".goa-online-code").textContent();
  await page.getByRole("button", { name: "I've saved it" }).click();
  return code ?? "";
}

export const mapSvg = (page: Page) => page.locator(".goa-map-svg");
export const clock = (page: Page) => page.locator(".goa-online-clock");
