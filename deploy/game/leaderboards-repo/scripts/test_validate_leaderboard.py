#!/usr/bin/env python3
"""Tests of the leaderboard validator. Run: python3 scripts/test_validate_leaderboard.py"""

import json
import os
import re
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import validate_leaderboard as v  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NOW = 1_800_000_000_000
HASH_A = "a" * 64
HASH_B = "b" * 64


def row(**changes):
    return {"id": "acct-1", "name": "Quinton", "rating": 1016, "games": 7, "wins": 4, **changes}


def snapshot(**changes):
    return {
        "schema": 1,
        "generatedAt": NOW,
        "logSha256": HASH_A,
        "matches": 12,
        "eloConfig": 1,
        "players": [row()],
        "heroes": [row(id="arien", name="Arien")],
        **changes,
    }


def raw(value):
    return json.dumps(value).encode()


class Valid(unittest.TestCase):
    def test_a_real_looking_snapshot_passes(self):
        self.assertEqual(v.validate(raw(snapshot())), snapshot())

    def test_names_people_really_have(self):
        for name in ["Former player", "Zoë", "李雷", "R&D", "a" * 64, "👩‍🚀 Ada", "O'Neil \"Q\""]:
            with self.subTest(name=name):
                self.assertEqual(v.validate(raw(snapshot(players=[row(name=name)])))["players"][0]["name"], name)

    def test_empty_board(self):
        self.assertEqual(v.validate(raw(snapshot(players=[], heroes=[], matches=0)))["players"], [])

    def test_output_is_rebuilt_not_copied(self):
        messy = b'  {"heroes":[],\n"players":[],"eloConfig":1,"matches":0,\t"logSha256":"' + HASH_A.encode() + (
            b'","generatedAt":5,"schema":1}  \n'
        )
        self.assertEqual(
            v.render(v.validate(messy)),
            '{"schema":1,"generatedAt":5,"logSha256":"' + HASH_A + '","matches":0,"eloConfig":1,"players":[],"heroes":[]}\n',
        )

    def test_output_is_ascii(self):
        text = v.render(v.validate(raw(snapshot(players=[row(name="Zoë 李")]))))
        self.assertTrue(text.isascii())
        self.assertEqual(json.loads(text)["players"][0]["name"], "Zoë 李")


