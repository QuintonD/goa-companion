#!/usr/bin/env python3
"""UI/UX hill climb harness: collect a screenshot corpus, judge it with gpt-6.1-sol, compare before/after,
generate art. The second judge (Claude Opus) runs as a Claude Code agent on the same corpus and rubric.

  python3 -I hc.py collect <out_dir> [--root <repo>] [--only id,id]
  python3 -I hc.py judge <corpus_dir> <out.json>                 # full scoring (rubric.md)
  python3 -I hc.py blind <before_dir> <after_dir> <cmp_dir> [--only id,id]   # X|Y composites, hidden order
  python3 -I hc.py compare <cmp_dir> <out.json> [--note "what changed"]      # sol picks per pair, unblinded
  python3 -I hc.py unblind <cmp_dir> <raw.json> <out.json> <judge>           # map another judge's X/Y picks
  python3 -I hc.py imagegen <out.png> "<prompt>" [--size 1024x1536] [--ref img.png ...] [--quality high]
  python3 -I hc.py board <runs_dir>                               # score history table (markdown)

API calls go through tools/ai/azure_ai.py: credentials from the secrets folder (~/.config/goa-companion/secrets/
azure-ai.env), machine-wide rate limits (gpt-6.1-sol 333k tokens/min, gpt-image 4 images/min), HTTP 429 waits a minute.
"""
import base64, glob, io, json, os, random, sys, time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "ai"))  # run with -I: the script dir is not on sys.path
import azure_ai  # noqa: E402

CHAT_MODEL = azure_ai.config()["AZURE_SOL_MODEL"]
ASPECTS = ["hierarchy", "aesthetic", "art_assets", "typography", "color_contrast", "information",
           "affordance", "navigation", "feedback", "accessibility", "table_fitness", "consistency"]
WIDTH = 600  # px per portrait screen sent to judges (about 1.46 px per dp: body text stays legible)


def corpus():
    rows = []
    for line in open(os.path.join(HERE, "corpus.tsv"), encoding="utf-8"):
        if line.startswith("#") or not line.strip():
            continue
        sid, shot, caption = line.rstrip("\n").split("\t")
        rows.append({"id": sid, "shot": shot, "caption": caption})
    return rows


def arg(flag, default=None):
    if flag in sys.argv:
        i = sys.argv.index(flag)
        return sys.argv[i + 1]
    return default


def collect(out_dir, root, only):
    from PIL import Image
    os.makedirs(out_dir, exist_ok=True)
    manifest = []
    for row in corpus():
        if only and row["id"] not in only:
            continue
        hits = glob.glob(os.path.join(root, "**/src/test/screenshots", f"dev.quinton.goa.{row['shot']}.png"),
                         recursive=True)
        hits = [h for h in hits if "/build/" not in h]
        if not hits:
            print(f"[hc] missing screenshot for {row['id']}: {row['shot']}", file=sys.stderr)
            continue
        im = Image.open(hits[0]).convert("RGB")
        portrait = im.height >= im.width
        w = WIDTH if portrait else int(WIDTH * 2.2)
        if im.width > w:
            im = im.resize((w, round(im.height * w / im.width)), Image.LANCZOS)
        path = os.path.join(out_dir, row["id"] + ".jpg")
        im.save(path, quality=88, optimize=True)
        manifest.append({**row, "file": os.path.abspath(path)})
    json.dump(manifest, open(os.path.join(out_dir, "manifest.json"), "w"), indent=1)
    print(f"[hc] {len(manifest)} screens in {out_dir}")


def compose(paths_labels, out):
    """Side by side, each under a label strip, as one JPEG (the judge API takes at most 50 images)."""
    from PIL import Image, ImageDraw, ImageFont
    ims = [(Image.open(p).convert("RGB"), label) for p, label in paths_labels]
    strip, gap = 44, 16
    w = sum(i.width for i, _ in ims) + gap * (len(ims) - 1)
    h = max(i.height for i, _ in ims) + strip
    canvas = Image.new("RGB", (w, h), (255, 255, 255))
    draw = ImageDraw.Draw(canvas)
    try:
        font = ImageFont.truetype("DejaVuSans-Bold.ttf", 28)
    except OSError:
        font = ImageFont.load_default()
    x = 0
    for im, label in ims:
        draw.text((x + 8, 6), label, fill=(0, 0, 0), font=font)
        canvas.paste(im, (x, strip))
        x += im.width + gap
    canvas.save(out, quality=88, optimize=True)
    return out


