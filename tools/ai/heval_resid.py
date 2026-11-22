#!/usr/bin/env python3
"""Where HEval's leaf disagrees with a teacher (HEVAL.md "Residuals").

Reads `record leaves` CSVs with context columns (AI_HEVAL_ROLL=1 adds the search-backed teachers). Within each
sample (one determinization of one searched decision) the teacher's and HEval's values are centred, HEval's
terms are fitted to the teacher by least squares, and the residual variance is broken down by context: leaf
turn and step, decision kind, hand sizes, the lane race, Life, heroes off the board and hero. A context whose
share of the residual is far above its share of the leaves is where a named term is missing.

  python3 -I tools/ai/heval_resid.py --teacher rollNet leaves.csv
Teachers: net (the learned net's logit), rollNet / rollHeval (static-book playout to the next round's first
card play, then the net / hand-weight HEval), all in coin-equivalents.
"""
import argparse
import csv
import importlib.util
import math
import os
from collections import defaultdict

_spec = importlib.util.spec_from_file_location(
    "heval_fit", os.path.join(os.path.dirname(os.path.abspath(__file__)), "heval_fit.py"))
_fit = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_fit)
HAND, solve = _fit.HAND, _fit.solve

NET_TO_COINS = 1.45 / 0.0186


def teacher_of(r, name):
    if name == "net":
        v = float(r["net"])
        return None if abs(v) >= 19.99 else v * NET_TO_COINS
    if name == "rollNet":
        v = float(r["rollNet"])
        return None if math.isnan(v) or abs(v) >= 19.99 else v * NET_TO_COINS
    v = float(r["rollHeval"])
    return None if math.isnan(v) or abs(v) >= 999 else v


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("csv", nargs="+")
    ap.add_argument("--teacher", default="rollNet")
    ap.add_argument("--extra", default="", help="extra feature columns to fit (comma list)")
    args = ap.parse_args()
    terms = list(HAND)
    extra = [e for e in args.extra.split(",") if e]
    groups = defaultdict(list)
    for path in args.csv:
        with open(path) as fh:
            for r in csv.DictReader(fh):
                t = teacher_of(r, args.teacher)
                if t is None or abs(float(r["net"])) >= 19.99:
                    continue
                f = [float(r[x]) for x in terms] + [float(r[x]) for x in extra]
                groups[(r["game"], r["decision"], r["sample"])].append((t, f, r))
    xs, ys, ctx = [], [], []
    for rows in groups.values():
        if len(rows) < 2:
            continue
        mt = sum(x[0] for x in rows) / len(rows)
        mf = [sum(x[1][i] for x in rows) / len(rows) for i in range(len(rows[0][1]))]
        spread = max(x[0] for x in rows) - min(x[0] for x in rows)
        for t, f, r in rows:
            xs.append([a - b for a, b in zip(f, mf)])
            ys.append(t - mt)
            ctx.append((r, spread))
    p = len(xs[0])
    names = terms + extra
    hand = [HAND[t] for t in terms] + [0.0] * len(extra)
    base = [sum(h * x for h, x in zip(hand, xi)) for xi in xs]
    sst = sum(y * y for y in ys)
    a = [[0.0] * p for _ in range(p)]
    b = [0.0] * p
    for x, y in zip(xs, ys):
        for i in range(p):
            if x[i] == 0.0:
                continue
            b[i] += x[i] * y
            for j in range(i, p):
                a[i][j] += x[i] * x[j]
    sd = [math.sqrt(a[i][i] / len(xs)) or 1e-6 for i in range(p)]
    for i in range(p):
        for j in range(i):
            a[i][j] = a[j][i]
        lam = 0.01 * len(xs) * sd[i] * sd[i]
        a[i][i] += lam + 1e-9
        b[i] += lam * hand[i]
    w = solve(a, b)
    pred = [sum(wi * x for wi, x in zip(w, xi)) for xi in xs]
    res = [y - q for y, q in zip(ys, pred)]
    # The hand weights rescaled by their own best slope, for a fair "as is" number.
    s = sum(q * y for q, y in zip(base, ys)) / max(1e-9, sum(q * q for q in base))
    r2_hand = 1 - sum((y - s * q) ** 2 for y, q in zip(ys, base)) / sst
    print(f"teacher {args.teacher}: {len(ys)} leaves in {len(groups)} samples; within-sample R2 "
          f"hand x {s:.2f}: {r2_hand:.3f}, refit: {1 - sum(e * e for e in res) / sst:.3f}")
    print("  refit weights: " + ", ".join(f"{n}={wi:.3f}" for n, wi in zip(names, w) if abs(wi) > 1e-9))
    total = sum(e * e for e in res)
    n = len(res)

    def breakdown(label, key):
        acc = defaultdict(lambda: [0, 0.0, 0.0])
        for e, y, (r, spread) in zip(res, ys, ctx):
            k = key(r)
            acc[k][0] += 1
            acc[k][1] += e * e
            acc[k][2] += y * y
        rows = sorted(acc.items(), key=lambda kv: -kv[1][1])
        print(f"  by {label}: " + "  ".join(
            f"{k}: {100 * v[0] / n:.0f}% of leaves, {100 * v[1] / total:.0f}% of residual, R2 {1 - v[1] / max(v[2], 1e-9):.2f}"
            for k, v in rows[:8]))

    breakdown("leaf turn", lambda r: f"t{r['turn']}")
    breakdown("step", lambda r: r["step"])
    breakdown("kind", lambda r: r["kind"])
    breakdown("own hand", lambda r: f"h{min(int(r['ownHand']), 8)}")
    breakdown("foe hand", lambda r: f"h{min(int(r['foeHand']), 8)}")
    breakdown("minion margin", lambda r: f"m{max(-4, min(4, int(r['ownMinions']) - int(r['foeMinions'])))}")
    breakdown("lane (to win/to lose)", lambda r: f"{r['toWin']}/{r['toLose']}w{r['waves']}")
    breakdown("life (own/foe)", lambda r: f"{r['ownLife']}/{r['foeLife']}")
    breakdown("off board (own/foe)", lambda r: f"{r['ownOff']}/{r['foeOff']}")
    hero = defaultdict(lambda: [0, 0.0])
    for e, (r, _) in zip(res, ctx):
        for h in r["ownHeroes"].split("+"):
            hero["own " + h][0] += 1
            hero["own " + h][1] += e * e
        for h in r["foeHeroes"].split("+"):
            hero["foe " + h][0] += 1
            hero["foe " + h][1] += e * e
    ranked = sorted(hero.items(), key=lambda kv: -(kv[1][1] / kv[1][0]))
    mean = total / n
    print("  heroes by residual per leaf (x mean): " + "  ".join(
        f"{k} {v[1] / v[0] / mean:.1f} ({v[0]})" for k, v in ranked[:14] if v[0] >= 200))


if __name__ == "__main__":
    main()
