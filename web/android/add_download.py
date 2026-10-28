"""Adds the Android download (ADR-048) to the built web site: android/ with the newest release's APK.

The APK is never in git: it is an asset of this repository's newest release (tag vX.Y.Z, asset
GoA-Companion-X.Y.Z.apk). The page (index.html beside this script) gets the version, size, date,
SHA-256 and the release's notes (rendered by GitHub's Markdown API, the title and Install sections
left out: the page has its own). android/GoA-Companion.apk is always the newest APK.

    GH_TOKEN=... python3 web/android/add_download.py web/app/build/site
"""

import hashlib
import json
import os
import pathlib
import re
import subprocess
import sys

REPO = os.environ.get("GITHUB_REPOSITORY", "QuintonD/goa-companion")


def gh(*args: str, stdin: str | None = None) -> str:
    return subprocess.run(["gh", *args], check=True, capture_output=True, text=True, input=stdin).stdout


def notes_html(body: str) -> str:
    """The notes from their first ### section up to ### Install, as HTML."""
    kept, keep = [], False
    for line in body.splitlines():
        if line.startswith("### "):
            keep = not line.lower().startswith("### install")
        if keep:
            kept.append(line)
    text = "\n".join(kept).strip() or body
    html = gh("api", "markdown", "--input", "-", stdin=json.dumps({"text": text, "mode": "markdown"}))
    # GitHub's heading permalinks point into github.com's page, not this one.
    html = re.sub(r'<a id="user-content-[^"]*" class="anchor".*?</a>', "", html, flags=re.S)
    return re.sub(r'<div class="markdown-heading">(.*?)</div>', lambda m: m.group(1), html, flags=re.S)


def main(site: str) -> None:
    out = pathlib.Path(site) / "android"
    out.mkdir(parents=True, exist_ok=True)
    release = json.loads(gh("release", "view", "-R", REPO, "--json", "tagName,publishedAt,body"))
    version = release["tagName"].removeprefix("v")
    apk = f"GoA-Companion-{version}.apk"
    gh("release", "download", release["tagName"], "-R", REPO, "-p", apk, "-D", str(out), "--clobber")
    data = (out / apk).read_bytes()
    (out / "GoA-Companion.apk").write_bytes(data)
    page = (pathlib.Path(__file__).parent / "index.html").read_text(encoding="utf-8")
    values = {
        "VERSION": version,
        "SIZE": str(round(len(data) / 1_000_000)),
        "DATE": release["publishedAt"][:10],
        "SHA256": hashlib.sha256(data).hexdigest(),
        "NOTES": notes_html(release["body"]),
    }
    page = re.sub(r"@([A-Z0-9]+)@", lambda m: values[m.group(1)], page)
    (out / "index.html").write_text(page, encoding="utf-8")
    print(f"android/: {apk} ({values['SIZE']} MB, sha256 {values['SHA256']})")


if __name__ == "__main__":
    main(sys.argv[1])
