package dev.quinton.goa.feature.board.board

import java.io.File

actual fun readRepoFile(path: String): String = File(System.getenv("GOA_REPO_ROOT") ?: "../..", path).readText()
