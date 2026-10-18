// Players area (ADR-030 §8). Plain JS, no inline scripts (strict CSP). Everything works without it:
// - "Share stats page" opens the system share sheet with the HTML file where the browser can share
//   files (iOS Safari, Android Chrome); otherwise the link downloads the file as before.
// - The match editor asks before leaving with unsaved edits, as the phone does.
(function () {
  "use strict";

  var STATS = "/players/stats-page";

  document.addEventListener("click", function (event) {
    var link = event.target.closest("a[href='" + STATS + "']");
    if (!link || !navigator.canShare || typeof File !== "function") return;
    var probe = new File([""], "probe.html", { type: "text/html" });
    if (!navigator.canShare({ files: [probe] })) return;
    event.preventDefault();
    fetch(STATS, { credentials: "same-origin" })
      .then(function (response) {
        if (!response.ok) throw new Error("HTTP " + response.status);
        var disposition = response.headers.get("Content-Disposition") || "";
        var match = /filename="?([^";]+)"?/.exec(disposition);
        var name = match ? match[1] : "goa-stats.html";
        return response.blob().then(function (blob) {
          var file = new File([blob], name, { type: "text/html" });
          return navigator.share({ files: [file], title: "Guards of Atlantis stats" });
        });
      })
      .catch(function (error) {
        // Cancelled by the user: nothing to do. Anything else: fall back to the download.
        if (error && error.name === "AbortError") return;
        window.location.href = STATS;
      });
  });

  // Unsaved edits in the match editor: the form keeps its id across htmx swaps.
  var dirty = false;
  var leaving = false;

  function editor() {
    return document.getElementById("match-editor");
  }

  function markFromForm() {
    var form = editor();
    if (form && form.getAttribute("data-unsaved") === "true") dirty = true;
  }

  document.addEventListener("input", function (event) {
    if (event.target.closest && event.target.closest("#match-editor")) dirty = true;
  });
  document.addEventListener("change", function (event) {
    if (event.target.closest && event.target.closest("#match-editor")) dirty = true;
  });
  document.addEventListener("submit", function (event) {
    var submitter = event.submitter;
    if (event.target.id === "match-editor" && (!submitter || submitter.value === "save")) leaving = true;
  }, true);
  document.addEventListener("htmx:afterSwap", function () {
    leaving = false;
    markFromForm();
  });
  window.addEventListener("beforeunload", function (event) {
    if (dirty && !leaving && editor()) {
      event.preventDefault();
      event.returnValue = "";
    }
  });
  markFromForm();
})();
