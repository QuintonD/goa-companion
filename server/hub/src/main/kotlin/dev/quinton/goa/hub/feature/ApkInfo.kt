package dev.quinton.goa.hub.feature

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * The version of an APK, read from its binary `AndroidManifest.xml` (the `manifest` element's
 * `versionName` / `versionCode`). Null fields when the APK can't be read; the page then shows
 * size and date only.
 */
data class ApkInfo(val versionName: String?, val versionCode: Long?) {
    companion object {
        fun read(apk: Path): ApkInfo = runCatching {
            ZipFile(apk.toFile()).use { zip ->
                val entry = zip.getEntry("AndroidManifest.xml") ?: return@use null
                parseManifest(zip.getInputStream(entry).use { it.readBytes() })
            }
        }.getOrNull() ?: ApkInfo(null, null)

        private const val STRING_POOL = 0x0001
        private const val START_ELEMENT = 0x0102
        private const val UTF8_FLAG = 0x100
        private const val TYPE_STRING = 0x03
        private const val TYPE_INT_DEC = 0x10
        private const val TYPE_INT_HEX = 0x11
        private const val NO_INDEX = -1
        private const val ATTRIBUTE_BYTES = 20

        /** Walks the chunks of an Android binary XML file up to the first element. */
        internal fun parseManifest(bytes: ByteArray): ApkInfo? {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            var strings: List<String> = emptyList()
            var pos = buf.getShort(2).toInt() and 0xFFFF
            while (pos + 8 <= bytes.size) {
                val type = buf.getShort(pos).toInt() and 0xFFFF
                val headerSize = buf.getShort(pos + 2).toInt() and 0xFFFF
                val size = buf.getInt(pos + 4)
                if (size <= 0) return null
                when (type) {
                    STRING_POOL -> strings = stringPool(buf, pos)
                    START_ELEMENT -> return element(buf, pos, headerSize, strings)
                }
                pos += size
            }
            return null
        }

        private fun element(buf: ByteBuffer, pos: Int, headerSize: Int, strings: List<String>): ApkInfo? {
            val body = pos + headerSize
            if (strings.getOrNull(buf.getInt(body + 4)) != "manifest") return null
            val attrStart = buf.getShort(body + 8).toInt() and 0xFFFF
            val attrSize = (buf.getShort(body + 10).toInt() and 0xFFFF).takeIf { it > 0 } ?: ATTRIBUTE_BYTES
            val count = buf.getShort(body + 12).toInt() and 0xFFFF
            var name: String? = null
            var code: Long? = null
            repeat(count) { i ->
                val at = body + attrStart + i * attrSize
                val attrName = strings.getOrNull(buf.getInt(at + 4))
                val raw = buf.getInt(at + 8)
                val dataType = buf.get(at + 15).toInt() and 0xFF
                val data = buf.getInt(at + 16)
                when (attrName) {
                    "versionName" -> name = strings.getOrNull(if (raw != NO_INDEX) raw else data).takeIf {
                        raw != NO_INDEX || dataType == TYPE_STRING
                    }

                    "versionCode" ->
                        code =
                            data.toLong().takeIf { dataType == TYPE_INT_DEC || dataType == TYPE_INT_HEX }
                }
            }
            return ApkInfo(name, code)
        }

        private fun stringPool(buf: ByteBuffer, pos: Int): List<String> {
            val count = buf.getInt(pos + 8)
            val utf8 = buf.getInt(pos + 16) and UTF8_FLAG != 0
            val stringsStart = pos + buf.getInt(pos + 20)
            val offsets = pos + (buf.getShort(pos + 2).toInt() and 0xFFFF)
            return List(count) { i ->
                val at = stringsStart + buf.getInt(offsets + i * 4)
                if (utf8) utf8String(buf, at) else utf16String(buf, at)
            }
        }

        private fun utf8String(buf: ByteBuffer, start: Int): String {
            var at = start
            at += if (buf.get(at).toInt() and 0x80 != 0) 2 else 1 // UTF-16 length, unused
            val first = buf.get(at).toInt() and 0xFF
            val length = if (first and 0x80 !=
                0
            ) {
                ((first and 0x7F) shl 8) or (buf.get(at + 1).toInt() and 0xFF)
            } else {
                first
            }
            at += if (first and 0x80 != 0) 2 else 1
            return String(ByteArray(length) { buf.get(at + it) }, Charsets.UTF_8)
        }

        private fun utf16String(buf: ByteBuffer, start: Int): String {
            val first = buf.getShort(start).toInt() and 0xFFFF
            val (length, at) = if (first and 0x8000 != 0) {
                (((first and 0x7FFF) shl 16) or (buf.getShort(start + 2).toInt() and 0xFFFF)) to start + 4
            } else {
                first to start + 2
            }
            return String(CharArray(length) { buf.getChar(at + it * 2) })
        }
    }
}
