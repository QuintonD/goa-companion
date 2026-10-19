package dev.quinton.goa.feature.draft.ui

import dev.quinton.goa.core.domain.draft.DraftCommand
import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftMethod
import dev.quinton.goa.core.domain.draft.DraftSession
import dev.quinton.goa.core.domain.draft.DraftSetup
import dev.quinton.goa.core.domain.draft.MethodOrigin
import dev.quinton.goa.core.domain.draft.PoolRule
import dev.quinton.goa.core.domain.draft.SequenceRule
import dev.quinton.goa.core.domain.draft.TeamSlot
import dev.quinton.goa.core.domain.draft.ban
import dev.quinton.goa.core.domain.draft.pick
import dev.quinton.goa.core.domain.teams.OddSeatMode
import dev.quinton.goa.core.domain.teams.SeatLayouts
import dev.quinton.goa.core.model.draft.DraftMethodKind
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.presentation.draft.HeroInfo

/**
 * Synthetic data for @Preview only ("Hero N", invented players). Never real game data; the
 * shipped methods come from draft-methods.json.
 */
internal object PreviewData {
    val players: List<Player> = listOf(
        "Ada" to 0xFF3FC1B0,
        "Bram" to 0xFFD9B44A,
        "Cleo" to 0xFFB892E6,
        "Dov" to 0xFFEE85B8,
        "Esme" to 0xFFEE9A52,
        "Finn" to 0xFF6CC27A,
    ).mapIndexed { i, (name, color) -> Player("p$i", name, color, i, 0L) }

    val ratings: Map<String, Rating> = players.withIndex().associate { (i, p) ->
        p.id to Rating(1150.0 + i * 23, games = 4 + i * 3, wins = 2 + i, provisional = i < 2)
    }

    val heroes: Map<String, HeroInfo> = (1..18).associate { i ->
        "hero-$i" to HeroInfo("hero-$i", "Hero $i", "the Placeholder", complexity = i % 4 + 1)
    }

    val pattern = DraftMethod(
        id = "preview",
        name = "Preview draft",
        kind = DraftMethodKind.CUSTOM,
        origin = MethodOrigin.HOUSE,
        summary = "Pool of 15. A bans and picks, B bans and picks 2, then pairs.",
        pool = PoolRule.Random(15),
        sequence = SequenceRule.Pattern(
            opening = listOf(ban(TeamSlot.A), pick(TeamSlot.A), ban(TeamSlot.B), pick(TeamSlot.B), pick(TeamSlot.B)),
            repeat = listOf(pick(TeamSlot.A), pick(TeamSlot.A), pick(TeamSlot.B), pick(TeamSlot.B)),
        ),
        notes = listOf("[APP] Synthetic preview method."),
    )

    fun session(choices: Int, seconds: Int? = 30): DraftSession {
        val ids = players.take(6).map { it.id }
        val seats = SeatLayouts.build(ids.take(3), ids.drop(3), OddSeatMode.HANDICAP).getOrThrow()
        var s = DraftEngine.start(
            DraftSetup("d", 0L, pattern, seats, Team.RED, heroes.keys.toList(), seed = 4L, stepSeconds = seconds),
        ).getOrThrow()
        repeat(choices) { s = DraftEngine.apply(s, DraftCommand(DraftEngine.available(s).first())).getOrThrow() }
        return s
    }
}
