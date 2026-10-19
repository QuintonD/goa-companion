// The "timer running" bar on the other pages (the phone's running-timer bar, ADR-020): counts the
// current step from the hub's instants (never by ticks), keeps the screen awake while it runs, and
// refreshes itself from the hub when a step ends. Plain JS, CSP-safe (no inline script).
(function () {
  "use strict";
  if (window.goaTimerBar) return;
  window.goaTimerBar = true;

  var offset = 0;
  var refreshedFor = null;
  var lock = null;

  function fmt(ms, roundUp) {
    var total = roundUp ? Math.floor((ms + 999) / 1000) : Math.floor(ms / 1000);
    if (total < 0) total = 0;
    var m = Math.floor(total / 60);
    var s = total % 60;
    return m + ":" + (s < 10 ? "0" : "") + s;
  }

  function bar() { return document.querySelector("[data-timer-bar]"); }

  function measure() {
    var el = bar();
    if (!el) return;
    var nav = performance.getEntriesByType && performance.getEntriesByType("navigation")[0];
    var arrived = nav ? performance.timeOrigin + nav.responseStart : Date.now();
    offset = Number(el.getAttribute("data-server-now")) - arrived;
  }

  function tick() {
    var el = bar();
    if (!el || el.getAttribute("data-status") !== "running") return;
    var zero = Number(el.getAttribute("data-zero-at"));
    var duration = el.getAttribute("data-duration");
    var elapsed = Date.now() + offset - zero;
    var clock = el.querySelector("[data-clock]");
    if (duration === null || duration === "") {
      clock.textContent = fmt(elapsed, false);
      return;
    }
    var remaining = Number(duration) - elapsed;
    clock.textContent = remaining >= 0 ? fmt(remaining, true) : "+" + fmt(-remaining, false);
    el.classList.toggle("is-overtime", remaining < 0);
    // A step ended: ask the hub what runs now (auto-advance), once per step.
    var key = zero + ":" + duration;
    if (remaining < -1000 && refreshedFor !== key && window.htmx) {
      refreshedFor = key;
      window.htmx.trigger("#timer-bar-host", "timerbar");
    }
  }

  function wake() {
    var el = bar();
    var running = el && el.getAttribute("data-status") === "running";
    if (!("wakeLock" in navigator)) return;
    if (running && !lock && document.visibilityState === "visible") {
      navigator.wakeLock.request("screen").then(function (l) {
        lock = l;
        l.addEventListener("release", function () { lock = null; });
      }).catch(function () {});
    }
  }

  document.addEventListener("htmx:afterRequest", function (e) {
    var xhr = e.detail.xhr;
    if (!xhr._goaBarSent) return;
    var server = xhr.getResponseHeader("X-Server-Now");
    if (server) offset = Number(server) - (xhr._goaBarSent + Date.now()) / 2;
  });
  document.addEventListener("htmx:beforeRequest", function (e) {
    if (e.detail.elt && e.detail.elt.id === "timer-bar-host") e.detail.xhr._goaBarSent = Date.now();
  });
  document.addEventListener("visibilitychange", function () {
    if (document.visibilityState === "visible" && window.htmx && bar()) {
      window.htmx.trigger("#timer-bar-host", "timerbar");
      wake();
    }
  });
  document.addEventListener("htmx:afterSettle", wake);

  function boot() {
    measure();
    wake();
    tick();
    setInterval(tick, 500);
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", boot);
  else boot();
})();
