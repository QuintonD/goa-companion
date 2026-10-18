#!/usr/bin/env bash
# Installs a new version of the Guards of Atlantis hub, or publishes the Android app.
#
#   sudo ./update.sh goa-hub-1.2.0.zip     install that hub version (the first time, too)
#   sudo ./update.sh app-release.apk       offer this APK to the group at https://<hub>/app
#
# A hub update: unpacks the zip next to the old version, stops the hub, backs up its data, switches
# /opt/goa-hub/current to the new version, starts it and checks /api/v1/health. If the new version
# does not come up within 90 seconds it puts back the old version and the data from just before.
set -euo pipefail

install_root=/opt/goa-hub
releases="$install_root/releases"
current="$install_root/current"
data_dir=/var/lib/goa-hub
backup_dir=/var/backups/goa-hub
service=goa-hub
health_url=http://127.0.0.1:8080/api/v1/health
keep_releases=3
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

say() { printf '\n==> %s\n' "$*"; }
die() { printf '\nERROR: %s\n' "$*" >&2; exit 1; }

[[ "$(id -u)" -eq 0 ]] || die "Run this with sudo: sudo $0 $*"
[[ $# -eq 1 ]] || die "Usage: sudo $0 <goa-hub-x.y.z.zip | app-release.apk>"
file="$(readlink -f "$1")"
[[ -f "$file" ]] || die "No such file: $1"
command -v unzip >/dev/null || die "unzip is missing. Install it (Debian/Ubuntu: apt install unzip; Fedora: dnf install unzip)."
id goa-hub >/dev/null 2>&1 || die "The goa-hub user does not exist yet. Run install.sh first."

# ---------------------------------------------------------------------------------------------
# The Android app
# ---------------------------------------------------------------------------------------------
if [[ "$file" == *.apk ]]; then
	unzip -l "$file" AndroidManifest.xml >/dev/null 2>&1 || die "$1 is not an Android app (APK)."
	install -d -o goa-hub -g goa-hub -m 0700 "$data_dir" "$data_dir/app"
	install -o goa-hub -g goa-hub -m 0600 "$file" "$data_dir/app/goa.apk.new"
	mv -f "$data_dir/app/goa.apk.new" "$data_dir/app/goa.apk"
	say "Published. Signed-in friends can download it from https://<your hub>/app"
	sha256sum "$data_dir/app/goa.apk"
	exit 0
fi

# ---------------------------------------------------------------------------------------------
# The hub
# ---------------------------------------------------------------------------------------------
[[ "$file" == *.zip ]] || die "Expected a goa-hub-*.zip (the hub) or an .apk (the Android app)."
[[ -f "/etc/systemd/system/$service.service" ]] || die "goa-hub.service is not installed. Run install.sh first."
command -v curl >/dev/null || die "curl is missing."

stamp="$(date +%Y%m%d-%H%M%S)"
name="$(basename "$file" .zip)"
name="${name//[^A-Za-z0-9._-]/_}"
new_release="$releases/$name-$stamp"
install -d -m 0755 "$install_root" "$releases"

staging="$(mktemp -d "$releases/.staging.XXXXXX")"
trap 'rm -rf "$staging"' EXIT

say "Unpacking $(basename "$file")"
unzip -q "$file" -d "$staging"
mapfile -t launchers < <(find "$staging" -mindepth 2 -maxdepth 3 -type f -path '*/bin/goa-hub')
[[ ${#launchers[@]} -eq 1 ]] || die "Expected exactly one bin/goa-hub inside the zip, found ${#launchers[@]}. Is this the hub's distZip?"
app_root="$(dirname "$(dirname "${launchers[0]}")")"
[[ -d "$app_root/lib" ]] || die "The zip has bin/goa-hub but no lib/ folder next to it."
mv "$app_root" "$new_release"
chown -R root:root "$new_release"
chmod -R u=rwX,go=rX "$new_release"
chmod 0755 "$new_release/bin/goa-hub"
# Fedora and other SELinux systems: give the new files the right labels.
if command -v restorecon >/dev/null; then restorecon -R "$install_root" || true; fi

previous=""
if [[ -L "$current" ]]; then previous="$(readlink -f "$current")"; fi

if systemctl is-active --quiet "$service"; then
	say "Stopping the hub"
	systemctl stop "$service"
fi

snapshot=""
if [[ -d "$data_dir" ]] && [[ -n "$(find "$data_dir" -type f -print -quit)" ]]; then
	snapshot="$backup_dir/goa-hub-before-update-$stamp.tar.gz"
	say "Backing up the data to $snapshot"
	backup_script=/usr/local/lib/goa-hub/goa-hub-backup.sh
	[[ -x "$backup_script" ]] || backup_script="$here/goa-hub-backup.sh"
	bash "$backup_script" "$snapshot"
fi

switch_to() {
	ln -sfn "$1" "$current.new"
	mv -Tf "$current.new" "$current"
}

# Waits up to 90 s for the hub to answer {"status":"ok"} on its health URL; gives up early if
# it has crashed three times.
wait_healthy() {
	local i restarts0
	restarts0="$(systemctl show -p NRestarts --value "$service" 2>/dev/null || echo 0)"
	for ((i = 0; i < 45; i++)); do
		if curl --silent --fail --max-time 3 "$health_url" 2>/dev/null | grep -q '"status" *: *"ok"'; then
			return 0
		fi
		if systemctl is-failed --quiet "$service"; then return 1; fi
		if (( $(systemctl show -p NRestarts --value "$service" 2>/dev/null || echo 0) - ${restarts0:-0} >= 3 )); then
			return 1
		fi
		sleep 2
	done
	return 1
}

say "Starting $(basename "$new_release")"
switch_to "$new_release"
start_time="$(date '+%Y-%m-%d %H:%M:%S')"
systemctl start "$service" || true

if wait_healthy; then
	say "The hub is up: $(curl --silent --max-time 3 "$health_url")"
	# Keep the newest few versions for a manual rollback; drop the rest.
	find "$releases" -mindepth 1 -maxdepth 1 -type d ! -name '.*' -printf '%T@ %p\n' | sort -rn \
		| tail -n +$((keep_releases + 1)) | cut -d' ' -f2- | while IFS= read -r old; do
			[[ "$old" == "$(readlink -f "$current")" ]] && continue
			echo "Removing old version $(basename "$old")"
			rm -rf -- "$old"
		done
	exit 0
fi

echo
echo "The new version did not come up. Its log:" >&2
journalctl -u "$service" --since "$start_time" --no-pager -n 25 -o cat >&2 || true

if [[ -z "$previous" || ! -d "$previous" ]]; then
	systemctl stop "$service" || true
	die "This was the first install, so there is nothing to roll back to. Fix the error above and run it again."
fi

say "Rolling back to $(basename "$previous")"
systemctl stop "$service" || true
if [[ -n "$snapshot" ]]; then
	failed_data="$data_dir.failed-$stamp"
	mv "$data_dir" "$failed_data"
	tar --extract --gzip --file "$snapshot" --directory "$(dirname "$data_dir")"
	echo "Data put back from $snapshot. What the new version left is in $failed_data (delete it when done)."
fi
switch_to "$previous"
systemctl start "$service" || true
if wait_healthy; then
	die "Update failed; the previous version is running again. The broken version stays in $new_release."
fi
die "Update failed and the previous version did not come back either. See: journalctl -u $service -n 100"
