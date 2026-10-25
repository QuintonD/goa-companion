// The engine (rules, every hero, the computer players, the puzzles) is a big bundle on its own; it
// loads once, in the background, and the service worker keeps it. Warn only well past its size.
config.performance = {
  hints: "warning",
  maxAssetSize: 8 * 1024 * 1024,
  maxEntrypointSize: 8 * 1024 * 1024,
};
// Runs as a dedicated worker (no window, no DOM).
config.target = "webworker";
