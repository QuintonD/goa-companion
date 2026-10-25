package dev.quinton.goa.core.domain.sync

/**
 * `crypto.getRandomValues`: in every browser the web app supports, and in Node 19+ (the tests). A
 * Kotlin/JS ByteArray is an Int8Array, so it is filled in place.
 */
internal actual fun secureRandomBytes(size: Int): ByteArray {
    val bytes = ByteArray(size)
    js("globalThis.crypto.getRandomValues(bytes)")
    return bytes
}
