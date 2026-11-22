#!/usr/bin/env python3
"""Fit HEval's term weights to how a search's leaves rank (HEVAL.md "Leaf ranking").

Reads the CSVs of HEvalCalibrationTest's `record leaves`: one row per leaf the hybrid search scored, with
the decision, the sample and the candidate, a teacher value (the old learned net's logit) and the HTerm
features from the deciding team's side. A leaf only has to order the candidates of one sample, so the fit
is on differences within a sample: least squares of (teacher - its sample mean) on (features - their sample
mean), ridge toward the hand weights. The teacher is put in coin-equivalents with the outcome slopes
(net logit x c / k, c = 1.45 and k = 0.0186 from heval_fit.py on h10 games).

Reported on held-out games, per searched decision (values averaged over the samples, as the search does):
how often the weights pick the teacher's best candidate, and the teacher's regret of their pick.

  python3 -I tools/ai/heval_rank.py --l2 0.01,0.1,1 leaves.csv
"""
import argparse
import csv
import math
import zlib
from collections import defaultdict

import importlib.util
import os

_spec = importlib.util.spec_from_file_location("heval_fit", os.path.join(os.path.dirname(os.path.abspath(__file__)), "heval_fit.py"))
_fit = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_fit)
HAND, solve, fold_of_game = _fit.HAND, _fit.solve, _fit.fold_of_game

NET_TO_COINS = 1.45 / 0.0186
WIN = 1000.0


def load(paths, kinds):
    groups = defaultdict(list)  # (game, decision, sample) -> [(cand, net, f, terminal)]
    meta = {}
    for path in paths:
        with open(path) as fh:
            for r in csv.DictReader(fh):
                if kinds and r["kind"] not in kinds:
                    continue
                net = float(r["net"])
                terminal = abs(net) >= 19.99
                f = [float(r.get(t) or 0.0) for t in HAND]
                key = (r["game"] + "@" + path, int(r["decision"]), int(r["sample"]))
                groups[key].append((int(r["cand"]), net, f, terminal))
                meta[key[:2]] = r["kind"]
    return groups, meta


def value(w, f, terminal, net):
    if terminal:
        return WIN if net > 0 else -WIN
    return sum(a * b for a, b in zip(w, f))