def packed(manifest, tmp):
    """Groups of up to two portrait screens (landscape alone) as composite images: [(ids, file)]."""
    os.makedirs(tmp, exist_ok=True)
    from PIL import Image
    groups, cur = [], []
    for m in manifest:
        w, h = Image.open(m["file"]).size
        if w > h:
            groups.append([m])
            continue
        cur.append(m)
        if len(cur) == 2:
            groups.append(cur); cur = []
    if cur:
        groups.append(cur)
    out = []
    for g in groups:
        f = os.path.join(tmp, "+".join(m["id"] for m in g) + ".jpg")
        compose([(m["file"], m["id"]) for m in g], f)
        out.append((g, f))
    return out


def b64(path):
    mime = "image/jpeg" if path.endswith(".jpg") else "image/png"
    return f"data:{mime};base64," + base64.b64encode(open(path, "rb").read()).decode()


def ask(content, effort="high"):
    return azure_ai.respond(content, effort)[0]


def parse_json(text):
    s, e = text.find("{"), text.rfind("}")
    return json.loads(text[s:e + 1])


def judge(corpus_dir, out):
    manifest = json.load(open(os.path.join(corpus_dir, "manifest.json")))
    content = [{"type": "input_text", "text": open(os.path.join(HERE, "rubric.md")).read()}]
    for group, f in packed(manifest, os.path.join(corpus_dir, "packed")):
        text = " | ".join(f"Screen `{m['id']}` (labelled): {m['caption']}" for m in group)
        content.append({"type": "input_text", "text": text})
        content.append({"type": "input_image", "image_url": b64(f), "detail": "original"})
    content.append({"type": "input_text", "text": "Now return the JSON object described in the brief."})
    for attempt in range(3):
        text = ask(content)
        try:
            result = parse_json(text)
            break
        except ValueError:
            print("[hc] reply was not JSON, asking again", file=sys.stderr)
    else:
        raise SystemExit(text[:2000])
    result["judge"] = CHAT_MODEL
    json.dump(result, open(out, "w"), indent=1)
    print(summary(result))


COMPARE = """You are a senior product designer judging a proposed UI/UX change to an Android app, from screenshots.
The app: Guards of Atlantis Companion, a companion app for the board game Guards of Atlantis II, used at the game
table (dim light, passed between players). It should look like the game itself (steel banner, slate plates,
parchment title plates, official art; bronze = selected/primary, teal = next/you can act).

For each screen you get two versions, X and Y, in random order. Decide which is the better UI/UX for a player,
or "same" if there is no meaningful difference. Then say whether, taken together, the change set should be kept.
Be strict about regressions: clipped text, lost information, worse contrast, clutter, inconsistency, off-style art.
Return only JSON:
{"pairs":[{"id":"<screen id>","better":"X|Y|same","why":"short"}],
 "regressions":[{"id":"<screen id>","version":"X|Y","problem":"a concrete flaw in that version the other one does not have"}]}"""


def blind(before_dir, after_dir, cmp_dir, only):
    """Composite X|Y image per screen in random order; the order is saved for unblinding (both judges use it)."""
    after = {m["id"]: m for m in json.load(open(os.path.join(after_dir, "manifest.json")))}
    before = {m["id"]: m for m in json.load(open(os.path.join(before_dir, "manifest.json")))}
    ids = [i for i in after if i in before and (not only or i in only)][:50]
    os.makedirs(cmp_dir, exist_ok=True)
    pairs = []
    for i in ids:
        flip = random.random() < 0.5  # X is "after" when flip
        x, y = (after[i], before[i]) if flip else (before[i], after[i])
        f = compose([(x["file"], f"{i}: X"), (y["file"], f"{i}: Y")], os.path.join(cmp_dir, f"cmp-{i}.jpg"))
        pairs.append({"id": i, "caption": after[i]["caption"], "file": os.path.abspath(f), "x_is_after": flip})
    json.dump(pairs, open(os.path.join(cmp_dir, "order.json"), "w"), indent=1)
    # What a judge may see (no order):
    json.dump([{k: p[k] for k in ("id", "caption", "file")} for p in pairs],
              open(os.path.join(cmp_dir, "pairs.json"), "w"), indent=1)
    open(os.path.join(cmp_dir, "prompt.md"), "w").write(COMPARE)
    return pairs


