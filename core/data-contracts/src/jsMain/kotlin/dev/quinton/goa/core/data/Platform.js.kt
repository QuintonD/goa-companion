package dev.quinton.goa.core.data

import kotlin.js.Date

internal actual fun epochMillis(): Long = Date.now().toLong()
