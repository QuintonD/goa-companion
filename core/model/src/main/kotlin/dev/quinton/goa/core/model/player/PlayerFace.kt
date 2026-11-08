package dev.quinton.goa.core.model.player

/**
 * The hero a player is shown as: their most-played hero in the match log, with its catalog portrait. Derived and
 * rebuilt from the log on every change (ADR-003), never stored or synced, so it adds nothing to `SyncDocument`.
 */
data class PlayerFace(val heroId: String, val portrait: String?)
