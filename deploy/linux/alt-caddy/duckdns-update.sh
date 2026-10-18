#!/usr/bin/env bash
# Tells DuckDNS this line's current public IPv4 address, so <name>.duckdns.org keeps pointing
# home. Run every 5 minutes by duckdns-update.timer. Two settings:
#   DUCKDNS_DOMAIN=ourgroup        (the part before .duckdns.org)
#   DUCKDNS_TOKEN=xxxxxxxx-xxxx-…  (from the top of https://www.duckdns.org after signing in)
# kept in /etc/goa-hub/duckdns.env (root only, 0600; install.sh writes it).
set -euo pipefail

env_file="${DUCKDNS_ENV_FILE:-/etc/goa-hub/duckdns.env}"
[[ -r "$env_file" ]] || { echo "duckdns: cannot read $env_file" >&2; exit 1; }

# Read KEY=value lines without running the file as a script.
domain="" token=""
while IFS='=' read -r key value; do
	value="${value%$'\r'}"
	value="${value#\"}"; value="${value%\"}"
	case "$key" in
		DUCKDNS_DOMAIN) domain="$value" ;;
		DUCKDNS_TOKEN) token="$value" ;;
	esac
done < "$env_file"

[[ "$domain" =~ ^[a-z0-9-]+$ ]] || { echo "duckdns: DUCKDNS_DOMAIN must be the name only, e.g. ourgroup" >&2; exit 1; }
[[ "$token" =~ ^[A-Za-z0-9-]+$ ]] || { echo "duckdns: DUCKDNS_TOKEN is missing or malformed" >&2; exit 1; }

# The URL (with the token) goes to curl on stdin, so it never shows in `ps` or in the logs.
# An empty ip= lets DuckDNS use the address this request comes from (IPv4).
answer="$(printf 'url = "https://www.duckdns.org/update?domains=%s&token=%s&ip=&verbose=true"\n' "$domain" "$token" \
	| curl --silent --show-error --fail --max-time 30 --retry 2 --config -)" || {
	echo "duckdns: could not reach www.duckdns.org" >&2
	exit 1
}

status="$(printf '%s\n' "$answer" | head -n 1)"
if [[ "$status" != "OK" ]]; then
	echo "duckdns: update refused ($status): check the name and the token in $env_file" >&2
	exit 1
fi
# verbose=true answers OK / IPv4 / IPv6 / UPDATED|NOCHANGE.
ipv4="$(printf '%s\n' "$answer" | sed -n 2p)"
change="$(printf '%s\n' "$answer" | sed -n 4p)"
echo "duckdns: $domain.duckdns.org -> ${ipv4:-?} (${change:-OK})"
