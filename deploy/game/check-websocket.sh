#!/usr/bin/env bash
# Smoke test of the public socket: connect, send a Hello with a token that can't exist, and expect
# Refused(BAD_TOKEN). That proves Funnel carries WebSockets to this server and that the server read the
# frame. It is tried as a phone (no Origin) and as the web app, then from a foreign site, which must not
# get that far. Python's standard library only; the WebSocket handshake is written out below.
#   check-websocket.sh [url]         # default wss://oldboi.taila4d38d.ts.net:8443/game/v1/play
set -euo pipefail

main() {
    local here
    here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    python3 -I -c "$PROGRAM" \
        "${1:-wss://oldboi.taila4d38d.ts.net:8443/game/v1/play}" \
        "$here/../../app/src/main/assets/catalog/index.json"
}

PROGRAM="$(
    cat <<'PY'
import base64, hashlib, json, os, socket, ssl, struct, sys, urllib.parse

WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
TIMEOUT = 15
PROTOCOL = 1  # OnlineProtocol.VERSION; a raised one answers UPDATE_APP, which this reports as such


class Failed(Exception):
    pass


def read_exactly(sock, n):
    data = b""
    while len(data) < n:
        chunk = sock.recv(n - len(data))
        if not chunk:
            raise Failed("the connection closed")
        data += chunk
    return data


def frame(opcode, payload):
    """A client frame: always masked (RFC 6455 section 5.3)."""
    mask = os.urandom(4)
    head = bytes([0x80 | opcode])
    if len(payload) < 126:
        head += bytes([0x80 | len(payload)])
    elif len(payload) < 65536:
        head += bytes([0x80 | 126]) + struct.pack(">H", len(payload))
    else:
        head += bytes([0x80 | 127]) + struct.pack(">Q", len(payload))
    return head + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(payload))


def read_frame(sock):
    first, second = read_exactly(sock, 2)
    length = second & 0x7F
    if length == 126:
        length = struct.unpack(">H", read_exactly(sock, 2))[0]
    elif length == 127:
        length = struct.unpack(">Q", read_exactly(sock, 8))[0]
    if length > 1 << 20:
        raise Failed("a frame over 1 MiB")
    mask = read_exactly(sock, 4) if second & 0x80 else None
    payload = read_exactly(sock, length)
    if mask:
        payload = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
    return first & 0x80 != 0, first & 0x0F, payload


def read_text(sock):
    """The next whole text message, answering pings on the way."""
    message = b""
    while True:
        fin, opcode, payload = read_frame(sock)
        if opcode == 0x8:
            code = struct.unpack(">H", payload[:2])[0] if len(payload) >= 2 else None
            raise Failed(f"the server closed the socket without an answer (code {code})")
        if opcode == 0x9:
            sock.sendall(frame(0xA, payload))
        elif opcode in (0x0, 0x1):
            message += payload
            if fin:
                return message.decode("utf-8")


def connect(url, origin):
    parts = urllib.parse.urlsplit(url)
    secure = parts.scheme == "wss"
    if parts.scheme not in ("ws", "wss") or not parts.hostname:
        raise Failed(f"not a WebSocket address: {url}")
    port = parts.port or (443 if secure else 80)
    sock = socket.create_connection((parts.hostname, port), timeout=TIMEOUT)
    if secure:
        # No ALPN: this has to be HTTP/1.1 for the Upgrade, and the certificate is checked as usual.
        sock = ssl.create_default_context().wrap_socket(sock, server_hostname=parts.hostname)
    key = base64.b64encode(os.urandom(16)).decode()
    host = parts.hostname if parts.port is None else f"{parts.hostname}:{parts.port}"
    lines = [
        f"GET {parts.path or '/'} HTTP/1.1",
        f"Host: {host}",
        "Upgrade: websocket",
        "Connection: Upgrade",
        f"Sec-WebSocket-Key: {key}",
        "Sec-WebSocket-Version: 13",
    ]
    if origin:
        lines.append(f"Origin: {origin}")
    sock.sendall(("\r\n".join(lines) + "\r\n\r\n").encode())

    response = b""
    while b"\r\n\r\n" not in response:
        response += read_exactly(sock, 1)
        if len(response) > 65536:
            raise Failed("an endless response to the handshake")
    head = response.decode("latin-1").split("\r\n")
    if head[0].split(" ")[1:2] != ["101"]:
        raise Failed(f"the handshake got: {head[0]}")
    headers = {k.strip().lower(): v.strip() for k, _, v in (line.partition(":") for line in head[1:] if line)}
    expected = base64.b64encode(hashlib.sha1((key + WS_GUID).encode()).digest()).decode()
    if headers.get("sec-websocket-accept") != expected:
        raise Failed("the handshake's Sec-WebSocket-Accept is wrong")
    return sock


def bad_hello(url, origin, catalog_version):
    """Connects and sends the Hello; returns the first answer as parsed JSON."""
    sock = connect(url, origin)
    try:
        hello = {
            "type": "hello",
            "protocol": PROTOCOL,
            "token": "smoke-test-token-that-does-not-exist",
            "client": "smoke/check-websocket",
            "catalogVersion": catalog_version,
        }
        sock.sendall(frame(0x1, json.dumps(hello).encode()))
        answer = json.loads(read_text(sock))
        sock.sendall(frame(0x8, struct.pack(">H", 1000)))
        return answer
    finally:
        sock.close()


def expect_refused(url, origin, catalog_version):
    who = f"Origin {origin}" if origin else "no Origin"
    try:
        answer = bad_hello(url, origin, catalog_version)
    except (OSError, Failed, ValueError) as e:
        print(f"FAIL {who}: {e}")
        return False
    if answer == {"type": "refused", "reason": "bad_token"}:
        print(f"ok   {who}: Refused(BAD_TOKEN)")
        return True
    if isinstance(answer, dict) and answer.get("reason") == "update_app":
        print(f"FAIL {who}: the socket works, but the server says UPDATE_APP (protocol {PROTOCOL}, catalog {catalog_version})")
    else:
        print(f"FAIL {who}: expected Refused(BAD_TOKEN), got {answer}")
    return False


def expect_blocked(url, origin, catalog_version):
    try:
        answer = bad_hello(url, origin, catalog_version)
    except (OSError, Failed, ValueError) as e:
        print(f"ok   Origin {origin}: blocked ({e})")
        return True
    print(f"FAIL Origin {origin}: a foreign site reached the server, which answered {answer}")
    return False


def main():
    url, index = sys.argv[1:]
    try:
        with open(index, encoding="utf-8") as f:
            catalog_version = json.load(f)["dataVersion"]
    except OSError:
        print(f"FAIL cannot read {index}: run this from a checkout of the repository")
        return 1
    print(f"==> {url} (catalog {catalog_version})")
    passed = [
        expect_refused(url, None, catalog_version),
        expect_refused(url, "https://quintond.github.io", catalog_version),
        expect_blocked(url, "https://example.invalid", catalog_version),
    ]
    return 0 if all(passed) else 1


sys.exit(main())
PY
)"

main "$@"
exit
