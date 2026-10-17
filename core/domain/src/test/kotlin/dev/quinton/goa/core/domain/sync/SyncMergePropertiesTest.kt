package dev.quinton.goa.core.domain.sync

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.rating.EloCalculator
import dev.quinton.goa.core.domain.sync.RandomSync.doc
import kotlin.random.Random
import org.junit.Test

/**
 * The guarantees group sync rests on (ADR-025), over seeded random phone data with many shared
 * ids and exact `updatedAt` ties: whatever order phones sync in, they end with the same data and
 * the same ratings.
 */
class SyncMergePropertiesTest {
    private val runs = 300

    @Test
    fun `merge is commutative`() = repeat(runs) { seed ->
        val random = Random(seed)
        val a = random.doc()
        val b = random.doc()
        assertThat(SyncMerge.merge(a, b)).isEqualTo(SyncMerge.merge(b, a))
    }

    @Test
    fun `merge is associative`() = repeat(runs) { seed ->
        val random = Random(seed)
        val (a, b, c) = List(3) { random.doc() }
        assertThat(SyncMerge.merge(SyncMerge.merge(a, b), c)).isEqualTo(SyncMerge.merge(a, SyncMerge.merge(b, c)))
    }

    @Test
    fun `merge is idempotent and merging a known document changes nothing`() = repeat(runs) { seed ->
        val random = Random(seed)
        val a = SyncMerge.normalize(random.doc())
        val b = random.doc()
        val ab = SyncMerge.merge(a, b)
        assertThat(SyncMerge.merge(a, a)).isEqualTo(a)
        assertThat(SyncMerge.merge(ab, b)).isEqualTo(ab)
        assertThat(SyncMerge.merge(ab, a)).isEqualTo(ab)
        assertThat(SyncMerge.changes(ab, SyncMerge.merge(ab, a)).isEmpty).isTrue()
    }

    @Test
    fun `any order of pairwise syncs converges on every phone`() = repeat(100) { seed ->
        val random = Random(seed)
        val initial = List(random.nextInt(2, 6)) { random.doc() }
        val phones = initial.toMutableList()
        // Random gossip: some pairs sync, in any order, some more than once.
        repeat(random.nextInt(0, 12)) {
            val i = random.nextInt(phones.size)
            val j = random.nextInt(phones.size)
            val merged = SyncMerge.merge(phones[i], phones[j])
            phones[i] = merged
            phones[j] = merged
        }
        // Then everyone syncs along a chain, there and back (each phone talks to its neighbours only).
        val order = phones.indices.shuffled(random)
        (order.zipWithNext() + order.reversed().zipWithNext()).forEach { (i, j) ->
            val merged = SyncMerge.merge(phones[i], phones[j])
            phones[i] = merged
            phones[j] = merged
        }
        val expected = SyncMerge.mergeAll(initial)
        phones.forEach { assertThat(it).isEqualTo(expected) }
        assertThat(SyncMerge.mergeAll(initial.shuffled(random))).isEqualTo(expected)
    }

    @Test
    fun `ratings replayed after convergence are identical on every phone`() = repeat(100) { seed ->
        val random = Random(seed)
        val initial = List(4) { random.doc() }
        val phones = initial.indices.shuffled(random).map { first ->
            // Each phone reaches the same state by its own route.
            (listOf(initial[first]) + initial.shuffled(random)).reduce(SyncMerge::merge)
        }
        val results = phones.map { doc ->
            val merges = PlayerMerges(doc.playerMerges)
            EloCalculator.replay(merges.resolve(doc.matches), doc.eloConfig.config)
        }
        results.forEach { assertThat(it).isEqualTo(results.first()) }
    }

    @Test
    fun `a record survives unless a tombstone at least as new exists`() = repeat(runs) { seed ->
        val random = Random(seed)
        val merged = SyncMerge.mergeAll(List(3) { random.doc() })
        val deleted = merged.deletions.associate { (it.kind to it.id) to it.deletedAt }
        merged.matches.forEach { m ->
            deleted[dev.quinton.goa.core.model.sync.DeletionKind.MATCH to m.id]?.let {
                assertThat(m.updatedAt).isGreaterThan(it)
            }
        }
        assertThat(merged.matches.map { it.id }).isInStrictOrder()
        assertThat(merged.players.map { it.id }).isInStrictOrder()
    }
}
