#!/usr/bin/env python3
"""Azure AI Foundry client for the project's agents: gpt-6.1-sol (review, judging) and gpt-image (assets).

Credentials come from the secrets folder, never from the repo:
  $GOA_SECRETS_DIR/azure-ai.env   (default ~/.config/goa-companion/secrets/azure-ai.env)
  AZURE_AI_ENDPOINT=https://<resource>.services.ai.azure.com/openai/v1
  AZURE_AI_KEY=...
  AZURE_SOL_MODEL=gpt-6.1-sol
  AZURE_IMAGE_MODEL=gpt-image-2.5-sunburst-Global
Environment variables of the same names override the file. See tools/ai/azure-ai.env.example.

Rate limits (shared by every process on this machine, through a lock file in ~/.cache/goa-companion):
  gpt-6.1-sol  333,000 tokens per minute (AZURE_SOL_TPM)
  gpt-image    4 images per minute       (AZURE_IMAGES_PM)
A request that would exceed a limit waits until the minute frees up; an HTTP 429 (or 5xx) waits a minute and
retries. Never loop on your own around this tool.

CLI:
  python3 -I tools/ai/azure_ai.py check
  python3 -I tools/ai/azure_ai.py ask [--effort high] [--image F]... [--prompt-file F | -] [--out F] [--json]
  python3 -I tools/ai/azure_ai.py image OUT.png "prompt" [--size 1024x1024] [--quality high] [--ref F]...
"""
import base64, fcntl, json, os, random, sys, time, urllib.error, urllib.request

SECRETS_DIR = os.environ.get("GOA_SECRETS_DIR", os.path.expanduser("~/.config/goa-companion/secrets"))
STATE_DIR = os.path.expanduser("~/.cache/goa-companion")
WINDOW = 60.0


# ---------- configuration ----------
def config():
    cfg = {}
    path = os.path.join(SECRETS_DIR, "azure-ai.env")
    if os.path.exists(path):
        for line in open(path, encoding="utf-8"):
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                cfg[k.strip()] = v.strip().strip('"').strip("'")
    for k in ("AZURE_AI_ENDPOINT", "AZURE_AI_KEY", "AZURE_SOL_MODEL", "AZURE_IMAGE_MODEL", "AZURE_SOL_TPM",
              "AZURE_IMAGES_PM"):
        if os.environ.get(k):
            cfg[k] = os.environ[k]
    missing = [k for k in ("AZURE_AI_ENDPOINT", "AZURE_AI_KEY") if not cfg.get(k)]
    if missing:
        raise SystemExit(f"azure_ai: missing {', '.join(missing)}; put them in {path} (see tools/ai/azure-ai.env.example)")
    cfg.setdefault("AZURE_SOL_MODEL", "gpt-6.1-sol")
    cfg.setdefault("AZURE_IMAGE_MODEL", "gpt-image-2.5-sunburst-Global")
    cfg["AZURE_SOL_TPM"] = int(cfg.get("AZURE_SOL_TPM", 333_000))
    cfg["AZURE_IMAGES_PM"] = int(cfg.get("AZURE_IMAGES_PM", 4))
    cfg["AZURE_AI_ENDPOINT"] = cfg["AZURE_AI_ENDPOINT"].rstrip("/")
    return cfg


def log(msg):
    print(f"[azure_ai] {msg}", file=sys.stderr, flush=True)


# ---------- machine-wide rate limiter ----------
class _Ledger:
    """Rolling one-minute budget per kind ('sol' tokens, 'image' count), shared across processes."""

    def __init__(self):
        os.makedirs(STATE_DIR, exist_ok=True)
        self.path = os.path.join(STATE_DIR, "ai-ratelimit.json")
        self.lock = os.path.join(STATE_DIR, "ai-ratelimit.lock")

    def _locked(self, fn):
        with open(self.lock, "a") as fh:
            fcntl.flock(fh, fcntl.LOCK_EX)
            try:
                try:
                    data = json.load(open(self.path))
                except (OSError, ValueError):
                    data = []
                now = time.time()
                data = [e for e in data if now - e["t"] < WINDOW]
                result = fn(data, now)
                json.dump(data, open(self.path, "w"))
                return result
            finally:
                fcntl.flock(fh, fcntl.LOCK_UN)

    def reserve(self, kind, amount, limit):
        """Block until `amount` fits in the last minute's budget; return an id to settle later."""
        while True:
            def attempt(data, now):
                used = sum(e["n"] for e in data if e["k"] == kind)
                if used + amount <= limit or used == 0:
                    eid = f"{now}-{random.random()}"
                    data.append({"t": now, "k": kind, "n": amount, "id": eid})
                    return eid, 0.0
                oldest = min(e["t"] for e in data if e["k"] == kind)
                return None, max(1.0, oldest + WINDOW - now)
            eid, wait = self._locked(attempt)
            if eid:
                return eid
            log(f"{kind} rate limit ({limit}/min) reached; waiting {wait:.0f} s")
            time.sleep(wait)

    def settle(self, eid, actual):
        def upd(data, now):
            for e in data:
                if e["id"] == eid:
                    e["n"] = actual
        self._locked(upd)


LEDGER = _Ledger()


# ---------- HTTP with retry on 429/5xx ----------
def _send(req_factory, timeout=900):
    for attempt in range(30):
        try:
            return json.load(urllib.request.urlopen(req_factory(), timeout=timeout))
        except urllib.error.HTTPError as e:
            body = e.read().decode(errors="replace")[:400]
            if e.code == 429 or e.code >= 500:
                wait = 60
                try:
                    wait = max(wait, int(e.headers.get("Retry-After", "0")))
                except ValueError:
                    pass
                log(f"HTTP {e.code} (rate limit or server busy); waiting {wait} s before retry {attempt + 1}: {body[:160]}")
                time.sleep(wait)
                continue
            raise SystemExit(f"azure_ai: HTTP {e.code}: {body}")
        except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
            log(f"{e}; waiting 60 s")
            time.sleep(60)
    raise SystemExit("azure_ai: gave up after 30 attempts")


