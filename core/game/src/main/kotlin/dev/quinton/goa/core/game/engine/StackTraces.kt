package dev.quinton.goa.core.game.engine

/**
 * Runs [block] without capturing stack traces for the exceptions it creates, where that costs: the engine's
 * flow signals ([ControlSignal], one per question asked) are exceptions, and on Kotlin/JS each one captures a
 * JavaScript stack in its constructor (a sixth of the AI's time in the browser). The JVM's signals capture
 * none already. Answers are the same; only a real error's trace inside [block] is shorter (empty on JS).
 */
internal expect fun <T> withoutStackTraces(block: () -> T): T
