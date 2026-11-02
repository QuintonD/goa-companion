#!/usr/bin/env bash
# Publish the game server at /game on port 8443, next to the relay at /. Safe to run again: it changes
# nothing when /game is already right, refuses anything it doesn't expect, and afterwards proves that
# everything except /game is byte-for-byte what it was. Ports 443 and 10000 are other apps'.
set -euo pipefail

HOST_PORT="${GOA_FUNNEL_HOST_PORT:-oldboi.taila4d38d.ts.net:8443}"
MOUNT=/game
TARGET=http://127.0.0.1:8788
RELAY=http://127.0.0.1:8787

# Reads a `tailscale serve status --json` file; prints "add" or "present", or a reason on stderr and
# exits 1. Keys are ipn.ServeConfig's: TCP, Web, AllowFunnel, Foreground, Handlers, Proxy.
check_before() {
    python3 -I - "$1" "$HOST_PORT" "$MOUNT" "$TARGET" "$RELAY" <<'PY'
import json, sys

path, host_port, mount, target, relay = sys.argv[1:]
with open(path, encoding="utf-8") as f:
    config = json.load(f)
port = host_port.rsplit(":", 1)[1]


def refuse(why):
    print("funnel: refusing, nothing changed: " + why, file=sys.stderr)
    sys.exit(1)


handlers = ((config.get("Web") or {}).get(host_port) or {}).get("Handlers") or {}
root = handlers.get("/")
if not root or root.get("Proxy") != relay:
    refuse(f"{host_port} / should proxy to {relay}, but it is {root!r}")
if not (config.get("AllowFunnel") or {}).get(host_port):
    refuse(f"Funnel is not on for {host_port}: adding /game would also make the relay public")
tcp = (config.get("TCP") or {}).get(port) or {}
if not tcp.get("HTTPS") or tcp.get("TCPForward"):
    refuse(f"port {port} is not a plain HTTPS web server: {tcp!r}")
for session, nested in (config.get("Foreground") or {}).items():
    if host_port in (nested.get("Web") or {}) or port in (nested.get("TCP") or {}):
        refuse(f"a foreground `tailscale serve` session ({session}) holds port {port}")
if mount + "/" in handlers:
    refuse(f"{mount}/ is mapped, and adding {mount} would replace it: {handlers[mount + '/']!r}")
current = handlers.get(mount)
if current is None:
    print("add")
elif current == {"Proxy": target}:
    print("present")
else:
    refuse(f"{mount} on {host_port} maps elsewhere: {current!r}")
PY
}

# Compares two status files: identical apart from the /game handler, which must be there and right.
check_after() {
    python3 -I - "$1" "$2" "$HOST_PORT" "$MOUNT" "$TARGET" "$RELAY" <<'PY'
import json, sys

before_path, after_path, host_port, mount, target, relay = sys.argv[1:]
with open(before_path, encoding="utf-8") as f:
    before = json.load(f)
with open(after_path, encoding="utf-8") as f:
    after = json.load(f)


def fail(why):
    print("funnel: WRONG AFTER THE CHANGE: " + why, file=sys.stderr)
    sys.exit(1)


handlers = ((after.get("Web") or {}).get(host_port) or {}).get("Handlers") or {}
if (handlers.get("/") or {}).get("Proxy") != relay:
    fail(f"{host_port} / no longer proxies to {relay}: {handlers.get('/')!r}")
if handlers.get(mount) != {"Proxy": target}:
    fail(f"{mount} is {handlers.get(mount)!r}, not a proxy to {target}")
if not (after.get("AllowFunnel") or {}).get(host_port):
    fail(f"Funnel is off for {host_port}")
del handlers[mount]
old = ((before.get("Web") or {}).get(host_port) or {}).get("Handlers") or {}
old.pop(mount, None)
if before != after:
    fail("something other than /game changed; compare the two configurations printed above")
PY
}

print_removal() {
    cat <<EOF

To remove only /game (the relay stays):
    sudo tailscale funnel --https=8443 --set-path $MOUNT off
    sudo tailscale serve status --json     # "/" must still proxy to $RELAY

Never leave out --set-path: \`tailscale funnel --https=8443 off\` deletes every handler on the port,
the relay's as well, and turns Funnel off for it.
EOF
}

print_restore() {
    cat >&2 <<EOF

To put the relay back on / (this changes only that path):
    sudo tailscale funnel --bg --https=8443 $RELAY
The configuration from before this run is in $1
EOF
}

main() {
    local ts before after state saved
    ts=(sudo tailscale)
    before="$(mktemp)"
    after="$(mktemp)"
    # shellcheck disable=SC2064 # the paths are fixed now
    trap "rm -f '$before' '$after'" EXIT

    "${ts[@]}" version | head -n 1
    "${ts[@]}" serve status --json >"$before"
    echo "==> current configuration"
    cat "$before"
    echo

    state="$(check_before "$before")"
    if [[ "$state" == "present" ]]; then
        echo "==> $MOUNT already proxies to $TARGET: nothing to do"
    else
        saved="$HOME/goa-funnel-before-$(date +%Y%m%d-%H%M%S).json"
        cp "$before" "$saved"
        echo "==> adding $MOUNT"
        "${ts[@]}" funnel --bg --https=8443 --set-path "$MOUNT" "$TARGET"
    fi

    "${ts[@]}" serve status --json >"$after"
    echo "==> configuration now"
    cat "$after"
    echo
    if ! check_after "$before" "$after"; then
        [[ -z "${saved:-}" ]] || print_restore "$saved"
        return 1
    fi
    echo "==> verified: / still proxies to $RELAY, $MOUNT to $TARGET, nothing else changed"
    [[ -z "${saved:-}" ]] || rm -f "$saved"
    print_removal
}

main "$@"
exit
