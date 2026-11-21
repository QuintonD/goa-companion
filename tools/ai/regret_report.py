#!/usr/bin/env python3
"""Summarise HeuristicRegretTest output (REVIEW-2-losses.md).

Usage: python3 -I tools/ai/regret_report.py FILE.jsonl [FILE.jsonl ...] [--examples N]

Reads the JSON lines the probe writes (one per question, a "game" line per game) and prints:
the robust regret by source (both oracles say h10's answer is worse by > 1 pp of win chance; the size is
the smaller cross-fitted gap), the same for the search side's own questions when the run had
AI_H_REGRET_BOTH=1, the oracle's noise (retest and second opinion), what an alternative strength
(AI_H_REGRET_ALT) would have changed, and whether summed regret separates won from lost games.
"""
import argparse
import collections
import json
import math

MARGIN = 0.01  # 1 pp of win chance


def load(files):
    games, qs = [], []
    for f in files:
        with open(f) as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                o = json.loads(line)
                (games if "game" in o else qs).append(o)
    for r in qs:
        q = r.get("qWpCf")
        r["rob"] = 100 * max(0.0, min(r.get("oWpCf", 0.0), q)) if q is not None else 0.0
        r["robSig"] = q is not None and r.get("oWpGap", 0) > MARGIN and r.get("qWpGap", 0) > MARGIN
    return games, [r for r in qs if not r.get("oMissing")]


def colour(label):
    parts = label.split(":")
    return parts[1].split("-")[1] if len(parts) > 1 and "-" in parts[1] else "?"


def source(r):
    """The concept a question belongs to (the rows of the review's table)."""
    k, t, p = r["kind"], r["turn"], r.get("prompt", "")
    if k == "PLAY_CARD":
        c, b = colour(r["choice"]), colour(r["oWpBest"])
        if t == 3:
            return "card T3: Red spent, oracle keeps it" if c == "red" and b != "red" else "card T3: other"
        if t in (1, 2):
            return "card T1-2: Red withheld" if b == "red" and c != "red" else "card T1-2: other"
        return "card T4: Blue cashed" if c == "blue" and b != "blue" else "card T4: other"
    if k == "CHOOSE_ACTION":
        if r["oWpBest"] == "action:HOLD" and r["choice"] != "action:HOLD":
            return "action: acts/moves, oracle holds"
        return "action: other (primary/move/FT)"
    if k == "CHOOSE_SPACE" and (r.get("subject") == r["seat"] or r.get("subjectOwn")):
        if r["oWpBest"].startswith("decline"):
            return "move: oracle stays put"
        return "move T3-4 / thin hand" if r["hand"] <= 1 or t >= 3 else "move T1-2: end square"
    if k == "CHOOSE_SPACE":
        return "space: token/other unit/placement"
    if k == "CHOOSE_UNIT":
        return "attack target" if p.startswith("Target") else "unit: push/swap/other"
    if k in ("CHOOSE_OPTION", "CHOOSE_TOKEN", "CONFIRM", "CHOOSE_NUMBER"):
        return "opaque: option/token/number"
    return {
        "DEFEND": "defend/decline", "CHOOSE_CARD": "card choice (discard/retrieve)", "UPGRADE_CARD": "upgrade",
        "KEEP_UPGRADE": "upgrade", "REMOVE_MINION": "minion removal", "RESPAWN": "respawn",
    }.get(k, k.lower())


def table(rows, n_games, title):
    agg = collections.defaultdict(lambda: [0, 0.0, 0])
    for r in rows:
        a = agg[source(r)]
        a[0] += 1
        a[1] += r["rob"]
        a[2] += r["robSig"]
    total = sum(a[1] for a in agg.values())
    print(f"\n== {title}: {n_games} games, robust regret {total / n_games:.1f} pp/game")
    print(f"{'source':40s} {'q/game':>7s} {'pp/game':>8s} {'coins/g':>8s} {'sig/game':>8s}")
    for k, (n, rob, sg) in sorted(agg.items(), key=lambda kv: -kv[1][1]):
        print(f"{k:40s} {n / n_games:7.1f} {rob / n_games:8.2f} {rob / n_games / 0.735:8.1f} {sg / n_games:8.2f}")


def noise(rows):
    for prefix, what in (("r", "same oracle, another seed"), ("q", "hand-evaluation oracle")):
        rt = [r for r in rows if prefix + "WpCf" in r]
        if not rt:
            continue
        same = sum(1 for r in rt if r[prefix + "WpBest"] == r["oWpBest"]) / len(rt)
        xs = [r["oWpCf"] for r in rt]
        ys = [r[prefix + "WpCf"] for r in rt]
        mx, my = sum(xs) / len(xs), sum(ys) / len(ys)
        vx = sum((a - mx) ** 2 for a in xs)
        vy = sum((b - my) ** 2 for b in ys)
        corr = sum((a - mx) * (b - my) for a, b in zip(xs, ys)) / math.sqrt(vx * vy) if vx and vy else float("nan")
        print(f"noise [{what}]: {len(rt)} questions, same best {100 * same:.0f}%, win-chance cf correlation {corr:.2f}")


def alternatives(rows, n_games):
    names = sorted({k[4:] for r in rows for k in r if k.startswith("alt_")})
    for a in names:
        val = [r for r in rows if ("altWp_" + a) in r and ("altQWp_" + a) in r]
        if not val:
            continue
        o = sum(r["altWp_" + a] for r in val) * 100 / n_games
        q = sum(r["altQWp_" + a] for r in val) * 100 / n_games
        both_a = sum(1 for r in val if r["altWp_" + a] > MARGIN and r["altQWp_" + a] > MARGIN)
        both_h = sum(1 for r in val if r["altWp_" + a] < -MARGIN and r["altQWp_" + a] < -MARGIN)
        print(f"{a} instead of h10 where they differ ({len(val) / n_games:.1f}/game): net oracle {o:+.1f} pp/game, "
              f"hand oracle {q:+.1f}; both favour {a} {both_a}, both favour h10 {both_h}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("files", nargs="+")
    ap.add_argument("--examples", type=int, default=0)
    args = ap.parse_args()
    games, qs = load(args.files)
    h = [r for r in qs if r["side"] == "h"]
    s = [r for r in qs if r["side"] == "s"]
    won = {g["game"]: g["hWon"] for g in games}
    print(f"{len(games)} games, h10 won {sum(won.values())}; endings "
          f"{dict(collections.Counter((g['hWon'], g['reason']) for g in games))}")
    table(h, len(games), "h10")
    s_games = len({r["g"] for r in s})
    if s_games:
        table(s, s_games, "search side (calibration)")
    noise(h)
    alternatives(h, len(games))
    per = collections.defaultdict(float)
    for r in h:
        per[r["g"]] += r["rob"]
    w = [per[g] for g, x in won.items() if x]
    lo = [per[g] for g, x in won.items() if not x]
    if w and lo:
        print(f"robust regret per game: won {sum(w) / len(w):.1f} pp, lost {sum(lo) / len(lo):.1f} pp")
    if args.examples:
        print("\n== largest robust regrets (replay with AI_H_REGRET_ONLY=<lineup>:<R|B>:<sN>:<id>)")
        for r in sorted(h, key=lambda r: -r["rob"])[: args.examples]:
            g = r["g"].split("|")
            print(f"  {g[1]}:{g[2]}:{g[3]}:{r['id']} {r['seat']} R{r['round']}T{r['turn']} {source(r)}: "
                  f"h10 {r['choice']} / oracle {r['oWpBest']} / hand oracle {r.get('qWpBest')} {r['rob']:.1f} pp")


if __name__ == "__main__":
    main()