class Rejects(unittest.TestCase):
    def assert_rejected(self, data, fragment=""):
        with self.assertRaises(v.Rejected) as caught:
            v.validate(data if isinstance(data, bytes) else raw(data))
        self.assertIn(fragment, str(caught.exception))

    def test_html_in_a_name(self):
        self.assert_rejected(snapshot(players=[row(name="<script>alert(1)</script>")]), "players[0].name")
        self.assert_rejected(snapshot(heroes=[row(name="a>b")]), "heroes[0].name")

    def test_html_in_an_id(self):
        self.assert_rejected(snapshot(players=[row(id="<img src=x>")]), "players[0].id")

    def test_escaped_html_is_still_html(self):
        text = raw(snapshot()).replace(b"Quinton", b"\\u003cb\\u003e")
        self.assert_rejected(text, "players[0].name")

    def test_control_characters(self):
        for ch in ["\n", "\x00", "\x1b", "\x7f", "\u0085", " ", " ", "‮", "⁦", "‏"]:
            with self.subTest(ch=hex(ord(ch))):
                self.assert_rejected(snapshot(players=[row(name=f"a{ch}b")]), "control character")

    def test_a_lone_surrogate(self):
        self.assert_rejected(raw(snapshot()).replace(b"Quinton", b"\\ud800"), "control character")

    def test_strings_too_long_or_empty(self):
        self.assert_rejected(snapshot(players=[row(name="a" * 65)]), "1 to 64")
        self.assert_rejected(snapshot(players=[row(id="a" * 65)]), "1 to 64")
        self.assert_rejected(snapshot(players=[row(name="")]), "1 to 64")

    def test_wrong_types_at_the_top(self):
        for field, bad in [
            ("schema", "1"),
            ("schema", True),
            ("schema", 1.0),
            ("generatedAt", "5"),
            ("generatedAt", 1.5),
            ("generatedAt", None),
            ("generatedAt", True),
            ("generatedAt", -1),
            ("generatedAt", 2**60),
            ("logSha256", 5),
            ("logSha256", None),
            ("matches", "12"),
            ("matches", -1),
            ("eloConfig", [1]),
            ("players", {}),
            ("players", "x"),
            ("heroes", None),
        ]:
            with self.subTest(field=field, bad=bad):
                self.assert_rejected(snapshot(**{field: bad}))

    def test_wrong_types_in_a_row(self):
        for field, bad in [
            ("id", 5),
            ("name", None),
            ("name", ["a"]),
            ("rating", "1000"),
            ("rating", 1000.5),
            ("rating", True),
            ("rating", 10**9),
            ("games", -1),
            ("games", "7"),
            ("wins", False),
        ]:
            with self.subTest(field=field, bad=bad):
                self.assert_rejected(snapshot(players=[row(**{field: bad})]))
        self.assert_rejected(snapshot(players=["acct-1"]))
        self.assert_rejected(snapshot(players=[None]))

    def test_nonsense_rows(self):
        self.assert_rejected(snapshot(players=[row(wins=8)]), "more wins")
        self.assert_rejected(snapshot(players=[row(), row()]), "repeats")

    def test_schema_number(self):
        self.assert_rejected(snapshot(schema=2), "schema")
        self.assert_rejected(snapshot(schema=0), "schema")

    def test_hash_format(self):
        for bad in ["", "abc", "A" * 64, "g" * 64, "a" * 65, HASH_A + "\n"]:
            with self.subTest(bad=bad):
                self.assert_rejected(snapshot(logSha256=bad), "logSha256")

    def test_extra_fields(self):
        self.assert_rejected(snapshot(script="<x>"), "1 unknown")
        self.assert_rejected(snapshot(players=[row(html="x")]), "1 unknown")
        self.assert_rejected(snapshot(heroes=[row(extra=1)]), "1 unknown")

    def test_missing_fields(self):
        data = snapshot()
        del data["eloConfig"]
        self.assert_rejected(data, "eloConfig")
        bad = row()
        del bad["wins"]
        self.assert_rejected(snapshot(players=[bad]), "wins")

    def test_a_reason_never_quotes_the_server(self):
        hostile = "::error::pwned"
        cases = [
            snapshot(players=[row(name="<" + hostile)]),
            snapshot(**{hostile: 1}),
            snapshot(logSha256=hostile),
            snapshot(schema=hostile),
        ]
        for case in cases:
            with self.assertRaises(v.Rejected) as caught:
                v.validate(raw(case))
            self.assertNotIn("pwned", str(caught.exception))

    def test_oversize(self):
        rows = [row(id=f"acct-{i}", name="n" * 64) for i in range(6000)]
        data = raw(snapshot(players=rows))
        self.assertGreater(len(data), v.MAX_BYTES)
        self.assert_rejected(data, "over")

    def test_oversize_by_whitespace(self):
        self.assert_rejected(raw(snapshot()) + b" " * v.MAX_BYTES, "over")

    def test_not_json(self):
        for bad in [b"", b"<html>", b"{", b"[]", b"null", b"5", b'"x"', b"\xef\xbb\xbf{}", b'{"schema":\xff}']:
            with self.subTest(bad=bad):
                self.assert_rejected(bad)

    def test_nan_and_infinity(self):
        self.assert_rejected(raw(snapshot()).replace(b"1016", b"NaN"), "NaN")
        self.assert_rejected(raw(snapshot()).replace(b"1016", b"Infinity"), "NaN")

    def test_duplicate_keys(self):
        self.assert_rejected(raw(snapshot()).replace(b'"matches": 12', b'"matches": 12, "matches": 13'), "duplicate")

    def test_deep_nesting(self):
        self.assert_rejected(b"[" * 200000 + b"]" * 200000)


