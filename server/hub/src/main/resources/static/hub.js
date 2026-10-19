// The hub web app's shared behaviour (ADR-030 §8). Plain JS, no inline scripts (strict CSP).
// - registers the service worker (static cache + push notifications)
// - <dialog> open/close: data-dialog-open="<id>" on a button, data-dialog-close inside the dialog
// - the top bar's Back returns through history when it leads to the previous page (data-back)
// - --dock-measured: the fixed bottom bars' height, for sticky bottom actions
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

  // The top bar's Back: when the page it names is the one we came from (e.g. a hero after a
  // search), go back in history instead, so the search, filters and scroll position come back.
  document.addEventListener("click", function (event) {
    var back = event.target.closest("a[data-back]");
    if (!back || event.defaultPrevented || event.metaKey || event.ctrlKey || event.shiftKey) return;
    try {
      var from = new URL(document.referrer);
      var to = new URL(back.href);
      // data-back="previous" (Rules, Card trivia, Settings): back to any page of the app that opened
      // this one, except this page's own sub-pages (a back from those already went through history).
      var previous = back.getAttribute("data-back") === "previous" && from.pathname !== location.pathname &&
        from.pathname.indexOf(location.pathname + "/") !== 0;
      if (window.history.length > 1 && from.origin === location.origin && (from.pathname === to.pathname || previous)) {
        event.preventDefault();
        window.history.back();
      }
    } catch (e) {
      // No referrer: follow the link.
    }
  });

  // "Go back" on the status pages: the previous page when there is one, else the link (the app).
  document.addEventListener("click", function (event) {
    var link = event.target.closest("a[data-history-back]");
    if (link && window.history.length > 1 && document.referrer) {
      event.preventDefault();
      window.history.back();
    }
  });

  // --dock: the measured height of the fixed bottom bars (tabs, "timer running"), so sticky
  // bottom actions and the page's end sit just above them at any text size.
  var dock = document.querySelector(".bottom-dock");
  if (dock && "ResizeObserver" in window) {
    var root = document.documentElement;
    new ResizeObserver(function () {
      root.style.setProperty("--dock-measured", dock.getBoundingClientRect().height + "px");
      root.setAttribute("data-dock", "");
    }).observe(dock);
  }

  // A dialog whose content was swapped in by htmx can ask to close itself: HX-Trigger: closeDialogs
  document.body.addEventListener("closeDialogs", function () {
    document.querySelectorAll("dialog[open]").forEach(function (d) { d.close(); });
  });
})();
