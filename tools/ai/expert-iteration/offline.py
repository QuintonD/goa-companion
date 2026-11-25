#!/usr/bin/env python3
"""Offline check of leaf nets for expert iteration (Wave 4 Track H).

Reads the dump of `LearnedTrainTest` "dump held-out logits" (AI_DUMP) and prints markdown tables:

  1. the search values' calibration on the TRAINING split (fit of P(Red wins) = sigmoid(v / s) to the results),
  2. held-out positions the search scored: each net and ensemble against the search's values (RMS, excess
     cross-entropy) and against the final results (log-loss, Brier), with a per-game paired bootstrap of the
     log-loss difference against the reference ensemble,
  3. every held-out position (book games and search games) against the results,
  4. a reliability table per net/ensemble on held-out positions,
  5. the last push: held-out positions where a lane is at its last Wave counter, and the subset late in the round.

Ensembles average the nets' logits weighted by their coin scales, exactly as the leaf averages their scores
(NetEvaluator: mean of logit x coinScale), divided by the mean coin scale: the leaf's value in probability.

Usage: python3 -I offline.py DUMP.tsv --nets-dir DIR [--ens NAME=a+b+c ...] [--ref NET6] [--sv-scale S]
No third-party packages; deterministic (fixed bootstrap seed).
"""

import argparse
import math
import random
import struct
import sys
from collections import defaultdict

CUR_SCALE = 39.14600984678122  # LearnedWeights / 2 / 3 COIN_SCALE (the checked-in nets)
CLAMP = 12.0  # the trainer's default SV_CLAMP
LEAF_SCALE = 37.852  # the six-net leaf's mean coin scale: the search's values are in its units


def sigmoid(z):
    if z >= 0:
        return 1.0 / (1.0 + math.exp(-z))
    e = math.exp(z)
    return e / (1.0 + e)


def coin_scale(name, nets_dir):
    if name.startswith("cur"):
        return CUR_SCALE
    with open(f"{nets_dir}/{name}.net", "rb") as f:
        head = f.read(28)
    # Learned.saveNet: schema long, inputs/h1/h2 ints, then coinScale (big-endian double).
    return struct.unpack(">d", head[20:28])[0]


def ll(p, y):
    p = min(max(p, 1e-9), 1 - 1e-9)
    return -(math.log(p) if y else math.log(1 - p))


def xent_excess(p, q):
    """Cross-entropy of p against soft target q minus q's entropy (= KL(q || p))."""
    p = min(max(p, 1e-9), 1 - 1e-9)
    q = min(max(q, 1e-9), 1 - 1e-9)
    return q * math.log(q / p) + (1 - q) * math.log((1 - q) / (1 - p))


