# Runbook — the sync relay on oldboi

The group sync server (ADR-036, [protocol](../../docs/SYNC-SERVER.md), [server](../../server/relay/README.md))
runs in Docker on `oldboi` (Ubuntu, x86_64), bound to host loopback, and is published by Tailscale.

| What | Where |
|---|---|
| Public URL | `https://oldboi.taila4d38d.ts.net:8443` (Tailscale Funnel → `http://127.0.0.1:8787`) |
| Container | `goa-relay` (compose project `goa-relay`, image `goa-relay:latest`) |
| Data | `/var/lib/goa-relay` on the host (owner uid/gid 10001, mode 0700) |
| Repo checkout | `~/goa-companion` (read-only deploy key, remote `goa-github:QuintonD/goa-companion.git`) |

Port 443 on the same name is a different, tailnet-only service (a node app on `127.0.0.1:3773`).
Leave it alone.

## Tailscale

**Funnel enabled 2026-10-25** for port 8443:

```sh
sudo tailscale funnel --bg --https=8443 http://127.0.0.1:8787   # already done; don't re-run
sudo tailscale funnel status    # "https://oldboi.taila4d38d.ts.net:8443 (Funnel on)"
```

Before Funnel, the same port was tailnet-only with
`sudo tailscale serve --bg --https=8443 http://127.0.0.1:8787`. To take the relay off the public
internet again without touching 443: `sudo tailscale funnel --https=8443 off` (the tailnet-only
serve can then be put back with the command above). Tailscale adds `X-Forwarded-For`, so the
relay's per-client rate limit sees each phone separately.

## Deploy and update

```sh
ssh quinton@oldboi
~/goa-companion/deploy/relay/deploy.sh             # git pull --ff-only, build, restart, wait for health
~/goa-companion/deploy/relay/deploy.sh --no-pull   # rebuild what is checked out
```

The script creates `/var/lib/goa-relay` (uid 10001), enables the Docker service at boot, runs
`docker compose up -d --build` (the image build runs the relay's unit tests; a failing test stops the
deploy and the old container keeps running) and polls `http://127.0.0.1:8787/v1/health`. To deploy
another branch: `cd ~/goa-companion && git fetch && git checkout <branch>`, then run the script.
The script runs from a function, so a pull that changes it takes effect on the next run.

The container: `restart: unless-stopped` (comes back after a reboot, since `docker.service` is
enabled), read-only root filesystem with only the data directory and a small `/tmp` tmpfs
writable, `no-new-privileges`, all capabilities dropped, 512 MiB memory cap, healthcheck on
`/v1/health` every 30 s, logs rotated at 3 × 10 MB.

## Check

```sh
curl -s http://127.0.0.1:8787/v1/health                      # on oldboi
curl -s https://oldboi.taila4d38d.ts.net:8443/v1/health       # anywhere
docker ps --filter name=goa-relay                             # STATUS ... (healthy)
```

## Logs

```sh
docker logs --tail 100 -f goa-relay
```

One line per request (method, path template, status, duration); never bodies, tokens or group ids.
Many `401`s from one phone mean its group key differs from the one that registered the group
(see *Wipe a group* in the server README). `503 relay_full` lines mean `GOA_RELAY_MAX_GROUPS` was hit.

## Backups

The data directory is ciphertext plus token hashes; back it up as plain files, no need to stop the
relay:

```sh
sudo tar -C /var/lib -czf ~/goa-relay-$(date +%F).tgz goa-relay
```

Restore: `docker compose -f ~/goa-companion/deploy/relay/compose.yaml down`, extract into
`/var/lib`, `sudo chown -R 10001:10001 /var/lib/goa-relay`, then run `deploy.sh --no-pull`.
Losing the directory is not a disaster: phones keep the full log and the next sync re-uploads it.

## Stop / remove

```sh
docker compose -f ~/goa-companion/deploy/relay/compose.yaml down     # stop (data stays)
sudo tailscale funnel --https=8443 off                               # unpublish
```
