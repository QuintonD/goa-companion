import { defineConfig, devices } from "@playwright/test";

// The built site (`:web:app:site`) served under /goa-companion/ as on GitHub Pages.
// WebKit with the iPhone 15 profile stands in for iOS Safari; Chromium with Pixel 7 for Android Chrome.
const port = Number(process.env.GOA_PORT ?? 4173);

export default defineConfig({
  testDir: "./tests",
  timeout: 90_000,
  expect: { timeout: 10_000 },
  fullyParallel: true,
  workers: process.env.CI ? 2 : 3,
  retries: process.env.CI ? 1 : 0,
  reporter: [["list"], ["html", { open: "never", outputFolder: "playwright-report" }]],
  use: {
    baseURL: `http://localhost:${port}/goa-companion/`,
    trace: "retain-on-failure",
    serviceWorkers: "allow",
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
