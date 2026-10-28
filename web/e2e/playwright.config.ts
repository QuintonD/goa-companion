import { defineConfig, devices } from "@playwright/test";
import { mkdirSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { guardPort } from "./guard.mjs";

// The built site (`:web:app:site`) served under /goa-companion/ as on GitHub Pages.
// WebKit with the iPhone 15 profile stands in for iOS Safari; Chromium with Pixel 7 for Android Chrome.
const port = Number(process.env.GOA_PORT ?? 4173);

// The network guard (guard.mjs, started by global-setup.mjs): every browser of the run sends what
// isn't on this machine to a proxy that refuses it, so no test, service worker or Web Worker can
// reach the production relay. The log of refused hosts is shared with the workers through the env.
const here = fileURLToPath(new URL(".", import.meta.url));
mkdirSync(join(here, ".guard"), { recursive: true });
process.env.GOA_GUARD_LOG ??= join(here, ".guard", `refused-${port}.log`);
const guard = `http://127.0.0.1:${guardPort(port)}`;

export default defineConfig({
  testDir: "./tests",
  timeout: 90_000,
  expect: { timeout: 10_000 },
  fullyParallel: true,
  workers: process.env.CI ? 2 : 3,
  retries: process.env.CI ? 1 : 0,
  reporter: [["list"], ["html", { open: "never", outputFolder: "playwright-report" }]],
  globalSetup: "./global-setup.mjs",
  use: {
    baseURL: `http://localhost:${port}/goa-companion/`,
    trace: "retain-on-failure",
    serviceWorkers: "allow",
    // Browser-wide (every context, the service worker and workers included); this machine bypasses it.
    launchOptions: { proxy: { server: guard, bypass: "localhost,127.0.0.1" } },
  },
  projects: [
    { name: "webkit-iphone15", use: { ...devices["iPhone 15"] } },
    { name: "chromium-pixel7", use: { ...devices["Pixel 7"] } },
  ],
  webServer: {
    command: "node serve.mjs",
    url: `http://localhost:${port}/goa-companion/`,
    reuseExistingServer: !process.env.CI,
    env: { GOA_PORT: String(port) },
  },
});
