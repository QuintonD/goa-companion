// Card trivia (ADR-028) in the browser: after an answer the feedback and the card scan come into
// view and Next takes the focus; a new question focuses its heading; Back once a question is
// answered asks "Leave this round?" (the page /trivia/quiz/leave without JS); the hero picker filter.
(function () {
  "use strict";

  var reduceMotion = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  function afterQuizSwap() {
    var quiz = document.getElementById("quiz");
    if (!quiz) return;
    if (quiz.getAttribute("data-answered") === "true") {
      var next = document.getElementById("quiz-next");
      if (next) next.focus({ preventScroll: true });
      var reveal = document.getElementById("reveal");
      if (reveal) {
        var bring = function () { reveal.scrollIntoView({ behavior: reduceMotion ? "auto" : "smooth", block: "nearest" }); };
        bring();
        // Once the scan has loaded it is taller: bring it into view again.
        reveal.querySelectorAll("img").forEach(function (img) {
          if (!img.complete) img.addEventListener("load", bring, { once: true });
        });
      }
    } else {
      var question = document.getElementById("question");
      if (question) question.focus({ preventScroll: true });
      window.scrollTo({ top: 0, behavior: "auto" });
    }
  }

  function requestPath(event) {
    var config = event.detail && event.detail.requestConfig;
    return (config && config.path) || "";
  }

  document.body.addEventListener("htmx:afterSettle", function (event) {
    if (requestPath(event).indexOf("/trivia/quiz") === 0) afterQuizSwap();
  });

  // Back from a started round asks first (the answers are saved; the round's results would go).
  document.addEventListener("click", function (event) {
    var back = event.target.closest && event.target.closest('a[href="/trivia/quiz/leave"]');
    if (!back) return;
    var quiz = document.getElementById("quiz");
    var dialog = document.getElementById("leave-dialog");
    if (quiz && quiz.getAttribute("data-started") === "true" && dialog && typeof dialog.showModal === "function") {
      event.preventDefault();
      dialog.showModal();
    }
  });

  // The hero picker's search: hides heroes whose name or title doesn't contain the text.
  document.addEventListener("input", function (event) {
    var input = event.target;
    if (!input.hasAttribute || !input.hasAttribute("data-hero-filter")) return;
    var query = input.value.trim().toLowerCase();
    var picker = input.closest(".picker-body");
    if (!picker) return;
    picker.querySelectorAll(".picker-row[data-name]").forEach(function (row) {
      row.hidden = query !== "" && row.getAttribute("data-name").indexOf(query) < 0;
    });
    var all = document.getElementById("act-all");
    if (all) all.hidden = query !== "";
  });

  // Keep the picker open across htmx swaps of the setup (choosing several heroes in a row).
  var pickerOpen = false;
  document.body.addEventListener("htmx:beforeSwap", function (event) {
    if (requestPath(event) !== "/trivia") return;
    var picker = document.querySelector(".hero-picker");
    pickerOpen = !!(picker && picker.open);
  });
  document.body.addEventListener("htmx:afterSettle", function (event) {
    if (requestPath(event) !== "/trivia") return;
    var picker = document.querySelector(".hero-picker");
    if (picker && pickerOpen) picker.open = true;
  });
})();
