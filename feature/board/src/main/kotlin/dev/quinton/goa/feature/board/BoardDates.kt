package dev.quinton.goa.feature.board

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinTimeZone

private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** "Changed today, 14:05", "Changed yesterday, 9:12", "Changed 12 Oct 2026": the time in the phone's locale. */
fun BoardText.changedHere(epochMillis: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    changed(epochMillis, now, zone.toKotlinTimeZone()) { it.toJavaLocalDateTime().format(time) }
