import { expect, test } from "@playwright/test";
import { open, shot, title, waitForServiceWorker } from "./helpers";

// The PWA (ADR-043): the shell and catalog work offline after the first visit, images once seen or
// downloaded, and a new deploy waits for the person's "Reload".

test("after the first visit the app opens offline, deep link included", async ({ page, context }) => {
  await open(page, "#/heroes/arien");
  await expect(page.getByRole("img", { name: "Arien artwork" })).toBeVisible();
  await waitForServiceWorker(page);
  // The portrait was requested before the worker took over: open it again so it is cached.
  await page.reload();
  await expect(page.getByRole("img", { name: "Arien artwork" })).toBeVisible();

  await context.setOffline(true);
  await page.reload();
  await expect(title(page)).toHaveText("Arien");
  await expect(page.getByRole("heading", { name: "Arien" })).toBeVisible();
  await expect.poll(() => imageLoaded(page, "Arien artwork")).toBe(true);
  await page.getByRole("button", { name: "Back" }).click();
  await expect(page.getByRole("link", { name: /^Widget/ })).toBeVisible(); // every hero file precached
  await context.setOffline(false);
});

test("Download everything puts every image on the device", async ({ page, context }, info) => {
  test.slow();
  await open(page, "#/settings");
  await waitForServiceWorker(page);
  await page.getByRole("button", { name: "Download everything" }).click();
  await expect(page.getByRole("progressbar", { name: "Download progress" })).toBeVisible();
  await shot(page, info, "download-progress-dark");
  await expect(page.getByText("Everything is on this device", { exact: true })).toBeVisible({ timeout: 150_000 });
  await expect(page.getByText(/Last downloaded/)).toBeVisible(); // kept in IndexedDB

  await context.setOffline(true);
  await page.reload();
  await expect(page.getByText(/Last downloaded/)).toBeVisible();
  await page.goto("./#/heroes/widget");
  await expect.poll(() => imageLoaded(page, "Widget portrait")).toBe(true);
  await context.setOffline(false);
});

test("a new version waits for Reload", async ({ page, context, baseURL }, info) => {
  await open(page, "#/heroes");
  await waitForServiceWorker(page);
  await page.evaluate(() => ((window as unknown as { marker: boolean }).marker = true));
  // A new deploy, for this browser only: the server answers its sw.js requests with changed bytes.
  await context.addCookies([{ name: "goa-deploy", value: "2", url: baseURL! }]);
  await page.evaluate(async () => (await navigator.serviceWorker.getRegistration())?.update());
  const prompt = page.getByText("A new version of the app is ready.");
  await expect(prompt).toBeVisible({ timeout: 30_000 });
  await shot(page, info, "update-prompt-dark");
  const reloaded = page.waitForEvent("load");
  await page.getByRole("button", { name: "Reload" }).click();
  await reloaded;
  await expect(title(page)).toHaveText("Heroes");
  expect(await page.evaluate(() => (window as unknown as { marker?: boolean }).marker ?? false)).toBe(false);
  await expect(prompt).toBeHidden();
});

async function imageLoaded(page: import("@playwright/test").Page, alt: string): Promise<boolean> {
  // The hero art is a labelled frame around the portrait <img>.
  return page
    .getByRole("img", { name: alt })
    .locator("img")
    .evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth > 0);
}
