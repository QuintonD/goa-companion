// R3 hands-on game night (ADR-043 Addendum R3): two iPhones (WebKit) and the Android phone's sync
// code (JVM) around a real relay on this machine.
//
//   1. starts server/relay (build it first: cd server/relay && ./gradlew installDist) on 127.0.0.1:8833
//      with CORS for the test site (http://localhost:4194, GOA_PORT);
//   2. JVM: LiveRelayInteropTest.phoneUploads — the phone uploads four players and three matches;
//   3. WebKit: tests/r3-game-night.spec.ts — iPhone A joins from the phone's invite link (Home Screen
//      mode), drafts, runs the timer through a locked screen, logs the result; iPhone B joins; trivia,
//      board, the computer at 5 and 10, a puzzle with nudges, offline and back;
//   4. JVM: a fresh phone syncs and must get the iPhones' match and the ratings they show.
//
//   GOA_PORT=4194 node scripts/r3-game-night.mjs   (from web/e2e, after :web:app:site)
import { spawn, spawnSync } from "node:child_process";
import { randomBytes, randomUUID } from "node:crypto";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = fileURLToPath(new URL(".", import.meta.url));
const repo = resolve(here, "..", "..", "..");
const windows = process.platform === "win32";
const relayPort = Number(process.env.GOA_R3_RELAY_PORT ?? 8833);
const sitePort = Number(process.env.GOA_PORT ?? 4194);
const url = `http://127.0.0.1:${relayPort}`;
const work = mkdtempSync(join(process.env.GOA_R3_TMP ?? tmpdir(), "goa-r3-"));
const live = {
  url,
  groupId: randomUUID(),
  keyHex: randomBytes(32).toString("hex"),
  jvmRatings: join(work, "jvm-seed.txt"),
  browserRatings: join(work, "browser.txt"),
};

function run(command, args, options = {}) {
  console.log(`\n$ ${command} ${args.join(" ")}`);
  const result = spawnSync(command, args, { stdio: "inherit", shell: windows, ...options });
  if (result.status !== 0) throw new Error(`${command} failed (${result.status})`);
}

function jvm(phase, out) {
  const config = join(repo, "core", "data", "build", "live-relay.json");
  mkdirSync(join(repo, "core", "data", "build"), { recursive: true });
  writeFileSync(config, JSON.stringify({ ...live, phase, out }));
  try {
    run(windows ? `"${join(repo, "gradlew.bat")}"` : "./gradlew", [
      "--max-workers=3", ":core:data:testDebugUnitTest", "--tests", "*LiveRelayInteropTest*", "--rerun",
    ], { cwd: repo });
  } finally {
    rmSync(config, { force: true });
  }
}

const relayBin = join(repo, "server", "relay", "build", "install", "goa-relay", "bin", windows ? "goa-relay.bat" : "goa-relay");
const relay = spawn(relayBin, [], {
  shell: windows,
  stdio: "inherit",
  env: {
    ...process.env,
    GOA_RELAY_DATA: join(work, "relay"),
    GOA_RELAY_PORT: String(relayPort),
    GOA_RELAY_CORS_ORIGINS: `http://localhost:${sitePort}`,
  },
});
try {
  for (let i = 0; ; i++) {
    try {
      if ((await fetch(`${url}/v1/health`)).ok) break;
    } catch {}
    if (i > 60) throw new Error("the relay didn't start");
    await new Promise((r) => setTimeout(r, 500));
  }
  jvm("seed", live.jvmRatings);
  writeFileSync(join(work, "live.json"), JSON.stringify(live));
  run("npx", ["playwright", "test", "tests/r3-game-night.spec.ts", "--project=webkit-iphone15", "--workers=1", "--reporter=line"], {
    cwd: resolve(here, ".."),
    env: { ...process.env, GOA_PORT: String(sitePort), GOA_R3_LIVE: join(work, "live.json") },
  });
  const jvmAfter = join(work, "jvm-check.txt");
  jvm("check", jvmAfter);
  const sorted = (file) => readFileSync(file, "utf8").split(",").sort().join(",");
  const browser = sorted(live.browserRatings);
  const phone = sorted(jvmAfter);
  console.log(`\niPhones' ratings: ${browser}\nphone's ratings:  ${phone}`);
  if (browser !== phone) throw new Error("the iPhones and the phone disagree on the ratings");
  console.log("\nGame night: phone → relay → two iPhones → relay → phone, same data and same ratings. OK");
} finally {
  if (windows) spawnSync("taskkill", ["/pid", String(relay.pid), "/T", "/F"]);
  else relay.kill();
  rmSync(work, { recursive: true, force: true });
}
