#!/usr/bin/env bash
# Tests of the deploy scripts with fakes for docker, sudo, tailscale, goa-gradle and curl: the order of
# the steps, the refusals and what happens on each failure. Nothing here touches a real service.
#   deploy/game/test/run-tests.sh
# Needs bash, git, python3. The backup tests also need sqlite3 and, for real uids, root with setpriv.
set -uo pipefail

GAME="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT="$(mktemp -d)"
trap 'rm -rf "$ROOT"' EXIT
export GIT_CONFIG_GLOBAL="$ROOT/gitconfig"
git config --file "$GIT_CONFIG_GLOBAL" user.name test
git config --file "$GIT_CONFIG_GLOBAL" user.email test@example.invalid
git config --file "$GIT_CONFIG_GLOBAL" init.defaultBranch main
git config --file "$GIT_CONFIG_GLOBAL" protocol.file.allow always

PASS=0
FAIL=0
PYTHON="$(command -v python3 || command -v python)"

ok() {
    PASS=$((PASS + 1))
    echo "  ok   $1"
}
bad() {
    FAIL=$((FAIL + 1))
    echo "  FAIL $1"
    [[ -z "${2:-}" ]] || sed 's/^/       | /' <<<"$2"
}
expect() { # description, command...
    local what="$1"
    shift
    if "$@"; then ok "$what"; else bad "$what"; fi
}
expect_not() {
    local what="$1"
    shift
    if "$@"; then bad "$what"; else ok "$what"; fi
}
has() { grep -qF -- "$2" "$1"; }
before() { # file, earlier, later: both present, in that order
    local a b
    a="$(grep -nF -- "$2" "$1" | head -n 1 | cut -d: -f1)"
    b="$(grep -nF -- "$3" "$1" | tail -n 1 | cut -d: -f1)"
    [[ -n "$a" && -n "$b" && "$a" -lt "$b" ]]
}

# The fakes -----------------------------------------------------------------------------------------

make_fakes() {
    local bin="$1"
    mkdir -p "$bin"
    [[ -n "$(command -v python3 || true)" ]] || printf '#!/bin/sh\nexec "%s" "$@"\n' "$PYTHON" >"$bin/python3"

    cat >"$bin/docker" <<'EOF'
#!/usr/bin/env bash
echo "docker $*" >>"$T/events"
case "$1" in
    info) exit 0 ;;
    inspect)
        [[ ! -e "$T/no_container" ]] || exit 1
        case "$3" in
            *Image*) echo sha256:running ;;
            *Health*) cat "$T/health_state" ;;
        esac
        ;;
    image) [[ -e "$T/has_previous" ]] ;;
    tag) exit 0 ;;
    exec)
        case "$3" in
            touch) : >"$T/data/drain" ;;
            rm) rm -f "$T/data/drain" ;;
            test) [[ -e "$T/data/drain" ]] ;;
        esac
        ;;
    compose)
        shift 3
        case "$1" in
            up)
                if [[ "$*" == *--build* ]]; then
                    here="$(dirname "$COMPOSE_FILE_SEEN")"
                fi
                [[ -e "$T/up_fails" ]] && exit 1
                if [[ -e "$T/up_bad" ]]; then
                    : >"$T/health_queue"
                    echo down >"$T/health_default"
                else
                    : >"$T/health_queue"
                    rules="$(sed -n 's/^GOA_GAME_RULES_BUILD=//p' "$T/checkout/deploy/game/.env" 2>/dev/null)"
                    echo "{\"ok\":true,\"rulesBuild\":\"${rules:-old}\",\"status\":\"open\",\"rooms\":0}" >"$T/health_default"
                fi
                ;;
        esac
        ;;
esac
EOF

    cat >"$bin/curl" <<'EOF'
#!/usr/bin/env bash
# As curl -sS -w '\n%{http_code}' does: the body, a newline, the status.
line=""
if [[ -s "$T/health_queue" ]]; then
    line="$(head -n 1 "$T/health_queue")"
    tail -n +2 "$T/health_queue" >"$T/health_queue.next"
    mv "$T/health_queue.next" "$T/health_queue"
else
    line="$(cat "$T/health_default")"
fi
[[ "$line" != down ]] || exit 7
if [[ "$line" == limited ]]; then
    printf '%s\n429' '{"error":"rate_limited"}'
else
    printf '%s\n200' "$line"
fi
EOF

    cat >"$bin/sudo" <<'EOF'
#!/usr/bin/env bash
echo "sudo $*" >>"$T/events"
[[ "${1:-}" != -n ]] || shift
case "${1:-}" in
    mkdir | chown | chmod | rm) exit 0 ;;
esac
exec "$@"
EOF

    cat >"$bin/systemctl" <<'EOF'
#!/usr/bin/env bash
state="$(head -n 1 "$T/plekje")"
if [[ -s "$T/plekje_after" ]] && grep -q 'goa-gradle\|exec goa-game touch' "$T/events" 2>/dev/null; then
    state="$(cat "$T/plekje_after")"
fi
echo "$state"
[[ "$state" == active ]]
EOF

    cat >"$bin/pgrep" <<'EOF'
