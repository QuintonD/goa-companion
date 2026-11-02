#!/usr/bin/env bash
# One run of the publisher, without any token: fetch, validate, commit locally. The workflow's next step
# pushes whatever this committed. Only leaderboard.json and heartbeat are ever added.
#
# oldboi being down is not an error: Pages keeps the last copy. A snapshot that fails validation is, so
# the run goes red; the published file stays as it was.
set -euo pipefail

URL=https://oldboi.taila4d38d.ts.net:8443/game/v1/leaderboard
MAX_BYTES=524288
KEEPALIVE_SECONDS=$((30 * 24 * 60 * 60))

commit() {
    local message="$1" file="$2"
    git add -- "$file"
    git -c user.name='github-actions[bot]' \
        -c user.email='41898282+github-actions[bot]@users.noreply.github.com' \
        commit --quiet --only -m "$message" -- "$file"
}

main() {
    local here incoming status=0 changed=false last
    here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    incoming="$(mktemp)"

    if curl --fail --silent --show-error --max-time 30 --proto '=https' --proto-redir '=https' \
        --max-filesize "$MAX_BYTES" --output "$incoming" "$URL"; then
        python3 -I "$here/validate_leaderboard.py" \
            --incoming "$incoming" --current leaderboard.json --write leaderboard.json || status=$?
        case "$status" in
            0)
                commit "Leaderboard update" leaderboard.json
                changed=true
                ;;
            10) status=0 ;;
        esac
    else
        echo "::notice title=Leaderboard not fetched::The game server did not answer; Pages keeps the last copy."
    fi
    rm -f "$incoming"

    # GitHub turns a schedule off after 60 days without activity in the repository. A quiet board or a
    # server that is away must not end the publishing.
    last="$(git log -1 --format=%ct)"
    if (($(date +%s) - last > KEEPALIVE_SECONDS)); then
        date -u +%FT%TZ >heartbeat
        commit "keepalive" heartbeat
    fi

    if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
        echo "changed=$changed" >>"$GITHUB_OUTPUT"
    fi
    return "$status"
}

main "$@"
