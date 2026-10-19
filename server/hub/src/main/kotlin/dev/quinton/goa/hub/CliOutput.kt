package dev.quinton.goa.hub

import java.io.OutputStream
import java.io.PrintStream
import java.text.Normalizer

/**
 * The command line's output streams, the same on every console. Elsewhere they write UTF-8. On
 * Windows a console's code page (437, 850, …), the JVM's pipe encoding (1252) and PowerShell's
 * decoding of a native command's output rarely agree, so "·" in "iPhone · Safari" came out as
 * mojibake; there the CLI writes ASCII only: typographic marks become their ASCII look-alikes and
 * accents are dropped ("Zoë" → "Zoe"). Only the CLI's own text goes through here; the hub's pages
 * and files are always UTF-8.
 */
object CliOutput {
    fun stream(base: OutputStream, asciiOnly: Boolean = isWindows()): PrintStream =
        if (asciiOnly) AsciiPrintStream(base) else PrintStream(base, true, Charsets.UTF_8)

    fun isWindows(): Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /** [text] in ASCII: "·" → "-", "—" → "-", "…" → "...", curly quotes straight, accents dropped, the rest "?". */
    fun ascii(text: String): String {
        if (text.all { it.code < ASCII_END }) return text
        val marks = buildString(text.length) {
            for (c in text) append(LOOK_ALIKES[c] ?: c)
        }
        return Normalizer.normalize(marks, Normalizer.Form.NFD)
            .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
            .map { if (it.code < ASCII_END) it else '?' }
            .joinToString("")
    }

    private const val ASCII_END = 0x80

    private val LOOK_ALIKES = mapOf(
        '·' to "-",
        '•' to "-",
        '—' to "-",
        '–' to "-",
        '−' to "-",
        '…' to "...",
        '‘' to "'",
        '’' to "'",
        '“' to "\"",
        '”' to "\"",
        '→' to "->",
        '×' to "x",
        ' ' to " ",
        'ß' to "ss",
        'æ' to "ae",
        'Æ' to "AE",
        'ø' to "o",
        'Ø' to "O",
        'ł' to "l",
        'Ł' to "L",
    )

    /** Every string printed is folded by [ascii] (println(String) and println(Any) both end in print(String)). */
    private class AsciiPrintStream(base: OutputStream) : PrintStream(base, true, Charsets.US_ASCII) {
        override fun print(s: String?) = super.print(s?.let(::ascii))

        override fun print(obj: Any?) = print(obj.toString())

        override fun println(x: String?) {
            synchronized(this) {
                print(x)
                println()
            }
        }

        override fun println(x: Any?) = println(x.toString())
    }
}
