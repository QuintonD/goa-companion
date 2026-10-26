// Code loaded on first use (the QR libraries, `import()` in InviteQr.kt) goes into chunks next to
// app.js (`<id>.chunk.js`); the site's asset manifest lists them, so the service worker precaches them.
config.output = config.output || {};
config.output.chunkFilename = "[name].chunk.js";
