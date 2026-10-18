#!/usr/bin/env bash
# Sets up the Guards of Atlantis group hub on this Linux machine (ADR-030 §10).
#
#   sudo ./install.sh goa-hub-1.0.0.zip                the usual way: Tailscale Funnel
#   sudo ./install.sh --with-caddy goa-hub-1.0.0.zip   the alternative: Caddy + DuckDNS + router
#
# Both ways: Java, the goa-hub user and folders, the hub as a systemd service on 127.0.0.1:8080,
# daily backups and automatic security updates. Then either Tailscale Funnel publishes the hub at
# https://<machine>.<tailnet>.ts.net, or (--with-caddy) Caddy does at https://<name>.duckdns.org
# with a DuckDNS updater, after you forward ports 80 and 443 on the router.
#
# Safe to run again: it keeps what is set up and fills in what is missing (run it again after
# copying a newer deploy/ folder). Without a zip it keeps the installed hub version.
# Questions can be answered in advance:
#   sudo GOA_HUB_INVITE='https://…/join#goa-sync:1?…' GOA_ANDROID_CERT_SHA256=AA:BB:… \
#        GOA_TS_HOSTNAME=goa ./install.sh goa-hub-1.0.0.zip
#   (--with-caddy also reads GOA_HUB_DOMAIN=ourgroup and DUCKDNS_TOKEN=…)
#   GOA_HUB_INVITE= with nothing after it means: don't ask, the hub has joined already.
# Debian 13 and Ubuntu 24.04 / 26.04 first; Fedora 43+ works too (dnf, firewalld, SELinux).
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
alt="$here/alt-caddy"
kit_root="$(dirname "$here")"
install_root=/opt/goa-hub
data_dir=/var/lib/goa-hub
config_dir=/etc/goa-hub
backup_dir=/var/backups/goa-hub
lib_dir=/usr/local/lib/goa-hub
duckdns_env="$config_dir/duckdns.env"
health_url=http://127.0.0.1:8080/api/v1/health

say() { printf '\n==> %s\n' "$*"; }
note() { printf '    %s\n' "$*"; }
warn() { printf '\nWARNING: %s\n' "$*" >&2; }
die() { printf '\nERROR: %s\n' "$*" >&2; exit 1; }
interactive() { [[ -t 0 ]]; }

# ---------------------------------------------------------------------------------------------
# Arguments and checks
# ---------------------------------------------------------------------------------------------
mode=tailscale
zip=""
for arg in "$@"; do
	case "$arg" in
		--with-caddy) mode=caddy ;;
		-h | --help) sed -n '2,20p' "$0"; exit 0 ;;
		-*) die "Unknown option $arg (see: $0 --help)" ;;
		*) zip="$(readlink -f "$arg")"; [[ -f "$zip" && "$zip" == *.zip ]] || die "Not a zip file: $arg" ;;
	esac
done

[[ "$(id -u)" -eq 0 ]] || die "Run this with sudo: sudo $0 $*"
[[ -d /run/systemd/system ]] || die "This machine does not run systemd; the kit needs it."
for f in goa-hub.service goa-hub-backup.sh goa-hub-backup.service goa-hub-backup.timer update.sh; do
	[[ -f "$here/$f" ]] || die "Missing $here/$f. Copy the whole deploy/ folder to this machine."
done
if [[ "$mode" == caddy ]]; then
	for f in Caddyfile duckdns-update.sh duckdns-update.service duckdns-update.timer; do
		[[ -f "$alt/$f" ]] || die "Missing $alt/$f. Copy the whole deploy/ folder to this machine."
	done
fi
if [[ -z "$zip" && ! -x "$install_root/current/bin/goa-hub" ]]; then
	die "Usage: sudo $0 [--with-caddy] goa-hub-x.y.z.zip   (build it on the PC: .\\gradlew.bat :server:hub:distZip)"
fi

if command -v apt-get >/dev/null; then
	pm=apt
