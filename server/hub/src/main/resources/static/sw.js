// The hub's service worker (ADR-030 §8–9), served at /sw.js. Deliberately small:
// - versioned /static/ files (CSS, JS, fonts, icons; ?v= changes with the content): cache first,
//   so the shell loads fast on a phone's data plan; an older version of a file is dropped.
// - everything else (pages, game data, the API) always goes to the network: it is private and live.
// - push: shows the hub's notification; a tap opens (or focuses) the page it names.
"use strict";

var CACHE = "goa-static-v2";

self.addEventListener("install", function () {
  self.skipWaiting();
});

self.addEventListener("activate", function (event) {
  event.waitUntil(
    caches.keys().then(function (names) {
      return Promise.all(names.filter(function (n) { return n !== CACHE; }).map(function (n) { return caches.delete(n); }));
    }).then(function () { return self.clients.claim(); })
  );
});

self.addEventListener("fetch", function (event) {
  var url = new URL(event.request.url);
  // Only versioned static files (/static/…?v=…): they never change under the same URL. Pages,
  // fragments, game data, the invite and the API always go to the network (private and live).
  if (event.request.method !== "GET" || url.origin !== self.location.origin ||
      url.pathname.indexOf("/static/") !== 0 || !url.searchParams.has("v")) {
    return; // the network, as if there were no service worker
  }
  event.respondWith(
    caches.open(CACHE).then(function (cache) {
      return cache.match(event.request).then(function (hit) {
        if (hit) return hit;
        return fetch(event.request).then(function (response) {
          if (response.ok) {
            cache.put(event.request, response.clone());
            pruneOlder(cache, url);
          }
          return response;
        });
      });
    })
  );
});

// A new build's file replaces the last one's: drop other versions of the same path.
function pruneOlder(cache, url) {
  cache.keys().then(function (requests) {
    requests.forEach(function (request) {
      var old = new URL(request.url);
      if (old.pathname === url.pathname && old.search !== url.search) cache.delete(request);
    });
  });
}

self.addEventListener("push", function (event) {
  var data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch (e) {
    data = { title: "Guards of Atlantis", body: event.data ? event.data.text() : "" };
  }
  var options = {
    body: data.body || "",
    icon: "/icons/icon-192.png",
    badge: "/icons/icon-192.png",
    data: { url: data.url || "/" }
  };
  if (data.tag) {
    options.tag = data.tag;
    options.renotify = true;
  }
  event.waitUntil(self.registration.showNotification(data.title || "Guards of Atlantis", options));
});

self.addEventListener("notificationclick", function (event) {
  event.notification.close();
  var target = new URL((event.notification.data && event.notification.data.url) || "/", self.location.origin);
  if (target.origin !== self.location.origin) target = new URL("/", self.location.origin);
  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then(function (windows) {
      for (var i = 0; i < windows.length; i++) {
        if (new URL(windows[i].url).pathname === target.pathname && "focus" in windows[i]) return windows[i].focus();
      }
      if (windows.length > 0 && "navigate" in windows[0]) {
        return windows[0].navigate(target.href).then(function (w) { return w && w.focus(); });
      }
      return self.clients.openWindow(target.href);
    })
  );
});
