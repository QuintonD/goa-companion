package dev.quinton.goa.core.game.ai.learned

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.EvalSource
import dev.quinton.goa.core.game.ai.Evaluator
import dev.quinton.goa.core.game.ai.ExperimentTest
import dev.quinton.goa.core.game.ai.Features
import dev.quinton.goa.core.game.ai.LearnedNet
import dev.quinton.goa.core.game.ai.SearchAi
import dev.quinton.goa.core.game.ai.Searcher
import dev.quinton.goa.core.game.ai.Term
import dev.quinton.goa.core.game.ai.Tournament
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.SavedPayload
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.match.Team
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Test-side helpers of the learned evaluation (AI.md "Learned evaluation"). */
internal object Learned {
    val JSON = Json {
        ignoreUnknownKeys = true
        allowStructuredMapKeys = true
    }

    /** One self-play game: enough to replay it exactly. */
    @Serializable
    class GameRecord(
        val game: Int,
        val size: Int,
        val lineup: Int,
        val family: Long,
        val red: String,
        val blue: String,
        /** RED, BLUE or null (unfinished). */
        val winner: String?,
        val rounds: Int,
        val reason: String,
        val setup: GameSetup,
        val answers: List<Choice>,
    )

    /** The answers given so far, in order (from the save). */
    fun EngineSession.answers(): List<Choice> =
        EngineSession.JSON.decodeFromString(SavedPayload.serializer(), save().payload).answers.map { it.choice }

    fun readGames(files: List<File>): List<GameRecord> =
        files.flatMap { f ->
            f.readLines().filter { it.isNotBlank() }.map { JSON.decodeFromString(GameRecord.serializer(), it) }
        }

    /** A tournament side: ExperimentTest's variant syntax plus `learned=LEAVES|EVERYWHERE` and `net=<file>`. */
    fun side(spec: String): Tournament.Side {
        val parts = spec.split("+")
        val strength = parts[0].removePrefix("s").toIntOrNull()
            ?: AiLevel.entries.first { it.name.equals(parts[0], true) }.strength
        val mine = parts.drop(1).filter { it.startsWith("learned=") || it.startsWith("net=") }
        var p = ExperimentTest.variant((listOf(parts[0]) + parts.drop(1).filter { it !in mine }).joinToString("+"))
        for (o in mine) {
            val (k, v) = o.split("=", limit = 2)
            p = when (k) {
                "learned" -> p.copy(learned = EvalSource.valueOf(v.uppercase()))

                else -> p.copy(
                    net = net(v),
                    learned = if (p.learned ==
                        EvalSource.HAND
                    ) {
                        EvalSource.LEAVES
                    } else {
                        p.learned
                    },
                )
            }
        }
        return Tournament.Side(spec) { seed -> SearchAi(AiLevel.of(strength), p, seed, timeCap = false) }
    }

    private val nets = ConcurrentHashMap<String, LearnedNet>()

    /** A net saved by [saveNet]; a bare name is looked up under the nets directory. */
    fun net(path: String): LearnedNet = nets.getOrPut(path) {
        val f = File(path).takeIf { it.isAbsolute || it.exists() } ?: File(NETS, "$path.net")
        loadNet(f)
    }

    val NETS = File(System.getenv("AI_NETS") ?: "D:/.goa-qa/v09/a2/nets")

    class NetData(
        val inputs: Int,
        val h1: Int,
        val h2: Int,
        val w1: FloatArray,
        val b1: FloatArray,
        val w2: FloatArray,
        val b2: FloatArray,
        val w3: FloatArray,
        val wl: FloatArray,
        val b3: Float,
        val coinScale: Double,
        val schema: Long,
    ) {
        fun toNet() = LearnedNet(inputs, h1, h2, w1, b1, w2, b2, w3, wl, b3, coinScale)
    }