elif command -v dnf >/dev/null; then
	pm=dnf
else
	die "Only Debian/Ubuntu (apt) and Fedora (dnf) are supported by this script. See deploy/README.md."
fi

# ---------------------------------------------------------------------------------------------
# Questions first, so the rest runs on its own
# ---------------------------------------------------------------------------------------------
read_env_value() { # <file> <KEY>
	[[ -r "$1" ]] || return 0
	sed -n "s/^$2=//p" "$1" | tail -n 1
}

normalize_domain() { # ourgroup | ourgroup.duckdns.org | https://ourgroup.duckdns.org/ -> ourgroup
	local d="${1,,}"
	d="${d#http://}"; d="${d#https://}"; d="${d%%/*}"; d="${d%.duckdns.org}"
	d="${d//[[:space:]]/}"
	printf '%s' "$d"
}

if [[ "$mode" == caddy ]]; then
	domain="$(normalize_domain "${GOA_HUB_DOMAIN:-$(read_env_value "$duckdns_env" DUCKDNS_DOMAIN)}")"
	if [[ -z "$domain" ]]; then
		interactive || die "Set GOA_HUB_DOMAIN (your DuckDNS name) or run this in a terminal."
		read -r -p "Your DuckDNS name (e.g. ourgroup, for ourgroup.duckdns.org): " answer
		domain="$(normalize_domain "$answer")"
	fi
	[[ "$domain" =~ ^[a-z0-9]([a-z0-9-]*[a-z0-9])?$ ]] || die "\"$domain\" is not a DuckDNS name (letters, digits and dashes)."

	token="${DUCKDNS_TOKEN:-$(read_env_value "$duckdns_env" DUCKDNS_TOKEN)}"
	if [[ -z "$token" ]]; then
		interactive || die "Set DUCKDNS_TOKEN or run this in a terminal."
		read -r -s -p "Your DuckDNS token (top of duckdns.org after signing in; not shown as you paste): " token
		echo
	fi
	token="${token//[[:space:]]/}"
	[[ "$token" =~ ^[A-Za-z0-9-]+$ ]] || die "That does not look like a DuckDNS token."
fi

invite="${GOA_HUB_INVITE:-}"
if [[ -z "$invite" && -z "${GOA_HUB_INVITE+set}" ]] && interactive; then
	echo
	echo "Join the hub to your group now? On an Android phone in the group: Settings -> Group sync ->"
	echo "Invite a friend -> Share as text, send it to yourself, and paste the last line here"
	echo "(it starts with goa-sync: or https://). Press Enter to skip if the hub already joined."
	read -r -p "Invite: " invite
fi
cert_sha256="${GOA_ANDROID_CERT_SHA256:-}"
ts_hostname="${GOA_TS_HOSTNAME:-goa}"
[[ "$ts_hostname" =~ ^[a-z0-9]([a-z0-9-]*[a-z0-9])?$ ]] || die "GOA_TS_HOSTNAME must be letters, digits and dashes."

# ---------------------------------------------------------------------------------------------
# Packages: Java and tools (and Caddy for --with-caddy)
# ---------------------------------------------------------------------------------------------
java_major() {
	command -v java >/dev/null || { echo 0; return; }
	java -XshowSettings:properties -version 2>&1 | sed -n 's/.*java.specification.version = \([0-9]*\).*/\1/p' | head -n 1
}

