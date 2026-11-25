package dev.quinton.goa.core.game.engine

/** The JVM's [ControlSignal]s are already created without a stack trace. */
internal actual fun <T> withoutStackTraces(block: () -> T): T = block()
