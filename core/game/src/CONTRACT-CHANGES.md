
## 2026-10-24 (lead, after the foundation merge)
- `HeroView.items` now lists card ids for the viewer's own hero only; everyone sees `HeroView.itemStats`
  (one item icon per item, incl. spare items). Which card was tucked is hidden (designer, BGG 3590819).
- `GameFactory.engineVersion` (= `GameEngine.ENGINE_VERSION`): the app refuses saves of another version.
- `GameFactoryProvider`: `suspend fun get(): GameFactory`, because the catalog and maps load asynchronously.

## 2026-11-02 (online games, ADR-056)
- `GameEngine.restoreFrom(start, answers)`: a save from the start of a game plus answers kept one row at a time. Additive.
- `EngineSession.forkHalting(stop)`: a fork that stops before a matching step, so a forecast on it can't cross the reveal or the pity coin (`server/game/.../room/SimultaneousWindow`). Additive; no rule changed.
