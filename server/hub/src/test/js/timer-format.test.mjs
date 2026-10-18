// The web timer's one piece of client-side logic: "m:ss" as TimerText.duration writes it (a countdown
// rounds up, elapsed time rounds down). Run: node --test server/hub/src/test/js/timer-format.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

function load() {
  const noop = () => {};
  const window = { addEventListener: noop };
  const context = {
    window,
    document: { readyState: "complete", addEventListener: noop, getElementById: () => null, querySelector: () => null, querySelectorAll: () => [], body: { classList: { toggle: noop }, contains: () => false } },
    navigator: {},
    performance: { getEntriesByType: () => [], timeOrigin: 0 },
    setInterval: noop,
    setTimeout: noop,
    localStorage: { getItem: () => null, setItem: noop },
  };
  const source = readFileSync(new URL("../../main/resources/static/timer/timer.js", import.meta.url), "utf8");
  vm.runInNewContext(source, context);
  return window.goaTimer.fmt;
}

test("countdowns round up and elapsed time rounds down, as TimerText.duration", () => {
  const fmt = load();
  // [millis, roundUp, TimerText.duration(millis, roundUp)]
  const cases = [
    [30_000, true, "0:30"], [29_001, true, "0:30"], [29_000, true, "0:29"], [1, true, "0:01"], [0, true, "0:00"],
    [90_000, true, "1:30"], [59_999, false, "0:59"], [60_000, false, "1:00"], [3_599_999, false, "59:59"],
    [3_600_000, false, "60:00"], [125_400, false, "2:05"],
  ];
  for (const [ms, roundUp, expected] of cases) assert.equal(fmt(ms, roundUp), expected, `${ms} ${roundUp}`);
});
