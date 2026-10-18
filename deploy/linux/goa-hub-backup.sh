#!/usr/bin/env bash
# Daily backup of the hub's data directory (/var/lib/goa-hub) to /var/backups/goa-hub, keeping
# 14 days. Run by goa-hub-backup.timer, and by update.sh before every update.
# The archives hold the group key: they are root-only (0600 in a 0700 directory).
#
#   goa-hub-backup.sh            make a backup now, then drop backups older than 14 days
#   goa-hub-backup.sh <file>     make a backup to exactly <file> (used by update.sh)
set -euo pipefail

data_dir="${GOA_HUB_DATA:-/var/lib/goa-hub}"
backup_dir="${GOA_HUB_BACKUPS:-/var/backups/goa-hub}"
keep_days=14

[[ "$(id -u)" -eq 0 ]] || { echo "backup: run as root (sudo)" >&2; exit 1; }
[[ -d "$data_dir" ]] || { echo "backup: $data_dir does not exist, nothing to back up"; exit 0; }

umask 077
install -d -m 0700 "$backup_dir"

target="${1:-$backup_dir/goa-hub-$(date +%Y%m%d-%H%M%S).tar.gz}"
partial="$target.partial"

# The hub writes each file atomically (temp file, then rename), so a copy taken while it runs
# holds whole files. Half-written temp files are left out.
tar --create --gzip --file "$partial" \
	--exclude='*.tmp' \
	--directory "$(dirname "$data_dir")" "$(basename "$data_dir")"
mv -f "$partial" "$target"
echo "backup: wrote $target ($(du -h "$target" | cut -f1))"

if [[ $# -eq 0 ]]; then
	# Older than 14 days: gone. -mtime +13 means "at least 14 full days old".
	find "$backup_dir" -maxdepth 1 -type f -name 'goa-hub-*.tar.gz' -mtime +$((keep_days - 1)) -print -delete \
		| sed 's/^/backup: removed /'
fi
