
## 2026-10-24 (lead, after the foundation merge)
- `HeroView.items` now lists card ids for the viewer's own hero only; everyone sees `HeroView.itemStats`
  (one item icon per item, incl. spare items). Which card was tucked is hidden (designer, BGG 3590819).
- `GameFactory.engineVersion` (= `GameEngine.ENGINE_VERSION`): the app refuses saves of another version.
- `GameFactoryProvider`: `suspend fun get(): GameFactory`, because the catalog and maps load asynchronously.