def evaluate(name, groups, meta, w, keys):
    # Per decision: candidate -> list of (heval, net coins) over samples.
    dec = defaultdict(lambda: defaultdict(list))
    for key in keys:
        for cand, net, f, term in groups[key]:
            dec[key[:2]][cand].append((value(w, f, term, net), (WIN if net > 0 else -WIN) if term else net * NET_TO_COINS))
    agree = 0
    regret = 0.0
    n = 0
    by_kind = defaultdict(lambda: [0, 0, 0.0])
    for d, cands in dec.items():
        if len(cands) < 2:
            continue
        mean = {c: (sum(v[0] for v in vs) / len(vs), sum(v[1] for v in vs) / len(vs)) for c, vs in cands.items()}
        # The book's answer is candidate 0 and keeps a tie, as in the search.
        pick = max(sorted(mean), key=lambda c: (round(mean[c][0], 9), -c))
        best = max(sorted(mean), key=lambda c: (round(mean[c][1], 9), -c))
        r = mean[best][1] - mean[pick][1]
        ok = mean[pick][1] >= mean[best][1] - 1e-9
        agree += ok
        regret += min(r, 200.0)
        n += 1
        k = by_kind[meta[d]]
        k[0] += 1
        k[1] += ok
        k[2] += min(r, 200.0)
    print(f"{name}: {n} decisions, picks the teacher's best {100 * agree / max(n, 1):.1f}%, "
          f"mean teacher regret {regret / max(n, 1):.2f} coins")
    print("   " + "  ".join(f"{kind} {v[1] * 100 / v[0]:.0f}%/{v[2] / v[0]:.1f} ({v[0]})"
                           for kind, v in sorted(by_kind.items(), key=lambda kv: -kv[1][0])[:8]))
    return regret / max(n, 1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("csv", nargs="+")
    ap.add_argument("--terms", default=",".join(HAND))
    ap.add_argument("--zero", default="")
    ap.add_argument("--l2", default="0.001,0.01,0.1,1")
    ap.add_argument("--kinds", default="")
    ap.add_argument("--fold", type=int, default=0)
    args = ap.parse_args()
    groups, meta = load(args.csv, set(k for k in args.kinds.split(",") if k))
    terms = list(HAND)
    zero = set(t for t in args.zero.split(",") if t)
    chosen = [t for t in args.terms.split(",") if t and t not in zero]
    ix = [terms.index(t) for t in chosen]
    fixed = [i for i, t in enumerate(terms) if t not in chosen and t not in zero]
    test_of = lambda key: fold_of_game(key[0]) == args.fold
    train = [k for k in groups if not test_of(k)]
    test = [k for k in groups if test_of(k)]
    print(f"{sum(len(v) for v in groups.values())} leaves, {len(groups)} samples, "
          f"{len({k[:2] for k in groups})} decisions, {len({k[0] for k in groups})} games")
    hand = [HAND[t] for t in terms]
    evaluate("hand weights", groups, meta, hand, test)
    # Centered design.
    xs, ys = [], []
    for key in train:
        rows = [r for r in groups[key] if not r[3]]
        if len(rows) < 2:
            continue
        mf = [sum(r[2][i] for r in rows) / len(rows) for i in range(len(terms))]
        mt = sum(r[1] for r in rows) / len(rows) * NET_TO_COINS
        for r in rows:
            d = [r[2][i] - mf[i] for i in range(len(terms))]
            xs.append([d[i] for i in ix])
            ys.append((r[1] * NET_TO_COINS - mt) - sum(hand[i] * d[i] for i in fixed))
    p = len(ix)
    sd = [max(1e-6, math.sqrt(sum(x[j] ** 2 for x in xs) / len(xs))) for j in range(p)]
    a0 = [[0.0] * p for _ in range(p)]
    b0 = [0.0] * p
    for x, y in zip(xs, ys):
        for i in range(p):
            if x[i] == 0.0:
                continue
            b0[i] += x[i] * y
            for j in range(i, p):
                a0[i][j] += x[i] * x[j]
    for i in range(p):
        for j in range(i):
            a0[i][j] = a0[j][i]
    sst = sum(y * y for y in ys)
    best = None
    for l2 in (float(v) for v in args.l2.split(",")):
        a = [row[:] for row in a0]
        b = b0[:]
        for i in range(p):
            lam = l2 * len(xs) * sd[i] * sd[i]
            a[i][i] += lam
            b[i] += lam * HAND[chosen[i]]
        v = solve(a, b)
        res = sum((y - sum(v[j] * x[j] for j in range(p))) ** 2 for x, y in zip(xs, ys))
        w = hand[:]
        for j, i in enumerate(ix):
            w[i] = v[j]
        for i, t in enumerate(terms):
            if t in zero:
                w[i] = 0.0
        reg = evaluate(f"fit l2 {l2} (within-sample R2 {1 - res / sst:.3f})", groups, meta, w, test)
        if best is None or reg < best[0]:
            best = (reg, l2, w, sd)
    reg, l2, w, sd = best
    print(f"best l2 {l2}: held-out regret {reg:.2f}")
    print(f"  {'term':12} {'hand':>8} {'fit':>9} {'fit/hand':>9} {'rms(df)':>8} {'rms(w.df)':>9}")
    for j, i in enumerate(ix):
        t = terms[i]
        ratio = f"{w[i] / HAND[t]:9.2f}" if HAND[t] else "      new"
        print(f"  {t:12} {HAND[t]:8.3f} {w[i]:9.3f} {ratio} {sd[j]:8.2f} {abs(w[i]) * sd[j]:9.2f}")
    print("  weights: " + ", ".join(f"{t}={w[i]:.4f}" for i, t in enumerate(terms)))


if __name__ == "__main__":
    main()
