#!/usr/bin/env bash
# Update and (re)start the sync relay on oldboi from this repository checkout:
#   ~/goa-companion/deploy/relay/deploy.sh            # git pull, rebuild, restart, health check
#   ~/goa-companion/deploy/relay/deploy.sh --no-pull  # rebuild what is checked out
# Everything runs inside main() so a `git pull` that rewrites this file can't confuse bash mid-run.
set -euo pipefail

main() {
    local here repo docker data=/var/lib/goa-relay pull=1
    [[ "${1:-}" == "--no-pull" ]] && pull=0
    here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    repo="$(git -C "$here" rev-parse --show-toplevel)"

    if ((pull)); then
        echo "==> git pull ($(git -C "$repo" rev-parse --abbrev-ref HEAD))"
        git -C "$repo" pull --ff-only
    fi
    echo "==> at $(git -C "$repo" log --oneline -1)"

    docker=(docker)
    docker info >/dev/null 2>&1 || docker=(sudo docker)

    # The container runs as uid/gid 10001 and only this directory is writable.
    sudo install -d -o 10001 -g 10001 -m 0700 "$data"
    # Restart on reboot: the Docker service must start at boot (compose has restart: unless-stopped).
    sudo systemctl enable --now docker.service >/dev/null 2>&1 || sudo systemctl enable docker.service

    echo "==> docker compose up -d --build"
    "${docker[@]}" compose -f "$here/compose.yaml" up -d --build --remove-orphans

    echo "==> waiting for http://127.0.0.1:8787/v1/health"
    local i
    for i in $(seq 1 45); do
        if curl -fsS --max-time 3 http://127.0.0.1:8787/v1/health; then
            echo
            "${docker[@]}" compose -f "$here/compose.yaml" ps
            return 0
        fi
        sleep 2
    done
    echo "relay did not become healthy; last logs:" >&2
    "${docker[@]}" compose -f "$here/compose.yaml" logs --tail 80 >&2
    return 1
}

main "$@"
exit