#!/usr/bin/env bash
[[ -e "$T/gradle_running" ]]
EOF

    cat >"$bin/goa-gradle" <<'EOF'
#!/usr/bin/env bash
# As the real one does: it builds in ~/goa-companion unless GOA_DIR says otherwise.
cd "${GOA_DIR:-$T/not-the-deploy-checkout}" || exit 1
echo "goa-gradle $* cwd=$PWD" >>"$T/events"
[[ ! -e "$T/gradle_fails" ]] || exit 1
[[ -e "$T/gradle_makes_nothing" ]] && exit 0
mkdir -p server/game/build/install/goa-game/bin server/game/build/install/goa-game/assets/catalog
echo '#!/bin/sh' >server/game/build/install/goa-game/bin/goa-game
chmod +x server/game/build/install/goa-game/bin/goa-game
echo '{}' >server/game/build/install/goa-game/assets/catalog/index.json
EOF
    chmod +x "$bin"/*
}

# A small repository holding just what deploy.sh looks at.
make_remote() {
    local remote="$1"
    mkdir -p "$remote"
    (
        cd "$remote" || exit 1
        git init -q
        mkdir -p core/game core/domain core/model app/src/main/assets/catalog server/game deploy/game
        echo a >core/game/a
        echo b >core/domain/b
        echo c >core/model/c
        echo d >app/src/main/assets/catalog/index.json
        echo e >server/game/build.gradle.kts
        echo 'services: {}' >deploy/game/compose.yaml
        echo r >README.md
        git add .
        git commit -qm first
        git tag v1
        git branch feature/x
        echo r2 >README.md
        git commit -qam docs
        git rev-parse HEAD >"$remote.docs"
        git checkout -q feature/x
        echo a2 >core/game/a
        git commit -qam rules
        git checkout -q main
    )
}

fresh() {
    T="$(mktemp -d "$ROOT/t.XXXXXX")"
    export T
    export HOME="$T/home"
    mkdir -p "$HOME/goa-companion" "$T/data"
    echo "someone's uncommitted edit" >"$HOME/goa-companion/precious"
    : >"$T/events"
    : >"$T/health_queue"
    echo down >"$T/health_default"
    echo healthy >"$T/health_state"
    echo inactive >"$T/plekje"
    : >"$T/plekje_after"
    export GOA_GAME_CHECKOUT="$T/checkout"
    export GOA_GAME_REMOTE="file://$REMOTE"
    export GOA_GAME_DATA_DIR="$T/data"
    export GOA_GAME_HEALTH_TRIES=2 GOA_GAME_HEALTH_SLEEP=0 GOA_GAME_DRAIN_POLL_SECONDS=0
    unset GOA_GAME_DRAIN_HOURS
}

run_deploy() {
    OUT="$(PATH="$FAKES:$PATH" bash "$GAME/deploy.sh" "$@" 2>&1)"
    CODE=$?
}

expected_rules() { # commit
    local path
    for path in core/game core/domain core/model app/src/main/assets/catalog; do
        git -C "$REMOTE" rev-parse "$1:$path"
    done | sha256sum | cut -c1-16
}

# deploy.sh -----------------------------------------------------------------------------------------

test_deploy() {
    echo "deploy.sh"
    local main_sha sha_x

    fresh
    main_sha="$(git -C "$REMOTE" rev-parse main)"
    run_deploy
    expect "first deploy with no server succeeds" test "$CODE" = 0
    [[ "$CODE" == 0 ]] || bad "output" "$OUT"
    expect "checks out the default ref, detached" test "$(git -C "$T/checkout" rev-parse HEAD)" = "$main_sha"
    expect_not "no drain when nothing is up" has "$T/events" "exec goa-game touch"
    expect "the build ran in the deploy checkout" has "$T/events" "goa-gradle :server:game:test :server:game:installDist cwd=$(cd "$T/checkout" && pwd)"
    expect "the distribution is staged" test -x "$T/checkout/deploy/game/dist/bin/goa-game"
    expect ".env has the hash of the four tree ids" has "$T/checkout/deploy/game/.env" "GOA_GAME_RULES_BUILD=$(expected_rules main)"
    expect "compose goes up with a build" has "$T/events" "up -d --build"
    expect "the build comes before compose" before "$T/events" "goa-gradle" "up -d --build"
    expect "~/goa-companion is untouched" test "$(ls -A "$HOME/goa-companion")" = "precious"

    # A second run, on another ref, reuses the clone.
    sha_x="$(git -C "$REMOTE" rev-parse feature/x)"
    run_deploy feature/x
    expect "a branch ref works" test "$(git -C "$T/checkout" rev-parse HEAD)" = "$sha_x"
    expect "rules build follows core/game" has "$T/checkout/deploy/game/.env" "GOA_GAME_RULES_BUILD=$(expected_rules feature/x)"
    expect_not "and differs from main's" test "$(expected_rules main)" = "$(expected_rules feature/x)"
    run_deploy v1
    expect "a tag works" test "$(git -C "$T/checkout" rev-parse HEAD)" = "$(git -C "$REMOTE" rev-parse 'v1^{commit}')"
    run_deploy "${main_sha:0:12}"
    expect "a commit works" test "$(git -C "$T/checkout" rev-parse HEAD)" = "$main_sha"
    expect "a docs commit keeps the rules build" test "$(expected_rules "$(cat "$REMOTE.docs")")" = "$(expected_rules v1)"

    # Stray edits in the deploy checkout don't reach the build.
    echo stray >>"$T/checkout/README.md"
    run_deploy main
    expect "a stray edit in the checkout is overwritten" test "$(git -C "$T/checkout" status --porcelain -- README.md)" = ""

    fresh
    run_deploy no-such-ref
    expect "an unknown ref fails" test "$CODE" != 0
    expect "and builds nothing" test "$(grep -c goa-gradle "$T/events")" = 0

    fresh
    run_deploy --bogus
    expect "an unknown option fails" test "$CODE" != 0
    run_deploy main extra
    expect "two refs fail" test "$CODE" != 0

    fresh
    export GOA_GAME_CHECKOUT="$HOME/goa-companion"
    run_deploy
    expect "the checkout may not be ~/goa-companion" test "$CODE" != 0
    expect "which stays untouched" test "$(ls -A "$HOME/goa-companion")" = "precious"

    # Draining
    fresh
    printf '%s\n' \
        '{"ok":true,"rulesBuild":"old","status":"open","rooms":2}' \
        '{"ok":true,"rulesBuild":"old","status":"draining","rooms":2}' \
        '{"ok":true,"rulesBuild":"old","status":"draining","rooms":1}' \
        '{"ok":true,"rulesBuild":"old","status":"draining","rooms":0}' >"$T/health_queue"
    run_deploy
    expect "a deploy with rooms succeeds" test "$CODE" = 0
    [[ "$CODE" == 0 ]] || bad "output" "$OUT"
    expect "the drain file comes before the build" before "$T/events" "exec goa-game touch /var/lib/goa-game/drain" "goa-gradle"
    expect "waits until no rooms are left" has <(echo "$OUT") "no rooms left"
    expect "the running image is tagged" has "$T/events" "tag sha256:running goa-game:previous"
    expect "the tag comes before compose" before "$T/events" "tag sha256:running" "up -d --build"
    expect "the drain file goes after compose" before "$T/events" "up -d --build" "exec goa-game rm -f /var/lib/goa-game/drain"
    expect "and is gone" test ! -e "$T/data/drain"
    expect_not "no warning from a server that reports draining" has <(echo "$OUT" | grep -v 'status open') "does not report"

    fresh
    printf '%s\n' \
        '{"ok":true,"rulesBuild":"old","status":"open","rooms":2}' \
        '{"ok":true,"rulesBuild":"old","status":"open","rooms":2}' \
        '{"ok":true,"rulesBuild":"old","status":"open","rooms":0}' >"$T/health_queue"
    run_deploy
    expect "warns when the server ignores the drain file" has <(echo "$OUT") "does not report 'draining'"

    # A flood at the limiter is not a server that has stopped: the drain carries on waiting.
    fresh
    printf '%s\n' \
        limited \
        '{"ok":true,"rulesBuild":"old","status":"draining","rooms":2}' \
        limited \
        limited \
        '{"ok":true,"rulesBuild":"old","status":"draining","rooms":0}' >"$T/health_queue"
    run_deploy
    expect "a 429 at the start still drains" has "$T/events" "exec goa-game touch /var/lib/goa-game/drain"
    expect "a 429 during the drain is still up" has <(echo "$OUT") "says too many requests"
    expect_not "and doesn't cut the drain short" has <(echo "$OUT") "stopped answering"
    expect "it waits for the rooms" has <(echo "$OUT") "no rooms left"

    fresh
    echo limited >"$T/health_default"
    export GOA_GAME_DRAIN_HOURS=0
    run_deploy
    expect "a 429 that never ends: the time limit still holds" has <(echo "$OUT") "refusing health checks"
    unset GOA_GAME_DRAIN_HOURS

    fresh
    echo '{"ok":true,"rulesBuild":"old","status":"draining","rooms":3}' >"$T/health_default"
    export GOA_GAME_DRAIN_HOURS=0
    run_deploy
    expect "at the time limit the deploy goes on" test "$CODE" = 0
    expect "and says so" has <(echo "$OUT") "room(s) left: going on"
    unset GOA_GAME_DRAIN_HOURS

    fresh
    export GOA_GAME_DRAIN_HOURS=abc
    echo '{"ok":true,"rulesBuild":"old","status":"open","rooms":0}' >"$T/health_default"
    run_deploy
    expect "a bad GOA_GAME_DRAIN_HOURS fails" test "$CODE" != 0
    expect "before the build" test "$(grep -c goa-gradle "$T/events")" = 0
    unset GOA_GAME_DRAIN_HOURS

    fresh
    echo '{"ok":true,"rulesBuild":"old","status":"open","rooms":3}' >"$T/health_default"
    run_deploy --no-drain
    expect "--no-drain succeeds with rooms running" test "$CODE" = 0
    expect_not "and touches no drain file" has "$T/events" "exec goa-game touch"
    expect_not "and removes none" has "$T/events" "exec goa-game rm"

    fresh
    echo '{"ok":true,"rulesBuild":"old","status":"open","rooms":3}' >"$T/health_default"
    : >"$T/data/drain"
    run_deploy --no-drain
    expect "--no-drain leaves a pause by hand, and says so" has <(echo "$OUT") "the drain file is still there"
    expect "the file stays" test -e "$T/data/drain"

    fresh
    : >"$T/data/drain"
    run_deploy
    expect "a stale drain file goes on a clean deploy" test ! -e "$T/data/drain"

    # One build at a time
    fresh
    echo active >"$T/plekje"
    run_deploy
    expect "refuses while the plekje build runs" test "$CODE" != 0
    expect "politely" has <(echo "$OUT") "Please try again"
    expect "before it clones" test ! -e "$T/checkout"
    expect "and before draining" test "$(grep -c 'exec goa-game' "$T/events")" = 0

    fresh
    : >"$T/gradle_running"
    run_deploy
    expect "refuses while another Gradle build runs" test "$CODE" != 0

    fresh
    echo '{"ok":true,"rulesBuild":"old","status":"draining","rooms":0}' >"$T/health_default"
    echo active >"$T/plekje_after"
    run_deploy
    expect "refuses if a build started during the drain" test "$CODE" != 0
    expect "with no Gradle started" test "$(grep -c goa-gradle "$T/events")" = 0
    expect "and rooms reopen" test ! -e "$T/data/drain"

    # Failures
    fresh
    echo '{"ok":true,"rulesBuild":"old","status":"draining","rooms":0}' >"$T/health_default"
    : >"$T/gradle_fails"
    run_deploy
    expect "red tests stop the deploy" test "$CODE" != 0
    expect_not "with no restart" has "$T/events" "up -d"
    expect "and rooms reopen" test ! -e "$T/data/drain"

    fresh
    : >"$T/gradle_makes_nothing"
    run_deploy
    expect "a build that leaves nothing here fails" test "$CODE" != 0
    expect "naming the file" has <(echo "$OUT") "the build left no"

    fresh
    echo '{"ok":true,"rulesBuild":"old","status":"draining","rooms":0}' >"$T/health_default"
    : >"$T/up_bad"
    run_deploy
    expect "a failed health check fails the deploy" test "$CODE" != 0
    expect "shows the logs" has "$T/events" "logs --tail 80"
    expect "gives the rollback command" has <(echo "$OUT") "deploy/game/rollback.sh"
    expect "keeps new rooms paused on the bad server" test -e "$T/data/drain"

    fresh
    echo '{"ok":true,"rulesBuild":"somethingelse","status":"open","rooms":0}' >"$T/health_default"
    : >"$T/up_fails"
    run_deploy
    expect "compose failing fails the deploy" test "$CODE" != 0

    fresh
    echo unhealthy >"$T/health_state"
    run_deploy
    expect "an unhealthy running image is not tagged previous" test "$(grep -c 'goa-game:previous' "$T/events")" = 0
    expect "and the deploy still goes on" test "$CODE" = 0

    fresh
    : >"$T/no_container"
    run_deploy
    expect "no container: nothing to tag" test "$(grep -c 'goa-game:previous' "$T/events")" = 0
}

test_rollback() {
    echo "rollback.sh"
    fresh
    : >"$T/has_previous"
    echo '{"ok":true}' >"$T/health_default"
    : >"$T/data/drain"
    OUT="$(PATH="$FAKES:$PATH" bash "$GAME/rollback.sh" 2>&1)"
    CODE=$?
    expect "succeeds" test "$CODE" = 0
    expect "tags previous as latest" has "$T/events" "tag goa-game:previous goa-game:latest"
    expect "recreates with no build" has "$T/events" "up -d --no-build --force-recreate"
    expect "reopens new rooms" test ! -e "$T/data/drain"

    fresh
    OUT="$(PATH="$FAKES:$PATH" bash "$GAME/rollback.sh" 2>&1)"
    CODE=$?
    expect "fails without a previous image" test "$CODE" != 0
    expect_not "and changes nothing" has "$T/events" "compose"

    fresh
    : >"$T/has_previous"
    : >"$T/up_bad"
    OUT="$(PATH="$FAKES:$PATH" bash "$GAME/rollback.sh" 2>&1)"
    CODE=$?
    expect "fails if that image is unhealthy too" test "$CODE" != 0
}

# funnel.sh -----------------------------------------------------------------------------------------

# Behaves as cmd/tailscale/cli/serve_v2.go does (v1.104): --set-path adds one handler, nothing else.
make_tailscale() {
    cat >"$1/tailscale" <<'EOF'
#!/usr/bin/env bash
echo "tailscale $*" >>"$T/events"
case "$1" in
    version) echo 1.104.4 ;;
    serve) cat "$T/serve.json" ;;
    funnel)
        [[ "$*" == "funnel --bg --https=8443 --set-path /game http://127.0.0.1:8788" ]] || exit 9
        "$PY" -I - "$T/serve.json" "${TS_MISBEHAVE:-}" <<'PY'
import json, sys
path, misbehave = sys.argv[1:]
config = json.load(open(path))
hp = "oldboi.taila4d38d.ts.net:8443"
config["Web"][hp]["Handlers"]["/game"] = {"Proxy": "http://127.0.0.1:8788"}
if misbehave == "clobber":
    del config["Web"][hp]["Handlers"]["/"]
if misbehave == "other":
    config["AllowFunnel"]["oldboi.taila4d38d.ts.net:443"] = True
if misbehave == "wrong":
    config["Web"][hp]["Handlers"]["/game"] = {"Proxy": "http://127.0.0.1:9999"}
json.dump(config, open(path, "w"))
PY
        ;;
esac
EOF
    chmod +x "$1/tailscale"
}

serve_config() { # game handler JSON or empty, extra python on config
    "$PYTHON" -I - "$1" "${2:-}" >"$T/serve.json" <<'PY'
import json, sys
game, extra = sys.argv[1:]
hp = "oldboi.taila4d38d.ts.net:8443"
config = {
    "TCP": {"443": {"HTTPS": True}, "8443": {"HTTPS": True}},
    "Web": {
        "oldboi.taila4d38d.ts.net:443": {"Handlers": {"/": {"Proxy": "http://127.0.0.1:3773"}}},
        hp: {"Handlers": {"/": {"Proxy": "http://127.0.0.1:8787"}}},
    },
    "AllowFunnel": {hp: True},
}
if game:
    config["Web"][hp]["Handlers"].update(json.loads(game))
exec(extra)
json.dump(config, sys.stdout)
PY
}

run_funnel() {
    OUT="$(PATH="$FAKES:$PATH" PY="$PYTHON" bash "$GAME/funnel.sh" 2>&1)"
    CODE=$?
}

test_funnel() {
    echo "funnel.sh"
    make_tailscale "$FAKES"

    fresh
    serve_config ""
    local untouched
    run_funnel
    expect "adds /game beside the relay" test "$CODE" = 0
    [[ "$CODE" == 0 ]] || bad "output" "$OUT"
    expect "with exactly the documented command" has "$T/events" "tailscale funnel --bg --https=8443 --set-path /game http://127.0.0.1:8788"
    expect "prints the configuration first" before "$T/events" "tailscale serve status --json" "tailscale funnel --bg"
    expect "prints the removal of /game only" has <(echo "$OUT") "sudo tailscale funnel --https=8443 --set-path /game off"
    expect "warns about the form without --set-path" has <(echo "$OUT") "Never leave out --set-path"
    expect "leaves no snapshot behind" test -z "$(ls "$HOME"/goa-funnel-before-* 2>/dev/null)"
    untouched="$(cat "$T/serve.json")"
    run_funnel
    expect "a second run succeeds" test "$CODE" = 0
    expect "and changes nothing" test "$(grep -c '^tailscale funnel' "$T/events")" = 1
    expect "not a byte" test "$(cat "$T/serve.json")" = "$untouched"
    expect "and says so" has <(echo "$OUT") "nothing to do"

    fresh
    serve_config '{"/game":{"Proxy":"http://127.0.0.1:9999"}}'
    run_funnel
    expect "refuses when /game maps elsewhere" test "$CODE" != 0
    expect_not "with no change" has "$T/events" "tailscale funnel"
    expect "and names it" has <(echo "$OUT") "maps elsewhere"

    fresh
    serve_config '{"/game/":{"Proxy":"http://127.0.0.1:8788"}}'
    run_funnel
    expect "refuses /game/, which the command would replace" test "$CODE" != 0
    expect_not "with no change" has "$T/events" "tailscale funnel"

    fresh
    serve_config '{"/game":{"Text":"hello"}}'
    run_funnel
    expect "refuses a /game that serves something else" test "$CODE" != 0

    local case
    for case in \
        'config["Web"]["oldboi.taila4d38d.ts.net:8443"]["Handlers"]["/"]["Proxy"]="http://127.0.0.1:1"' \
        'del config["Web"]["oldboi.taila4d38d.ts.net:8443"]["Handlers"]["/"]' \
        'del config["Web"]["oldboi.taila4d38d.ts.net:8443"]' \
        'del config["AllowFunnel"]' \
        'config["TCP"]["8443"]={"TCPForward":"127.0.0.1:1"}' \
        'config["Foreground"]={"abc":{"TCP":{"8443":{"HTTPS":True}}}}' \
        'config={}'; do
        fresh
        serve_config "" "$case"
        run_funnel
        expect "refuses: $case" test "$CODE" != 0
        expect_not "  with no change" has "$T/events" "tailscale funnel"
    done

    fresh
    serve_config '{"/other":{"Proxy":"http://127.0.0.1:1234"}}'
    run_funnel
    expect "keeps another path that is already there" test "$CODE" = 0
    expect "  still there" has "$T/serve.json" '"/other"'

    local mode
    for mode in clobber other wrong; do
        fresh
        serve_config ""
        export TS_MISBEHAVE="$mode"
        run_funnel
        unset TS_MISBEHAVE
        expect "catches tailscale doing the wrong thing ($mode)" test "$CODE" != 0
        expect "  and prints how to restore the relay" has <(echo "$OUT") "sudo tailscale funnel --bg --https=8443 http://127.0.0.1:8787"
    done
}

# check-websocket.sh --------------------------------------------------------------------------------

test_websocket() {
    echo "check-websocket.sh"
    local server="$ROOT/ws_server.py" port_file mode pid
    cat >"$server" <<'PY'
# Answers as the real server will: a strict RFC 6455 server side, which refuses unmasked client frames.
import base64, hashlib, json, socket, struct, sys, threading

mode, port_file = sys.argv[1:]
GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"


def read_exactly(c, n):
    data = b""
    while len(data) < n:
        chunk = c.recv(n - len(data))
        if not chunk:
            raise EOFError
        data += chunk
    return data


def send_text(c, text):
    payload = text.encode()
    if mode == "long":
        payload += b" " * 300
    head = bytes([0x81, len(payload)]) if len(payload) < 126 else bytes([0x81, 126]) + struct.pack(">H", len(payload))
    c.sendall(head + payload)


def handle(c):
    try:
        converse(c)
    except EOFError:
        pass  # the client hung up, as it should on a handshake it rejects


def converse(c):
    request = b""
    while b"\r\n\r\n" not in request:
        request += c.recv(1)
    lines = request.decode().split("\r\n")
    headers = {k.lower(): v.strip() for k, _, v in (x.partition(":") for x in lines[1:] if x)}
    origin = headers.get("origin")
    path = lines[0].split(" ")[1]
    if mode == "404" or path != "/game/v1/play":
        c.sendall(b"HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n")
        return
    if origin == "https://example.invalid" and mode != "open-origin":
        c.sendall(b"HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n")
        return
    accept = base64.b64encode(hashlib.sha1((headers["sec-websocket-key"] + GUID).encode()).digest()).decode()
    if mode == "bad-accept":
        accept = "AAAA"
    c.sendall(f"HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: {accept}\r\n\r\n".encode())
    if mode == "ping":
        c.sendall(bytes([0x89, 2]) + b"hi")
    first, second = read_exactly(c, 2)
    assert first == 0x81 and second & 0x80, "client frames must be final text frames, masked"
    length = second & 0x7F
    if length == 126:
        length = struct.unpack(">H", read_exactly(c, 2))[0]
    mask = read_exactly(c, 4)
    hello = json.loads(bytes(b ^ mask[i % 4] for i, b in enumerate(read_exactly(c, length))))
    assert hello["type"] == "hello" and hello["protocol"] == 1 and type(hello["catalogVersion"]) is int, hello
    assert set(hello) == {"type", "protocol", "token", "client", "catalogVersion"}, hello
    if mode == "ping":
        first, second = read_exactly(c, 2)
        assert first == 0x8A and second & 0x80, "a ping needs a masked pong"
        read_exactly(c, 4 + (second & 0x7F))
    if mode == "close":
        c.sendall(bytes([0x88, 2]) + struct.pack(">H", 1008))
    elif mode == "welcome":
        send_text(c, '{"type":"welcome","protocol":1,"accountId":"x","name":"x","serverTime":1}')
    elif mode == "update":
        send_text(c, '{"type":"refused","reason":"update_app"}')
    else:
        send_text(c, '{"type":"refused","reason":"bad_token"}')
    try:
        read_exactly(c, 8)  # the client's close
    except (EOFError, OSError):
        pass


s = socket.socket()
s.bind(("127.0.0.1", 0))
s.listen()
with open(port_file, "w") as f:
    f.write(str(s.getsockname()[1]))
while True:
    conn, _ = s.accept()
    conn.settimeout(10)
    threading.Thread(target=handle, args=(conn,), daemon=True).start()
PY
    for mode in refused ping long close welcome update 404 bad-accept open-origin; do
        port_file="$ROOT/port.$mode"
        "$PYTHON" -I "$server" "$mode" "$port_file" 2>"$ROOT/ws.$mode.err" &
        pid=$!
        while [[ ! -s "$port_file" ]]; do sleep 0.1; done
        OUT="$(PATH="$FAKES:$PATH" bash "$GAME/check-websocket.sh" "ws://127.0.0.1:$(cat "$port_file")/game/v1/play" 2>&1)"
        CODE=$?
        kill "$pid" 2>/dev/null
        wait "$pid" 2>/dev/null
        case "$mode" in
            refused | ping | long)
                expect "server answers BAD_TOKEN ($mode): passes" test "$CODE" = 0
                [[ "$CODE" == 0 ]] || bad "output" "$OUT"$'\n'"$(cat "$ROOT/ws.$mode.err")"
                ;;
            *)
                expect "$mode: fails" test "$CODE" != 0
                ;;
        esac
    done
    local errors
    errors="$(cat "$ROOT"/ws.*.err)"
    if [[ -z "$errors" ]]; then ok "the server's asserts on the client's frames held"; else bad "the server's asserts on the client's frames held" "$errors"; fi
    OUT="$(PATH="$FAKES:$PATH" bash "$GAME/check-websocket.sh" "ws://127.0.0.1:1/game/v1/play" 2>&1)"
    CODE=$?
    expect "nothing listening: fails" test "$CODE" != 0
}

# backup.sh -----------------------------------------------------------------------------------------

test_backup() {
    echo "backup.sh"
    if ! command -v sqlite3 >/dev/null; then
        echo "  SKIP sqlite3 is not installed"
        return
    fi
    fresh
    local data="$T/gamedata" backups="$T/backups" bin="$T/bin" holder pid today
    mkdir -p "$data" "$backups" "$bin"
    if [[ "$(id -u)" != 0 ]]; then
        printf '#!/bin/sh\necho 0\n' >"$bin/id"
        printf '#!/usr/bin/env bash\nwhile [[ "$1" == --* ]]; do shift; done\nexec "$@"\n' >"$bin/setpriv"
        printf '#!/bin/sh\nexit 0\n' >"$bin/chown"
        chmod +x "$bin"/*
    else
        chown -R 10001:10001 "$data" "$backups"
        chmod 0755 "$ROOT" "$T"
    fi

    # A live database: rows sitting in the WAL, a connection open, as the server would have.
    holder="$ROOT/hold.py"
    cat >"$holder" <<'PY'
import sqlite3, sys, time
db = sqlite3.connect(sys.argv[1], isolation_level=None)
db.execute("PRAGMA journal_mode=WAL")
db.execute("PRAGMA wal_autocheckpoint=0")
db.execute("CREATE TABLE matches(id TEXT)")
db.execute("INSERT INTO matches VALUES ('in-the-wal')")
open(sys.argv[2], "w").close()
time.sleep(30)
PY
    "$PYTHON" -I "$holder" "$data/game.db" "$T/ready" &
    pid=$!
    while [[ ! -e "$T/ready" ]]; do sleep 0.1; done
    echo 'not a database' >"$data/drain"
    echo 'native library' >"$data/sqlite-native.so"
    [[ "$(id -u)" != 0 ]] || chown -R 10001:10001 "$data"

    for d in 2026-01-01 2026-01-02 2026-01-03 2026-01-04 2026-01-05 2026-01-06 2026-01-07 2026-01-08 \
        2026-01-09 2026-01-10 2026-01-11 2026-01-12 2026-01-13 2026-01-14 2026-01-15 2026-01-16; do
        mkdir -p "$backups/$d"
    done
    mkdir -p "$backups/keep-me"

    OUT="$(PATH="$bin:$PATH" GOA_GAME_DATA_DIR="$data" GOA_GAME_BACKUP_DIR="$backups" bash "$GAME/backup.sh" 2>&1)"
    CODE=$?
    today="$(date -u +%F)"
    expect "succeeds on a live WAL database" test "$CODE" = 0
    [[ "$CODE" == 0 ]] || bad "output" "$OUT"
    expect "the copy has the rows still in the WAL" test "$(sqlite3 "$backups/$today/game.db" 'SELECT id FROM matches;' 2>&1)" = "in-the-wal"
    expect "takes only the database" test "$(ls "$backups/$today")" = "game.db"
    expect "keeps 14 daily copies" test "$(find "$backups" -mindepth 1 -maxdepth 1 -type d -name '20*' | wc -l | tr -d ' ')" = 14
    expect "dropping the oldest" test ! -e "$backups/2026-01-03"
    expect "keeping the newest" test -e "$backups/2026-01-16"
    expect "and leaving other directories alone" test -e "$backups/keep-me"
    expect "no working directory left" test -z "$(find "$backups" -maxdepth 1 -name '.incoming*')"
    if [[ "$(id -u)" == 0 ]]; then
        expect "sqlite3 ran as uid 10001: nothing in the data directory is root's" test -z "$(find "$data" -user root)"
    fi
    OUT="$(PATH="$bin:$PATH" GOA_GAME_DATA_DIR="$data" GOA_GAME_BACKUP_DIR="$backups" bash "$GAME/backup.sh" 2>&1)"
    CODE=$?
    expect "a second run the same day replaces the copy" test "$CODE" = 0
    kill "$pid" 2>/dev/null
    wait "$pid" 2>/dev/null

    mkdir -p "$T/empty"
    [[ "$(id -u)" != 0 ]] || chown 10001:10001 "$T/empty"
    local count
    count="$(find "$backups" -mindepth 1 -maxdepth 1 -type d | wc -l)"
    OUT="$(PATH="$bin:$PATH" GOA_GAME_DATA_DIR="$T/empty" GOA_GAME_BACKUP_DIR="$backups" bash "$GAME/backup.sh" 2>&1)"
    CODE=$?
    expect "no database is an error" test "$CODE" != 0
    expect "and removes no older copy" test "$(find "$backups" -mindepth 1 -maxdepth 1 -type d | wc -l)" = "$count"

    if [[ "$(id -u)" != 0 ]]; then
        OUT="$(GOA_GAME_DATA_DIR="$data" GOA_GAME_BACKUP_DIR="$backups" bash "$GAME/backup.sh" 2>&1)"
        CODE=$?
        expect "refuses to run as a normal user" test "$CODE" != 0
    fi
}

# leaderboards-repo/scripts/publish.sh --------------------------------------------------------------

make_publish_repo() {
    PUB="$T/pub"
    mkdir -p "$PUB"
    cp -r "$GAME/leaderboards-repo/." "$PUB/"
    git -C "$PUB" init -q
    git -C "$PUB" add .
    git -C "$PUB" commit -qm seed
    mkdir -p "$T/pubbin"
    [[ -n "$(command -v python3 || true)" ]] || printf '#!/bin/sh\nexec "%s" "$@"\n' "$PYTHON" >"$T/pubbin/python3"
    cat >"$T/pubbin/curl" <<'EOF'
#!/usr/bin/env bash
echo "$*" >"$T/curl_args"
[[ ! -e "$T/curl_fails" ]] || { echo "curl: (7) could not connect" >&2; exit 7; }
while [[ $# -gt 0 ]]; do
    if [[ "$1" == --output ]]; then cp "$T/payload" "$2"; fi
    shift
done
EOF
    chmod +x "$T"/pubbin/*
}

run_publish() {
    : >"$T/gh_output"
    OUT="$(cd "$PUB" && PATH="$T/pubbin:$PATH" GITHUB_OUTPUT="$T/gh_output" bash scripts/publish.sh 2>&1)"
    CODE=$?
}

payload() { # generatedAt, hash letter, name
    printf '{"schema":1,"generatedAt":%s,"logSha256":"%s","matches":6,"eloConfig":1,"players":[{"id":"a1","name":"%s","rating":1010,"games":6,"wins":3}],"heroes":[]}' \
        "$1" "$(printf "$2%.0s" $(seq 1 64))" "$3" >"$T/payload"
}

commits() { git -C "$PUB" rev-list --count HEAD; }

test_publish() {
    echo "publish.sh"
    fresh
    make_publish_repo

    payload 2000 a Ada
    run_publish
    expect "a new snapshot is committed" test "$CODE" = 0
    expect "as one commit" test "$(commits)" = 2
    expect "touching only leaderboard.json" test "$(git -C "$PUB" show --pretty= --name-only HEAD)" = "leaderboard.json"
    expect "with the output set" has "$T/gh_output" "changed=true"
    expect "fetching https only, capped" has "$T/curl_args" "--fail --silent --show-error --max-time 30 --proto =https --proto-redir =https --max-filesize 524288"
    expect "from the game server" has "$T/curl_args" "https://oldboi.taila4d38d.ts.net:8443/game/v1/leaderboard"
    expect "the file is clean JSON, not the bytes sent" test "$(wc -l <"$PUB/leaderboard.json" | tr -d ' ')" = 1

    payload 3000 a Ada
    run_publish
    expect "a static board: success" test "$CODE" = 0
    expect "makes no commit" test "$(commits)" = 2
    expect "says unchanged" has "$T/gh_output" "changed=false"

    : >"$T/curl_fails"
    run_publish
    rm -f "$T/curl_fails"
    expect "server down: exit 0" test "$CODE" = 0
    expect "with a notice" has <(echo "$OUT") "::notice"
    expect "and no commit" test "$(commits)" = 2

    payload 4000 b '<script>x</script>'
    run_publish
    expect "HTML in a name fails the run" test "$CODE" != 0
    expect "with no commit" test "$(commits)" = 2
    expect "and the file as it was" test -z "$(git -C "$PUB" status --porcelain)"

    payload 1000 c Ada
    run_publish
    expect "an old generatedAt fails the run" test "$CODE" != 0
    expect "with no commit" test "$(commits)" = 2

    # Thirty quiet days
    GIT_COMMITTER_DATE="2020-01-01T00:00:00Z" GIT_AUTHOR_DATE="2020-01-01T00:00:00Z" \
        git -C "$PUB" commit -q --allow-empty -m old
    local n
    n="$(commits)"
    : >"$T/curl_fails"
    run_publish
    expect "keepalive: with the server down too, exit 0" test "$CODE" = 0
    expect "one commit" test "$(commits)" = "$((n + 1))"
    expect "named keepalive" test "$(git -C "$PUB" log -1 --format=%s)" = keepalive
    expect "touching only heartbeat" test "$(git -C "$PUB" show --pretty= --name-only HEAD)" = "heartbeat"
    expect "not asking for a deploy" has "$T/gh_output" "changed=false"
    run_publish
    rm -f "$T/curl_fails"
    expect "and not again the next run" test "$(commits)" = "$((n + 1))"

    # An untracked file can't get in, whatever else is lying around.
    echo '<html>' >"$PUB/index.html"
    payload 5000 d Ada
    run_publish
    expect "only leaderboard.json is ever added" test "$(git -C "$PUB" show --pretty= --name-only HEAD)" = "leaderboard.json"
    expect "the stray file stays out" test "$(git -C "$PUB" ls-files index.html)" = ""
}

# ----------------------------------------------------------------------------------------------------

FAKES="$ROOT/fakes"
REMOTE="$ROOT/remote"
make_fakes "$FAKES"
make_remote "$REMOTE"

test_deploy
test_rollback
test_funnel
test_websocket
test_backup
test_publish

echo
echo "$PASS passed, $FAIL failed"
((FAIL == 0))
