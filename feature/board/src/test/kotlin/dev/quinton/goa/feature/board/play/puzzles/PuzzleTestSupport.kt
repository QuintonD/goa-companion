package dev.quinton.goa.feature.board.play.puzzles

import dev.quinton.goa.core.data.puzzle.PuzzleProgress
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.game.puzzles.EnginePuzzleBook
import dev.quinton.goa.core.game.puzzles.Move
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.book.Puzzles
import dev.quinton.goa.feature.board.bundledCatalog
import dev.quinton.goa.feature.board.bundledMaps
import dev.quinton.goa.feature.board.play.table.TableUi

/** The real puzzle book on the real engine with the bundled catalog and maps, built once. */
val realPuzzleBook: EnginePuzzleBook by lazy { EnginePuzzleBook(GameEngine(bundledCatalog, bundledMaps)) }

/**
 * One phone's puzzle progress for every screenshot that shows it (the Play tab and the puzzle list agree): the
 * first position solved, the second tried three times with two nudges and the solution seen, the first full
 * round solved.
 */
fun screenshotPuzzleProgress(): Map<String, PuzzleProgress> {
    val (rounds, positions) = realPuzzleBook.puzzles.partition { it.fullRound }
    return buildMap {
        positions.getOrNull(0)?.let { put(it.id, PuzzleProgress(solved = true, solvedAt = 1, attempts = 1)) }
        positions.getOrNull(1)?.let { put(it.id, PuzzleProgress(attempts = 3, nudgesUsed = 2, solutionShown = true)) }
        rounds.getOrNull(0)?.let { put(it.id, PuzzleProgress(solved = true, solvedAt = 1, attempts = 2)) }
    }
}

/** The puzzle file behind a book entry (its stored solution and wrong tries). */
fun puzzleFile(id: String): Puzzle = (Puzzles.all + Puzzles.fullRounds).first { it.info.id == id }

/** The option a stored move stands for at the table's pending question (as the engine's test resolves it). */
fun TableUi.choiceOf(move: Move): Choice {
    val d = requireNotNull(decision) { "No question pending for $move" }
    val choice = when (move) {
        is Move.Pick -> move.choice

        is Move.UnitAt -> (
            view.heroes.firstOrNull { it.at == move.hex }?.heroId
                ?: view.minions.firstOrNull { it.at == move.hex }?.id
                ?: view.pieces.firstOrNull { it.at == move.hex && it.isUnit }?.id
            )?.let { Choice.Unit(it) }

        is Move.TokenAt -> view.pieces.firstOrNull { it.at == move.hex && !it.isUnit }?.let { Choice.Token(it.id) }

        Move.DeclineMove -> d.options.firstOrNull { it is Choice.Decline }

        is Move.OptionAt -> d.options.firstOrNull { it is Choice.Option && it.index == move.index }
    }
    require(choice != null && choice in d.options) { "$move is not offered at ${d.prompt}: ${d.options}" }
    return choice
}
