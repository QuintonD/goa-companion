#!/usr/bin/env python3
"""Fit HEval's term weights to game outcomes (core/game/ai/heuristic/HEVAL.md "Calibration").

Reads the CSVs that HEvalCalibrationTest's `record positions` writes (one row per team at the first
decision of every turn: the HTerm features from that team's side, the learned net's logit, who won) and
fits P(win) = sigmoid(sum v_i f_i) by logistic regression (no intercept: the evaluation is zero-sum),
ridge toward the hand-set weights times k, the slope of the current evaluation. The weights in
coin-equivalents are v_i / k. Games, not rows, are split: a quarter of the games is held out.

Pure Python (no numpy). Example:
  python3 -I tools/ai/heval_fit.py --l2 0.03,0.1,0.3 --terms lane,laneMatch,life a.csv b.csv
"""
import argparse
import csv
import math
import random
import re
import zlib

# HTerm order and hand weights (HEvalWeights.HAND). Keep in step with HEvalWeights.kt.
HAND = {
    "lane": 1.4, "laneMatch": 1.4, "margin": 1.0, "life": 1.0, "lifeDanger": 0.0, "pieces": 1.0,
    "coins": 1.0, "levels": 1.0, "counters": 1.0, "hand": 1.0, "handRating": 1.0, "offBoard": -8.0,
    "presence": 1.0, "opportunity": 2.0, "laneThreat": 0.3, "zonePull": 1.0, "exposure": -1.0,
    "playbook": 1.0, "oppPool": 0.0, "access": 0.0, "laneProj": 0.0,
}


def sigmoid(z):
    if z < -40:
        return 0.0
    if z > 40:
        return 1.0
    return 1.0 / (1.0 + math.exp(-z))


def load(paths, players, rounds):
    rows = []
    for path in paths:
        with open(path) as fh:
            for r in csv.DictReader(fh):
                if players and not re.search(players, r["players"]):
                    continue
                rnd = int(r["round"])
                if rounds and not (rounds[0] <= rnd <= rounds[1]):
                    continue
                f = {t: float(r.get(t) or 0.0) for t in HAND}
                rows.append((r["game"] + "@" + path, rnd, int(r["turn"]), r["won"] == "1", float(r["net"]), f))
    return rows


def solve(a, b):
    n = len(b)
    m = [a[i][:] + [b[i]] for i in range(n)]
    for c in range(n):
        piv = max(range(c, n), key=lambda i: abs(m[i][c]))
        m[c], m[piv] = m[piv], m[c]
        if abs(m[c][c]) < 1e-12:
            continue
        for r in range(n):
            if r != c:
                f = m[r][c] / m[c][c]
                if f != 0.0:
                    mr, mc = m[r], m[c]
                    for q in range(c, n + 1):
                        mr[q] -= f * mc[q]
    return [0.0 if abs(m[i][i]) < 1e-12 else m[i][n] / m[i][i] for i in range(n)]


def fit(xs, ys, prior, sd, l2, offsets=None, iters=30):
    p = len(prior)
    w = prior[:]
    n = len(xs)
    for _ in range(iters):
        g = [0.0] * p
        h = [[0.0] * p for _ in range(p)]
        for r, x in enumerate(xs):
            z = offsets[r] if offsets else 0.0
            for a in range(p):
                z += w[a] * x[a]
            q = sigmoid(z)
            e = (1.0 if ys[r] else 0.0) - q
            s = q * (1 - q)
            for a in range(p):
                xa = x[a]
                if xa == 0.0:
                    continue
                g[a] += e * xa
                sa = s * xa
                ha = h[a]
                for b in range(a, p):
                    ha[b] += sa * x[b]
        for a in range(p):
            for b in range(a):
                h[a][b] = h[b][a]
            lam = l2 * n * sd[a] * sd[a]
            g[a] -= lam * (w[a] - prior[a])
            h[a][a] += lam + 1e-9
        step = solve(h, g)
        w = [w[a] + step[a] for a in range(p)]
        if all(abs(step[a]) * sd[a] < 1e-6 for a in range(p)):
            break
    return w


