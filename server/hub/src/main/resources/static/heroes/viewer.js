// The card viewer (ADR-030 §8): swipe is native (CSS scroll snap); this adds the arrow buttons and
// the keyboard (← →), keeps the position line, title and URL in step with the card on screen, and
// opens at the card that was tapped. Without it, the arrows are plain links to the next card.
(function () {
  "use strict";

  var track = document.getElementById("viewer-track");
  if (!track) return;
  var cards = Array.prototype.slice.call(track.querySelectorAll(".viewer-card"));
  var position = document.getElementById("viewer-position");
  var title = document.querySelector(".top-title");
  var prev = document.querySelector("[data-viewer=prev]");
  var next = document.querySelector("[data-viewer=next]");
  var current = indexOfPath(location.pathname);
  var reduceMotion = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  function indexOfPath(path) {
    for (var i = 0; i < cards.length; i++) {
      if (cards[i].getAttribute("data-href") === path) return i;
    }
    return 0;
  }

  function show(i, smooth) {
    if (i < 0 || i >= cards.length) return;
    cards[i].scrollIntoView({ behavior: smooth && !reduceMotion ? "smooth" : "auto", block: "nearest", inline: "center" });
  }

  /** The card whose centre is nearest the track's centre. */
  function visibleIndex() {
    var mid = track.getBoundingClientRect().left + track.clientWidth / 2;
    var best = 0;
    var bestDistance = Infinity;
    for (var i = 0; i < cards.length; i++) {
      var r = cards[i].getBoundingClientRect();
      var d = Math.abs(r.left + r.width / 2 - mid);
      if (d < bestDistance) { bestDistance = d; best = i; }
    }
    return best;
  }

  function setArrow(arrow, i) {
    if (!arrow) return;
    var enabled = i >= 0 && i < cards.length;
    arrow.classList.toggle("disabled", !enabled);
    arrow.setAttribute("aria-disabled", String(!enabled));
    if (enabled) {
      arrow.removeAttribute("tabindex");
      arrow.setAttribute("href", cards[i].getAttribute("data-href") + "#" + cards[i].id);
    } else {
      arrow.setAttribute("tabindex", "-1");
    }
  }

  function settle() {
    var i = visibleIndex();
    if (i === current) return;
    current = i;
    var card = cards[i];
    if (position) position.textContent = card.getAttribute("data-position");
    if (title) title.textContent = card.getAttribute("data-name");
    document.title = card.getAttribute("data-name") + " · Guards of Atlantis";
    history.replaceState(null, "", card.getAttribute("data-href"));
    setArrow(prev, i - 1);
    setArrow(next, i + 1);
  }

  var timer = 0;
  track.addEventListener("scroll", function () {
    clearTimeout(timer);
    timer = setTimeout(settle, 80);
  }, { passive: true });

  function step(delta, event) {
    var target = current + delta;
    if (target < 0 || target >= cards.length) return;
    if (event) event.preventDefault();
    show(target, true);
  }

  if (prev) prev.addEventListener("click", function (e) { step(-1, e); });
  if (next) next.addEventListener("click", function (e) { step(1, e); });
  document.addEventListener("keydown", function (e) {
    if (e.altKey || e.ctrlKey || e.metaKey) return;
    var tag = (e.target && e.target.tagName) || "";
    if (tag === "INPUT" || tag === "SELECT" || tag === "TEXTAREA") return;
    if (e.key === "ArrowLeft") step(-1, e);
    else if (e.key === "ArrowRight") step(1, e);
  });

  // Open at the tapped card (the page lists the whole deck).
  show(current, false);
  setArrow(prev, current - 1);
  setArrow(next, current + 1);
})();
