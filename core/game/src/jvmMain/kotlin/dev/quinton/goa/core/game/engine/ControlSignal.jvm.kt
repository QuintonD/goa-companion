package dev.quinton.goa.core.game.engine

/** No stack trace (it is control flow, not an error): the engine throws these all the time. */
actual open class ControlSignal actual constructor(message: String?) : RuntimeException(message, null, false, false)
