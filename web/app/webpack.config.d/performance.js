// The Compose runtime makes app.js ~1 MB minified (~250 KB gzipped, cached by the service worker).
// Warn only if it grows well past that.
config.performance = {
  hints: "warning",
  maxAssetSize: 1536 * 1024,
  maxEntrypointSize: 1536 * 1024,
};