say "Installing packages (Java, curl, unzip$([[ "$mode" == caddy ]] && echo ', Caddy'))"
if [[ "$pm" == apt ]]; then
	export DEBIAN_FRONTEND=noninteractive
	apt-get update -q
	apt-get install -y -q curl unzip ca-certificates gpg
	if (( "$(java_major)" < 21 )); then
		if apt-cache show openjdk-21-jre-headless >/dev/null 2>&1; then
			apt-get install -y -q openjdk-21-jre-headless
		elif apt-cache show openjdk-25-jre-headless >/dev/null 2>&1; then
			apt-get install -y -q openjdk-25-jre-headless
		else
			die "No Java 21+ package found. Debian 12 has only Java 17: upgrade to Debian 13, or see deploy/README.md."
		fi
	fi
	if [[ "$mode" == caddy ]]; then
		if [[ ! -f /usr/share/keyrings/caddy-stable-archive-keyring.gpg ]]; then
			# Caddy's official Debian/Ubuntu repository (https://caddyserver.com/docs/install).
			curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' \
				| gpg --batch --yes --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
			curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' \
				> /etc/apt/sources.list.d/caddy-stable.list
			chmod o+r /usr/share/keyrings/caddy-stable-archive-keyring.gpg /etc/apt/sources.list.d/caddy-stable.list
			apt-get update -q
		fi
		apt-get install -y -q caddy
	fi
else
	dnf install -y curl unzip ca-certificates policycoreutils
	if (( "$(java_major)" < 21 )); then
		if dnf -q info java-21-openjdk-headless >/dev/null 2>&1; then
			dnf install -y java-21-openjdk-headless
		else
			dnf install -y java-25-openjdk-headless
		fi
	fi
	if [[ "$mode" == caddy ]] && ! rpm -q caddy >/dev/null 2>&1; then
		dnf install -y dnf5-plugins 2>/dev/null || dnf install -y dnf-plugins-core
		dnf copr enable -y @caddy/caddy
		dnf install -y caddy
	fi
fi
(( "$(java_major)" >= 21 )) || die "java on the PATH is older than 21. Pick 21 or newer with: sudo update-alternatives --config java"
note "Java $(java_major)$([[ "$mode" == caddy ]] && echo ", Caddy $(caddy version | cut -d' ' -f1)")"

# ---------------------------------------------------------------------------------------------
# User, folders, scripts, services
# ---------------------------------------------------------------------------------------------
say "Creating the goa-hub user and folders"
if ! id goa-hub >/dev/null 2>&1; then
	nologin="$(command -v nologin || echo /usr/sbin/nologin)"
	useradd --system --user-group --home-dir "$data_dir" --no-create-home --shell "$nologin" \
		--comment "Guards of Atlantis hub" goa-hub
fi
install -d -m 0755 "$install_root" "$install_root/releases" "$lib_dir"
install -d -o goa-hub -g goa-hub -m 0700 "$data_dir" "$data_dir/app"
install -d -m 0700 "$config_dir" "$backup_dir"
note "$data_dir (goa-hub only), $backup_dir and $config_dir (root only)"

say "Installing the scripts and services"
# A copy of the kit stays on the machine: /opt/goa-hub/deploy/linux/update.sh for updates.
if [[ "$kit_root" != "$install_root/deploy" ]]; then
	rm -rf "$install_root/deploy.new"
	install -d -m 0755 "$install_root/deploy.new"
	cp -r "$here" "$install_root/deploy.new/linux"
	if [[ -f "$kit_root/README.md" ]]; then cp "$kit_root/README.md" "$install_root/deploy.new/"; fi
	chown -R root:root "$install_root/deploy.new"
	chmod -R u=rwX,go=rX "$install_root/deploy.new"
	rm -rf "$install_root/deploy"
	mv "$install_root/deploy.new" "$install_root/deploy"
fi
install -m 0755 "$here/goa-hub-backup.sh" "$lib_dir/"
for unit in goa-hub.service goa-hub-backup.service goa-hub-backup.timer; do
	install -m 0644 "$here/$unit" "/etc/systemd/system/$unit"
done
if [[ "$mode" == caddy ]]; then
	install -m 0755 "$alt/duckdns-update.sh" "$lib_dir/"
	for unit in duckdns-update.service duckdns-update.timer; do
		install -m 0644 "$alt/$unit" "/etc/systemd/system/$unit"
	done
fi
if command -v restorecon >/dev/null; then
	restorecon -R "$install_root" "$lib_dir" "$data_dir" /etc/systemd/system || true