    fun saveNet(n: NetData, f: File) {
        f.parentFile.mkdirs()
        DataOutputStream(f.outputStream().buffered()).use { o ->
            o.writeLong(n.schema)
            o.writeInt(n.inputs)
            o.writeInt(n.h1)
            o.writeInt(n.h2)
            o.writeDouble(n.coinScale)
            for (a in listOf(n.w1, n.b1, n.w2, n.b2, n.w3, n.wl)) for (x in a) o.writeFloat(x)
            o.writeFloat(n.b3)
        }
    }

    fun loadNetData(f: File): NetData = DataInputStream(f.inputStream().buffered()).use { i ->
        val schema = i.readLong()
        check(schema == Features.SCHEMA_HASH) { "$f was trained on another feature schema" }
        val inputs = i.readInt()
        val h1 = i.readInt()
        val h2 = i.readInt()
        val scale = i.readDouble()
        fun arr(n: Int) = FloatArray(n) { i.readFloat() }
        NetData(
            inputs, h1, h2,
            arr(
                inputs * h1,
            ),
            arr(h1), arr(h1 * h2), arr(h2), arr(h2), arr(inputs), i.readFloat(), scale, schema,
        )
    }

    fun loadNet(f: File): LearnedNet = loadNetData(f).toNet()

    /** One recorded position of a game. */
    class Position(
        val game: Int,
        val lineup: Int,
        val size: Int,
        /** Order within its game. */
        val index: Int,
        val boundary: Boolean,
        val redWon: Boolean,
        /** The tuned hand evaluation, Red's view, and its twelve terms. */
        val hand: Float,
        val terms: FloatArray,
        /** Red's then Blue's features. */
        val x: FloatArray,
    )

    /**
     * Replays [r] and records positions: every turn boundary (the first decision of a new turn, what
     * a playout to the end of the turn ends on) and, with probability [resolvedRate], the decision
     * after a card has resolved (what a one-ply look inside a playout sees). Unfinished games give none.
     */
    fun positions(r: GameRecord, resolvedRate: Double): List<Position> {
        val winner = r.winner ?: return emptyList()
        val data = TestData.engine.data
        val features = Features(data)
        // The hand teacher the checked-in net was trained with (v0.8's TUNED; no team-size knobs).
        val hand = Evaluator(data, Features.KNOBS.copy(scales = Features.SCALES))
        val s = TestData.engine.newGame(r.setup) as EngineSession
        val out = ArrayList<Position>()
        val rng = kotlin.random.Random(r.game * 7919L + r.family)
        var lastTurn = -1
        var lastResolved = 0
        for (c in r.answers) {
            val d = s.pending ?: break
            val st = s.state
            val turn = Searcher.turnIndex(st)
            val resolved = st.heroes.sumOf { h -> h.cards.count { it.zone == Zone.RESOLVED } }
            val boundary = turn != lastTurn
            val after = !boundary && resolved > lastResolved && rng.nextDouble() < resolvedRate
            if ((boundary || after) &&
                st.winner == null
            ) {
                out += position(r, out.size, boundary, winner, st, features, hand)
            }
            lastTurn = turn
            lastResolved = resolved
            // A game recorded before an engine fix may no longer replay: keep what was played before.
            if (s.answer(d.id, c) !is AnswerResult.Accepted) {
                println("LEARNED game ${r.game} no longer replays after ${out.size} positions; kept those")
                break
            }
        }
        return out
    }

    private fun position(
        r: GameRecord,
        index: Int,
        boundary: Boolean,
        winner: String,
        st: GameState,
        features: Features,
        hand: Evaluator,
    ): Position {
        val x = FloatArray(2 * Features.SIZE)
        features.extract(st, x)
        val ctx = hand.context(st)
        val terms = ctx.termsRed()
        return Position(
            r.game, r.lineup, r.size, index, boundary, winner == Team.RED.name,
            ctx.scoreRed().toFloat(), FloatArray(Term.entries.size) { terms[it].toFloat() }, x,
        )
    }
}
