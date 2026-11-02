#!/usr/bin/env bash
# Go back to the image deploy.sh tagged goa-game:previous, and reopen new rooms. No build. The image
# keeps its own rules build; a game started on the newer server is checked on replay (docs/ONLINE.md).
set -euo pipefail

main() {
    local here docker
    here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    docker=(docker)
    docker info >/dev/null 2>&1 || docker=(sudo docker)

    "${docker[@]}" image inspect goa-game:previous >/dev/null 2>&1 || {
        echo "rollback: there is no goa-game:previous image to go back to" >&2
        return 1
    }
    "${docker[@]}" tag goa-game:previous goa-game:latest
    "${docker[@]}" compose -f "$here/compose.yaml" up -d --no-build --force-recreate

    for _ in $(seq 1 "${GOA_GAME_HEALTH_TRIES:-45}"); do
        if curl -fsS --max-time 3 "${GOA_GAME_HEALTH_URL:-http://127.0.0.1:8788/v1/health}"; then
            echo
            "${docker[@]}" exec goa-game rm -f /var/lib/goa-game/drain
            "${docker[@]}" compose -f "$here/compose.yaml" ps
            echo "==> rolled back; new rooms are open"
            return 0
        fi
        sleep "${GOA_GAME_HEALTH_SLEEP:-2}"
    done
    echo "rollback: the previous image did not become healthy either; last logs:" >&2
    "${docker[@]}" compose -f "$here/compose.yaml" logs --tail 80 >&2 || true
    return 1
}

main "$@"
exit
