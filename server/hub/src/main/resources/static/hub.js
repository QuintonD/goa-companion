// The hub web app's shared behaviour (ADR-030 §8). Plain JS, no inline scripts (strict CSP).
// - registers the service worker (static cache + push notifications)
// - <dialog> open/close: data-dialog-open="<id>" on a button, data-dialog-close inside the dialog
// - htmx: sends nothing extra; the hub checks Origin on every POST
(function () {
  "use strict";

  if ("serviceWorker" in navigator) {
    window.addEventListener("load", function () {
      navigator.serviceWorker.register("/sw.js", { scope: "/" }).catch(function () {
        // Private browsing or an old browser: the app works without it.
      });
    });
  }

  document.addEventListener("click", function (event) {
    var opener = event.target.closest("[data-dialog-open]");
    if (opener) {
      var dialog = document.getElementById(opener.getAttribute("data-dialog-open"));
      if (dialog && typeof dialog.showModal === "function") {
        event.preventDefault();
        dialog.showModal();
      }
      return;
    }
    var closer = event.target.closest("[data-dialog-close]");
    if (closer) {
      var parent = closer.closest("dialog");
      if (parent) {
        event.preventDefault();
        parent.close();
      }
      return;
    }
    // A click on the backdrop (the dialog element itself, outside its content) closes it.
    if (event.target.tagName === "DIALOG" && event.target.open) {
      var rect = event.target.getBoundingClientRect();
      var inside = event.clientX >= rect.left && event.clientX <= rect.right &&
        event.clientY >= rect.top && event.clientY <= rect.bottom;
      if (!inside) event.target.close();
    }
  });

  // A dialog whose content was swapped in by htmx can ask to close itself: HX-Trigger: closeDialogs
  document.body.addEventListener("closeDialogs", function () {
    document.querySelectorAll("dialog[open]").forEach(function (d) { d.close(); });
  });
})();
