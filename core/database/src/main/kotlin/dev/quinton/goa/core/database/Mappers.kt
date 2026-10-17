package dev.quinton.goa.core.database

import dev.quinton.goa.core.database.entity.DraftRecordEntity
import dev.quinton.goa.core.database.entity.DraftStepEntity
import dev.quinton.goa.core.database.entity.DraftWithSteps
import dev.quinton.goa.core.database.entity.MatchEntity
import dev.quinton.goa.core.database.entity.MatchWithParticipants
import dev.quinton.goa.core.database.entity.ParticipantEntity
import dev.quinton.goa.core.database.entity.PlayerEntity
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/*
 * Entity <-> model mapping. Enum columns hold the lower-case serial names used in JSON, which
 * equal `name.lowercase()` for every enum stored here.
 */

private val json = Json { ignoreUnknownKeys = true }
private val stringList = ListSerializer(String.serializer())

internal fun Enum<*>.dbName(): String = name.lowercase()

private inline fun <reified E : Enum<E>> parse(value: String): E = enumValueOf(value.uppercase())

fun PlayerEntity.toModel() = Player(
    id = id,
    name = name,
    color = color,
    avatarId = avatarId,
    createdAt = createdAt,
    archived = archived,
)

fun Player.toEntity() = PlayerEntity(
    id = id,
    name = name,
    color = color,
    avatarId = avatarId,
    createdAt = createdAt,
    archived = archived,
)

fun MatchWithParticipants.toModel() = Match(
    id = match.id,
    createdAt = match.createdAt,
    playedAt = match.playedAt,
    status = parse<MatchStatus>(match.status),
    mode = parse<GameMode>(match.mode),
    map = match.map,
    winner = match.winner?.let { parse<Team>(it) },
    winCondition = match.winCondition?.let { parse<WinCondition>(it) },
    durationMin = match.durationMin,
    notes = match.notes,
    draftId = match.draftId,
    participants = participants.sortedBy { it.seatIndex }.map {
        Participant(playerId = it.playerId, heroId = it.heroId, team = parse(it.team), handicap = it.handicap)
    },
)

fun Match.toEntity() = MatchEntity(
    id = id,
    createdAt = createdAt,
    playedAt = playedAt,
    status = status.dbName(),
    mode = mode.dbName(),
    map = map,
    winner = winner?.dbName(),
    winCondition = winCondition?.dbName(),
    durationMin = durationMin,
    notes = notes,
    draftId = draftId,
)

/** Seats keep their list order via `seat_index`. */
fun Match.participantEntities() = participants.mapIndexed { index, p ->
    ParticipantEntity(
        matchId = id,
        seatIndex = index,
        playerId = p.playerId,
        heroId = p.heroId,
        team = p.team.dbName(),
        handicap = p.handicap,
    )
}

fun DraftWithSteps.toModel() = DraftRecord(
    id = draft.id,
    createdAt = draft.createdAt,
    methodId = draft.methodId,
    methodKind = parse<DraftMethodKind>(draft.methodKind),
    pool = json.decodeFromString(stringList, draft.pool),
    teamA = parse(draft.teamA),
    steps = steps.sortedBy { it.stepIndex }.map {
        DraftStepRecord(team = parse(it.team), action = parse<DraftAction>(it.action), heroId = it.heroId)
    },
    matchId = draft.matchId,
)

fun DraftRecord.toEntity() = DraftRecordEntity(
    id = id,
    createdAt = createdAt,
    methodId = methodId,
    methodKind = methodKind.dbName(),
    pool = json.encodeToString(stringList, pool),
    teamA = teamA.dbName(),
    matchId = matchId,
)

fun DraftRecord.stepEntities() = steps.mapIndexed { index, step ->
    DraftStepEntity(
        draftId = id,
        stepIndex = index,
        team = step.team.dbName(),
        action = step.action.dbName(),
        heroId = step.heroId,
    )
}
