// Settings pages. Marks the page as scripted (the Save buttons hide: htmx saves each change), and
// drives "Lock-screen alerts" with push.js: Turn on / Send a test / Turn off, each from a tap.
(function () {
  "use strict";

  document.documentElement.classList.add("js");

  var card = document.querySelector("[data-push]");
  if (!card || !window.goaPush) return;
  var status = card.querySelector(".push-status");
  var buttons = {};
  card.querySelectorAll("[data-push-action]").forEach(function (b) {
    buttons[b.getAttribute("data-push-action")] = b;
  });
  var serverSaysOn = card.getAttribute("data-push") === "on";

  var ON = "On: the timer alerts this browser at each phase end, even when the phone is locked.";
  var OFF = "Off. Turn them on so a running timer can reach you when the phone is locked.";

  function show(state, text) {
    card.setAttribute("data-push", state);
    status.textContent = text;
    buttons.on.hidden = state !== "off";
    buttons.test.hidden = state !== "on";
    buttons.off.hidden = state !== "on";
  }

  function render() {
    if (!goaPush.supported()) {
      show("unsupported",
        "This browser can't show lock-screen alerts. On an iPhone: Share → Add to Home Screen, open the app " +
        "from that icon, then turn them on here (iOS 16.4 or later).");
      return;
    }
    if (Notification.permission === "denied") {
      show("blocked",
        "Notifications are blocked for this app. Allow them in the phone's settings (iPhone: Settings → " +
        "Notifications → the app), then come back here.");
      return;
    }
    navigator.serviceWorker.ready.then(function (registration) {
      return registration.pushManager.getSubscription();
    }).then(function (subscription) {
      // On only when both sides know it: this browser has a subscription and the hub has it too.
      show(subscription && serverSaysOn ? "on" : "off", subscription && serverSaysOn ? ON : OFF);
    }).catch(function () {
      show("off", OFF);
    });
  }

  function busy(button, on) {
    button.disabled = on;
    button.setAttribute("aria-busy", on ? "true" : "false");
  }

  buttons.on.addEventListener("click", function () {
    busy(buttons.on, true);
    goaPush.subscribe().then(function (result) {
      busy(buttons.on, false);
      if (result === "granted") {
        serverSaysOn = true;
        show("on", "On. Send a test to check it reaches this phone.");
      } else {
        render();
      }
    }).catch(function () {
      busy(buttons.on, false);
      status.textContent = "Couldn't turn them on. Check the connection to the hub and try again.";
    });
  });

  buttons.test.addEventListener("click", function () {
    busy(buttons.test, true);
    goaPush.test().then(function (sent) {
      busy(buttons.test, false);
      status.textContent = sent > 0
        ? "Test sent. It should appear in a few seconds."
        : "Nothing was sent: turn the alerts off and on again.";
    }).catch(function () {
      busy(buttons.test, false);
      status.textContent = "Couldn't send a test. Check the connection to the hub.";
    });
  });

  buttons.off.addEventListener("click", function () {
    busy(buttons.off, true);
    goaPush.unsubscribe().then(function () {
      busy(buttons.off, false);
      serverSaysOn = false;
      show("off", "Off. The timer won't alert this browser when the phone is locked.");
    }).catch(function () {
      busy(buttons.off, false);
      render();
    });
  });

  render();
})();
