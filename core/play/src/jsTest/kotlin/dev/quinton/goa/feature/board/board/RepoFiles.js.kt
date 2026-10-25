package dev.quinton.goa.feature.board.board

/** Node only (the JS tests run on Node). */
actual fun readRepoFile(path: String): String {
    val root = js("process.env.GOA_REPO_ROOT") as String? ?: error("GOA_REPO_ROOT is not set")
    val fs = js("require('fs')")
    return fs.readFileSync("$root/$path", "utf8") as String
}
