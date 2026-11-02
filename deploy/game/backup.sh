#!/usr/bin/env bash
# A safe daily copy of the game server's SQLite data, keeping the newest 14 days. Run as root (cron,
# README.md). Copies use sqlite3's .backup: a plain file copy of a live WAL database can be torn.
# sqlite3 runs as uid 10001, or a root-owned -wal or -shm file would lock the server out of its data.
# Any SQLite file in the directory is taken, by its header and not its name; lock and native library
# files are not data.
set -euo pipefail

DATA="${GOA_GAME_DATA_DIR:-/var/lib/goa-game}"
BACKUPS="${GOA_GAME_BACKUP_DIR:-/var/backups/goa-game}"
KEEP=14

as_game() {
    setpriv --reuid=10001 --regid=10001 --clear-groups "$@"
}

is_sqlite() {
    [[ "$(head -c 15 "$1" 2>/dev/null)" == "SQLite format 3" ]]
}

main() {
    local today work out file name count=0 check old
    [[ "$(id -u)" == "0" ]] || {
        echo "backup: run as root" >&2
        return 1
    }
    command -v sqlite3 >/dev/null || {
        echo "backup: sqlite3 is not installed (sudo apt install sqlite3)" >&2
        return 1
    }

    mkdir -p "$BACKUPS"
    chown 10001:10001 "$BACKUPS"
    chmod 0700 "$BACKUPS"
    today="$(date -u +%F)"
    out="$BACKUPS/$today"
    work="$BACKUPS/.incoming-$today"
    rm -rf "$work"
    as_game mkdir "$work"

    for file in "$DATA"/*; do
        if [[ ! -f "$file" ]] || ! is_sqlite "$file"; then
            continue
        fi
        name="$(basename "$file")"
        as_game sqlite3 "$file" ".timeout 30000" ".backup '$work/$name'"
        check="$(as_game sqlite3 "$work/$name" "PRAGMA integrity_check;")"
        if [[ "$check" != "ok" ]]; then
            echo "backup: $name failed its integrity check: $check" >&2
            rm -rf "$work"
            return 1
        fi
        count=$((count + 1))
    done

    # Nothing to copy is an error: a backup job that quietly saves nothing is found out too late.
    if ((count == 0)); then
        echo "backup: no SQLite file in $DATA" >&2
        rm -rf "$work"
        return 1
    fi

    # The older copies go only once today's is whole.
    rm -rf "$out"
    mv "$work" "$out"
    while IFS= read -r old; do
        rm -rf "${BACKUPS:?}/${old:?}"
    done < <(find "$BACKUPS" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' \
        | grep -E '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' | sort -r | tail -n "+$((KEEP + 1))")
    echo "backup: $count database(s) in $out"
}

main "$@"
exit
