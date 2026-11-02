#!/usr/bin/env python3
"""Checks a leaderboard fetched from the game server before it can reach the public site.

Pages sites of one account share a browser origin with the app and its stored keys (docs/ONLINE.md in
goa-companion), so what the server sends is treated as hostile: the file that gets written is rebuilt
from the checked values, never the bytes that arrived. Reasons printed name a field and a rule, and
never quote a value, so nothing from the server reaches the Actions log as a workflow command.

    validate_leaderboard.py --incoming FILE [--current FILE] [--write FILE]

Exit status: 0 valid and new (written, with --write); 10 valid, same match log; 1 rejected.
"""

import argparse
import json
import os
import re
import sys
import time
import unicodedata

SCHEMA = 1
MAX_BYTES = 512 * 1024
MAX_TEXT = 64
MAX_COUNT = 2**53 - 1
MAX_RATING = 1_000_000
# A clock far ahead would pass once and then block every honest snapshot after it.
MAX_FUTURE_MS = 24 * 60 * 60 * 1000
SHA256 = re.compile(r"[0-9a-f]{64}\Z")
TOP_FIELDS = {"schema", "generatedAt", "logSha256", "matches", "eloConfig", "players", "heroes"}
ROW_FIELDS = {"id", "name", "rating", "games", "wins"}

EXIT_NEW = 0
EXIT_REJECTED = 1
EXIT_UNCHANGED = 10


class Rejected(Exception):
    pass


def _no_duplicates(pairs):
    keys = [k for k, _ in pairs]
    if len(keys) != len(set(keys)):
        raise Rejected("an object has a duplicate key")
    return dict(pairs)


def _no_constants(_):
    raise Rejected("NaN or Infinity is not JSON")


def _parse(raw):
    if len(raw) > MAX_BYTES:
        raise Rejected(f"over {MAX_BYTES} bytes")
    try:
        text = raw.decode("utf-8")
        return json.loads(text, object_pairs_hook=_no_duplicates, parse_constant=_no_constants)
    except (UnicodeDecodeError, json.JSONDecodeError, RecursionError) as e:
        raise Rejected(f"not JSON ({type(e).__name__})") from None


def _integer(value, where, low, high):
    # bool is a subclass of int in Python, and 1.0 is not an integer on the wire.
    if type(value) is not int or not low <= value <= high:
        raise Rejected(f"{where} must be a whole number from {low} to {high}")
    return value


def _text(value, where):
    if type(value) is not str:
        raise Rejected(f"{where} must be a string")
    if not 1 <= len(value) <= MAX_TEXT:
        raise Rejected(f"{where} must be 1 to {MAX_TEXT} characters")
    for ch in value:
        if ch in "<>":
            raise Rejected(f"{where} contains < or >")
        # Cc control, Cs a lone surrogate, Zl and Zp line breaks; Bidi_Control can reorder a page.
        if unicodedata.category(ch) in ("Cc", "Cs", "Zl", "Zp") or ch in "؜‎‏" or (
            "‪" <= ch <= "‮" or "⁦" <= ch <= "⁩"
        ):
            raise Rejected(f"{where} contains a control character")
    return value


def _fields(value, allowed, where):
    if type(value) is not dict:
        raise Rejected(f"{where} must be an object")
    if set(value) != allowed:
        # Sorted names of our own whitelist only: an unknown key is counted, not printed.
        missing = sorted(allowed - set(value))
        extra = len(set(value) - allowed)
        raise Rejected(f"{where}: {extra} unknown field(s), missing {missing}")


def _rows(value, where):
    if type(value) is not list:
        raise Rejected(f"{where} must be a list")
    rows = []
    seen = set()
    for i, row in enumerate(value):
        here = f"{where}[{i}]"
        _fields(row, ROW_FIELDS, here)
        clean = {
            "id": _text(row["id"], f"{here}.id"),
            "name": _text(row["name"], f"{here}.name"),
            "rating": _integer(row["rating"], f"{here}.rating", -MAX_RATING, MAX_RATING),
            "games": _integer(row["games"], f"{here}.games", 0, MAX_COUNT),
            "wins": _integer(row["wins"], f"{here}.wins", 0, MAX_COUNT),
        }
        if clean["wins"] > clean["games"]:
            raise Rejected(f"{here}: more wins than games")
        if clean["id"] in seen:
            raise Rejected(f"{here}.id repeats an earlier row")
        seen.add(clean["id"])
        rows.append(clean)
    return rows


def validate(raw):
    """Returns a new dict built from the checked values, in the wire order."""
    data = _parse(raw)
    _fields(data, TOP_FIELDS, "snapshot")
    if type(data["schema"]) is not int or data["schema"] != SCHEMA:
        raise Rejected(f"schema must be {SCHEMA}")
    log_hash = data["logSha256"]
    if type(log_hash) is not str or not SHA256.match(log_hash):
        raise Rejected("logSha256 must be 64 lowercase hex digits")
    return {
        "schema": SCHEMA,
        "generatedAt": _integer(data["generatedAt"], "generatedAt", 0, MAX_COUNT),
        "logSha256": log_hash,
        "matches": _integer(data["matches"], "matches", 0, MAX_COUNT),
        "eloConfig": _integer(data["eloConfig"], "eloConfig", 0, MAX_COUNT),
        "players": _rows(data["players"], "players"),
        "heroes": _rows(data["heroes"], "heroes"),
    }


def is_new(incoming, current, now_ms):
    """True when the board says something new; a clock running backwards is rejected.

    A rename or a deleted account changes the rows and not the log, and a deleted name must leave this file.
    """
    if incoming["generatedAt"] > now_ms + MAX_FUTURE_MS:
        raise Rejected("generatedAt is more than a day in the future")
    if current is None:
        return True
    if incoming["generatedAt"] < current["generatedAt"]:
        raise Rejected("generatedAt is older than the published copy")
    if all(incoming[k] == current[k] for k in ("logSha256", "matches", "eloConfig", "players", "heroes")):
        return False
    if incoming["generatedAt"] == current["generatedAt"]:
        raise Rejected("a different board with the same generatedAt")
    return True


def render(snapshot):
    """Plain ASCII, so the file's bytes cannot depend on anything but the values."""
    return json.dumps(snapshot, ensure_ascii=True, separators=(",", ":")) + "\n"


def _read_capped(path):
    with open(path, "rb") as f:
        return f.read(MAX_BYTES + 1)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--incoming", required=True)
    parser.add_argument("--current")
    parser.add_argument("--write")
    args = parser.parse_args(argv)

    try:
        incoming = validate(_read_capped(args.incoming))
        current = validate(_read_capped(args.current)) if args.current else None
        new = is_new(incoming, current, int(time.time() * 1000))
    except Rejected as e:
        print(f"rejected: {e}")
        return EXIT_REJECTED
    except OSError as e:
        print(f"rejected: cannot read a file ({type(e).__name__})")
        return EXIT_REJECTED

    if not new:
        print("unchanged: the same match log")
        return EXIT_UNCHANGED
    if args.write:
        temporary = args.write + ".tmp"
        with open(temporary, "w", encoding="ascii", newline="\n") as f:
            f.write(render(incoming))
        os.replace(temporary, args.write)
    print(f"valid: {incoming['matches']} matches, {len(incoming['players'])} players, {len(incoming['heroes'])} heroes")
    return EXIT_NEW


if __name__ == "__main__":
    sys.exit(main())
