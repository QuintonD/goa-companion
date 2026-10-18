// /join: an invite link is https://<hub>/join#goa-sync:1?g=…&k=…  The fragment (with the group key)
// never reaches a server or its logs; this script reads it, clears it from the address bar and
// history, and posts it to the hub, which sets the device cookie (ADR-030 §7).
(function () {
  "use strict";

  var hash = (location.hash || "").replace(/^#/, ""); // still percent-encoded, as GroupInvite expects
  if (hash.indexOf("goa-sync:") === -1) return; // no invite in the link: the paste form stays

  history.replaceState(null, "", location.pathname);
  var status = document.getElementById("join-status");
  var form = document.getElementById("join-form");
  if (form) form.hidden = true;
  if (status) status.textContent = "Signing in…";

  var body = new URLSearchParams();
  body.set("invite", hash);
  fetch("/join", {
    method: "POST",
    credentials: "same-origin",
    headers: { "Accept": "application/json", "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString()
  }).then(function (response) {
    return response.json().then(function (json) { return { ok: response.ok, json: json }; });
  }).then(function (result) {
    if (result.ok && result.json.redirect) {
      location.replace(result.json.redirect);
    } else {
      fail(result.json.error || "That didn't work. Scan the invite again.");
    }
  }).catch(function () {
    fail("Can't reach the hub. Check the connection and scan the invite again.");
  });

  function fail(message) {
    if (status) {
      status.textContent = "";
      var box = document.createElement("div");
      box.className = "error-state";
      box.setAttribute("role", "alert");
      var text = document.createElement("p");
      text.textContent = message;
      box.appendChild(text);
      status.appendChild(box);
    }
    if (form) form.hidden = false;
  }
})();