fi
systemctl daemon-reload
systemctl enable goa-hub.service >/dev/null
systemctl enable --now goa-hub-backup.timer >/dev/null

# ---------------------------------------------------------------------------------------------
# The hub itself (on 127.0.0.1:8080 only)
# ---------------------------------------------------------------------------------------------
if [[ -n "$zip" ]]; then
	say "Installing the hub from $(basename "$zip")"
	bash "$here/update.sh" "$zip"
fi

hub() { # runs the hub's command line as the goa-hub user; files it writes are 0600
	(umask 077 && cd / && runuser -u goa-hub -- "$install_root/current/bin/goa-hub" "$@")
}

wait_healthy() {
	local i
	for ((i = 0; i < 45; i++)); do
		curl --silent --fail --max-time 3 "$health_url" 2>/dev/null | grep -q '"status" *: *"ok"' && return 0
		sleep 2
	done
	return 1
}

# ---------------------------------------------------------------------------------------------
# Publishing: Tailscale Funnel (default)
# ---------------------------------------------------------------------------------------------
if [[ "$mode" == tailscale ]]; then
	say "Tailscale"
	if ! command -v tailscale >/dev/null; then
		# Tailscale's official installer: adds their package repository and starts tailscaled.
		curl -fsSL https://tailscale.com/install.sh | sh
	fi
	systemctl enable --now tailscaled >/dev/null

	ts_state() { tailscale status --json 2>/dev/null | sed -n 's/^ *"BackendState": *"\([^"]*\)".*/\1/p' | head -n 1; }
	if [[ "$(ts_state)" != Running ]]; then
		interactive || die "Tailscale is not logged in. Run: sudo tailscale up --hostname=$ts_hostname   then this script again."
		note "Log this machine in to your tailnet: open the link below on your PC or phone."
		tailscale up --hostname="$ts_hostname"
	fi
	[[ "$(ts_state)" == Running ]] || die "Tailscale is not connected. Check: tailscale status"
	# Keep Tailscale itself up to date (its own updater; works with the package repository).
	tailscale set --auto-update >/dev/null 2>&1 || note "(Tailscale auto-update not available on this install.)"

	# This machine's full name in the tailnet, e.g. goa.tail1234.ts.net. Self comes first.
	ts_name="$(tailscale status --json | sed -n 's/^ *"DNSName": *"\([^"]*\)".*/\1/p' | head -n 1)"
	ts_name="${ts_name%.}"
	[[ "$ts_name" == *.ts.net ]] || die "Could not read this machine's tailnet name (got \"$ts_name\"). Is MagicDNS on? See deploy/README.md."
	public_url="https://$ts_name"
else
	public_url="https://$domain.duckdns.org"
fi

# ---------------------------------------------------------------------------------------------
# Join and configure the hub
# ---------------------------------------------------------------------------------------------
say "Configuring the hub for $public_url"
systemctl stop goa-hub
if [[ -n "$invite" ]]; then
	if hub join "$invite" --data "$data_dir"; then
		note "Joined the group."
	else
		warn "Joining failed (see above). Run this script again with a fresh invite, or see deploy/README.md."
	fi
fi
config_args=(config --public-url "$public_url" --data "$data_dir")
if [[ -n "$cert_sha256" ]]; then config_args+=(--android-cert-sha256 "$cert_sha256"); fi
hub "${config_args[@]}"
systemctl start goa-hub
wait_healthy || die "The hub did not answer on $health_url. See: journalctl -u goa-hub -n 100"
note "The hub answers on $health_url (this machine only)."
hub status --data "$data_dir" || true

if [[ "$mode" == tailscale ]]; then
	say "Publishing the hub with Tailscale Funnel"
	note "If Funnel is not allowed for this machine yet, Tailscale prints a link: open it, approve,"
	note "and this continues by itself. (Or set it up first: deploy/README.md, step 3.)"
	# --bg: Tailscale keeps this setting and restores it after restarts and reboots.
	tailscale funnel --bg --https=443 http://127.0.0.1:8080
	tailscale funnel status || true
fi

# ---------------------------------------------------------------------------------------------
# Alternative: DuckDNS + Caddy (--with-caddy)
# ---------------------------------------------------------------------------------------------
if [[ "$mode" == caddy ]]; then
	say "Setting up DuckDNS for $domain.duckdns.org"
	(
		umask 077
		printf '# DuckDNS settings for the Guards of Atlantis hub. Root only: the token controls the name.\nDUCKDNS_DOMAIN=%s\nDUCKDNS_TOKEN=%s\n' \
			"$domain" "$token" > "$duckdns_env.new"
	)
	chmod 0600 "$duckdns_env.new"
	mv -f "$duckdns_env.new" "$duckdns_env"
	systemctl enable --now duckdns-update.timer >/dev/null
	if systemctl start duckdns-update.service; then
		note "$(journalctl -u duckdns-update.service -n 20 --no-pager -o cat | grep '^duckdns:' | tail -n 1)"
	else
		journalctl -u duckdns-update.service -n 20 --no-pager -o cat | grep '^duckdns:' | tail -n 1 >&2 || true
		warn "DuckDNS did not take the update. Check the name and the token, then: sudo systemctl start duckdns-update"
	fi

	say "Configuring Caddy for $public_url"
	access_log=/var/log/caddy/goa-hub-access.log
	install -d -o caddy -g caddy -m 0750 /var/log/caddy
	caddyfile=/etc/caddy/Caddyfile
	rendered="$(mktemp)"
	sed -e "s|{\$GOA_HUB_DOMAIN}|$domain.duckdns.org|g" -e "s|{\$GOA_HUB_ACCESS_LOG}|$access_log|g" "$alt/Caddyfile" > "$rendered"
	if [[ -f "$caddyfile" ]] && ! cmp -s "$rendered" "$caddyfile" && ! grep -q "Guards of Atlantis hub" "$caddyfile"; then
		cp -p "$caddyfile" "$caddyfile.before-goa-hub"
		note "Your old Caddyfile is saved as $caddyfile.before-goa-hub"
	fi
	install -m 0644 "$rendered" "$caddyfile"
	rm -f "$rendered"
	if command -v restorecon >/dev/null; then restorecon -R /etc/caddy /var/log/caddy || true; fi
	# Validate as the caddy user, so any file it touches (the access log) belongs to caddy.
	if [[ -e "$access_log" ]]; then chown caddy:caddy "$access_log"; fi
	caddy_validate() { (cd / && runuser -u caddy -- env HOME=/var/lib/caddy caddy validate --config "$caddyfile" --adapter caddyfile); }
	caddy_validate >/dev/null 2>&1 || { caddy_validate || true; die "Caddy does not accept $caddyfile (see above)."; }
	systemctl enable caddy >/dev/null
	systemctl reload-or-restart caddy

	say "Firewall"
	if command -v ufw >/dev/null && ufw status | grep -q "Status: active"; then
		ufw allow 80/tcp comment 'GoA hub: Caddy (certificates, redirect)' >/dev/null
		ufw allow 443/tcp comment 'GoA hub: Caddy HTTPS' >/dev/null
		note "ufw: opened TCP 80 and 443."
	elif command -v firewall-cmd >/dev/null && firewall-cmd --state >/dev/null 2>&1; then
		firewall-cmd --quiet --permanent --add-service=http --add-service=https
		firewall-cmd --quiet --reload
		note "firewalld: opened http (80) and https (443)."
	else
		note "No active firewall found (ufw or firewalld), nothing to open."
	fi
fi

# ---------------------------------------------------------------------------------------------
# Automatic security updates
# ---------------------------------------------------------------------------------------------
say "Automatic security updates"
if [[ "$pm" == apt ]]; then
	apt-get install -y -q unattended-upgrades >/dev/null
	echo 'unattended-upgrades unattended-upgrades/enable_auto_updates boolean true' | debconf-set-selections
	dpkg-reconfigure -f noninteractive unattended-upgrades
	if [[ "$mode" == caddy ]]; then
		cat > /etc/apt/apt.conf.d/52goa-hub-caddy <<'EOF'
// Added by the Guards of Atlantis hub installer: install Caddy updates automatically too.
Unattended-Upgrade::Origins-Pattern { "origin=cloudsmith/caddy/stable"; };
EOF
	fi
	note "unattended-upgrades: on."
else
	dnf install -y dnf5-plugin-automatic >/dev/null 2>&1 || dnf install -y dnf-automatic >/dev/null 2>&1 || true
	if systemctl cat dnf5-automatic.timer >/dev/null 2>&1; then
		if [[ -f /etc/dnf/automatic.conf ]] && grep -q '^apply_updates' /etc/dnf/automatic.conf; then
			sed -i 's/^apply_updates.*/apply_updates = yes/' /etc/dnf/automatic.conf
		else
			printf '[commands]\napply_updates = yes\n' >> /etc/dnf/automatic.conf
		fi
		systemctl enable --now dnf5-automatic.timer >/dev/null
		note "dnf5-automatic: on."
	elif systemctl cat dnf-automatic-install.timer >/dev/null 2>&1; then
		systemctl enable --now dnf-automatic-install.timer >/dev/null
		note "dnf-automatic: on."
	else
		warn "Could not turn on automatic updates. See deploy/README.md, Security."
	fi
fi

# ---------------------------------------------------------------------------------------------
# What's next
# ---------------------------------------------------------------------------------------------
if [[ "$mode" == tailscale ]]; then
	cat <<EOF

=====================================================================================
 The hub is running and published at:

     $public_url

 Next steps (details in /opt/goa-hub/deploy/README.md):
  1. On a phone with Tailscale switched OFF, using mobile data, open:
       $public_url/api/v1/health
     It should say {"status":"ok"}. A brand-new name can take up to 10 minutes to appear.
  2. Android: Settings -> Group sync -> Hub: $public_url
     Then show the invite QR code to friends; iPhones open it with the Camera.
  No router change, no open port: Funnel connects out from this machine.

 Logs:     journalctl -u goa-hub -f      Funnel: tailscale funnel status
 Update:   sudo /opt/goa-hub/deploy/linux/update.sh goa-hub-x.y.z.zip
 New APK:  sudo /opt/goa-hub/deploy/linux/update.sh app-release.apk
=====================================================================================
EOF
else
	lan_ip="$(ip -4 route get 1.1.1.1 2>/dev/null | sed -n 's/.* src \([0-9.]*\).*/\1/p' | head -n 1)"
	public_ip="$(curl --silent --max-time 10 https://api.ipify.org || true)"
	lan_ip="${lan_ip:-<this machine address>}"
	public_ip="${public_ip:-<unknown>}"
	cat <<EOF

=====================================================================================
 The hub is installed and running behind Caddy.

 Next steps (details in /opt/goa-hub/deploy/README.md, "Alternative: without Tailscale"):
  1. Router: forward TCP ports 80 and 443 to this machine, $lan_ip.
     Give this machine a fixed address in the router (DHCP reservation) if you haven't.
  2. Check for carrier-grade NAT: your public address is $public_ip.
     It must match the WAN / Internet address on the router's status page.
  3. On a phone using mobile data (Wi-Fi off), open:
       $public_url/api/v1/health
     It should say {"status":"ok"}. The first visit may take a minute while Caddy gets the
     certificate (sudo journalctl -u caddy -f shows it).
  4. Android: Settings -> Group sync -> Hub: $public_url

 Logs:     journalctl -u goa-hub -f      Caddy: journalctl -u caddy -f
 Update:   sudo /opt/goa-hub/deploy/linux/update.sh goa-hub-x.y.z.zip
 New APK:  sudo /opt/goa-hub/deploy/linux/update.sh app-release.apk
=====================================================================================
EOF
fi
