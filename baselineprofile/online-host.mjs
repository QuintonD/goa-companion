// The other player of OnlineLiveTest (ADR-056), on the real game server: makes an unranked 1v1 room, sits as Wasp, starts
// when the other seat is ready, and answers every question it's asked with its first option. Prints the room's code,
// and deletes its account when GOA_HOST_STOP's file appears, or after the time limit.
// Usage: node online-host.mjs [limit seconds]
import { existsSync } from "node:fs";

const BASE = process.env.GOA_GAME_SERVER_URL ?? "https://oldboi.taila4d38d.ts.net:8443/game";
const LIMIT_SECONDS = Number(process.argv[2] ?? 900);
const CATALOG_VERSION = 5;

async function call(method, path, token, body) {
  const response = await fetch(BASE + path, {
    method,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`${method} ${path}: ${response.status} ${text}`);
  return text ? JSON.parse(text) : null;
}

const { token } = await call("POST", "/v1/accounts", null, { name: "Check Host" });
const { code } = await call("POST", "/v1/rooms", token, {
  mapId: "forgotten-island",
  length: "quick",
  teamSize: 1,
  ranked: false,
});
await call("POST", `/v1/rooms/${code}/join`, token);

let started = false;
let answered = 0;
const socket = new WebSocket(`${BASE.replace(/^http/, "ws")}/v1/play`);
const send = (message) => socket.send(JSON.stringify(message));

socket.onopen = () => {
  send({ type: "hello", protocol: 1, token, client: "check/android-host", catalogVersion: CATALOG_VERSION });
  send({ type: "room.watch", code });
  send({ type: "room.seat", code, team: "red", index: 0, heroId: "wasp" });
};

socket.onmessage = (event) => {
  const frame = JSON.parse(event.data);
  if (frame.type === "room") {
    const mine = frame.room.seats.find((s) => s.heroId === "wasp");
    if (mine && !mine.ready) send({ type: "room.ready", code, ready: true });
    if (!started && frame.room.seats.every((s) => s.ready)) {
      started = true;
      send({ type: "room.start", code });
    }
  } else if (frame.type === "game.table" && frame.decision?.id && frame.decision.options?.length) {
    answered += 1;
    send({ type: "game.answer", gameId: frame.gameId, ticket: frame.decision.id, choice: frame.decision.options[0] });
  } else if (frame.type === "refused") {
    console.log(`refused: ${frame.reason}`);
  } else if (frame.type === "game.end") {
    console.log(`the game ended: winner ${frame.winner}, rated ${frame.rated}`);
  }
};
socket.onclose = () => console.log("socket closed");

console.log(`ROOM ${code}`);

async function finish() {
  console.log(`answered ${answered}, started ${started}`);
  socket.close();
  await call("DELETE", "/v1/me", token);
  process.exit(0);
}

const stop = process.env.GOA_HOST_STOP;
setInterval(() => {
  if (stop && existsSync(stop)) finish();
}, 1000);
setTimeout(finish, LIMIT_SECONDS * 1000);
