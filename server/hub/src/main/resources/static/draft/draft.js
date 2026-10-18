// The Draft area's small browser-side helpers (ADR-030 §8). Plain JS, no inline scripts (strict CSP).
// The hub decides everything; this file only:
// - reloads a draft page the browser restored from its back/forward cache, so an older board
//   (say, the moment before a hidden pick was covered) is never shown again;
// - counts the optional step clock down from the value the hub rendered;
// - filters the pool by name and role (progressive: without JS every hero shows);
// - keeps the custom-pool count in step with the ticks.
(function () {
  "use strict";

  window.addEventListener("pageshow", function (event) {
    if (event.persisted) {
      document.documentElement.classList.add("draft-reloading");
      window.location.reload();
    }
  });

  function format(seconds) {
    var over = seconds < 0;
    var s = Math.abs(seconds);
    var text = Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0");
    return (over ? "+" : "") + text;
  }

  document.querySelectorAll(".step-clock[data-remaining]").forEach(function (clock) {
    var remaining = parseInt(clock.getAttribute("data-remaining"), 10);
    if (isNaN(remaining)) return;
    var started = Date.now();
    var maxOvertime = 3600;
    function tick() {
      var left = remaining - Math.floor((Date.now() - started) / 1000);
      clock.textContent = format(left);
      clock.classList.toggle("over", left < 0);
      clock.setAttribute("aria-label", left < 0 ? "Overtime " + -left + " seconds" : left + " seconds left");
      if (left > -maxOvertime) window.setTimeout(tick, 1000 - ((Date.now() - started) % 1000));
    }
    tick();
  });

  var filters = document.querySelector("[data-pool-filters]");
  if (filters) {
    filters.hidden = false;
    var search = filters.querySelector("[data-pool-search]");
    var roleButtons = filters.querySelectorAll("[data-role-filter]");
    var tiles = document.querySelectorAll(".pool-grid .hero-tile");
    var apply = function () {
      var query = (search.value || "").trim().toLowerCase();
      var roles = [];
      roleButtons.forEach(function (b) {
        if (b.getAttribute("aria-pressed") === "true") roles.push(b.getAttribute("data-role-filter"));
      });
      tiles.forEach(function (tile) {
        var name = tile.getAttribute("data-name") || "";
        var heroRoles = (tile.getAttribute("data-roles") || "").split(" ");
        var byName = !query || name.indexOf(query) !== -1;
        var byRole = roles.length === 0 || roles.some(function (r) { return heroRoles.indexOf(r) !== -1; });
        tile.classList.toggle("filtered", !(byName && byRole));
      });
    };
    search.addEventListener("input", apply);
    roleButtons.forEach(function (b) {
      b.addEventListener("click", function () {
        var on = b.getAttribute("aria-pressed") !== "true";
        b.setAttribute("aria-pressed", String(on));
        b.classList.toggle("selected", on);
        apply();
      });
    });
  }

  var poolForm = document.querySelector("[data-pool-form]");
  if (poolForm) {
    var count = poolForm.querySelector("[data-pool-count]");
    var boxes = poolForm.querySelectorAll("input[type=checkbox][name=hero]");
    var update = function () {
      var n = 0;
      boxes.forEach(function (b) { if (b.checked) n++; });
      count.textContent = n + " of " + boxes.length + " chosen";
    };
    poolForm.addEventListener("change", update);
  }
})();
