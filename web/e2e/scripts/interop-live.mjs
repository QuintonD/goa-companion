// Live interop proof (ADR-043 W2): the phone's sync code and the web app's, through a real relay.
//
//   1. starts server/relay (build it first: cd server/relay && ./gradlew installDist) on 127.0.0.1:8799
//      with CORS for the test site (http://localhost:4173);
//   2. JVM: LiveRelayInteropTest.phoneUploads — Room + OkHttp + javax.crypto upload three matches;
//   3. browser (WebKit, iPhone 15): opens the upload, must show the phone's exact ratings, logs a match;
//   4. JVM: a fresh phone syncs and must get the browser's match, and the same ratings the browser shows.
//
//   node scripts/interop-live.mjs        (from web/e2e, after :web:app:site; Windows or POSIX)
import { spawn, spawnSync } from "node:child_process";
import { randomBytes, randomUUID } from "node:crypto";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = fileURLToPath(new URL(".", import.meta.url));
const repo = resolve(here, "..", "..", "..");
const windows = process.platform === "win32";
const port = 8799;
const url = `http://127.0.0.1:${port}`;
const work = mkdtempSync(join(tmpdir(), "goa-live-"));
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
    GOA_RELAY_PORT: String(port),
    GOA_RELAY_CORS_ORIGINS: "http://localhost:4173",
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
  run("npx", ["playwright", "test", "tests/interop.spec.ts", "--project=webkit-iphone15", "--reporter=line"], {
    cwd: resolve(here, ".."),
    env: { ...process.env, GOA_LIVE_RELAY: join(work, "live.json") },
  });
  const jvmAfter = join(work, "jvm-check.txt");
  jvm("check", jvmAfter);
  const sorted = (file) => readFileSync(file, "utf8").split(",").sort().join(",");
  const browser = sorted(live.browserRatings);
  const phone = sorted(jvmAfter);
  console.log(`\nbrowser ratings: ${browser}\nphone ratings:   ${phone}`);
  if (browser !== phone) throw new Error("the browser and the phone disagree on the ratings");
  console.log("\nLive interop: phone → relay → browser → relay → phone, same data and same ratings. OK");
} finally {
  if (windows) spawnSync("taskkill", ["/pid", String(relay.pid), "/T", "/F"]);
  else relay.kill();
  rmSync(work, { recursive: true, force: true });
}