def unblind(cmp_dir, result, judge_name):
    order = {p["id"]: p["x_is_after"] for p in json.load(open(os.path.join(cmp_dir, "order.json")))}
    def side(v, i):
        if v not in ("X", "Y") or i not in order:
            return "same"
        return ("after" if v == "X" else "before") if order[i] else ("before" if v == "X" else "after")
    for p in result.get("pairs", []):
        p["better"] = side(p.get("better"), p.get("id"))
    for r in result.get("regressions", []):
        r["version"] = side(r.get("version"), r.get("id"))
    wins = sum(p["better"] == "after" for p in result["pairs"])
    losses = sum(p["better"] == "before" for p in result["pairs"])
    result["after_wins"], result["before_wins"] = wins, losses
    result["judge"] = judge_name
    print(f"{judge_name}: after better on {wins}, before better on {losses}, "
          f"same on {len(result['pairs']) - wins - losses}")
    for p in result["pairs"]:
        print(f"  {p['id']}: {p['better']} — {p.get('why', '')}")
    for r in result.get("regressions", []):
        print(f"  flaw in {r.get('version')} {r.get('id')}: {r.get('problem')}")
    return result


def compare(cmp_dir, out, note):
    pairs = json.load(open(os.path.join(cmp_dir, "pairs.json")))
    content = [{"type": "input_text", "text": COMPARE + (f"\nWhat the change intends: {note}" if note else "")}]
    for p in pairs:
        content.append({"type": "input_text", "text": f"Screen `{p['id']}`: {p['caption']} (X left, Y right)"})
        content.append({"type": "input_image", "image_url": b64(p["file"]), "detail": "original"})
    result = unblind(cmp_dir, parse_json(ask(content)), CHAT_MODEL)
    json.dump(result, open(out, "w"), indent=1)


def imagegen(out, prompt, size, refs, quality):
    azure_ai.image(out, prompt, size, quality, refs)


def summary(r):
    a = r.get("aspects", {})
    cells = " ".join(f"{k}={a.get(k, {}).get('score', '?')}" for k in ASPECTS)
    low = min((a.get(k, {}).get("score", 0) for k in ASPECTS), default=0)
    return f"{r.get('judge', '?')}: overall {r.get('overall')} min {low} | {cells}"


def board(runs_dir):
    print("| round | judge | min | " + " | ".join(ASPECTS) + " |")
    print("|---" * (len(ASPECTS) + 3) + "|")
    for f in sorted(glob.glob(os.path.join(runs_dir, "*/score-*.json"))):
        r = json.load(open(f))
        a = r.get("aspects", {})
        s = [a.get(k, {}).get("score", "?") for k in ASPECTS]
        nums = [x for x in s if isinstance(x, (int, float))]
        print(f"| {os.path.basename(os.path.dirname(f))} | {r.get('judge', os.path.basename(f))} | "
              f"{min(nums) if nums else '?'} | " + " | ".join(str(x) for x in s) + " |")


if __name__ == "__main__":
    cmd = sys.argv[1]
    only = set(arg("--only", "").split(",")) - {""}
    if cmd == "collect":
        collect(sys.argv[2], arg("--root", os.path.abspath(os.path.join(HERE, "../.."))), only)
    elif cmd == "judge":
        judge(sys.argv[2], sys.argv[3])
    elif cmd == "blind":  # blind <before> <after> <cmp_dir> [--only]
        print(len(blind(sys.argv[2], sys.argv[3], sys.argv[4], only)), "pairs in", sys.argv[4])
    elif cmd == "compare":  # compare <cmp_dir> <out.json> [--note]
        compare(sys.argv[2], sys.argv[3], arg("--note"))
    elif cmd == "unblind":  # unblind <cmp_dir> <raw.json> <out.json> <judge>
        json.dump(unblind(sys.argv[2], json.load(open(sys.argv[3])), sys.argv[5]), open(sys.argv[4], "w"), indent=1)
    elif cmd == "imagegen":
        refs = [sys.argv[i + 1] for i, a in enumerate(sys.argv) if a == "--ref"]
        imagegen(sys.argv[2], sys.argv[3], arg("--size", "1024x1024"), refs, arg("--quality", "high"))
    elif cmd == "board":
        board(sys.argv[2])
    else:
        raise SystemExit(__doc__)
