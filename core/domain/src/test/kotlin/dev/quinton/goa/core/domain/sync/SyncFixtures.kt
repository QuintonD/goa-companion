package dev.quinton.goa.core.domain.sync

import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.draft.DraftMethodKind
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.draft.DraftStepRecord
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.sync.Deletion
import dev.quinton.goa.core.model.sync.DeletionKind
import dev.quinton.goa.core.model.sync.PlayerMerge
import dev.quinton.goa.core.model.trivia.TriviaRound
import dev.quinton.goa.core.model.trivia.TriviaRoundKind
import kotlin.random.Random

internal fun player(id: String, name: String = id, updatedAt: Long = 0, archived: Boolean = false) =
    Player(id = id, name = name, color = 1, avatarId = 0, createdAt = 0, archived = archived, updatedAt = updatedAt)

internal fun match(
    id: String,
    red: List<String>,
    blue: List<String>,
    updatedAt: Long = 0,
    winner: Team? = Team.RED,
    playedAt: Long = 0,
    heroes: List<String?>? = null,
    notes: String = "",
): Match {
    val seats = red.map { it to Team.RED } + blue.map { it to Team.BLUE }
    return Match(
        id = id,
        createdAt = 0,
        playedAt = playedAt,
        status = if (winner == null) MatchStatus.PENDING else MatchStatus.COMPLETED,
        mode = GameMode.QUICK,
        winner = winner,
        notes = notes,
        participants = seats.mapIndexed { i, (p, t) ->
            Participant(
                p,
                if (heroes !=
                    null
                ) {
                    heroes.getOrNull(i)
                } else {
                    "h$i"
                },
                t,
            )
        },
        updatedAt = updatedAt,
    )
}

internal fun round(
    id: String,
    playerId: String,
    updatedAt: Long = 0,
    kind: TriviaRoundKind = TriviaRoundKind.CHALLENGE,
    correct: Int = 15,
    questions: Int = 20,
    playedAt: Long = 0,
) = TriviaRound(
    id = id,
    playerId = playerId,
    kind = kind,
    playedAt = playedAt,
    length = 20,
    questions = questions,
    correct = correct,
    bestStreak = minOf(correct, 5),
    answerMillis = 90_000,
    heroCount = 32,
    catalogVersion = 7,
    updatedAt = updatedAt,
)

/**
 * Random phone data drawn from small shared id pools, so the same ids (and the same `updatedAt`,
 * i.e. exact ties) turn up on several phones: the cases the merge must get right.
 */
internal object RandomSync {
    private val playerIds = (1..8).map { "p$it" }
    private val matchIds = (1..12).map { "m$it" }
    private val draftIds = (1..4).map { "d$it" }
    private val roundIds = (1..10).map { "r$it" }

    fun Random.doc(): SyncDocument = SyncDocument(
        players = playerIds.filter { nextInt(3) > 0 }.map { player(it, name = "N${nextInt(3)}", updatedAt = stamp()) },
        matches = matchIds.filter { nextInt(3) > 0 }.map { randomMatch(it) },
        drafts = draftIds.filter { nextBoolean() }.map { randomDraft(it) },
        triviaRounds = roundIds.filter { nextInt(3) > 0 }.map { randomRound(it) },
        deletions = (matchIds.filter { nextInt(5) == 0 }.map { Deletion(DeletionKind.MATCH, it, stamp()) }) +
            draftIds.filter { nextInt(6) == 0 }.map { Deletion(DeletionKind.DRAFT, it, stamp()) } +
            roundIds.filter { nextInt(5) == 0 }.map { Deletion(DeletionKind.TRIVIA_ROUND, it, stamp()) },
        playerMerges = playerIds.filter { nextInt(6) == 0 }.map { PlayerMerge(it, playerIds.random(this), stamp()) },
        eloConfig = if (nextBoolean()) StampedEloConfig.UNSET else StampedEloConfig(config(), stamp()),
    )

    private fun Random.stamp(): Long = nextLong(0, 6)

    private fun Random.config() = EloConfig(heroInfluence = nextInt(0, 3) / 4.0, kProvisional = 30.0 + nextInt(3) * 5)

    private fun Random.randomMatch(id: String): Match {
        val shuffled = playerIds.shuffled(this)
        val size = nextInt(1, 4)
        return match(
            id = id,
            red = shuffled.take(size),
            blue = shuffled.drop(size).take(size),
            updatedAt = stamp(),
            winner = if (nextInt(5) == 0) {
                null
            } else if (nextBoolean()) {
                Team.RED
            } else {
                Team.BLUE
            },
            playedAt = nextLong(0, 20),
            notes = "v${nextInt(3)}",
            // Unknown-hero seats, so ties between copies with and without a hero occur too.
            heroes = List(size * 2) { if (nextInt(4) == 0) null else "h$it" },
        )
    }

    /** Rounds are immutable in the app, but two phones can still hold different copies of one id. */
    private fun Random.randomRound(id: String): TriviaRound {
        val questions = nextInt(1, 21)
        val correct = nextInt(0, questions + 1)
        return TriviaRound(
            id = id,
            playerId = playerIds.random(this),
            kind = if (nextBoolean()) TriviaRoundKind.CHALLENGE else TriviaRoundKind.PRACTICE,
            playedAt = nextLong(0, 20),
            length = 20,
            questions = questions,
            correct = correct,
            bestStreak = nextInt(0, correct + 1),
            answerMillis = nextLong(0, 3) * 1_000,
            heroCount = 32,
            catalogVersion = 7,
            updatedAt = stamp(),
        )
    }

    private fun Random.randomDraft(id: String) = DraftRecord(
        id = id,
        createdAt = 0,
        methodId = "house",
        methodKind = DraftMethodKind.CUSTOM,
        pool = listOf("h1", "h2", "h3"),
        teamA = if (nextBoolean()) Team.RED else Team.BLUE,
        steps = listOf(DraftStepRecord(Team.RED, DraftAction.BAN, "h${nextInt(1, 4)}")),
        updatedAt = stamp(),
    )
}
