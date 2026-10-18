package dev.quinton.goa.hub.store

import dev.quinton.goa.core.model.settings.AppSettings
import kotlinx.serialization.builtins.serializer

/**
 * Per-browser state that several areas read (ADR-030 §3). Each has one writer; everyone else only
 * reads, and a missing slot means the default.
 */
object SharedSlots {
    /** "Which player are you?" for this browser (a player id, read through the merges). Writer: Group. */
    val Me = DeviceSlot("me", String.serializer())

    /**
     * This browser's app settings, as a phone keeps them (owned products, timer defaults, sounds,
     * haptics, running-timer layout, …). Missing = `AppSettings()`. Writer: Settings. The theme
     * stays in `ShellSlots.Theme`.
     */
    val Settings = DeviceSlot("settings", AppSettings.serializer())
}