def b64_image(path):
    mime = "image/jpeg" if path.lower().endswith((".jpg", ".jpeg")) else "image/png"
    if path.lower().endswith(".webp"):
        mime = "image/webp"
    return f"data:{mime};base64," + base64.b64encode(open(path, "rb").read()).decode()


# ---------- gpt-6.1-sol ----------
def respond(content, effort="high", cfg=None):
    """content: list of Responses API input parts ({type: input_text|input_image, ...}). Returns (text, usage)."""
    cfg = cfg or config()
    chars = sum(len(p.get("text", "")) for p in content if p.get("type") == "input_text")
    images = sum(1 for p in content if p.get("type") == "input_image")
    estimate = chars // 4 + images * 2000 + 8000  # input + room for reasoning and the reply
    eid = LEDGER.reserve("sol", estimate, cfg["AZURE_SOL_TPM"])
    body = json.dumps({"model": cfg["AZURE_SOL_MODEL"], "reasoning": {"effort": effort},
                       "input": [{"role": "user", "content": content}]}).encode()
    t = time.time()
    d = _send(lambda: urllib.request.Request(cfg["AZURE_AI_ENDPOINT"] + "/responses", data=body, headers={
        "api-key": cfg["AZURE_AI_KEY"], "Content-Type": "application/json"}))
    usage = d.get("usage", {})
    LEDGER.settle(eid, int(usage.get("total_tokens") or estimate))
    text = "".join(c.get("text", "") for o in d.get("output", []) if o.get("type") == "message"
                   for c in o.get("content", []))
    log(f"{cfg['AZURE_SOL_MODEL']} ({effort}) {time.time() - t:.0f} s, {usage.get('total_tokens')} tokens")
    return text, usage


def ask(prompt, images=(), effort="high", detail="original"):
    content = [{"type": "input_text", "text": prompt}]
    for img in images:
        content.append({"type": "input_image", "image_url": b64_image(img), "detail": detail})
    return respond(content, effort)


# ---------- gpt-image ----------
def image(out, prompt, size="1024x1024", quality="high", refs=(), cfg=None):
    cfg = cfg or config()
    LEDGER.reserve("image", 1, cfg["AZURE_IMAGES_PM"])
    headers = {"api-key": cfg["AZURE_AI_KEY"]}
    if refs:
        boundary = "----goa" + str(random.randint(0, 1 << 30))
        parts = []

        def field(name, value):
            parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"\r\n\r\n{value}\r\n".encode())
        field("model", cfg["AZURE_IMAGE_MODEL"]); field("prompt", prompt); field("size", size); field("quality", quality)
        for r in refs:
            parts.append((f"--{boundary}\r\nContent-Disposition: form-data; name=\"image[]\"; "
                          f"filename=\"{os.path.basename(r)}\"\r\nContent-Type: image/png\r\n\r\n").encode()
                         + open(r, "rb").read() + b"\r\n")
        parts.append(f"--{boundary}--\r\n".encode())
        body = b"".join(parts)
        headers["Content-Type"] = f"multipart/form-data; boundary={boundary}"
        url = cfg["AZURE_AI_ENDPOINT"] + "/images/edits"
    else:
        body = json.dumps({"model": cfg["AZURE_IMAGE_MODEL"], "prompt": prompt, "size": size, "n": 1,
                           "quality": quality}).encode()
        headers["Content-Type"] = "application/json"
        url = cfg["AZURE_AI_ENDPOINT"] + "/images/generations"
    d = _send(lambda: urllib.request.Request(url, data=body, headers=headers))
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    open(out, "wb").write(base64.b64decode(d["data"][0]["b64_json"]))
    log(f"wrote {out}")
    return out


# ---------- CLI ----------
def _arg(argv, flag, default=None):
    return argv[argv.index(flag) + 1] if flag in argv else default


def _all(argv, flag):
    return [argv[i + 1] for i, a in enumerate(argv) if a == flag]


def main(argv):
    if len(argv) < 2 or argv[1] in ("-h", "--help"):
        raise SystemExit(__doc__)
    cmd = argv[1]
    if cmd == "check":
        cfg = config()
        text, usage = ask("Reply with the single word: ready", effort="low")
        print(f"sol: {text.strip()} ({cfg['AZURE_SOL_MODEL']}, {usage.get('total_tokens')} tokens); "
              f"image model: {cfg['AZURE_IMAGE_MODEL']}; limits {cfg['AZURE_SOL_TPM']} tokens/min, "
              f"{cfg['AZURE_IMAGES_PM']} images/min")
    elif cmd == "ask":
        src = _arg(argv, "--prompt-file", "-")
        prompt = sys.stdin.read() if src == "-" else open(src, encoding="utf-8").read()
        text, usage = ask(prompt, _all(argv, "--image"), _arg(argv, "--effort", "high"), _arg(argv, "--detail", "original"))
        out = json.dumps({"text": text, "usage": usage}, indent=1) if "--json" in argv else text
        if _arg(argv, "--out"):
            open(_arg(argv, "--out"), "w", encoding="utf-8").write(out)
        print(out)
    elif cmd == "image":
        image(argv[2], argv[3], _arg(argv, "--size", "1024x1024"), _arg(argv, "--quality", "high"), _all(argv, "--ref"))
    else:
        raise SystemExit(__doc__)


if __name__ == "__main__":
    main(sys.argv)