class Monotonic(unittest.TestCase):
    def test_first_copy_is_new(self):
        self.assertTrue(v.is_new(snapshot(), None, NOW))

    def test_newer_log_is_new(self):
        current = snapshot(generatedAt=NOW - 1, logSha256=HASH_B)
        self.assertTrue(v.is_new(snapshot(), current, NOW))

    def test_same_log_is_not_new_even_if_generated_later(self):
        self.assertFalse(v.is_new(snapshot(), snapshot(generatedAt=NOW - 1000), NOW))

    def test_old_generated_at(self):
        current = snapshot(generatedAt=NOW + 1, logSha256=HASH_B)
        with self.assertRaisesRegex(v.Rejected, "older"):
            v.is_new(snapshot(), current, NOW + 1)

    def test_old_generated_at_with_the_same_log(self):
        with self.assertRaisesRegex(v.Rejected, "older"):
            v.is_new(snapshot(), snapshot(generatedAt=NOW + 1), NOW + 1)

    def test_a_deleted_name_is_new_though_the_log_is_the_same(self):
        row = {"id": "a1", "name": "Sam", "rating": 1000, "games": 5, "wins": 3}
        current = snapshot(generatedAt=NOW - 1000, players=[row])
        gone = snapshot(players=[dict(row, name="Former player")])
        self.assertTrue(v.is_new(gone, current, NOW))

    def test_different_log_same_time(self):
        with self.assertRaisesRegex(v.Rejected, "same generatedAt"):
            v.is_new(snapshot(), snapshot(logSha256=HASH_B), NOW)

    def test_far_future_cannot_wedge_the_board(self):
        with self.assertRaisesRegex(v.Rejected, "future"):
            v.is_new(snapshot(generatedAt=NOW + v.MAX_FUTURE_MS + 1), None, NOW)
        self.assertTrue(v.is_new(snapshot(generatedAt=NOW + v.MAX_FUTURE_MS), None, NOW))


class CommandLine(unittest.TestCase):
    """main() uses the real clock, so these snapshots are dated from a past that stays past."""

    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.dir.cleanup)
        self.incoming = os.path.join(self.dir.name, "incoming.json")
        self.current = os.path.join(self.dir.name, "leaderboard.json")
        with open(self.current, "wb") as f:
            f.write(v.render(snapshot(generatedAt=1000, logSha256=HASH_B)).encode())
        with open(self.current, "rb") as f:
            self.before = f.read()

    def run_it(self, data):
        with open(self.incoming, "wb") as f:
            f.write(data)
        return v.main(["--incoming", self.incoming, "--current", self.current, "--write", self.current])

    def read(self):
        with open(self.current, "rb") as f:
            return f.read()

    def test_new_is_written_clean(self):
        self.assertEqual(self.run_it(b"  " + raw(snapshot(generatedAt=2000)) + b"  "), v.EXIT_NEW)
        self.assertEqual(self.read(), v.render(snapshot(generatedAt=2000)).encode())
        self.assertEqual(os.listdir(self.dir.name).count("leaderboard.json.tmp"), 0)

    def test_same_log_writes_nothing(self):
        self.assertEqual(self.run_it(raw(snapshot(generatedAt=2000, logSha256=HASH_B))), v.EXIT_UNCHANGED)
        self.assertEqual(self.read(), self.before)

    def test_rejected_writes_nothing(self):
        for data in [
            raw(snapshot(generatedAt=2000, players=[row(name="<b>")])),
            raw(snapshot(generatedAt=2000, script="x")),
            raw(snapshot(generatedAt=500)),
            b"x" * (v.MAX_BYTES + 1),
            b"<html>",
        ]:
            self.assertEqual(self.run_it(data), v.EXIT_REJECTED)
            self.assertEqual(self.read(), self.before)

    def test_missing_file(self):
        self.assertEqual(v.main(["--incoming", os.path.join(self.dir.name, "none.json")]), v.EXIT_REJECTED)


class Template(unittest.TestCase):
    def test_seed_leaderboard_is_valid_and_canonical(self):
        with open(os.path.join(ROOT, "leaderboard.json"), "rb") as f:
            data = f.read()
        self.assertEqual(v.render(v.validate(data)).encode(), data)

    def test_network_file(self):
        with open(os.path.join(ROOT, "network.json"), encoding="utf-8") as f:
            network = json.load(f)
        self.assertEqual(set(network), {"schema", "minProtocol", "games"})
        self.assertEqual((network["schema"], network["minProtocol"]), (1, 1))
        self.assertEqual(network["games"], [{"origin": "https://oldboi.taila4d38d.ts.net:8443/game", "status": "open"}])

    def test_every_action_is_pinned_to_a_commit(self):
        with open(os.path.join(ROOT, ".github", "workflows", "publish.yml"), encoding="utf-8") as f:
            uses = re.findall(r"uses:\s*(\S+)", f.read())
        self.assertTrue(uses)
        for action in uses:
            self.assertRegex(action, r"^[\w./-]+@[0-9a-f]{40}$")


if __name__ == "__main__":
    unittest.main()
