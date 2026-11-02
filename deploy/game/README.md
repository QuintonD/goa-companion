# Runbook: the game server on oldboi

The server of online games (ADR-056, [contract](../../docs/ONLINE.md)) runs in Docker on `oldboi`, bound to
host loopback, and Tailscale Funnel publishes it next to the group sync relay.

| What | Where |
|---|---|
| Public URL | `https://oldboi.taila4d38d.ts.net:8443/game` (Funnel to `http://127.0.0.1:8788`) |
| Container | `goa-game` (compose project `goa-game`, images `goa-game:latest`, `goa-game:previous`) |
| Data | `/var/lib/goa-game` on the host (owner uid/gid 10001, mode 0700) |
| Checkout | `~/goa-game-deploy`, its own clone through the deploy key, always a detached commit (about 1.3 GB) |
| Backups | `/var/backups/goa-game`, 14 daily copies |

**Hands off:** ports 443 and 10000 are other apps'. `/` on 8443 is the relay and must stay as it is.
`~/goa-companion` may hold someone's edits or another branch, and nothing here reads or changes it.

The image holds no build step: `deploy.sh` builds on the host through `goa-gradle`, because a compile
inside Docker BuildKit would run outside the memory cap (ADR-038). `deploy.sh` refuses to start while
`plekje-apk-build` or another Gradle build is running.

## First time, in this order

**1. A session that survives a dropped connection.** Draining can take hours.

```sh
ssh quinton@oldboi
tmux new -s goa-game        # later: tmux attach -t goa-game
```

If `docker` needs sudo for your user, sudo may ask for the password again after a long drain, so stay
in tmux and look in on it.

**2. The checkout.** `<ref>` is a branch on origin, a tag or a commit that contains the real server.

```sh
git clone --filter=blob:none --no-checkout goa-github:QuintonD/goa-companion.git ~/goa-game-deploy
git -C ~/goa-game-deploy checkout --detach origin/<ref>
```

**3. The data directory.** The container runs as uid 10001 and can write only here.

```sh
sudo mkdir -p /var/lib/goa-game
sudo chown 10001:10001 /var/lib/goa-game
sudo chmod 0700 /var/lib/goa-game
```

**4. Deploy.**

```sh
~/goa-game-deploy/deploy/game/deploy.sh <ref>
curl -s http://127.0.0.1:8788/v1/health     # {"ok":true,...,"rulesBuild":"...",...}
docker ps --filter name=goa-game            # (healthy) after about 40 s
```

**5. Funnel.** Run this only after step 4 is healthy.

```sh
~/goa-game-deploy/deploy/game/funnel.sh
curl -s https://oldboi.taila4d38d.ts.net:8443/game/v1/health    # the game server: has "rulesBuild"
curl -s https://oldboi.taila4d38d.ts.net:8443/v1/health         # still the relay: no "rulesBuild"
```

The script prints the configuration first. It refuses, changing nothing, unless `/` on 8443 proxies to
`127.0.0.1:8787` with Funnel on and `/game` is free or already right. Then it adds `/game` alone and
checks that everything else is unchanged. Run again, it changes nothing.

**6. WebSocket smoke test.** This needs a server that has `/v1/play`.

```sh
~/goa-game-deploy/deploy/game/check-websocket.sh
```

Expected: `Refused(BAD_TOKEN)` with no Origin and with the web app's Origin, and a foreign Origin blocked.

**7. Backups.**

```sh
sudo apt install sqlite3
sudo install -m 0755 ~/goa-game-deploy/deploy/game/backup.sh /usr/local/sbin/goa-game-backup
echo '17 4 * * * root /usr/local/sbin/goa-game-backup >>/var/log/goa-game-backup.log 2>&1' \
    | sudo tee /etc/cron.d/goa-game-backup
sudo /usr/local/sbin/goa-game-backup     # try it: fails with "no SQLite file" until the server has one
```

A copy of the script runs as root rather than the checkout's, so a deploy can't change what root runs.
Reinstall it after a change to `backup.sh`.

