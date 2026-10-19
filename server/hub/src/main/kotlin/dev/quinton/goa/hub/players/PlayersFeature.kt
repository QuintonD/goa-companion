package dev.quinton.goa.hub.players

import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.feature.HubFeature
import io.ktor.server.routing.Route

/**
 * The Players tab on the web (ADR-030 §8, ADR-031 §4): leaderboard, player pages, "Same people?",
 * the match log and editor, "Who won?", the Meta tab and the stats page. Every number comes from
 * `core:presentation` over one ratings snapshot ([PlayersData]); every write goes through the
 * `GroupStore` helpers, so a web edit syncs like a phone edit.
 */
object PlayersFeature : HubFeature {
    override fun Route.routes(ctx: HubContext) {
        leaderboardRoutes(ctx)
        samePeopleRoutes(ctx)
        metaRoutes(ctx)
        matchLogRoutes(ctx)
        matchRoutes(ctx)
        matchEditorRoutes(ctx)
        playerRoutes(ctx)
    }
}