def report(name, zs, rows, quiet=False):
    qs = [sigmoid(z) for z in zs]
    ys = [r[3] for r in rows]
    loss = sum(-math.log(min(max(q, 1e-9), 1 - 1e-9)) if y else -math.log(min(max(1 - q, 1e-9), 1 - 1e-9))
               for q, y in zip(qs, ys)) / len(qs)
    brier = sum((q - (1.0 if y else 0.0)) ** 2 for q, y in zip(qs, ys)) / len(qs)
    acc = sum((q > 0.5) == y for q, y in zip(qs, ys)) / len(qs)
    print(f"{name}: held-out log-loss {loss:.4f}, Brier {brier:.4f}, accuracy {acc * 100:.1f}%")
    if not quiet:
        bins = [[] for _ in range(10)]
        for q, y in zip(qs, ys):
            bins[min(9, int(q * 10))].append(y)
        print("  calibration " + " ".join(
            f"{b * 10}-{b * 10 + 10}%:{100 * sum(v) / len(v):.0f}%({len(v)})" for b, v in enumerate(bins) if len(v) >= 30))
        parts = []
        for label, lo, hi in (("r1", 1, 1), ("r2-3", 2, 3), ("r4+", 4, 99)):
            ix = [i for i, r in enumerate(rows) if lo <= r[1] <= hi]
            if ix:
                l = sum(-math.log(max(qs[i] if ys[i] else 1 - qs[i], 1e-9)) for i in ix) / len(ix)
                parts.append(f"{label} {l:.4f} ({len(ix)})")
        print("  by round " + " ".join(parts))
    return loss


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("csv", nargs="+")
    ap.add_argument("--terms", default=",".join(HAND), help="terms fitted; the rest keep k x hand")
    ap.add_argument("--zero", default="", help="terms dropped (weight 0)")
    ap.add_argument("--l2", default="0.01,0.03,0.1,0.3,1")
    ap.add_argument("--players", default=None, help="regex on the players column")
    ap.add_argument("--rounds", default=None, help="lo-hi")
    ap.add_argument("--fold", type=int, default=0, help="which quarter of the games is held out (0-3)")
    ap.add_argument("--cv", action="store_true", help="4-fold cross-validation; the weights from all games")
    ap.add_argument("--distil", action="store_true", help="least squares onto the learned net's calibrated logit")
    args = ap.parse_args()
    rounds = tuple(int(x) for x in args.rounds.split("-")) if args.rounds else None
    rows = load(args.csv, args.players, rounds)
    if args.distil:
        distil(rows, args)
        return
    if args.cv:
        cross_validate(rows, args)
        return
    test_of = lambda r: (zlib.crc32(r[0].encode()) & 3) == args.fold
    train = [r for r in rows if not test_of(r)]
    test = [r for r in rows if test_of(r)]
    print(f"{len(rows)} rows ({len({r[0] for r in rows})} games): train {len(train)}, test {len(test)}")
    terms = list(HAND)

    def score(r):
        return sum(HAND[t] * r[5][t] for t in terms)

    k = fit([[score(r)] for r in train], [r[3] for r in train], [0.0], [1.0], 0.0)[0]
    report(f"current HEval (k = {k:.5f})", [k * score(r) for r in test], test)
    c = fit([[r[4]] for r in train], [r[3] for r in train], [0.0], [1.0], 0.0)[0]
    report(f"learned net (slope {c:.3f})", [c * r[4] for r in test], test)
    chosen = [t for t in args.terms.split(",") if t]
    zero = set(t for t in args.zero.split(",") if t)
    chosen = [t for t in chosen if t not in zero]
    rest = [t for t in terms if t not in chosen and t not in zero]

    def offset(r):
        return sum(k * HAND[t] * r[5][t] for t in rest)

    xs = [[r[5][t] for t in chosen] for r in train]
    offs = [offset(r) for r in train]
    sd = [max(1e-6, math.sqrt(sum(x[j] ** 2 for x in xs) / len(xs))) for j in range(len(chosen))]
    prior = [k * HAND[t] for t in chosen]
    best = None
    txs = [[r[5][t] for t in chosen] for r in test]
    toffs = [offset(r) for r in test]
    for l2 in (float(x) for x in args.l2.split(",")):
        v = fit(xs, [r[3] for r in train], prior, sd, l2, offs)
        zs = [o + sum(a * b for a, b in zip(v, x)) for o, x in zip(toffs, txs)]
        loss = report(f"fitted (l2 {l2})", zs, test)
        if best is None or loss < best[0]:
            best = (loss, l2, v)
    loss, l2, v = best
    print(f"best l2 {l2}: held-out log-loss {loss:.4f}")
    print(f"  {'term':12} {'hand':>8} {'fit':>9} {'fit/hand':>9} {'rms(f)':>8} {'rms(w.f)':>9}")
    for j, t in enumerate(chosen):
        w = v[j] / k
        ratio = f"{w / HAND[t]:9.2f}" if HAND[t] else "      new"
        print(f"  {t:12} {HAND[t]:8.3f} {w:9.3f} {ratio} {sd[j]:8.2f} {abs(w) * sd[j]:9.2f}")
    print("  weights: " + ", ".join(f"{t}={v[j] / k:.4f}" for j, t in enumerate(chosen)))


