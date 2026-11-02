# goa-leaderboards

The public leaderboards and game server list of the Guards of Atlantis companion app's online games.
The app reads two files from `https://quintond.github.io/goa-leaderboards/`:

| File | What it is |
|---|---|
| `network.json` | Where live games are hosted, and the lowest protocol allowed |
| `leaderboard.json` | Ratings of players and heroes |

**All the data here is derived.** The leaderboard is worked out from the game server's log of rated
matches, and that log is the source of truth. Nothing in this repository is ever edited to change a
result, and it can be replaced at any time.

## How it updates

`.github/workflows/publish.yml` runs every 15 minutes. It fetches the server's snapshot, checks it with
`scripts/validate_leaderboard.py`, rewrites `leaderboard.json` from the checked values, and commits only
when the board says something new: a new result, a rename, or a deleted name. If the server is down the run ends quietly and the last copy stays
up. About once a month it commits to `heartbeat`, because GitHub turns off the schedule of a repository
with no activity for 60 days.

Only `network.json` and `leaderboard.json` go on the site. No HTML may ever be published from this
repository: Pages sites of one account share a browser origin with the app's stored data.

## Tests

```sh
python3 scripts/test_validate_leaderboard.py
```

The app and the server are in [goa-companion](https://github.com/QuintonD/goa-companion), which is
private; this template lives there in `deploy/game/leaderboards-repo`.
