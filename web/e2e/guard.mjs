// The suite's network guard (web review R2): every browser Playwright launches sends its traffic
// to the internet through this "proxy", which refuses all of it, so no test can ever reach the
// production relay (oldboi.taila4d38d.ts.net) or anything else outside this machine — whatever
// sends the request: the page, the service worker (WebKit sends a controlled page's fetches past
// Playwright's routes) or a Web Worker. localhost and 127.0.0.1 bypass it (the site, the fake relay,
// server/relay). Every refused host is appended to GOA_GUARD_LOG, so a test can prove it was stopped.
import { createServer } from "node:http";
import { appendFileSync, writeFileSync } from "node:fs";

export const PRODUCTION_RELAY_HOST = "oldboi.taila4d38d.ts.net";

export function guardPort(sitePort) {
  return Number(process.env.GOA_GUARD_PORT ?? sitePort + 20000);
}

/** Starts the refusing proxy on [port]; [log] gets one JSON line per refused request. */
export function startGuard(port, log) {
  writeFileSync(log, "");
  const record = (kind, target) => appendFileSync(log, JSON.stringify({ kind, target, at: Date.now() }) + "\n");
  const server = createServer((req, res) => {
    // A plain-HTTP request through a proxy names the absolute URL.
    record("http", req.url);
    res.writeHead(403, { "content-type": "text/plain", connection: "close" }).end("Blocked by the test guard");
  });
  // HTTPS through a proxy starts with CONNECT host:port: refuse it before any TLS.
  server.on("connect", (req, socket) => {
    record("connect", req.url);
    socket.end("HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n");
  });
  server.on("clientError", (_err, socket) => socket.destroy());
  return new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(port, "127.0.0.1", () => resolve(server));
  });
}
