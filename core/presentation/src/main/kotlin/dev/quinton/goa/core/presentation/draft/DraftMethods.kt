package dev.quinton.goa.core.presentation.draft

import dev.quinton.goa.core.domain.draft.DraftError
import dev.quinton.goa.core.domain.draft.DraftMethod
import dev.quinton.goa.core.domain.draft.DraftPlanner
import dev.quinton.goa.core.domain.draft.MethodOrigin
import dev.quinton.goa.core.domain.draft.PoolRule
import dev.quinton.goa.core.domain.draft.SequenceRule
import dev.quinton.goa.core.domain.draft.StepSpec
import dev.quinton.goa.core.domain.draft.TeamSlot
import dev.quinton.goa.core.domain.draft.ban
import dev.quinton.goa.core.domain.draft.pick
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.draft.DraftMethodKind
import kotlinx.serialization.Serializable

/** Seat layout used for previews: heroes on Team A and Team B. */
@Serializable
data class PreviewLayout(val a: Int, val b: Int) {
    val label: String get() = "$a v $b"

    /** "3v3", for links and form values. */
    val key: String get() = "${a}v$b"

    companion object {
        val OPTIONS =
            listOf(
                PreviewLayout(2, 2),
                PreviewLayout(3, 3),
                PreviewLayout(4, 4),
                PreviewLayout(5, 5),
                PreviewLayout(3, 2),
            )

        fun fromKey(key: String?): PreviewLayout? = OPTIONS.firstOrNull { it.key == key }
    }
}

/** A method's sequence for one layout, or why it can't run. */
data class SequencePreview(val tokens: List<StepToken>, val error: String?, val heroesNeeded: Int)

fun previewOf(method: DraftMethod, layout: PreviewLayout): SequencePreview {
    val plan = DraftPlanner.plan(method, layout.a, layout.b)
    val steps = plan.getOrNull()
    val error = when (val e = plan.exceptionOrNull()) {
        null -> null
        is DraftError.NotSupported -> e.reason
        is DraftError.SequenceTooShort -> "Never fills ${layout.label} seats — add picks for both teams"
        else -> e.message
    }
    val needed = when (val pool = method.pool) {
        is PoolRule.SeatsPlus -> layout.a + layout.b + pool.extra
        is PoolRule.DealPerSeat -> (layout.a + layout.b) * pool.perSeat
        else -> steps?.size ?: 0
    }
    return SequencePreview(steps?.tokens().orEmpty(), error, if (method.sequence is SequenceRule.Lineup) 0 else needed)
}

data class MethodRow(val method: DraftMethod, val preview: SequencePreview, val isDefault: Boolean)

data class MethodsUiState(
    val loading: Boolean = true,
    val layout: PreviewLayout = PreviewLayout.OPTIONS.first(),
    val builtIn: List<MethodRow> = emptyList(),
    val custom: List<MethodRow> = emptyList(),
) {
    companion object {
        fun of(library: MethodLibrary, layout: PreviewLayout): MethodsUiState {
            fun row(m: DraftMethod) = MethodRow(m, previewOf(m, layout), m.id == library.defaultId)
            return MethodsUiState(
                loading = false,
                layout = layout,
                builtIn = library.builtIn.map(::row),
                custom = library.custom.map(::row),
            )
        }
    }
}

@Serializable
enum class PoolKind(val label: String) {
    ALL("All"),
    RANDOM("Random N"),
    SEATS_PLUS("Heroes + N"),
    DEAL("Deal N each"),
}

@Serializable
enum class StepSection { OPENING, REPEAT }

/**
 * The method editor's form. Editing a built-in creates a copy; built-ins are never changed in
 * place. Every edit is a pure function here (the phone's ViewModel and the hub's editor page share
 * them); serialisable so the hub can keep a half-edited method per browser.
 */