def distil(rows, args):
    """The terms fitted to the net's logit (slope-calibrated to outcomes), ridge toward k x hand."""
    terms = list(HAND)
    zero = set(t for t in args.zero.split(",") if t)
    chosen = [t for t in args.terms.split(",") if t and t not in zero]
    rest = [t for t in terms if t not in chosen and t not in zero]
    fold_of = lambda r: zlib.crc32(r[0].encode()) & 3
    train = [r for r in rows if fold_of(r) != args.fold]
    test = [r for r in rows if fold_of(r) == args.fold]
    score = lambda r: sum(HAND[t] * r[5][t] for t in terms)
    k = fit([[score(r)] for r in train], [r[3] for r in train], [0.0], [1.0], 0.0)[0]
    c = fit([[r[4]] for r in train], [r[3] for r in train], [0.0], [1.0], 0.0)[0]
    report(f"current HEval (k = {k:.5f})", [k * score(r) for r in test], test, quiet=True)
    report(f"learned net (slope {c:.3f})", [c * r[4] for r in test], test, quiet=True)
    p = len(chosen)
    for l2 in (float(x) for x in args.l2.split(",")):
        xs = [[r[5][t] for t in chosen] for r in train]
        ys = [c * r[4] - sum(k * HAND[t] * r[5][t] for t in rest) for r in train]
        sd = [max(1e-6, math.sqrt(sum(x[j] ** 2 for x in xs) / len(xs))) for j in range(p)]
        prior = [k * HAND[t] for t in chosen]
        a = [[0.0] * p for _ in range(p)]
        b = [0.0] * p
        for x, y in zip(xs, ys):
            for i in range(p):
                b[i] += x[i] * y
                for j in range(i, p):
                    a[i][j] += x[i] * x[j]
        for i in range(p):
            for j in range(i):
                a[i][j] = a[j][i]
            lam = l2 * len(xs) * sd[i] * sd[i]
            a[i][i] += lam
            b[i] += lam * prior[i]
        v = solve(a, b)
        pred = [sum(v[j] * x[j] for j in range(p)) for x in xs]
        mean = sum(ys) / len(ys)
        r2 = 1 - sum((q - y) ** 2 for q, y in zip(pred, ys)) / sum((y - mean) ** 2 for y in ys)
        zs = [sum(k * HAND[t] * r[5][t] for t in rest) + sum(v[j] * r[5][t] for j, t in enumerate(chosen)) for r in test]
        report(f"distilled (l2 {l2}, train R2 {r2:.3f})", zs, test, quiet=True)
    for j, t in enumerate(chosen):
        w = v[j] / k
        ratio = f"{w / HAND[t]:9.2f}" if HAND[t] else "      new"
        print(f"  {t:12} {HAND[t]:8.3f} {w:9.3f} {ratio} {sd[j]:8.2f} {abs(w) * sd[j]:9.2f}")
    print("  weights: " + ", ".join(f"{t}={v[j] / k:.4f}" for j, t in enumerate(chosen)))