def fit_scale(vs, ys):
    """1-D logistic regression without intercept: P = sigmoid(a v); returns s = 1 / a (Newton)."""
    a = 1 / 37.852
    for _ in range(50):
        g = h = 0.0
        for v, y in zip(vs, ys):
            z = max(-CLAMP, min(CLAMP, a * v))
            q = sigmoid(z)
            g += (y - q) * v
            h += q * (1 - q) * v * v
        step = g / (h + 1e-12)
        a += step
        if abs(step) < 1e-10:
            break
    return 1 / a


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("dump")
    ap.add_argument("--nets-dir", required=True)
    ap.add_argument("--ens", action="append", default=[], help="NAME=a+b+c (coin-scale-weighted mean logit)")
    ap.add_argument("--ref", default="NET6", help="reference for the paired bootstrap")
    ap.add_argument("--sv-scale", type=float, default=LEAF_SCALE,
                    help="coin-equivalents per logit [the six-net leaf's mean coin scale]")
    ap.add_argument("--sv-cap", type=float, default=None,
                    help="cap on the search value's logit [NET6's 99th-percentile |logit| on the training split]")
    ap.add_argument("--boot", type=int, default=1000)
    ap.add_argument("--members", action="store_true", help="also list every single net")
    ap.add_argument("--print-cap", action="store_true", help="print the search-value cap only (for round.sh)")
    a = ap.parse_args()

    with open(a.dump) as f:
        head = f.readline().rstrip("\n").split("\t")
        rows = [line.rstrip("\n").split("\t") for line in f if line.strip()]
    base = head.index("mpBlue") + 1
    nets = head[base:]
    scales = {n: coin_scale(n, a.nets_dir) for n in nets}
    ens = {}
    if all(n in nets for n in ["cur1", "cur2", "cur3", "g1", "g2", "g3"]):
        ens["NET6"] = ["cur1", "cur2", "cur3", "g1", "g2", "g3"]
        ens["NET3 (cur)"] = ["cur1", "cur2", "cur3"]
        ens["g1-3"] = ["g1", "g2", "g3"]
    for e in a.ens:
        k, v = e.split("=", 1)
        ens[k] = v.split("+")
    models = dict(ens)
    if a.members:
        for n in nets:
            models[n] = [n]

    col = {k: i for i, k in enumerate(head)}
    P = []  # positions
    for r in rows:
        z = {n: float(r[base + i]) for i, n in enumerate(nets)}
        P.append({
            "file": r[col["file"]], "split": r[col["split"]], "game": r[col["game"]],
            "size": int(r[col["size"]]), "y": r[col["redWon"]] == "1",
            "sv": float(r[col["sv"]]), "turn": float(r[col["turn"]]), "round": float(r[col["round"]]),
            "waves": float(r[col["waves"]]), "mp": float(r[col["mpRed"]]) + float(r[col["mpBlue"]]),
            "z": z,
        })

    def prob(p, model):
        ns = models[model]
        return sigmoid(sum(scales[n] * p["z"][n] for n in ns) / sum(scales[n] for n in ns))

    # 1. The search values on the training split: the cap (NET6's own confidence) and how they predict the result.
    tr = [p for p in P if p["split"] == "train" and not math.isnan(p["sv"])]
    if a.sv_cap is None and "NET6" in models and tr:
        zs = sorted(abs(math.log(prob(p, "NET6") / (1 - prob(p, "NET6")))) for p in tr)
        a.sv_cap = round(zs[int(0.99 * len(zs))], 2)
    cap = a.sv_cap or CLAMP
    if a.print_cap:
        print(f"{cap:.2f}")
        return 0
    s = a.sv_scale
    fitted = fit_scale([p["sv"] for p in tr], [p["y"] for p in tr]) if tr else float("nan")
    print(f"## Search values\n\nTraining-split positions with a search value: {len(tr)}. Read as P(Red wins) = "
          f"σ(v / {s:.2f}) with the logit capped at ±{cap:.2f} (NET6's 99th-percentile confidence there). For "
          f"reference, one scale fitted to the results without a cap: {fitted:.2f}.\n")
    for p in P:
        if math.isnan(p["sv"]):
            p["q"] = p["q0"] = p["q1"] = float("nan")
            continue
        p["q"] = sigmoid(max(-cap, min(cap, p["sv"] / s)))
        p["q0"] = sigmoid(max(-CLAMP, min(CLAMP, p["sv"] / s)))
        p["q1"] = sigmoid(max(-CLAMP, min(CLAMP, p["sv"] / fitted)))
    if tr:
        print("| On the training split | n | log-loss vs result | Brier |\n|---|---:|---:|---:|")
        for key, lab in (("q0", f"search value σ(v/{s:.2f}), cap ±{CLAMP:.0f}"),
                         ("q1", f"search value σ(v/{fitted:.2f}), cap ±{CLAMP:.0f}"),
                         ("q", f"search value σ(v/{s:.2f}), cap ±{cap:.2f} (used)")):
            print(f"| {lab} | {len(tr)} | {sum(ll(p[key], p['y']) for p in tr) / len(tr):.4f} | "
                  f"{sum((p[key] - p['y']) ** 2 for p in tr) / len(tr):.4f} |")
        for m in ("NET6",) if "NET6" in models else ():
            print(f"| {m} at the same positions | {len(tr)} | "
                  f"{sum(ll(prob(p, m), p['y']) for p in tr) / len(tr):.4f} | "
                  f"{sum((prob(p, m) - p['y']) ** 2 for p in tr) / len(tr):.4f} |")
        print()

    test = [p for p in P if p["split"] == "test"]
    searched = [p for p in test if not math.isnan(p["q"])]

    def metrics(ps, m):
        n = len(ps)
        pr = [prob(p, m) for p in ps]
        out = {
            "n": n,
            "ll": sum(ll(x, p["y"]) for x, p in zip(pr, ps)) / n,
            "brier": sum((x - p["y"]) ** 2 for x, p in zip(pr, ps)) / n,
        }
        sv = [(x, p["q"]) for x, p in zip(pr, ps) if not math.isnan(p["q"])]
        if sv:
            out["rms"] = math.sqrt(sum((x - q) ** 2 for x, q in sv) / len(sv))
            out["kl"] = sum(xent_excess(x, q) for x, q in sv) / len(sv)
        return out

    def boot(ps, m, ref):
        """Per-game paired bootstrap of mean log-loss(m) - log-loss(ref): 2.5 / 97.5 percentiles."""
        games = defaultdict(list)
        for p in ps:
            games[(p["file"], p["game"])].append(ll(prob(p, m), p["y"]) - ll(prob(p, ref), p["y"]))
        keys = sorted(games)
        sums = [(sum(games[k]), len(games[k])) for k in keys]
        rng = random.Random(20261124)
        ds = []
        for _ in range(a.boot):
            t = c = 0.0
            for _ in keys:
                s_, n_ = sums[rng.randrange(len(keys))]
                t += s_
                c += n_
            ds.append(t / c)
        ds.sort()
        d = sum(x for x, _ in sums) / sum(n for _, n in sums)
        return d, ds[int(0.025 * len(ds))], ds[int(0.975 * len(ds)) - 1]

    def table(title, ps, with_sv):
        if not ps:
            return
        games = len({(p["file"], p["game"]) for p in ps})
        print(f"## {title}\n\n{len(ps)} positions from {games} games.\n")
        hdr = "| Model | log-loss vs result | Brier |"
        sep = "|---|---:|---:|"
        if with_sv:
            hdr += " RMS vs search value | KL(search ‖ model) |"
            sep += "---:|---:|"
        if a.ref in models:
            hdr += f" Δ log-loss vs {a.ref} (95% per-game bootstrap) |"
            sep += "---|"
        print(hdr + "\n" + sep)
        if with_sv:
            q = [p["q"] for p in ps]
            y = [p["y"] for p in ps]
            print(f"| *the search's value itself* | {sum(ll(x, b) for x, b in zip(q, y)) / len(q):.4f} | "
                  f"{sum((x - b) ** 2 for x, b in zip(q, y)) / len(q):.4f} | 0 | 0 |" +
                  (" |" if a.ref in models else ""))
        for m in models:
            r = metrics(ps, m)
            line = f"| {m} | {r['ll']:.4f} | {r['brier']:.4f} |"
            if with_sv:
                line += f" {r.get('rms', float('nan')):.4f} | {r.get('kl', float('nan')):.4f} |"
            if a.ref in models:
                if m == a.ref:
                    line += " — |"
                else:
                    d, lo, hi = boot(ps, m, a.ref)
                    line += f" {d:+.4f} ({lo:+.4f} … {hi:+.4f}) |"
            print(line)
        print()

    table("Held out, positions the search scored (search games, line-up % 10 = 9)", searched, True)
    table("Held out, every position of the search games", [p for p in test if p["file"].startswith("x-")], False)
    table("Held out, every position of the book games", [p for p in test if not p["file"].startswith("x-")], False)

    # 4. Reliability on held-out search-game positions (the leaf's distribution) and on all held-out positions.
    def reliability(title, ps):
        print(f"## Reliability: {title} ({len(ps)} positions)\n")
        bins = 10
        print("| Model | " + " | ".join(f"{i / bins:.1f}–{(i + 1) / bins:.1f}" for i in range(bins)) +
              " | ECE |\n|---|" + "---|" * (bins + 1))
        rel = list(models.items())
        cols = [("search value", None)] if all(not math.isnan(p["q"]) for p in ps) else []
        for name, _ in cols + rel:
            acc = [[0, 0.0, 0] for _ in range(bins)]
            for p in ps:
                x = p["q"] if name == "search value" else prob(p, name)
                b = min(bins - 1, int(x * bins))
                acc[b][0] += 1
                acc[b][1] += x
                acc[b][2] += p["y"]
            ece = sum(abs(c[1] / c[0] - c[2] / c[0]) * c[0] for c in acc if c[0]) / len(ps)
            cells = [f"{c[1] / c[0]:.2f}→{c[2] / c[0]:.2f} ({c[0]})" if c[0] else "—" for c in acc]
            print(f"| {name} | " + " | ".join(cells) + f" | {ece:.4f} |")
        print("\nCells: mean predicted P(Red wins) → observed Red win rate (positions).\n")

    reliability("held out, positions the search scored", searched)
    reliability("held out, every position", test)

    def delta(ps, m):
        if a.ref not in models or m == a.ref:
            return "—"
        d, lo, hi = boot(ps, m, a.ref)
        return f"{d:+.4f} ({lo:+.4f} … {hi:+.4f})"

    # 5. The last push.
    lp = [p for p in test if p["waves"] <= 1.0001]
    late = [p for p in lp if p["turn"] >= 3]
    mp = [p for p in test if p["mp"] > 0]
    turns = sorted({p["turn"] for p in test})
    print(f"## The last push\n\nTurn values seen: {turns}. Last push = Wave counters ≤ 1 (lane mean), "
          f"{len(lp)} held-out positions; late = turn ≥ 3 of the round: {len(late)}; match point (a team one push "
          f"from the Throne): {len(mp)}.\n")
    for title, ps in (("last push", lp), ("last push, late in the round", late), ("match point", mp),
                      ("last push, positions the search scored", [p for p in lp if not math.isnan(p["q"])])):
        if not ps:
            continue
        print(f"### {title} ({len(ps)} positions, {len({(p['file'], p['game']) for p in ps})} games)\n")
        print(f"| Model | log-loss vs result | Brier | mean abs(P − P_{a.ref}) | RMS vs search value (n) | "
              f"Δ log-loss vs {a.ref} (95% per-game bootstrap) |\n|---|---:|---:|---:|---:|---|")
        sq = [p for p in ps if not math.isnan(p["q"])]
        if len(sq) == len(ps):
            print(f"| *the search's value itself* | {sum(ll(p['q'], p['y']) for p in ps) / len(ps):.4f} | "
                  f"{sum((p['q'] - p['y']) ** 2 for p in ps) / len(ps):.4f} | | | |")
        for m in models:
            pr = [prob(p, m) for p in ps]
            refp = [prob(p, a.ref) for p in ps] if a.ref in models else pr
            svp = [(x, p["q"]) for x, p in zip(pr, ps) if not math.isnan(p["q"])]
            rms = f"{math.sqrt(sum((x - q) ** 2 for x, q in svp) / len(svp)):.4f} ({len(svp)})" if svp else "—"
            print(f"| {m} | {sum(ll(x, p['y']) for x, p in zip(pr, ps)) / len(ps):.4f} | "
                  f"{sum((x - p['y']) ** 2 for x, p in zip(pr, ps)) / len(ps):.4f} | "
                  f"{sum(abs(x - r) for x, r in zip(pr, refp)) / len(ps):.4f} | {rms} | {delta(ps, m)} |")
        print()


if __name__ == "__main__":
    sys.exit(main())
