package dev.quinton.goa.core.data.trivia

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.PlayerMergeResult
import dev.quinton.goa.core.data.player.RoomPlayerRepository
import dev.quinton.goa.core.database.GoaDatabase
import dev.quinton.goa.core.database.toModel
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.sync.Deletion
import dev.quinton.goa.core.model.sync.DeletionKind
import dev.quinton.goa.core.model.trivia.TriviaRound
import dev.quinton.goa.core.model.trivia.TriviaRoundKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Trivia rounds are stored like the match log: stamped, tombstoned on delete, read through merges. */
@RunWith(RobolectricTestRunner::class)
class RoomTriviaRoundRepositoryTest {
    private val db = GoaDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
    private var now = 10_000L
    private val clock = EditClock { now }
    private val rounds = RoomTriviaRoundRepository(db, clock)
    private val players = RoomPlayerRepository(db, clock)

    @After
    fun tearDown() = db.close()

    private fun round(id: String, playerId: String = "ann", playedAt: Long = 5_000, correct: Int = 15) = TriviaRound(
        id = id,
        playerId = playerId,
        kind = TriviaRoundKind.CHALLENGE,
        playedAt = playedAt,
        length = 20,
        questions = 20,
        correct = correct,
        bestStreak = 7,
        answerMillis = 95_000,
        heroCount = 32,
        catalogVersion = 7,
    )

    @Test
    fun `a round is stored as written, stamped with the write time, oldest first`() = runTest {
        rounds.add(round("late", playedAt = 9_000))
        now = 11_000
        rounds.add(round("early", playedAt = 1_000))
        val stored = rounds.observeRounds().first()
        assertThat(stored.map { it.id }).containsExactly("early", "late").inOrder()
        assertThat(stored.first()).isEqualTo(round("early", playedAt = 1_000).copy(updatedAt = 11_000))
        assertThat(stored.last().updatedAt).isEqualTo(10_000)
    }

    @Test
    fun `writing a round again never goes back in time, even with the clock behind`() = runTest {
        rounds.add(round("r1"))
        now = 2_000 // the clock moved back
        rounds.add(round("r1"))
        assertThat(rounds.observeRounds().first().single().updatedAt).isEqualTo(10_001)
    }

    @Test
    fun `delete leaves a tombstone later than the round, and a later write beats it`() = runTest {
        rounds.add(round("r1"))
        rounds.add(round("r2"))
        rounds.delete("r1")
        assertThat(rounds.observeRounds().first().map { it.id }).containsExactly("r2")
        val tombstone = db.deletionDao().getAll().single().toModel()
        assertThat(tombstone).isEqualTo(Deletion(DeletionKind.TRIVIA_ROUND, "r1", 10_001))

        // Deleting an unknown round writes nothing.
        rounds.delete("nope")
        assertThat(db.deletionDao().getAll()).hasSize(1)

        // A round written again after its deletion (say, restored) is stamped after the tombstone.
        rounds.add(round("r1"))
        assertThat(rounds.observeRounds().first().first { it.id == "r1" }.updatedAt).isEqualTo(10_002)
    }

    @Test
    fun `a merged duplicate's rounds read as the player it was merged into, stored as recorded`() = runTest {
        players.upsert(Player("ann", "Ann", 1, 0, createdAt = 1))
        players.upsert(Player("ann-2", "Ann", 1, 0, createdAt = 2))
        rounds.add(round("r1", playerId = "ann"))
        rounds.add(round("r2", playerId = "ann-2"))
        assertThat(players.merge("ann-2", "ann")).isEqualTo(PlayerMergeResult.Merged)

        assertThat(rounds.observeRounds().first().map { it.playerId }).containsExactly("ann", "ann")
        assertThat(db.triviaRoundDao().getAll().map { it.playerId }).containsExactly("ann", "ann-2")
    }
}
