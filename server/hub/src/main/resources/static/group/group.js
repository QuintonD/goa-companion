// Group pages: "Copy link" and "Share…" for the invite (Web Share API). Without JS the link stays
// in a read-only field to select by hand, and both buttons stay hidden.
(function () {
  "use strict";

  function status(text) {
    var el = document.querySelector(".copy-status");
    if (el) el.textContent = text;
  }

  var copy = document.querySelector("[data-copy]");
  if (copy && navigator.clipboard && window.isSecureContext) {
    copy.hidden = false;
    copy.addEventListener("click", function () {
      var field = document.getElementById(copy.getAttribute("data-copy"));
      if (!field) return;
      navigator.clipboard.writeText(field.value).then(function () {
        status("Link copied. It's the group's key: send it only to people in the group.");
      }, function () {
        field.select();
        status("Couldn't copy: the link is selected, copy it by hand.");
      });
    });
  }

  var share = document.querySelector("[data-share-text]");
  if (share && typeof navigator.share === "function") {
    share.hidden = false;
    share.addEventListener("click", function () {
      navigator.share({
        title: share.getAttribute("data-share-title") || undefined,
        text: share.getAttribute("data-share-text")
      }).catch(function () {
        // Cancelled by the user, or not allowed: nothing to do.
      });
    });
  }

  // Select the whole link on focus, so it can be copied by hand.
  var link = document.getElementById("invite-link");
  if (link) link.addEventListener("focus", function () { link.select(); });
})();
