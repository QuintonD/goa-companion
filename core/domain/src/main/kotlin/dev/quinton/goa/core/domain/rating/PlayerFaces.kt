package dev.quinton.goa.core.domain.rating

import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.player.PlayerFace

/**
 * Who each player is shown as: their most-played hero ([RatingsResult.mostPlayedHeroes]) with its portrait from the
 * catalog. Rebuilt from the log like every rating, so nothing is stored or synced. Heroes the catalog doesn't know
 * are left out, so those players keep their crest.
 */
object PlayerFaces {
    fun from(ratings: RatingsResult, catalog: Catalog): Map<String, PlayerFace> =
        ratings.mostPlayedHeroes().mapNotNull { (playerId, heroId) ->
            catalog.hero(heroId)?.let { playerId to PlayerFace(heroId, it.portrait) }
        }.toMap()
}
