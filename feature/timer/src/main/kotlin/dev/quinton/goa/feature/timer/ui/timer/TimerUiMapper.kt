package dev.quinton.goa.feature.timer.ui.timer

import dev.quinton.goa.core.domain.timer.MatchDuration
import dev.quinton.goa.core.domain.timer.PhaseScope
import dev.quinton.goa.core.domain.timer.RoundSection
import dev.quinton.goa.core.domain.timer.TimerReadout
import dev.quinton.goa.core.domain.timer.TimerSnapshot
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.core.domain.timer.TimerStep
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.settings.TimerLayout
import dev.quinton.goa.feature.timer.TimerText

/** Seat names for the linked match (null when no match or no seats). */
data class MatchSeats(val match: Match, val seats: List<SeatInfo>)

data class SeatInfo(
    val heroName: String,
    val heroId: String?,
    val portrait: String?,
    val team: dev.quinton.goa.core.model.match.Team,
    val playerName: String?,
)

internal object TimerUiMapper {
    fun seats(match: Match, catalog: Catalog?, players: List<Player>): List<SeatInfo> = match.participants.map { p ->
        val hero = p.heroId?.let { id -> catalog?.heroes?.firstOrNull { it.id == id } }
        val player = players.firstOrNull { it.id == p.playerId }
        SeatInfo(
            heroName = hero?.name ?: if (p.heroId == null) "Hero not picked" else "Unknown hero",
            heroId = p.heroId,
            portrait = hero?.portrait,
            team = p.team,
            playerName = player?.name,
        )
    }

    fun active(
        s: TimerSnapshot,
        r: TimerReadout,
        seats: List<SeatInfo>?,
        alertsOff: Boolean = false,
        layout: TimerLayout = TimerLayout.STEPS,
    ): ActiveUiState {
        val step = r.step
        val firstCue = step.cues.filter { it > 0 }.maxOrNull()
        val remaining = r.remainingMillis
        val seatList = if (step.isAction && seats != null && seats.isNotEmpty()) seatUi(s, step, seats) else null
        return ActiveUiState(
            presetName = s.preset.name,
            round = r.round,
            title = seatList?.firstOrNull { it.acting }?.let(::actingTitle) ?: TimerText.stepTitle(step),
            context = TimerText.stepContext(step, s.preset, s.endOfRound.size, s.actionSlots),
            kind = step.phase?.kind,
            section = step.section,
            clock = TimerText.clock(r),
            spokenClock = TimerText.spokenClock(r),
            fraction = r.fractionRemaining,
            timed = step.timed,
            overtime = r.overtime,
            warning = remaining != null && remaining >= 0 && firstCue != null && remaining <= firstCue * 1000L,
            paused = s.status == TimerStatus.PAUSED,
            pips = RoundPipsUi(
                hasRoundStart = s.plan.steps.any { it.section == RoundSection.ROUND_START },
                turns = s.preset.turnsPerRound,
                section = step.section,
                currentTurn = step.turn,
            ),
            checklist = if (step.section == RoundSection.ROUND_END) checklist(s, step) else null,
            seats = seatList,
            actionSlot = step.actionSlot,
            actionSlots = s.actionSlots,
            nextLabel = TimerText.nextLabel(s),
            tieBreaker = s.tieBreaker,
            matchClock = TimerText.duration(r.matchElapsedMillis, roundUp = false),
            autoAdvance = s.preset.autoAdvance,
            alertsOff = alertsOff,
            hint = TimerText.stepHint(step),
            shotClock = r.shotClockRemainingMillis?.let { shotClock(it, s.shotClock?.durationMillis ?: 1L) },
            shotClockSeconds = s.preset.shotClockSeconds,
            timeOuts = timeOuts(s, step),
            volumeKeys = s.preset.volumeKeys,
            heroIds = seats.orEmpty().mapNotNull { it.heroId },
            stepIndex = s.stepIndex,
            layout = layout,
            rows = if (layout == TimerLayout.ROUND) roundRows(s, seats) else emptyList(),
        )
    }

    /**
     * The round view: one row per step of this round, done / current / upcoming. Action rows name
     * the hero recorded for their slot this turn (the engine only keeps the current turn's).
     */
    fun roundRows(s: TimerSnapshot, seats: List<SeatInfo>?): List<RoundRowUi> = s.plan.steps.map { step ->
        val group = when (step.section) {
            RoundSection.ROUND_START -> "Round start"
            RoundSection.TURN -> "Turn ${step.turn}"
            RoundSection.ROUND_END -> "End of round"
        }
        val title = step.endStep?.title ?: step.phase?.name.orEmpty()
        val slot = step.actionSlot?.takeIf { step.isAction }?.let { "Hero ${it + 1} of ${s.actionSlots}" }
        val context = when {
            slot != null -> listOfNotNull(slot, actor(s, step, seats))

            step.endStepNumber != null ->
                listOfNotNull(
                    "Step ${step.endStepNumber} of ${s.endOfRound.size}",
                    step.phase?.let {
                        "${it.name} timer"
                    },
                )

            else -> emptyList()
        }
        RoundRowUi(
            index = step.index,
            group = group,
            title = title,
            context = context.joinToString(" · ").ifEmpty { null },
            length = TimerText.durationLabel(step.seconds),
            state = when {
                step.index < s.stepIndex -> RoundRowState.DONE
                step.index == s.stepIndex -> RoundRowState.CURRENT
                else -> RoundRowState.UPCOMING
            },
            jumpName = listOfNotNull(group, title, slot).joinToString(" · "),
            label = (listOf(group, title) + context).joinToString(", "),
            spokenLength = step.seconds?.let { TimerText.spokenDuration(it.toLong()) } ?: "untimed",
        )
    }

