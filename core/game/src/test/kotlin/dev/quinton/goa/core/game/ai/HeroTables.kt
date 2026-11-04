package dev.quinton.goa.core.game.ai

import java.io.File

/**
 * Per-hero evaluation tables by name for the `table=` variant key (ADR-063): a file of
 * `<hero>.<TERM>=<scale>` lines (`#` comments), by path or under `AI_TABLES_DIR`
 * (default `D:/.goa-qa/v14/d/tables`). No table ships: no entry confirmed (AI.md "Stream D").
 */
object HeroTables {
    private val DIR = File(System.getenv("AI_TABLES_DIR") ?: "D:/.goa-qa/v14/d/tables")

    internal fun named(name: String): Map<String, Map<Term, Double>> {
        val f = File(name).takeIf { it.isAbsolute || it.exists() } ?: File(DIR, "$name.txt")
        return parse(f.readLines())
    }

    internal fun parse(lines: List<String>): Map<String, Map<Term, Double>> {
        val out = LinkedHashMap<String, Map<Term, Double>>()
        for (raw in lines) {
            val line = raw.substringBefore("#").trim()
            if (line.isEmpty()) continue
            val (key, v) = line.split("=")
            val (hero, term) = key.trim().split(".")
            out[hero] = out[hero].orEmpty() + (Term.valueOf(term) to v.trim().toDouble())
        }
        return out
    }

    /** The variant keys of a table (`+h.<hero>.<TERM>=<scale>` each). */
    internal fun keys(table: Map<String, Map<Term, Double>>): String =
        table.entries.joinToString("") { (hero, row) -> row.entries.joinToString("") { (t, v) -> "+h.$hero.$t=$v" } }
}
