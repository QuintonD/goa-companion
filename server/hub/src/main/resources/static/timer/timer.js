// The web timer's clock (ADR-030 §9). Plain JS, no inline scripts (strict CSP).
// The hub runs the session (the same TimerEngine as the phones) and renders every label; this file
// only counts down from the hub's instants, with the hub's clock offset measured on every response,
// never by counting ticks. At zero it chimes and, with auto-advance, asks the hub to move on.
// - Screen Wake Lock while a session is on screen (re-acquired when the page comes back)
// - chimes through Web Audio, unlocked by a tap (the Start tap: Start swaps this page in place)
// - cues at the step's thresholds (Sounds / Haptics settings; navigator.vibrate where it exists)
// - re-syncs with the hub when the page comes back into view (a locked iPhone suspends it)
// - lock-screen alerts: subscribes through the core's push.js from a tap
(function () {
  "use strict";

  var TICK_MS = 250;
  var STALE_CUE_MS = 2000; // a longer gap (page was hidden) doesn't replay old cues
  var SHOT_CUES = [10, 5, 0];
  var SYNC_GAP_MS = 1500;

  var offset = 0; // hub clock - this device's clock
  var root = null; // the current #timer-root
  var view = null; // what was read from it
  var lastStep = null;
  var cue = { key: null, elapsed: 0 };
  var shot = { key: null, remaining: 0 };
  var dueAsked = null;
  var lastSync = 0;
  var lastScroll = 0;

  // ---- time --------------------------------------------------------------------------------

  function now() { return Date.now() + offset; }

  // TimerText.duration: "m:ss"; a countdown rounds up (0:01 until it truly hits zero).
  function fmt(ms, roundUp) {
    var total = roundUp ? Math.floor((ms + 999) / 1000) : Math.floor(ms / 1000);
    if (total < 0) total = 0;
    var m = Math.floor(total / 60);
    var s = total % 60;
    return m + ":" + (s < 10 ? "0" : "") + s;
  }

  function num(el, name) {
    var v = el.getAttribute(name);
    return v === null || v === "" ? null : Number(v);
  }

  /** Offset from a page load: the hub's time against when its response arrived. */
  function offsetFromLoad(serverNow) {
    var nav = performance.getEntriesByType && performance.getEntriesByType("navigation")[0];
    var arrived = nav ? performance.timeOrigin + nav.responseStart : Date.now();
    offset = serverNow - arrived;
  }

  // htmx responses carry X-Server-Now: offset = hub time - the request's midpoint.
  document.addEventListener("htmx:beforeRequest", function (e) { e.detail.xhr._goaSent = Date.now(); });
  document.addEventListener("htmx:afterRequest", function (e) {
    var xhr = e.detail.xhr;
    var server = xhr && xhr.getResponseHeader && xhr.getResponseHeader("X-Server-Now");
    if (server && xhr._goaSent) offset = Number(server) - (xhr._goaSent + Date.now()) / 2;
  });

  // ---- reading the page --------------------------------------------------------------------

  function read(el) {
    return {
      status: el.getAttribute("data-status"),
      stepKey: el.getAttribute("data-step-key"),
      zeroAt: num(el, "data-zero-at"),
      duration: num(el, "data-duration"),
      matchZeroAt: num(el, "data-match-zero-at"),
      shotZeroAt: num(el, "data-shot-zero-at"),
      shotDuration: num(el, "data-shot-duration"),
      cues: (el.getAttribute("data-cues") || "").split(",").filter(Boolean).map(Number),
      warnMs: num(el, "data-warn-ms"),
      autoAdvance: el.getAttribute("data-auto-advance") === "true",
      sounds: el.getAttribute("data-sounds") !== "false",
      haptics: el.getAttribute("data-haptics") !== "false",
      announce: el.getAttribute("data-announce") || "",
      session: el.getAttribute("data-session"),
      round: el.classList.contains("tr-round")
    };
  }

  function attach(el, fromLoad) {
    if (fromLoad) offsetFromLoad(num(el, "data-server-now") || Date.now());
    root = el;
    view = read(el);
    if (lastStep !== null && view.stepKey !== lastStep) announce(view.announce);
    if (view.round && view.stepKey !== lastStep) scrollCurrent(lastStep === null);
    lastStep = view.stepKey;
    document.body.classList.toggle("has-tabs", !!document.querySelector(".tab-bar"));
    showPushBanner();
    showSoundHint();
    wake(view.status === "running" || view.status === "paused");
    tick();
  }

  function announce(text) {
    var live = document.getElementById("timer-announce");
    if (live && text) live.textContent = text;
  }

  // ---- the tick ----------------------------------------------------------------------------

  function tick() {
    if (!root || !document.body.contains(root)) {
      var found = document.getElementById("timer-root");
      if (found && found.hasAttribute("data-timer")) attach(found, false);
      else { root = null; view = null; return; }
    }
    if (view.status !== "running") return;
    var t = now();
    var elapsed = t - view.zeroAt;
    var clock = root.querySelector("[data-clock]");
    var label = root.querySelector("[data-clock-label]");
    var progress = root.querySelector("[data-progress]");
    if (view.duration !== null) {
      var remaining = view.duration - elapsed;
      if (clock) clock.textContent = remaining >= 0 ? fmt(remaining, true) : "+" + fmt(-remaining, false);
      if (progress) progress.value = Math.round(Math.max(0, Math.min(1, remaining / view.duration)) * 1000);
      var over = remaining < 0;
      root.classList.toggle("is-overtime", over);
      root.classList.toggle("is-warning", !over && view.warnMs !== null && remaining <= view.warnMs);
      if (label && over) label.textContent = "Overtime";
      cues(elapsed);
      if (view.autoAdvance && remaining <= 0) askToAdvance();
    } else if (clock) {
      clock.textContent = fmt(elapsed, false);
    }
    var match = root.querySelector("[data-match-clock]");
    if (match && view.matchZeroAt !== null) match.textContent = fmt(t - view.matchZeroAt, false);
    shotClock(t);
  }

  function cues(elapsed) {
    var key = view.stepKey + ":" + view.zeroAt;
    if (key === cue.key && elapsed - cue.elapsed <= STALE_CUE_MS) {
      var crossed = view.cues.slice().sort(function (a, b) { return b - a; }).filter(function (c) {
        var at = view.duration - c * 1000;
        return at >= 0 && cue.elapsed < at && at <= elapsed;
      });
      if (crossed.length) play(crossed[crossed.length - 1]);
    }
    cue.key = key;
    cue.elapsed = elapsed;
  }

  function shotClock(t) {
    var panel = root.querySelector("[data-shot]");
    if (!panel || view.shotZeroAt === null || view.shotDuration === null) { shot.key = null; return; }
    var remaining = view.shotDuration - (t - view.shotZeroAt);
    var text = panel.querySelector("[data-shot-clock]");
    if (text) text.textContent = fmt(Math.max(0, remaining), true);
    var over = remaining <= 0;
    panel.classList.toggle("is-over", over);
    panel.classList.toggle("is-warning", remaining > 0 && remaining <= 10000);
    var label = panel.querySelector("[data-shot-label]");
    if (label) label.textContent = over ? "HURRY UP · TIME!" : "HURRY UP";
    var key = view.stepKey + ":" + view.shotZeroAt;
    if (key === shot.key && shot.remaining - remaining <= STALE_CUE_MS) {
      var crossed = SHOT_CUES.filter(function (c) { return c * 1000 >= remaining && c * 1000 < shot.remaining; });
      if (crossed.length) play(crossed[crossed.length - 1]);
    }
    shot.key = key;
    shot.remaining = remaining;
  }

  /** At zero with auto-advance: ask the hub once per phase end (it checks with its own clock). */
  function askToAdvance() {
    var key = view.stepKey + ":" + view.zeroAt;
    if (dueAsked && dueAsked.key === key && Date.now() - dueAsked.at < 1000) return;
    if (dueAsked && dueAsked.key === key && dueAsked.tries >= 5) return;
    dueAsked = { key: key, at: Date.now(), tries: dueAsked && dueAsked.key === key ? dueAsked.tries + 1 : 1 };
    command("due");
  }

  function command(cmd) {
    if (!root || !window.htmx) return;
    window.htmx.ajax("POST", "/timer/run/cmd", { source: root, target: root, swap: "outerHTML", values: { cmd: cmd } });
  }

  // ---- sound and vibration -----------------------------------------------------------------

  var audio = null;
  var chime = null;

  function unlockAudio() {
    var Ctx = window.AudioContext || window.webkitAudioContext;
    if (!Ctx) return;
    if (!audio) {
      audio = new Ctx();
      fetch("/static/timer/phase_chime.wav", { credentials: "same-origin" })
        .then(function (r) { return r.arrayBuffer(); })
        .then(function (bytes) { return new Promise(function (ok, fail) { audio.decodeAudioData(bytes, ok, fail); }); })
        .then(function (buffer) { chime = buffer; })
        .catch(function () { chime = null; });
    }
    if (audio.state !== "running") audio.resume();
    // A silent blip inside the tap is what iOS needs to let later sounds play.
    var silent = audio.createBuffer(1, 1, 22050);
    var src = audio.createBufferSource();
    src.buffer = silent;
    src.connect(audio.destination);
    src.start(0);
    setTimeout(showSoundHint, 300);
  }

  function beep(ms, freq) {
    var osc = audio.createOscillator();
    var gain = audio.createGain();
    osc.frequency.value = freq;
    gain.gain.setValueAtTime(0.0001, audio.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.4, audio.currentTime + 0.01);
    gain.gain.exponentialRampToValueAtTime(0.0001, audio.currentTime + ms / 1000);
    osc.connect(gain);
    gain.connect(audio.destination);
    osc.start();
    osc.stop(audio.currentTime + ms / 1000 + 0.05);
  }

  /** [secondsLeft]: the threshold crossed; 0 = time's up (the chime and a long buzz). */
  function play(secondsLeft) {
    var final = secondsLeft === 0;
    if (view.sounds && audio && audio.state === "running") {
      if (final && chime) {
        var src = audio.createBufferSource();
        src.buffer = chime;
        src.connect(audio.destination);
        src.start(0);
      } else {
        beep(final ? 600 : 150, final ? 660 : 880);
      }
    }
    if (view.haptics && navigator.vibrate) {
      navigator.vibrate(final ? [250, 120, 250, 120, 450] : [60]);
    }
  }

  function showSoundHint() {
    var hint = root && root.querySelector("[data-sound-unlock]");
    if (!hint) return;
    hint.hidden = !!(audio && audio.state === "running");
  }

  // Any tap on the page can unlock audio; Start and Next are marked for it, but any will do.
  document.addEventListener("click", function (e) {
    var hinted = e.target.closest("[data-sound-unlock]");
    if (hinted || !audio || audio.state !== "running") unlockAudio();
  }, true);

  // ---- wake lock ---------------------------------------------------------------------------

  var lock = null;
  var wantLock = false;

  function wake(on) {
    wantLock = on;
    if (!("wakeLock" in navigator)) return;
    if (on && !lock && document.visibilityState === "visible") {
      navigator.wakeLock.request("screen").then(function (l) {
        lock = l;
        l.addEventListener("release", function () { lock = null; });
      }).catch(function () { lock = null; });
    } else if (!on && lock) {
      lock.release().catch(function () {});
      lock = null;
    }
  }

  // ---- coming back into view ---------------------------------------------------------------

  function resync() {
    if (document.visibilityState !== "visible") return;
    if (wantLock) wake(true);
    if (audio && audio.state !== "running") audio.resume().then(showSoundHint, showSoundHint);
    if (!root || Date.now() - lastSync < SYNC_GAP_MS) return;
    lastSync = Date.now();
    command("sync");
  }

  document.addEventListener("visibilitychange", resync);
  window.addEventListener("focus", resync);
  window.addEventListener("pageshow", function (e) { if (e.persisted) resync(); });

  // ---- round view: keep the current step in view, but never while someone scrolls ----------

  window.addEventListener("scroll", function () { lastScroll = Date.now(); }, { passive: true });

  function scrollCurrent(first) {
    if (!first && Date.now() - lastScroll < 1500) return;
    var row = root && root.querySelector("[data-current-row]");
    if (!row) return;
    var reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    var target = row.previousElementSibling && !row.previousElementSibling.classList.contains("tr-group")
      ? row.previousElementSibling : row;
    requestAnimationFrame(function () {
      target.scrollIntoView({ block: "start", behavior: reduce || first ? "auto" : "smooth" });
    });
  }

  document.addEventListener("click", function (e) {
    var jump = e.target.closest("[data-jump]");
    if (!jump) return;
    var dialog = document.getElementById("jump-dialog");
    if (!dialog) return;
    e.preventDefault();
    dialog.querySelector("h2").textContent = "Jump to " + jump.getAttribute("data-jump-name") + "?";
    dialog.querySelector("[data-jump-confirm]").value = jump.getAttribute("data-jump");
    dialog.showModal();
  });

  // ---- confirmations, lock-screen alerts ---------------------------------------------------

  document.addEventListener("submit", function (e) {
    var by = e.submitter;
    var text = by && by.getAttribute("data-confirm");
    if (text && !window.confirm(text)) e.preventDefault();
  }, true);

  var PUSH_TEXT = {
    denied: "Notifications are blocked for this site. Allow them in the browser's settings " +
      "(iPhone: Settings → Notifications → GoA).",
    unsupported: "This browser can't show lock-screen alerts here. On iPhone, add the app to the Home Screen " +
      "(Share → Add to Home Screen, iOS 16.4 or later) and open it from there.",
    failed: "Couldn't turn them on. Check the connection to the hub and try again."
  };

  function pushState(text) {
    document.querySelectorAll("[data-push-state]").forEach(function (el) { el.textContent = text; });
  }

  document.addEventListener("click", function (e) {
    var sub = e.target.closest("[data-push-subscribe]");
    var unsub = e.target.closest("[data-push-unsubscribe]");
    var test = e.target.closest("[data-push-test]");
    var dismiss = e.target.closest("[data-push-dismiss]");
    if (!(sub || unsub || test || dismiss)) return;
    e.preventDefault();
    if (dismiss) {
      var banner = dismiss.closest("[data-push-banner]");
      try { localStorage.setItem("goa.timer.pushBanner", banner.getAttribute("data-push-banner")); } catch (x) {}
      banner.hidden = true;
      return;
    }
    if (!window.goaPush) return;
    if (sub) {
      // Asked from this tap: iOS shows the permission prompt only for a user gesture.
      window.goaPush.subscribe().then(function (result) {
        if (result === "granted") {
          if (root) { command("sync"); } else { window.location.reload(); }
          pushState("On for this browser.");
        } else {
          pushState(PUSH_TEXT[result] || PUSH_TEXT.failed);
          var banner = document.querySelector("[data-push-banner] p");
          if (banner) banner.textContent = PUSH_TEXT[result] || PUSH_TEXT.failed;
        }
      }).catch(function () { pushState(PUSH_TEXT.failed); });
    } else if (unsub) {
      window.goaPush.unsubscribe().then(function () { window.location.reload(); }, function () { pushState(PUSH_TEXT.failed); });
    } else if (test) {
      window.goaPush.test().then(function (sent) {
        pushState(sent > 0 ? "Test sent. It should appear in a moment." : "Nothing was sent: subscribe again.");
      }, function () { pushState(PUSH_TEXT.failed); });
    }
  });

  function showPushBanner() {
    var banner = root && root.querySelector("[data-push-banner]");
    if (!banner) return;
    var dismissed = null;
    try { dismissed = localStorage.getItem("goa.timer.pushBanner"); } catch (x) {}
    banner.hidden = dismissed === banner.getAttribute("data-push-banner");
  }

  // Show the browser's own state on the alerts card (supported? blocked?).
  function describePush() {
    var card = document.querySelector("[data-push-card]");
    if (!card || !window.goaPush) return;
    if (!window.goaPush.supported()) { if (card.getAttribute("data-push-on") !== "true") pushState(PUSH_TEXT.unsupported); return; }
    if (window.Notification && Notification.permission === "denied") pushState(PUSH_TEXT.denied);
  }

  // ---- start -------------------------------------------------------------------------------

  function boot() {
    var el = document.getElementById("timer-root");
    if (el && el.hasAttribute("data-timer")) {
      attach(el, true);
      // The page GET changes nothing; this POST stores the catch-up, alerts for a phase end nobody
      // heard, and re-arms the lock-screen push (e.g. after alerts were turned on in Settings).
      if (view.status === "running" || view.status === "paused") { lastSync = Date.now(); command("sync"); }
    }
    describePush();
  }

  document.addEventListener("htmx:afterSettle", function () {
    var el = document.getElementById("timer-root");
    if (el && el !== root && el.hasAttribute("data-timer")) attach(el, false);
    describePush();
  });

  // The pure part, for test/js/timer-format.test.mjs.
  window.goaTimer = { fmt: fmt };

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", boot);
  else boot();
  setInterval(tick, TICK_MS);
})();
