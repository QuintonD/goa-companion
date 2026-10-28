// Starts the network guard (guard.mjs) for the whole run; Playwright calls the returned function
// at the end of the run to stop it.
import { guardPort, startGuard } from "./guard.mjs";

export default async function globalSetup() {
  const sitePort = Number(process.env.GOA_PORT ?? 4173);
  const server = await startGuard(guardPort(sitePort), process.env.GOA_GUARD_LOG);
  return async () => {
    await new Promise((resolve) => server.close(() => resolve()));
  };
}