def cross_validate(rows, args):
    terms = list(HAND)
    zero = set(t for t in args.zero.split(",") if t)
    chosen = [t for t in args.terms.split(",") if t and t not in zero]
    rest = [t for t in terms if t not in chosen and t not in zero]
    fold_of = lambda r: zlib.crc32(r[0].encode()) & 3
    score = lambda r: sum(HAND[t] * r[5][t] for t in terms)
    l2s = [float(x) for x in args.l2.split(",")]
    totals = {"current": 0.0, "net": 0.0}
    totals.update({l2: 0.0 for l2 in l2s})
    n = 0

    def ll(zs, rs):
        return sum(-math.log(max(sigmoid(z) if r[3] else 1 - sigmoid(z), 1e-9)) for z, r in zip(zs, rs))

    def design(rs, k):
        return [[r[5][t] for t in chosen] for r in rs], [sum(k * HAND[t] * r[5][t] for t in rest) for r in rs]

    for fold in range(4):
        train = [r for r in rows if fold_of(r) != fold]
        test = [r for r in rows if fold_of(r) == fold]
        k = fit([[score(r)] for r in train], [r[3] for r in train], [0.0], [1.0], 0.0)[0]
        c = fit([[r[4]] for r in train], [r[3] for r in train], [0.0], [1.0], 0.0)[0]
        totals["current"] += ll([k * score(r) for r in test], test)
        totals["net"] += ll([c * r[4] for r in test], test)
        xs, offs = design(train, k)
        txs, toffs = design(test, k)
        sd = [max(1e-6, math.sqrt(sum(x[j] ** 2 for x in xs) / len(xs))) for j in range(len(chosen))]
        prior = [k * HAND[t] for t in chosen]
        for l2 in l2s:
            v = fit(xs, [r[3] for r in train], prior, sd, l2, offs)
            totals[l2] += ll([o + sum(a * b for a, b in zip(v, x)) for o, x in zip(toffs, txs)], test)
        n += len(test)
    print(f"{len(rows)} rows ({len({r[0] for r in rows})} games), 4-fold cross-validated log-loss:")
    print(f"  current HEval {totals['current'] / n:.4f}   learned net {totals['net'] / n:.4f}")
    best = min(l2s, key=lambda l2: totals[l2])
    print("  fitted " + "  ".join(f"l2 {l2}: {totals[l2] / n:.4f}" for l2 in l2s))
    k = fit([[score(r)] for r in rows], [r[3] for r in rows], [0.0], [1.0], 0.0)[0]
    xs, offs = design(rows, k)
    sd = [max(1e-6, math.sqrt(sum(x[j] ** 2 for x in xs) / len(xs))) for j in range(len(chosen))]
    v = fit(xs, [r[3] for r in rows], [k * HAND[t] for t in chosen], sd, best, offs)
    print(f"  weights from all games at l2 {best} (k = {k:.5f}):")
    for j, t in enumerate(chosen):
        w = v[j] / k
        ratio = f"{w / HAND[t]:9.2f}" if HAND[t] else "      new"
        print(f"  {t:12} {HAND[t]:8.3f} {w:9.3f} {ratio} {sd[j]:8.2f} {abs(w) * sd[j]:9.2f}")
    print("  weights: " + ", ".join(f"{t}={v[j] / k:.4f}" for j, t in enumerate(chosen)))


if __name__ == "__main__":
    main()
