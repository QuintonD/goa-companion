// Web Push helper (ADR-030 §9) for pages that offer notifications (Settings, the timer).
// Load with HubPage(scripts = listOf("push.js")); then, from a user tap:
//   goaPush.supported()            -> true if this browser can do it (iOS: 16.4+, home-screen app only)
//   goaPush.subscribe()            -> Promise<"granted" | "denied" | "unsupported">
//   goaPush.unsubscribe()          -> Promise
//   goaPush.test()                 -> Promise<number of notifications sent>
// iOS asks for permission only from a user gesture and only in an app added to the home screen.
(function () {
  "use strict";

  function supported() {
    return "serviceWorker" in navigator && "PushManager" in window && "Notification" in window;
  }

  function keyBytes(base64url) {
    var padded = (base64url + "===".slice((base64url.length + 3) % 4)).replace(/-/g, "+").replace(/_/g, "/");
    var raw = atob(padded);
    var bytes = new Uint8Array(raw.length);
    for (var i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
    return bytes;
  }

  function post(path, body) {
    return fetch(path, {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      body: body === undefined ? "{}" : JSON.stringify(body)
    }).then(function (response) {
      if (!response.ok) throw new Error("The hub answered " + response.status);
      return response;
    });
  }

  function subscribe() {
    if (!supported()) return Promise.resolve("unsupported");
    return Notification.requestPermission().then(function (permission) {
      if (permission !== "granted") return permission;
      return navigator.serviceWorker.register("/sw.js", { scope: "/" })
        .then(function () { return navigator.serviceWorker.ready; })
        .then(function (registration) {
          return fetch("/api/v1/push/key", { credentials: "same-origin" })
            .then(function (response) { return response.json(); })
            .then(function (json) {
              return registration.pushManager.subscribe({
                userVisibleOnly: true,
                applicationServerKey: keyBytes(json.publicKey)
              });
            });
        })
        .then(function (subscription) { return post("/api/v1/push/subscribe", subscription.toJSON()); })
        .then(function () { return "granted"; });
    });
  }

  function unsubscribe() {
    if (!supported()) return Promise.resolve();
    return navigator.serviceWorker.ready.then(function (registration) {
      return registration.pushManager.getSubscription();
    }).then(function (subscription) {
      if (!subscription) return undefined;
      return post("/api/v1/push/unsubscribe", { endpoint: subscription.endpoint }).then(function () {
        return subscription.unsubscribe();
      });
    });
  }

  function test() {
    return post("/api/v1/push/test").then(function (response) { return response.json(); })
      .then(function (json) { return json.sent; });
  }

  window.goaPush = { supported: supported, subscribe: subscribe, unsubscribe: unsubscribe, test: test };
})();
