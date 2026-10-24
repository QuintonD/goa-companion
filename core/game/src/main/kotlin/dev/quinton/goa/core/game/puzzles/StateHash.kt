package dev.quinton.goa.core.game.puzzles

import dev.quinton.goa.core.game.engine.GameState
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * A 128-bit fingerprint of an engine state for the solver's transposition table. It walks the
 * state's generated serializer (so a new state field is never left out) into two hashes, after
 * removing what only numbers the path: the decision counter and the absolute card stamps (kept as
 * ranks: their order still matters).
 */
internal object StateHash {
    fun of(state: GameState): Pair<Long, Long> {
        val c = state.copy()
        c.decisions = 0
        val stamps = c.heroes.flatMap { h -> h.cards.map { it.stamp } }.distinct().sorted()
        val rank = HashMap<Long, Long>(stamps.size * 2)
        stamps.forEachIndexed { i, s -> rank[s] = i.toLong() }
        for (h in c.heroes) for (card in h.cards) card.stamp = rank.getValue(card.stamp)
        c.stamp = 0
        val enc = HashingEncoder()
        enc.encodeSerializableValue(GameState.serializer(), c)
        return enc.a to enc.b
    }
}

/** Feeds every primitive of a serializable value, with structure marks, into two 64-bit hashes. */
@OptIn(ExperimentalSerializationApi::class)
private class HashingEncoder : AbstractEncoder() {
    var a = FNV_OFFSET
    var b = SEED_B

    override val serializersModule: SerializersModule = EmptySerializersModule()

    private fun mix(v: Long) {
        a = (a xor v) * FNV_PRIME
        b = (b + v) * MIX
        b = b xor (b ushr SHIFT)
    }

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        mix(ELEMENT + index)
        return true
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        mix(BEGIN)
        return this
    }

    override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder {
        mix(COLLECTION + collectionSize)
        return this
    }

    override fun endStructure(descriptor: SerialDescriptor) = mix(END)

    override fun encodeNull() = mix(NULL)

    override fun encodeNotNullMark() = mix(NOT_NULL)

    override fun encodeBoolean(value: Boolean) = mix(if (value) 1 else 0)

    override fun encodeByte(value: Byte) = mix(value.toLong())

    override fun encodeShort(value: Short) = mix(value.toLong())

    override fun encodeChar(value: Char) = mix(value.code.toLong())

    override fun encodeInt(value: Int) = mix(value.toLong())

    override fun encodeLong(value: Long) = mix(value)

    override fun encodeFloat(value: Float) = mix(value.toRawBits().toLong())

    override fun encodeDouble(value: Double) = mix(value.toRawBits())

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) = mix(ENUM + index)

    override fun encodeString(value: String) {
        mix(STRING + value.length)
        for (ch in value) mix(ch.code.toLong())
    }

    private companion object {
        const val FNV_OFFSET = -0x340d631b7bdddcdbL
        const val FNV_PRIME = 0x100000001b3L
        const val SEED_B = 0x2545F4914F6CDD1DL
        const val MIX = -0x61c8864680b583ebL
        const val SHIFT = 29
        const val ELEMENT = 0x1000_0000L
        const val BEGIN = 0x2000_0001L
        const val COLLECTION = 0x3000_0000L
        const val END = 0x4000_0001L
        const val NULL = 0x5000_0001L
        const val NOT_NULL = 0x5000_0002L
        const val ENUM = 0x6000_0000L
        const val STRING = 0x7000_0000L
    }
}
