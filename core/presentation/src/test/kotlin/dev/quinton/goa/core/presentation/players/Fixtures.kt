package dev.quinton.goa.core.presentation.players

import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.draft.DraftMethodKind
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.draft.DraftStepRecord
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.core.model.player.Player
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/*
 * The same synthetic log as feature/players' test Fixtures (the screenshots use that one), for the
 * builders' own tests here. Fake names, never real game data.
 */

/** Synthetic fixture data — fake names, never real game data. Heroes come from TestCatalog. */
object Fixtures {
    val ann = Player("p-ann", "Ann", 0xFF3FC1B0, avatarId = 0, createdAt = 1)
    val bob = Player("p-bob", "Bob", 0xFFEE7F6F, avatarId = 1, createdAt = 2)
    val cid = Player("p-cid", "Cid", 0xFF62A8F2, avatarId = 2, createdAt = 3)
    val dee = Player("p-dee", "Dee", 0xFFB892E6, avatarId = 3, createdAt = 4)
    val eve = Player("p-eve", "Eve", 0xFFEE9A52, avatarId = 4, createdAt = 5)
    val fay = Player("p-fay", "Fay", 0xFFC3CCD6, avatarId = 5, createdAt = 6, archived = true)
    val players = listOf(ann, bob, cid, dee, eve, fay)

    /** 18:30 local time on 2026-10-[day]. */
    fun at(day: Int, hour: Int = 18, minute: Int = 30): Long =
        LocalDate.of(
            2026,
            10,
            day,
        ).atTime(LocalTime.of(hour, minute)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun hero(i: Int) = "test-hero-$i"

    fun match(
        id: String,
        day: Int,
        winner: Team?,
        vararg seats: Participant,
        status: MatchStatus = MatchStatus.COMPLETED,
        hour: Int = 18,
        mode: GameMode = GameMode.QUICK,
        condition: WinCondition? = WinCondition.THRONE,
        duration: Int? = 55,
        notes: String = "",
    ) = Match(
        id = id,
        createdAt = at(day, hour),
        playedAt = at(day, hour),
        status = status,
        mode = mode,
        winner = winner,
        winCondition = if (status == MatchStatus.COMPLETED) condition else null,
        durationMin = duration,
        notes = notes,
        participants = seats.toList(),
    )

    fun red(player: Player, hero: Int?, handicap: Boolean = false) = Participant(
        player.id,
        hero?.let(::hero),
        Team.RED,
        handicap,
    )

    fun blue(player: Player, hero: Int?, handicap: Boolean = false) = Participant(
        player.id,
        hero?.let(::hero),
        Team.BLUE,
        handicap,
    )

    /** A small but varied log: double pilots, a handicap game, a pending game, unknown hero. */
    val matches: List<Match> = buildList {
        add(match("m01", 3, Team.RED, red(ann, 1), red(bob, 2), blue(cid, 3), blue(dee, 4)))
        add(
            match(
                "m02",
                4,
                Team.BLUE,
                red(ann, 1),
                red(cid, 5),
                blue(bob, 2),
                blue(dee, 6),
                condition = WinCondition.LIFE_COUNTERS,
            ),
        )
        add(match("m03", 5, Team.RED, red(ann, 1), red(ann, 7, handicap = true), blue(bob, 3), blue(cid, 4)))
        add(match("m04", 6, Team.RED, red(ann, 1), red(dee, 2), blue(eve, 3), blue(bob, 8)))
        add(
            match(
                "m05", 8, Team.BLUE,
                red(
                    cid,
                    1,
                ),
                red(dee, 9), red(eve, 10), blue(ann, 2), blue(bob, 3), mode = GameMode.LONG,
            ),
        )
        add(
            match(
                "m06",
                10,
                Team.RED,
                red(ann, 1),
                red(bob, null),
                blue(cid, 2),
                blue(dee, 3),
                condition = WinCondition.LAST_PUSH,
            ),
        )
        add(
            match(
                "m07", 12, Team.RED,
                red(
                    ann,
                    1,
                ),
                red(eve, 4), blue(bob, 2), blue(cid, 3), duration = 71, notes = "Comeback on the last wave",
            ),
        )
        add(match("m08", 14, Team.BLUE, red(dee, 1), red(eve, 5), blue(ann, 2), blue(ann, 6), blue(bob, 3)))
        add(match("m09", 15, Team.RED, red(ann, 2), red(bob, 1), blue(cid, 3), blue(dee, 4), hour = 20))
        add(
            match(
                "m10", 15, null,
                red(
                    ann,
                    4,
                ),
                red(cid, 5), blue(bob, 6), blue(eve, 7), status = MatchStatus.PENDING, hour = 21, duration = null,
            ),
        )
    }

    val drafts = listOf(
        DraftRecord(
            id = "d1",
            createdAt = at(15),
            methodId = "house",
            methodKind = DraftMethodKind.CUSTOM,
            pool = (1..15).map(::hero),
            teamA = Team.RED,
            steps = listOf(
                DraftStepRecord(Team.RED, DraftAction.BAN, hero(11)),
                DraftStepRecord(Team.BLUE, DraftAction.BAN, hero(12)),
            ),
            matchId = "m10",
        ),
    )
}
