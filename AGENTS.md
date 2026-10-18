# Guards of Atlantis Companion — agent instructions

Native Android companion app for the **Guards of Atlantis II** board game (Wolff Designa).
Private, local-only, never sold or published (see ADR-006). This file is the canonical,
tool-agnostic entry point; `CLAUDE.md` imports it.

**Status:** feature-complete v1 plus a full validation round (rules audit, card-scan check,
hands-on game nights, rules reference) — built and tested on JVM + emulator (2026-10-17);
device verification on the S25 Ultra pending. See [docs/ROADMAP.md](docs/ROADMAP.md).

## The product in one breath

Seven features, pass-and-play on one phone at the table, on Android and — through the group's
self-hosted hub — in any browser, so iPhone players have the whole app too (ADR-030):

1. **Hero catalog** — browse/search/filter heroes, their cards, tiers and abilities.
2. **Phase timer** — configurable countdowns per game phase, built for table use.
3. **Players & meta** — friends list, match log, Elo per player and per hero, meta stats.
4. **Draft** — configurable pick/ban drafting that feeds straight into a match record.
5. **Rules reference** — level & defeat ladder and verbatim quick-reference topics, checked
   against the pinned rulebook (`--rules-only`, ADR-019).
6. **Group sync** — each friend's phone keeps the same match log, synced automatically with the
   group's hub over HTTPS (ADR-030) and phone to phone at the table over Nearby (ADR-025); joined
   by a QR invite that can name the player it is for (ADR-031).
7. **Card trivia** — a quiz on the numbers printed on every hero's cards (Initiative, Attack,
   Defense, Movement, Range, Radius), generated from the catalog, with per-number mastery (ADR-028).

Details and acceptance criteria: [docs/PRODUCT.md](docs/PRODUCT.md).

## Read before you work

| Doc | Read when |
|-----|-----------|
| [docs/PRODUCT.md](docs/PRODUCT.md) | Any feature work — scope, non-goals, acceptance criteria |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Writing code — stack, modules, layering, testing |
| [docs/DOMAIN.md](docs/DOMAIN.md) | Game rules, data sources, roster, data model, Elo and draft |
| [docs/DESIGN.md](docs/DESIGN.md) | Any UI or asset work — design system and asset policy |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Picking the next task; update status when done |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Before changing anything architectural; append new ADRs |

## Non-negotiable rules

- **Never invent game data.** All hero, card and rules data comes from the pinned sources in
  [docs/DOMAIN.md](docs/DOMAIN.md#sources) through the import pipeline (`import-catalog`
  skill); corrections go through `fix-hero`. Memory is not a source. Unresolved → `null`,
  `"verified": false`, and the UI marks it.
- **Official and community game assets are allowed** (private, non-commercial use): card
  images, artwork and verbatim card text may be bundled, each recorded in
  `official/SOURCES.md`. Our own UI and brand assets stay original
  (see [docs/DESIGN.md](docs/DESIGN.md#asset-policy)).
- **2nd printing is the data baseline.** Every hero/card records its printing; a 3rd printing
  (2027) will need a refresh, not a rewrite.
- **Keep it private.** Never push to a public remote and never publish the APK publicly. The
  owner may share the signed APK with their play group via a private Drive link (ADR-006
  addendum) or the hub's signed-in `/app` page (ADR-030); never via public releases or the
  owner's company accounts. Official assets sit in `assets/official/` so they can be removed
  easily if that ever changes. The hub serves nothing but `/join` and health without a
  signed-in device of the group, and never gets indexed.
- **Match history is the source of truth.** Ratings and meta stats are derived, recomputable
  from the ordered match log. Never store a rating that cannot be rebuilt.
- **Domain logic is pure Kotlin** (`:core:domain`) with unit tests. Timer, Elo and draft
  engines must not depend on Android classes.
- **The group's own hub is the only server.** The app talks to nothing but the hub address of
  its group (ADR-030): no third-party services, analytics, ads or cloud. Catalog data and images
  are still fetched only at dev time by `tools/catalog-import` and bundled, in the app and in the
  hub. Everything works without the hub; it only adds sync and the web app.
- **One implementation of every rule.** The hub runs the same `:core:domain` and
  `:core:presentation` code as the phone; never re-implement a rule, statistic or engine in the
  web layer (HTML/JS only presents what the hub computed).
- **Synced data is last-writer-wins by `updatedAt`.** Every write to players, matches and drafts
  goes through the repositories (they stamp it); deletes leave tombstones; never rewrite the log
  for merges. A new field on a synced type bumps `SyncDocument.VERSION` (ADR-025).
- Keep docs true: if code diverges from a doc, fix one of them in the same change. Record
  non-trivial choices as an ADR in [docs/DECISIONS.md](docs/DECISIONS.md).

## Commands (once M0 lands)

Windows (PowerShell) uses `.\gradlew.bat`; POSIX uses `./gradlew`. C: is nearly full, so set
`GRADLE_USER_HOME=D:\.gradle-home` and `TMP`/`TEMP=D:\.tmp-build` first (ADR-010).

```
.\gradlew.bat :tools:catalog-import:run  # rebuild catalog JSON + images from pinned sources
.\gradlew.bat :tools:catalog-import:run --args="--rules-only"  # rules-reference.json vs the pinned rulebook
.\gradlew.bat assembleDebug            # build
.\gradlew.bat testDebugUnitTest        # JVM unit tests (domain, data, VM)
.\gradlew.bat verifyRoborazziDebug     # screenshot regression
.\gradlew.bat lintDebug detekt ktlintCheck
.\gradlew.bat connectedDebugAndroidTest  # end-to-end UI tests (orchestrator, clean data per test)
.\gradlew.bat :app:assembleRelease        # signed release APK (needs keystore.properties, see README)
.\gradlew.bat :server:hub:test            # hub: sync API, sign-in, web pages (Ktor test host)
.\gradlew.bat :server:hub:run --args="serve --data D:\.goa-hub-dev --port 8080"  # local hub
.\gradlew.bat :server:hub:installDist     # deployable hub (server/hub/build/install/goa-hub, see deploy/)
```

A change is done when build, unit tests, lint and screenshot checks pass. Run the
`ship-check` skill before declaring a milestone complete.

## Working agreements

- Physical test device: Samsung S25 Ultra (ARM64). Emulator-only proof is not device proof.
- Distinguish "builds" from "verified on device" in every status report.
- Local git only. Small, reviewable commits on a branch; one feature module per branch where
  practical.
- Project path contains spaces (`guards of Atlantis`) — quote paths in scripts.
