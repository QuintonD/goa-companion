#!/usr/bin/env bash
# Build and (re)start the game server on oldboi, from its own checkout at an explicit ref:
#   deploy.sh [--no-drain] [ref]     # ref: a branch on origin, a tag or a commit; default main
# Run it inside tmux: draining can take hours. See README.md.
#
# ~/goa-companion is never read or changed: it may hold someone's edits or another branch. Everything
# happens in ~/goa-game-deploy, its own clone through the deploy key. Everything runs inside main() so
# a checkout that rewrites this file can't confuse bash mid-run; a new version applies on the next run.
set -euo pipefail

DATA="${GOA_GAME_DATA_DIR:-/var/lib/goa-game}"
CONTAINER_DATA=/var/lib/goa-game
HEALTH_URL="${GOA_GAME_HEALTH_URL:-http://127.0.0.1:8788/v1/health}"
DOCKER=()
# True while this run has rooms paused and nothing later will reopen them; read by the exit trap.
DRAIN_SET=0

die() {
    echo "deploy: $*" >&2
    exit 1
}

health() {
    curl -fsS --max-time 3 "$HEALTH_URL" 2>/dev/null
}

# Through the running container, as uid 10001: sudo's timestamp is long gone after hours of draining.
clear_drain() {
    "${DOCKER[@]}" exec goa-game rm -f "$CONTAINER_DATA/drain" 2>/dev/null \
        || sudo -n rm -f "$DATA/drain" 2>/dev/null
}

# shellcheck disable=SC2329 # called by the trap in main
on_exit() {
    local code=$?
    trap - EXIT
    if ((DRAIN_SET)); then
        clear_drain || echo "deploy: could not remove $DATA/drain; new rooms stay paused until it is gone" >&2
    fi
    exit "$code"
}

# One Gradle build at a time on oldboi (ADR-038).
refuse_if_build_running() {
    local state
    state="$(systemctl --user is-active plekje-apk-build 2>/dev/null || true)"
    case "$state" in
        active | activating | reloading)
            die "the plekje APK build is running on this machine, and oldboi takes one build at a time. Please try again when it has finished."
            ;;
    esac
    if pgrep -f org.gradle.wrapper.GradleWrapperMain >/dev/null 2>&1; then
        die "another Gradle build is running on this machine, and oldboi takes one build at a time. Please try again when it has finished."
    fi
}

checkout_ref() {
    local checkout="$1" ref="$2" remote="${GOA_GAME_REMOTE:-goa-github:QuintonD/goa-companion.git}" commit

    if [[ ! -d "$checkout/.git" ]]; then
        echo "==> cloning into $checkout" >&2
        git clone --quiet --filter=blob:none --no-checkout "$remote" "$checkout"
    fi
    echo "==> fetching $ref" >&2
    git -C "$checkout" fetch --quiet --prune --force --tags origin '+refs/heads/*:refs/remotes/origin/*'

    commit="$(git -C "$checkout" rev-parse --verify --quiet "refs/remotes/origin/$ref^{commit}" \
        || git -C "$checkout" rev-parse --verify --quiet "refs/tags/$ref^{commit}" \
        || { [[ "$ref" =~ ^[0-9a-f]{7,40}$ ]] && git -C "$checkout" rev-parse --verify --quiet "$ref^{commit}"; } \
        || true)"
    [[ -n "$commit" ]] || die "no branch, tag or commit named '$ref' on origin"

    git -C "$checkout" checkout --quiet --force --detach "$commit"
    [[ -f "$checkout/deploy/game/compose.yaml" ]] || die "$ref has no deploy/game"
    [[ -f "$checkout/server/game/build.gradle.kts" ]] || die "$ref has no server/game"
    printf '%s\n' "$commit"
}

# The rules sources and the catalog (docs/ONLINE.md "A rules fix during a running game").
rules_build() {
    local checkout="$1" commit="$2" path
    for path in core/game core/domain core/model app/src/main/assets/catalog; do
        git -C "$checkout" rev-parse --verify "$commit:$path"
    done | sha256sum | cut -c1-16
}

prepare_data_dir() {
    if [[ "$(stat -c '%u %a' "$DATA" 2>/dev/null || true)" != "10001 700" ]]; then
        echo "==> preparing $DATA (uid 10001, mode 0700)"
        sudo mkdir -p "$DATA"
        sudo chown 10001:10001 "$DATA"
        sudo chmod 0700 "$DATA"
    fi
}

