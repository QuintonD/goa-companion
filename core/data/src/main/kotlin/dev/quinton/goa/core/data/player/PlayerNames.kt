package dev.quinton.goa.core.data.player

import java.util.Locale

/**
 * Player names are unique in the group, compared trimmed and case-insensitively, so one person
 * never ends up with two ids (and two split ratings).
 */
object PlayerNames {
    fun key(name: String): String = name.trim().lowercase(Locale.ROOT)

    fun same(a: String, b: String): Boolean = key(a) == key(b)
}
