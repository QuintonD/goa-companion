package dev.quinton.goa.tools.catalogimport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/* ---- Source 1: Stats of Atlantis ---------------------------------------------------------- */

/**
 * One card in `src/new_heroes.json` / `src/spells.json`. Parsed strictly: if the source adds a
 * field, the import fails so the mapping gets reviewed instead of silently dropping data.
 */
@Serializable
data class SoaCard(
    val name: String,
    val description: String,
    val color: String,
    val traits: List<String> = emptyList(),
    val level: Int? = null,
    val handicapped: Boolean = false,
    val extra: Boolean = false,
    val variant: SoaVariant? = null,
    val initiative: Int? = null,
    val primaryAction: String? = null,
    val primaryValue: Int? = null,
    val primaryValueSign: String? = null,
    val modifier: String? = null,
    val modifierValue: Int? = null,
    val modifierValueSign: String? = null,
    val secondaryMovement: Int? = null,
    val secondaryDefense: Int? = null,
    val secondaryAttack: Int? = null,
    val item: String? = null,
    val school: String? = null,
)

/** `first` = which of the two (or three) options, `second` = how many options exist. */
@Serializable
data class SoaVariant(val first: Int, val second: Int)

/** One `new Hero(...)` entry of `src/states.ts`. Stats are `[base, max]` in A, D, I, M order. */
data class SoaHero(
    val id: String,
    val name: String,
    val stats: List<List<Int>>,
    val title: String,
    val stars: Int,
    val pack: String,
    val traits: List<String>,
)

object SoaParser {
    private val strict = Json { ignoreUnknownKeys = false }

    fun heroCards(json: String): Map<String, List<SoaCard>> = strict.decodeFromString(json)

    fun spells(json: String): List<SoaCard> = strict.decodeFromString(json)

    /** `"id": new Hero("Name", [[a,b],…], "title", stars, Pack.X, [Trait.A, …])` */
    private val heroLine = Regex(
        listOf(
            """"(?<id>\w+)":\s*new Hero\(\s*""",
            """"(?<name>[^"]+)",\s*(?<stats>\[\[[\d,\s\[\]]+]]),\s*"(?<title>[^"]+)",\s*""",
            """(?<stars>\d+),\s*Pack\.(?<pack>\w+),\s*\[(?<traits>[^\]]*)]\s*\)""",
        ).joinToString(""),
    )

    /** Reads the `heroes` object of states.ts (the `oldHeroes` 1st-printing table is ignored). */
    fun heroes(statesTs: String): List<SoaHero> {
        val start = statesTs.indexOf("export const heroes")
        val end = statesTs.indexOf("export const oldHeroes")
        require(start >= 0) { "states.ts: 'export const heroes' not found" }
        val block = if (end > start) statesTs.substring(start, end) else statesTs.substring(start)
        return heroLine.findAll(block).map { m ->
            fun group(name: String) = requireNotNull(m.groups[name]).value
            SoaHero(
                id = group("id"),
                name = group("name"),
                stats = Json.parseToJsonElement(group("stats")).jsonArray.map { pair ->
                    pair.jsonArray.map { it.jsonPrimitive.int }
                },
                title = group("title"),
                stars = group("stars").toInt(),
                pack = group("pack"),
                traits = group("traits").split(',').map { it.trim().removePrefix("Trait.") }.filter { it.isNotEmpty() },
            )
        }.toList()
    }
}

/* ---- Source 3: goa2-faq -------------------------------------------------------------------- */

@Serializable
data class FaqHero(
    val id: String,
    @SerialName("class") val title: String,
    val name: String,
    val icon: String? = null,
    val expansion: String,
    val complexity: Int,
    val roles: List<String>,
    val stats: Map<String, JsonElement>,
    val cards: String,
    val lore: String? = null,
    val advice: String? = null,
    val spellbook: String? = null,
) {
    /** `8` or `[5, 6]` → `[base, max]`. */
    fun stat(name: String): List<Int>? = when (val v = stats[name]) {
        null -> null
        is JsonPrimitive -> listOf(v.int, v.int)
        else -> v.jsonArray.map { it.jsonPrimitive.int }
    }
}

@Serializable
data class FaqCard(
    val id: String,
    val name: String,
    val color: String,
    val type: String,
    val tier: String? = null,
    val text: String,
    val alternative: Boolean? = null,
    val school: String? = null,
)

@Serializable
data class FaqEntry(val q: String, val a: String, val ref: List<String> = emptyList())

object FaqParser {
    private val lenient = Json { ignoreUnknownKeys = true }

    fun heroes(json: String): List<FaqHero> = lenient.decodeFromString(fixMojibake(json))

    fun cards(json: String): List<FaqCard> = lenient.decodeFromString(fixMojibake(json))

    fun faqs(json: String): Map<String, FaqEntry> = lenient.decodeFromString(fixMojibake(json))

    private val mojibake = mapOf(
        "â€™" to "’", "â€˜" to "‘", "â€œ" to "“", "â€\u009d" to "”", "â€" + '"' to "”",
        "â€”" to "—", "â€“" to "–", "â€¦" to "…", "Ã©" to "é", "Ã¨" to "è", "Ã " to "à",
    )

    /** Repairs UTF-8 text that was decoded as Windows-1252 somewhere upstream (`â€™` → `’`). */
    fun fixMojibake(text: String): String {
        if ('â' !in text && 'Ã' !in text) return text
        return mojibake.entries.fold(text) { acc, (bad, good) -> acc.replace(bad, good) }
    }
}

/* ---- Source 4: colefin8 CSV ---------------------------------------------------------------- */

/** One CSV row keyed by header name (column order differs between files). */
data class ColeRow(val fields: Map<String, String>) {
    operator fun get(key: String): String? = fields[key]?.trim()?.takeIf { it.isNotEmpty() }
}

object Csv {
    /** RFC-4180 style: quoted fields may contain commas, `""` escapes and newlines. */
    fun parse(text: String): List<ColeRow> {
        val records = mutableListOf<List<String>>()
        var field = StringBuilder()
        var record = mutableListOf<String>()
        var quoted = false
        var i = 0
        val src = text.replace("\r\n", "\n").removePrefix("")
        while (i < src.length) {
            val c = src[i]
            when {
                quoted && c == '"' && i + 1 < src.length && src[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }

                c == '"' -> quoted = !quoted

                !quoted && c == ',' -> {
                    record += field.toString()
                    field = StringBuilder()
                }

                !quoted && c == '\n' -> {
                    record += field.toString()
                    field = StringBuilder()
                    if (record.any { it.isNotBlank() }) records += record
                    record = mutableListOf()
                }

                else -> field.append(c)
            }
            i++
        }
        record += field.toString()
        if (record.any { it.isNotBlank() }) records += record
        if (records.isEmpty()) return emptyList()
        val header = records.first().map { it.trim() }
        return records.drop(1).map { row ->
            ColeRow(header.zip(row + List(maxOf(0, header.size - row.size)) { "" }).toMap())
        }
    }
}