# Pauses new rooms and waits for the rooms that remain. A running game survives a restart anyway
# (docs/ONLINE.md), so at the limit the deploy goes on.
drain() {
    local hours="${GOA_GAME_DRAIN_HOURS:-3}" poll="${GOA_GAME_DRAIN_POLL_SECONDS:-60}" body rooms status deadline
    [[ "$hours" =~ ^[0-9]+$ ]] || die "GOA_GAME_DRAIN_HOURS must be a whole number, got '$hours'"

    if ! body="$(health)"; then
        echo "==> the server is not up: nothing to drain"
        return 0
    fi
    echo "==> pausing new rooms, waiting up to ${hours} h for rooms to finish"
    "${DOCKER[@]}" exec goa-game touch "$CONTAINER_DATA/drain" || die "could not write the drain file"
    DRAIN_SET=1

    deadline=$((SECONDS + hours * 3600))
    while :; do
        if ! body="$(health)"; then
            echo "==> the server stopped answering: going on"
            return 0
        fi
        rooms="$(sed -n 's/.*"rooms":\([0-9][0-9]*\).*/\1/p' <<<"$body")"
        status="$(sed -n 's/.*"status":"\([^"]*\)".*/\1/p' <<<"$body")"
        [[ -n "$rooms" ]] || die "no room count in /v1/health: $body"
        if ((rooms == 0)); then
            echo "==> no rooms left"
            return 0
        fi
        if ((SECONDS >= deadline)); then
            echo "==> ${hours} h are up with ${rooms} room(s) left: going on; running games resume from their saved answers"
            return 0
        fi
        echo "    $(date +%H:%M) ${rooms} room(s), status ${status:-unknown}"
        if [[ "$status" != "draining" ]]; then
            echo "    warning: the server does not report 'draining', so it may not be honouring $CONTAINER_DATA/drain"
        fi
        sleep "$poll"
    done
}

tag_previous() {
    local image health_state
    image="$("${DOCKER[@]}" inspect -f '{{.Image}}' goa-game 2>/dev/null || true)"
    health_state="$("${DOCKER[@]}" inspect -f '{{.State.Health.Status}}' goa-game 2>/dev/null || true)"
    if [[ -n "$image" && "$health_state" == "healthy" ]]; then
        "${DOCKER[@]}" tag "$image" goa-game:previous
        echo "==> running image tagged goa-game:previous"
    elif [[ -n "$image" ]]; then
        # After a failed deploy the running image is the bad one: keep the last good tag.
        echo "==> the running container is not healthy: goa-game:previous stays as it is"
    else
        echo "==> no running container: nothing to tag"
    fi
}

main() {
    local ref="" no_drain=0 arg checkout here commit rules gradle install body
    for arg in "$@"; do
        case "$arg" in
            --no-drain) no_drain=1 ;;
            -*) die "unknown option $arg (usage: deploy.sh [--no-drain] [ref])" ;;
            *)
                [[ -z "$ref" ]] || die "one ref only (usage: deploy.sh [--no-drain] [ref])"
                ref="$arg"
                ;;
        esac
    done
    ref="${ref:-main}"

    checkout="${GOA_GAME_CHECKOUT:-$HOME/goa-game-deploy}"
    [[ "$checkout" != "$HOME/goa-companion" ]] || die "the checkout must not be ~/goa-companion"
    gradle="$(command -v goa-gradle || echo "$HOME/.local/bin/goa-gradle")"
    [[ -x "$gradle" ]] || die "goa-gradle not found"
    refuse_if_build_running

    DOCKER=(docker)
    docker info >/dev/null 2>&1 || DOCKER=(sudo docker)
    trap on_exit EXIT
    trap 'exit 130' INT TERM HUP

    commit="$(checkout_ref "$checkout" "$ref")"
    here="$checkout/deploy/game"
    install="$checkout/server/game/build/install/goa-game"
    echo "==> at $(git -C "$checkout" log --oneline -1)"
    prepare_data_dir

    if ((no_drain)); then
        echo "==> --no-drain: running games restart along with the server"
    else
        drain
    fi

    # Checked again: draining can outlast someone else's build starting. Rooms reopen on the way out.
    refuse_if_build_running
    # Gone first, so a finished build proves Gradle ran in this tree and not in some other one.
    rm -rf "$install"
    echo "==> goa-gradle :server:game:test :server:game:installDist"
    (cd "$checkout" && "$gradle" :server:game:test :server:game:installDist)
    [[ -x "$install/bin/goa-game" ]] || die "the build left no $install/bin/goa-game"
    [[ -f "$install/assets/catalog/index.json" ]] || die "the distribution has no assets/catalog"

    rules="$(rules_build "$checkout" "$commit")"
    echo "==> rules build $rules"
    rm -rf "$here/dist"
    mkdir -p "$here/dist"
    cp -a "$install/." "$here/dist/"
    # A file, not the environment: `sudo docker` would drop a variable.
    printf 'GOA_GAME_RULES_BUILD=%s\n' "$rules" >"$here/.env"

    tag_previous
    echo "==> docker compose up -d --build"
    "${DOCKER[@]}" compose -f "$here/compose.yaml" up -d --build --remove-orphans

    echo "==> waiting for $HEALTH_URL to report rules build $rules"
    for _ in $(seq 1 "${GOA_GAME_HEALTH_TRIES:-45}"); do
        body="$(health || true)"
        if [[ "$body" == *"\"rulesBuild\":\"$rules\""* ]]; then
            echo "$body"
            "${DOCKER[@]}" compose -f "$here/compose.yaml" ps
            if ((no_drain)); then
                if "${DOCKER[@]}" exec goa-game test -e "$CONTAINER_DATA/drain"; then
                    echo "note: the drain file is still there; new rooms stay paused"
                fi
            else
                clear_drain || die "could not remove the drain file: new rooms stay paused (see README.md)"
                DRAIN_SET=0
                echo "==> new rooms are open"
            fi
            return 0
        fi
        sleep "${GOA_GAME_HEALTH_SLEEP:-2}"
    done

    # New rooms stay paused on a server that is not right; rollback.sh reopens them.
    DRAIN_SET=0
    echo "the server did not become healthy; last logs:" >&2
    "${DOCKER[@]}" compose -f "$here/compose.yaml" logs --tail 80 >&2 || true
    {
        echo
        echo "To go back to the image that was running before this deploy:"
        echo "    $here/rollback.sh"
    } >&2
    return 1
}

main "$@"
exit
