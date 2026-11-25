package dev.quinton.goa.core.game.engine

/** V8 captures at most `Error.stackTraceLimit` frames: none while [block] runs (single-threaded, restored after). */
internal actual fun <T> withoutStackTraces(block: () -> T): T {
    val error: dynamic = js("Error")
    val was = error.stackTraceLimit
    error.stackTraceLimit = 0
    try {
        return block()
    } finally {
        error.stackTraceLimit = was
    }
}
