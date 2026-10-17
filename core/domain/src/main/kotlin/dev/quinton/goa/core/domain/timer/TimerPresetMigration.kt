package dev.quinton.goa.core.domain.timer

/**
 * Brings stored copies of the bundled presets up to the bundled data revision. Saved presets are
 * user data, so only additive, targeted changes are made, and only to presets whose id matches a
 * bundled preset with a higher [TimerPreset.revision]. Presets the user made (other ids) are
 * never touched. Pure and idempotent: the repository applies it on every read.
 *
 * Revision 1 (2026-10-17):
 * - adds the bundled per-turn Resolve step after the Turn phase, unless the preset already has
 *   one (or a per-turn phase the user named "Resolve");
 * - renames the Turn phase from the old default "Turn" to the bundled name ("Play a card"),
 *   only when the user had not renamed it.
 */
object TimerPresetMigration {
    private const val OLD_TURN_NAME = "Turn"
    private const val RESOLVE_NAME = "resolve"

    fun upgrade(stored: List<TimerPreset>, bundled: List<TimerPreset>): List<TimerPreset> = stored.map { preset ->
        val target = bundled.firstOrNull { it.id == preset.id }
        if (target == null || preset.revision >= target.revision) preset else upgradeOne(preset, target)
    }

    private fun upgradeOne(preset: TimerPreset, target: TimerPreset): TimerPreset {
        var phases = preset.phases
        val bundledResolve = target.phases.firstOrNull { it.kind == PhaseKind.RESOLVE }
        val hasResolve = phases.any {
            it.kind == PhaseKind.RESOLVE ||
                (it.scope == PhaseScope.TURN && it.name.trim().equals(RESOLVE_NAME, ignoreCase = true))
        }
        if (bundledResolve != null && !hasResolve && phases.none { it.id == bundledResolve.id }) {
            val turnIndex = phases.indexOfFirst { it.kind == PhaseKind.TURN }
            val at = if (turnIndex >= 0) turnIndex + 1 else phases.indexOfLast { it.scope == PhaseScope.TURN } + 1
            phases = phases.toMutableList().apply { add(at.coerceIn(0, size), bundledResolve) }
        }
        val bundledTurnName = target.phases.firstOrNull { it.kind == PhaseKind.TURN }?.name
        if (bundledTurnName != null) {
            phases = phases.map {
                if (it.kind == PhaseKind.TURN && it.name == OLD_TURN_NAME) it.copy(name = bundledTurnName) else it
            }
        }
        return preset.copy(phases = phases, revision = target.revision)
    }
}
