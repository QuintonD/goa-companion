import { test } from "@playwright/test";
import { writeFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";
import { open } from "./helpers";

// How long the computer thinks in this browser (W4, re-measured per strength 1-10 for ADR-039): the
// real engine worker, the phone's strengths and seeds (AiPlayers.forApp), a 2v2 game of computers on
// Forgotten Island; each GameComputer round trip is timed from the
// page (what a player waits for). Card plays (the strong strengths' biggest searches) apart. Not part of
// the regular suite: run with GOA_AI_TIMING=1. Writes D:\.goa-qa\web\shots-w4\ai-timing.json.

const out = process.env.GOA_W4_SHOTS ?? (process.platform === "win32" ? "D:/.goa-qa/web/shots-w4" : "test-results/shots-w4");

test.skip(!process.env.GOA_AI_TIMING, "set GOA_AI_TIMING=1 to measure");

test("ai think time per strength", async ({ page }, info) => {
  test.setTimeout(1_800_000);
  await open(page, "#/heroes");
  // [strength, decisions]: fewer for the strong ones, whose card plays take seconds.
  const plan: [number, number][] = [
    [1, 120], [2, 120], [3, 120], [4, 120], [5, 80], [6, 80], [7, 50], [8, 50], [9, 30], [10, 30],
  ];
  const results: Record<string, unknown> = {};
  for (const [strength, decisions] of plan) {
    const r = await page.evaluate(async ({ strength, decisions }) => {
      const worker = new Worker("engine-worker.js");
      let next = 0;
      const pending = new Map<number, (v: any) => void>();
      worker.onmessage = (e) => {
        const reply = JSON.parse(e.data as string);
        pending.get(reply.id)?.(reply.response);
        pending.delete(reply.id);
      };
      const call = (request: unknown): Promise<any> =>
        new Promise((resolve) => {
          const id = ++next;
          pending.set(id, resolve);
          worker.postMessage(JSON.stringify({ id, request }));
        });
      // Controller.Ai: the band's level (1-3 Easy, 4-6 Normal, 7-8 Hard, 9-10 Expert) and the strength.
      const level = strength <= 3 ? "easy" : strength <= 6 ? "normal" : strength <= 8 ? "hard" : "expert";
      const ai = { type: "ai", level, strength };
      const seat = (heroId: string, team: string) => ({ heroId, team, controller: ai });
      const t0 = performance.now();
      let game = (await call({
        type: "game.start",
        setup: {
          mapId: "forgotten-island",
          seats: [seat("brogan", "red"), seat("arien", "red"), seat("wasp", "blue"), seat("tigerclaw", "blue")],
          length: "quick",
          tieBreaker: "red",
        },
      })).game;
      const startMs = performance.now() - t0;
      const cards: number[] = [];
      const other: number[] = [];
      for (let i = 0; i < decisions && game.pending; i++) {
        const kind = game.pending.kind;
        const s = performance.now();
        game = (await call({ type: "game.computer", decisionId: game.pending.id })).game;
        (kind === "play_card" ? cards : other).push(performance.now() - s);
      }
      worker.terminate();
      return { startMs, cards, other };
    }, { strength, decisions });
    const stats = (xs: number[]) => {
      const s = [...xs].sort((a, b) => a - b);
      const q = (p: number) => (s.length ? Math.round(s[Math.min(s.length - 1, Math.floor(p * s.length))]) : null);
      return { n: s.length, median: q(0.5), p90: q(0.9), max: s.length ? Math.round(s[s.length - 1]) : null };
    };
    results[String(strength)] = { firstStartMs: Math.round(r.startMs), cardPlay: stats(r.cards), otherDecisions: stats(r.other) };
  }
  mkdirSync(out, { recursive: true });
  const file = join(out, "ai-timing.json");
  writeFileSync(file, JSON.stringify({ browser: info.project.name, measuredAt: new Date().toISOString(), results }, null, 2));
  console.log(JSON.stringify(results, null, 2));
});