    /** "Mrak · Ben": the hero recorded for an Action slot of the current turn. */
    private fun actor(s: TimerSnapshot, step: TimerStep, seats: List<SeatInfo>?): String? {
        if (seats == null || step.turn != s.step.turn || s.step.section != RoundSection.TURN) return null
        val seat = s.actors.firstOrNull { it.slot == step.actionSlot }?.seat ?: return null
        val info = seats.getOrNull(seat) ?: return null
        return info.playerName?.let { "${info.heroName} · $it" } ?: info.heroName
    }

    /** "Mrak · Ben": who is resolving now in the Action timer. */
    private fun actingTitle(seat: SeatUi): String = seat.playerName?.let { "${seat.heroName} · $it" } ?: seat.heroName

    private fun shotClock(remaining: Long, duration: Long): ShotClockUi {
        val left = remaining.coerceAtLeast(0)
        return ShotClockUi(
            clock = TimerText.duration(left, roundUp = true),
            spokenClock = if (remaining <= 0) {
                "Hurry up: time"
            } else {
                "Hurry up, ${TimerText.spokenDuration((left + 999) / 1000)} left"
            },
            fraction = (left.toFloat() / duration.coerceAtLeast(1)).coerceIn(0f, 1f),
            over = remaining <= 0,
            warning = remaining in 1..SHOT_WARNING_MS,
        )
    }

    private fun timeOuts(s: TimerSnapshot, step: TimerStep): TimeOutsUi? {
        val per = s.preset.timeOutsPerRound
        if (per <= 0) return null
        fun left(team: Team) = (per - (s.timeOutsUsed[team] ?: 0)).coerceAtLeast(0)
        return TimeOutsUi(
            seconds = s.preset.timeOutSeconds,
            redLeft = left(Team.RED),
            blueLeft = left(Team.BLUE),
            usable = step.timed,
        )
    }

    private const val SHOT_WARNING_MS = 10_000L

    private fun checklist(s: TimerSnapshot, step: TimerStep): List<ChecklistItemUi> {
        val current = step.endStepNumber ?: (s.endOfRound.size + 1)
        return s.endOfRound.mapIndexed { i, end ->
            val number = i + 1
            val timer = s.preset.phases.firstOrNull {
                it.enabled && it.scope == PhaseScope.ROUND_END && it.endStepId == end.id
            }
            ChecklistItemUi(
                number = number,
                title = end.title,
                detail = end.detail,
                state = when {
                    number < current -> ChecklistState.DONE
                    number == current -> ChecklistState.CURRENT
                    else -> ChecklistState.UPCOMING
                },
                timerName = timer?.let { "${it.name} ${TimerText.durationLabel(it.seconds)}" },
                id = end.id,
            )
        }
    }

    private fun seatUi(s: TimerSnapshot, step: TimerStep, seats: List<SeatInfo>): List<SeatUi> {
        val slot = step.actionSlot
        val resolved = s.actors.filter { it.slot < (slot ?: 0) }.map { it.seat }.toSet()
        val acting = s.actors.firstOrNull { it.slot == slot }?.seat
        return seats.mapIndexed { i, seat ->
            SeatUi(
                seat = i,
                heroName = seat.heroName,
                heroId = seat.heroId,
                portrait = seat.portrait,
                team = seat.team,
                playerName = seat.playerName,
                resolved = i in resolved,
                acting = i == acting,
            )
        }
    }

    fun summary(s: TimerSnapshot, r: TimerReadout): SummaryUiState {
        val minutes = durationMinutes(r.matchElapsedMillis)
        return SummaryUiState(
            duration = humanDuration(r.matchElapsedMillis),
            durationMin = minutes,
            rounds = s.round,
            presetName = s.preset.name,
            mode = s.mode,
            hasMatch = s.matchId != null,
        )
    }

    /** Whole minutes for the match log, rounded to nearest, clamped like the logged value. */
    fun durationMinutes(millis: Long): Int = MatchDuration.minutes(millis)

    fun humanDuration(millis: Long): String {
        val minutes = durationMinutes(millis)
        return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${"%02d".format(minutes % 60)} min"
    }
}
