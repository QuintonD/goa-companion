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
 *
 * Revision 2 (2026-10-18, owner's table rules): every hero gets its own resolve timer each turn.
 * Where the bundled Action phase is on, the stored copy's Action phase is switched on too (it
 * shipped switched off, so an off Action is the old default), or added after the Resolve step
 * when the copy has none. Its length and cues are left as the user set them.
 *
 * Revision 3 (2026-10-18, owner request): adds the bundled "Speed guards" preset (0:30 strategy,
 * 0:10 per hero resolving). A stored list only replaces the bundled list, so a new bundled preset
 * is added to it once: when its newest bundled copy predates the preset. Any later write stores
 * the copies at the new revision, so deleting the new preset keeps it deleted. A list with no
 * bundled copy at all is left as the user curated it. No phase changes.
 */
object TimerPresetMigration {
    private const val OLD_TURN_NAME = "Turn"
    private const val RESOLVE_NAME = "resolve"
    private const val ACTION_ON_REVISION = 2

    /** Bundled presets added after the first release, by id, with the revision that added them. */
    private val ADDED_IN = mapOf("speed-guards" to 3)

    fun upgrade(stored: List<TimerPreset>, bundled: List<TimerPreset>): List<TimerPreset> {
        val upgraded = stored.map { preset ->
            val target = bundled.firstOrNull { it.id == preset.id }
            if (target == null || preset.revision >= target.revision) preset else upgradeOne(preset, target)
        }
        return addNewBundled(stored, upgraded, bundled)
    }

    /** Adds each newer bundled preset after the bundled presets that precede it in the catalog. */
    private fun addNewBundled(
        stored: List<TimerPreset>,
        upgraded: List<TimerPreset>,
        bundled: List<TimerPreset>,
    ): List<TimerPreset> {
        val bundledIds = bundled.map { it.id }.toSet()
        val storedRevision = stored.filter { it.id in bundledIds }.maxOfOrNull { it.revision } ?: return upgraded
        val list = upgraded.toMutableList()
        bundled.forEachIndexed { index, preset ->
            val since = ADDED_IN[preset.id]
            if (since != null && storedRevision < since && list.none { it.id == preset.id }) {
                val before = bundled.take(index).map { it.id }.toSet()
                list.add(list.indexOfLast { it.id in before } + 1, preset)
            }
        }
        return list
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
        if (preset.revision < ACTION_ON_REVISION && target.revision >= ACTION_ON_REVISION) {
            phases = actionOn(phases, target)
        }
        return preset.copy(phases = phases, revision = target.revision)
    }

    private fun actionOn(phases: List<PhaseSpec>, target: TimerPreset): List<PhaseSpec> {
        val bundledAction = target.phases.firstOrNull { it.kind == PhaseKind.ACTION && it.scope == PhaseScope.TURN }
        if (bundledAction == null || !bundledAction.enabled) return phases
        if (phases.any { it.kind == PhaseKind.ACTION }) {
            return phases.map { if (it.kind == PhaseKind.ACTION) it.copy(enabled = true) else it }
        }
        if (phases.any { it.id == bundledAction.id }) return phases
        val after = phases.indexOfLast { it.scope == PhaseScope.TURN } + 1
        return phases.toMutableList().apply { add(after.coerceIn(0, size), bundledAction) }
    }
}
