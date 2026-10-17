package dev.quinton.goa.feature.timer.ui.timer

import dev.quinton.goa.core.domain.timer.PhaseScope
import dev.quinton.goa.core.domain.timer.RoundSection
import dev.quinton.goa.core.domain.timer.TimerReadout
import dev.quinton.goa.core.domain.timer.TimerSnapshot
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.core.domain.timer.TimerStep
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.player.Player
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

    fun active(s: TimerSnapshot, r: TimerReadout, seats: List<SeatInfo>?): ActiveUiState {
        val step = r.step
        val firstCue = step.cues.filter { it > 0 }.maxOrNull()
        val remaining = r.remainingMillis
        return ActiveUiState(
            presetName = s.preset.name,
            round = r.round,
            title = TimerText.stepTitle(step),
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
            seats = if (step.isAction && seats != null && seats.isNotEmpty()) seatUi(s, step, seats) else null,
            actionSlot = step.actionSlot,
            actionSlots = s.actionSlots,
            nextLabel = nextLabel(s),
            tieBreaker = s.tieBreaker,
            matchClock = TimerText.duration(r.matchElapsedMillis, roundUp = false),
            autoAdvance = s.preset.autoAdvance,
        )
    }

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

    private fun nextLabel(s: TimerSnapshot): String {
        val nextIndex = s.stepIndex + 1
        if (nextIndex > s.plan.lastIndex) return "Start round ${s.round + 1}"
        val next = s.plan.steps[nextIndex]
        return when {
            next.isAction -> "Next hero"
            next.section == RoundSection.TURN && next.turn != s.step.turn -> "Next: Turn ${next.turn}"
            else -> "Next: ${TimerText.stepTitle(next)}"
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

    /** Whole minutes for the match log, rounded to nearest, at least 1. */
    fun durationMinutes(millis: Long): Int = ((millis + 30_000) / 60_000).toInt().coerceAtLeast(1)

    fun humanDuration(millis: Long): String {
        val minutes = durationMinutes(millis)
        return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${"%02d".format(minutes % 60)} min"
    }
}