**8. The public repo.** See [goa-leaderboards](#the-goa-leaderboards-repo) below.

## Update

```sh
~/goa-game-deploy/deploy/game/deploy.sh <ref>             # default ref: main
~/goa-game-deploy/deploy/game/deploy.sh --no-drain <ref>  # restart now, with games running
```

What it does, in order:

1. Fetches `<ref>` and checks out exactly that commit.
2. Pauses new rooms and waits for `rooms` in `/v1/health`, the running games, to reach 0, for up to
   `GOA_GAME_DRAIN_HOURS` (default 3). Rooms still waiting for players don't count: they can't start now and
   survive the restart. No Gradle build runs during the wait. It skips this if the server isn't up.
3. Runs `goa-gradle :server:game:test :server:game:installDist`. Red tests stop here, the old container
   keeps running and rooms reopen.
4. Computes the rules build, a hash of the tree ids of `core/game`, `core/domain`, `core/model` and
   `app/src/main/assets/catalog`.
5. Tags the running image `goa-game:previous`, if it is healthy.
6. Runs `docker compose up -d --build` and waits until `/v1/health` reports the new rules build.
7. Shows `ps` and reopens rooms.

At the time limit it goes on anyway: running games resume from their saved answers, and one whose past
a rules fix changed is voided, unrated, with a message to its players (docs/ONLINE.md).

A new version of `deploy.sh` itself takes effect on the run after the one that fetched it.

## Roll back

One command, with no build:

```sh
~/goa-game-deploy/deploy/game/rollback.sh
```

It puts `goa-game:previous` back as `goa-game:latest`, recreates the container, waits for health and
reopens rooms. The image keeps its own rules build. After a failed health check `deploy.sh` leaves new
rooms paused on the bad server and prints this command.

## Pause new rooms

```sh
docker exec goa-game touch /var/lib/goa-game/drain    # running games carry on
docker exec goa-game rm -f /var/lib/goa-game/drain    # open again
```

A normal deploy removes the file when it finishes; `--no-drain` leaves it and says so.

## Check and logs

```sh
curl -s http://127.0.0.1:8788/v1/health
docker logs --tail 100 -f goa-game
sudo tailscale serve status --json
```

## Restore a backup

```sh
docker compose -f ~/goa-game-deploy/deploy/game/compose.yaml down
sudo sh -c 'rm -f /var/lib/goa-game/*-wal /var/lib/goa-game/*-shm'
sudo cp /var/backups/goa-game/<YYYY-MM-DD>/* /var/lib/goa-game/
sudo chown -R 10001:10001 /var/lib/goa-game
docker compose -f ~/goa-game-deploy/deploy/game/compose.yaml up -d --no-build
```

Removing the `-wal` and `-shm` files matters: an old WAL beside a restored database can corrupt it.

## Remove everything

Take it off the internet first. **Keep `--set-path`:** `sudo tailscale funnel --https=8443 off` deletes
every handler on the port, the relay's too, and turns Funnel off for it.

```sh
sudo tailscale funnel --https=8443 --set-path /game off
sudo tailscale serve status --json     # "/" must still proxy to http://127.0.0.1:8787
curl -s https://oldboi.taila4d38d.ts.net:8443/v1/health    # the relay still answers
```

Then the server. The last two lines delete the match log, which every rating is derived from:

```sh
docker compose -f ~/goa-game-deploy/deploy/game/compose.yaml down
docker image rm goa-game:latest goa-game:previous
sudo rm -f /etc/cron.d/goa-game-backup /usr/local/sbin/goa-game-backup
rm -rf ~/goa-game-deploy
sudo rm -rf /var/lib/goa-game /var/backups/goa-game
```

## Limits

- **Funnel has bandwidth limits that can't be configured.** Tailscale's docs say only that: "Traffic
  sent over a Funnel is subject to non-configurable bandwidth limits", with no numbers. The relay and
  this server share them.
- **Funnel ports are 443, 8443 and 10000 only**, which is why this is a path on the relay's port.
- **Web CSP:** `connect-src` must allow `https://oldboi.taila4d38d.ts.net:8443` and
  `wss://oldboi.taila4d38d.ts.net:8443`.
- **A browser can't tell a blocked port from a server that is down.** Some networks block 8443 and the
  app sees the same failure either way, so its words must cover both. `curl` from that network helps.
- **The relay's runbook** gives `sudo tailscale funnel --https=8443 off` as its way to unpublish. Since
  `/game` joined the port, that command also takes the game server off.

## What Funnel does with the path

From the Tailscale source (`ipn/ipnlocal/serve.go`), which the docs don't cover: the mount path is
stripped before proxying, so `/game/v1/play` reaches the server as `/v1/play`, and the server answers on
both forms. `--set-path` adds one handler to the port and leaves the others alone
(`ipn/serve.go`, `SetWebHandler`). `/gamexyz` does not match the mount and still goes to the relay.

## The goa-leaderboards repo

The template is `leaderboards-repo/`, and nothing in this repository runs it. Create it once, from a
machine with `gh`:

```sh
gh repo create QuintonD/goa-leaderboards --public
cp -r deploy/game/leaderboards-repo /tmp/goa-leaderboards && cd /tmp/goa-leaderboards
git init -b main && git add . && git commit -m "The leaderboards site"
git remote add origin https://github.com/QuintonD/goa-leaderboards.git && git push -u origin main
gh api -X POST repos/QuintonD/goa-leaderboards/pages -f build_type=workflow
gh workflow run publish.yml -R QuintonD/goa-leaderboards
```

The first push starts a run whose deploy job fails, because Pages isn't on yet; the manual run after
enabling it is the real first deploy. Check:

```sh
curl -s https://quintond.github.io/goa-leaderboards/network.json
curl -s https://quintond.github.io/goa-leaderboards/leaderboard.json
```

Pages' source must be **GitHub Actions**, not a branch. GitHub's docs: "Commits pushed by a GitHub Actions
workflow that uses the `GITHUB_TOKEN` do not trigger a GitHub Pages build." So the workflow deploys the
site itself, and only `network.json` and `leaderboard.json` go on it. A commit to `network.json` deploys
by the `push` trigger.

No secret is involved, and oldboi holds no GitHub credential. Until the server has `/v1/leaderboard`, each
run ends with a notice and changes nothing.

## Tests

```sh
deploy/game/test/run-tests.sh                                      # the scripts, against fakes
python3 deploy/game/leaderboards-repo/scripts/test_validate_leaderboard.py
```

The first needs bash, git and python3, plus sqlite3 for the backup tests. Run as root it also checks the
real uid change. Nothing in it touches a real service.
