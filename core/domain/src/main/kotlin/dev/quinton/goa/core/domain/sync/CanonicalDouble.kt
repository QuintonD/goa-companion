package dev.quinton.goa.core.domain.sync

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonUnquotedLiteral

/**
 * A synced Double written in JSON the way the JVM writes it (`1200.0`, `0.5`, `1.0E-4`) on every
 * platform (ADR-043). [SyncJson]'s encoding of a record is the merge tie-break ([SyncMerge]), so a
 * browser must produce the phones' exact text; Kotlin/JS alone would write `1200`. Use it with
 * `@file:UseSerializers(CanonicalDoubleSerializer::class)` on files of synced types with Doubles.
 */
object CanonicalDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.quinton.goa.core.domain.sync.CanonicalDouble", PrimitiveKind.DOUBLE)

    override fun serialize(encoder: Encoder, value: Double) {
        if (encoder is JsonEncoder && value.isFinite()) {
            encoder.encodeJsonElement(JsonUnquotedLiteral(jvmDoubleString(value)))
        } else {
            encoder.encodeDouble(value)
        }
    }

    override fun deserialize(decoder: Decoder): Double = decoder.decodeDouble()
}

/** `java.lang.Double.toString(value)`: shortest digits, `E` notation outside [1e-3, 1e7), `NaN`, `Infinity`. */
expect fun jvmDoubleString(value: Double): String