@Serializable
data class MethodEditUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    /** Editing a built-in creates a copy; built-ins are never changed in place. */
    val copyOf: String? = null,
    val id: String = "",
    val name: String = "",
    val poolKind: PoolKind = PoolKind.RANDOM,
    val poolValue: Int = 15,
    val opening: List<StepSpec> = emptyList(),
    val repeat: List<StepSpec> = emptyList(),
    /** Non-pattern sequences (per-count tables, lineups) keep their sequence; only pool/timer edit. */
    val fixedSequence: SequenceRule? = null,
    val stepSeconds: Int? = null,
    val layout: PreviewLayout = PreviewLayout.OPTIONS.first(),
    val saving: Boolean = false,
) {
    val sequence: SequenceRule get() = fixedSequence ?: SequenceRule.Pattern(opening, repeat)

    val pool: PoolRule
        get() = when (poolKind) {
            PoolKind.ALL -> PoolRule.All
            PoolKind.RANDOM -> PoolRule.Random(poolValue)
            PoolKind.SEATS_PLUS -> PoolRule.SeatsPlus(poolValue)
            PoolKind.DEAL -> PoolRule.DealPerSeat(poolValue)
        }

    fun toMethod(): DraftMethod = DraftMethod(
        id = id,
        name = name.trim(),
        kind = DraftMethodKind.CUSTOM,
        origin = MethodOrigin.CUSTOM,
        summary = describe(),
        pool = pool,
        sequence = sequence,
        stepSeconds = stepSeconds,
        notes = copyOf?.let { listOf("Based on $it.") }.orEmpty(),
    )

    val preview: SequencePreview get() = previewOf(toMethod(), layout)

    val error: String?
        get() = when {
            name.isBlank() -> "Give the method a name"

            fixedSequence == null && repeat.none { it.action == DraftAction.PICK } &&
                opening.none { it.action == DraftAction.PICK } ->
                "Add at least one pick"

            // Checked for fixed sequences too (e.g. a Pick and Ban copy with dealt hands).
            DraftPlanner.problem(toMethod()) != null -> DraftPlanner.problem(toMethod())?.message

            fixedSequence != null -> null

            else -> PreviewLayout.OPTIONS.firstNotNullOfOrNull {
                previewOf(toMethod(), it).error?.let { e -> "${it.label}: $e" }
            }
        }

    private fun describe(): String {
        val poolText = when (poolKind) {
            PoolKind.ALL -> "All eligible heroes"
            PoolKind.RANDOM -> "Pool of $poolValue"
            PoolKind.SEATS_PLUS -> "Heroes + $poolValue revealed"
            PoolKind.DEAL -> "Deal $poolValue to each seat"
        }
        val bans = (opening + repeat).count { it.action == DraftAction.BAN }
        return if (fixedSequence !=
            null
        ) {
            "$poolText."
        } else {
            "$poolText. ${bans.takeIf { it > 0 }?.let { "${plural(it, "ban")}. " } ?: ""}Custom order."
        }
    }

    // --- Edits ---

    fun withName(value: String): MethodEditUiState = copy(name = value.take(MAX_NAME))

    fun withPoolKind(kind: PoolKind): MethodEditUiState = copy(
        poolKind = kind,
        poolValue = when (kind) {
            PoolKind.ALL -> poolValue
            PoolKind.RANDOM -> 15
            PoolKind.SEATS_PLUS -> 2
            PoolKind.DEAL -> 3
        },
    )

    fun withPoolValue(value: Int): MethodEditUiState = copy(poolValue = value.coerceIn(minPool(poolKind), MAX_POOL))

    fun addStep(section: StepSection, step: StepSpec): MethodEditUiState = edit(section) { it + step }

    fun removeStep(section: StepSection, index: Int): MethodEditUiState =
        edit(section) { list -> list.filterIndexed { i, _ -> i != index } }

    fun toggleTeam(section: StepSection, index: Int): MethodEditUiState =
        updateStep(section, index) { it.copy(team = it.team.other) }

    fun toggleAction(section: StepSection, index: Int): MethodEditUiState = updateStep(section, index) {
        it.copy(action = if (it.action == DraftAction.BAN) DraftAction.PICK else DraftAction.BAN)
    }

    fun toggleHidden(section: StepSection, index: Int): MethodEditUiState =
        updateStep(section, index) { it.copy(hidden = !it.hidden) }

    fun moveStep(section: StepSection, index: Int, delta: Int): MethodEditUiState = edit(section) { list ->
        val target = index + delta
        if (target !in list.indices || index !in list.indices) {
            list
        } else {
            list.toMutableList().apply { add(target, removeAt(index)) }
        }
    }

    fun withStepSeconds(seconds: Int?): MethodEditUiState = copy(stepSeconds = seconds)

    fun withLayout(value: PreviewLayout): MethodEditUiState = copy(layout = value)

    private fun updateStep(section: StepSection, index: Int, transform: (StepSpec) -> StepSpec) = edit(
        section,
    ) { list ->
        list.mapIndexed { i, s -> if (i == index) transform(s) else s }
    }

    private fun edit(section: StepSection, transform: (List<StepSpec>) -> List<StepSpec>): MethodEditUiState =
        if (fixedSequence != null) {
            this
        } else {
            when (section) {
                StepSection.OPENING -> copy(opening = transform(opening).take(MAX_STEPS))
                StepSection.REPEAT -> copy(repeat = transform(repeat).take(MAX_STEPS))
            }
        }

    companion object {
        const val MAX_NAME = 40
        const val MAX_POOL = 60
        const val MAX_STEPS = 30

        private fun minPool(kind: PoolKind) = if (kind == PoolKind.SEATS_PLUS) 0 else 1

        /**
         * The editor for [methodId]: a new method (Ban A, Ban B, then Pick A, Pick B repeated), an
         * editable copy of a built-in, or a custom method in place. [newId] names a new method or copy.
         */
        fun forEditing(library: MethodLibrary, methodId: String?, newId: String): MethodEditUiState {
            val source = library.find(methodId)
            return when {
                source == null -> MethodEditUiState(
                    loading = false,
                    id = "custom-$newId",
                    name = "",
                    opening = listOf(ban(TeamSlot.A), ban(TeamSlot.B)),
                    repeat = listOf(pick(TeamSlot.A), pick(TeamSlot.B)),
                )

                source.isBuiltIn -> fromMethod(source).copy(
                    isNew = true,
                    copyOf = source.name,
                    id = "custom-$newId",
                    name = "${source.name} (copy)",
                )

                else -> fromMethod(source).copy(isNew = false)
            }
        }

        fun fromMethod(m: DraftMethod): MethodEditUiState {
            val (kind, value) = when (val p = m.pool) {
                PoolRule.All -> PoolKind.ALL to 15
                is PoolRule.Random -> PoolKind.RANDOM to p.size
                is PoolRule.SeatsPlus -> PoolKind.SEATS_PLUS to p.extra
                is PoolRule.DealPerSeat -> PoolKind.DEAL to p.perSeat
            }
            val pattern = m.sequence as? SequenceRule.Pattern
            return MethodEditUiState(
                loading = false,
                id = m.id,
                name = m.name,
                poolKind = kind,
                poolValue = value,
                opening = pattern?.opening.orEmpty(),
                repeat = pattern?.repeat.orEmpty(),
                fixedSequence = if (pattern == null) m.sequence else null,
                stepSeconds = m.stepSeconds,
            )
        }
    }
}
