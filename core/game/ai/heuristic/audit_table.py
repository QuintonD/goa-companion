#!/usr/bin/env python3
"""StrategyAuditTest summaries side by side (strategy round, Track M; RESULTS.md "Strategy round - Track M").

    python3 -I audit_table.py [--no-diff] before.log after.log [more.log ...]

Reads every `AUDIT_SUMMARY` line of the files (a log or an AI_AUDIT_OUT file) and prints one Markdown table:
a row per key, a column per summary line (`pair format range side`), the per-game means as printed. With two
columns a third shows after - before. Only reads text; nothing is executed.
"""
import sys


def parse(path):
    out = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            if not line.startswith("AUDIT_SUMMARY "):
                continue
            words = line.split()
            # AUDIT_SUMMARY <pair> <format> <first+n> <label> key=v ...
            head = " ".join(words[1:5])
            values = {}
            for w in words[5:]:
                if "=" in w:
                    k, v = w.split("=", 1)
                    values[k] = float(v)
            out.append((head, values))
    return out


def main(paths):
    no_diff = "--no-diff" in paths
    paths = [p for p in paths if p != "--no-diff"]
    cols = []
    for c in (c for p in paths for c in parse(p)):
        # A self-play pair (h9:h9) prints the same side twice: keep one.
        if not (cols and cols[-1] == c):
            cols.append(c)
    if not cols:
        sys.exit("no AUDIT_SUMMARY lines")
    keys = []
    for _, values in cols:
        for k in values:
            if k not in keys:
                keys.append(k)
    diff = len(cols) == 2 and not no_diff
    header = ["key"] + [h for h, _ in cols] + (["after - before"] if diff else [])
    print("| " + " | ".join(header) + " |")
    print("|" + "---|" * len(header))
    for k in keys:
        row = [k] + ["%.2f" % v[k] if k in v else "" for _, v in cols]
        if diff and k in cols[0][1] and k in cols[1][1]:
            row.append("%+.2f" % (cols[1][1][k] - cols[0][1][k]))
        print("| " + " | ".join(row) + " |")


if __name__ == "__main__":
    main(sys.argv[1:])
