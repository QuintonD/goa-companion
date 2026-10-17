package dev.quinton.goa.core.domain.draft

import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.match.Team
import java.io.File

/** Synthetic ids only — tests never use real game data. */
object DraftFixtures {
    val heroes: List<String> = (1..40).map { "hero-%02d".format(it) }

    /** The bundled methods file, read from the app's assets so tests guard the shipped data. */
    val bundled: DraftMethodsFile by lazy { DraftJson.decodeMethods(bundledFile().readText()) }

    fun method(id: String): DraftMethod = bundled.methods.first { it.id == id }

    private fun bundledFile(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "app/src/main/assets/catalog/draft-methods.json")
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        error("draft-methods.json not found above ${System.getProperty("user.dir")}")
    }

    fun seats(red: Int, blue: Int, pilots: Boolean = true): List<Seat> =
        (0 until red).map { Seat(Team.RED, it, if (pilots) "p-red-$it" else null) } +
            (0 until blue).map { Seat(Team.BLUE, it, if (pilots) "p-blue-$it" else null) }

    fun setup(
        method: DraftMethod,
        seats: List<Seat> = seats(2, 2),
        teamA: Team = Team.RED,
        candidates: List<String> = heroes,
        seed: Long = 7L,
    ) = DraftSetup(
        id = "draft-1",
        createdAt = 1_000L,
        method = method,
        seats = seats,
        teamA = teamA,
        candidates = candidates,
        seed = seed,
    )

    /** "Ban A · Pick A · …" — compact notation for asserting sequences. */
    fun List<SlotStep>.notation(): String = joinToString(" · ") {
        (if (it.action == DraftAction.BAN) "Ban " else "Pick ") + it.team.name
    }
}
