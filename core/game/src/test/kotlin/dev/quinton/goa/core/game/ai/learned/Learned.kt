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
import dev.quinton.goa.core.game.ai.LevelProfile
import dev.quinton.goa.core.game.ai.NetEvaluator
import dev.quinton.goa.core.game.ai.PositionEvaluator
import dev.quinton.goa.core.game.ai.SearchAi
import dev.quinton.goa.core.game.ai.Searcher
import dev.quinton.goa.core.game.ai.Term
import dev.quinton.goa.core.game.ai.Tournament
import dev.quinton.goa.core.game.ai.learned.v09.BaselineV09
import dev.quinton.goa.core.game.engine.EngineData
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

    /** Appends one tournament game to [f] as a [GameRecord] (replayable: setup and every answer; ADR-063). */
    fun store(f: File, p: Teams.Played, a: String, b: String, family: Long) {
        val r = p.result
        val rec = GameRecord(
            game = (p.spec.seed * 2 + if (p.aRed) 0 else 1).toInt(),
            size = p.spec.lineup.first.size,
            lineup = (p.spec.seed % 100_000).toInt(),
            family = family,
            red = if (p.aRed) a else b,
            blue = if (p.aRed) b else a,
            winner = r.winner?.name,
            rounds = r.rounds,
            reason = r.session.view(null).winReason?.name ?: "UNFINISHED",
            setup = r.session.setup,
            answers = r.session.answers(),
        )
        f.parentFile?.mkdirs()
        f.appendText(JSON.encodeToString(GameRecord.serializer(), rec) + "\n")
    }

    fun readGames(files: List<File>): List<GameRecord> =
        files.flatMap { f ->
            f.readLines().filter { it.isNotBlank() }.map { JSON.decodeFromString(GameRecord.serializer(), it) }
        }

    /**
     * A tournament side: ExperimentTest's variant syntax plus `learned=LEAVES|EVERYWHERE` and `net=<file>`;
     * `@<name>` is an exploiter of [HeroBenchTest.EXPLOITERS] (`@life`, `@hunter`, `@bold`).
     */
    fun side(name: String): Tournament.Side {
        val spec = if (name.startsWith("@")) HeroBenchTest.EXPLOITERS.getValue(name.drop(1)) else name
        val parts = spec.split("+")
        val strength = parts[0].removePrefix("s").toIntOrNull()
            ?: AiLevel.entries.first { it.name.equals(parts[0], true) }.strength
        val p = profile(spec)
        return Tournament.Side(spec) { seed -> SearchAi(AiLevel.of(strength), p, seed, timeCap = false) }
    }

    /** The profile of a [side]. */
    fun profile(spec: String): LevelProfile {
        val parts = spec.split("+")
        val mine = parts.drop(1).filter {
            it.startsWith("learned=") || it.startsWith("net=") || it.startsWith("inner=")
        }
        var p = ExperimentTest.variant((listOf(parts[0]) + parts.drop(1).filter { it !in mine }).joinToString("+"))
        for (o in mine) {
            val (k, v) = o.split("=", limit = 2)
            p = when (k) {
                "learned" -> p.copy(learned = EvalSource.valueOf(v.uppercase()))

                // What scores the one-ply looks inside playouts, apart from the leaves (ADR-060).
                "inner" -> p.copy(innerEvaluator = evaluator(v))

                else -> {
                    // A net at the leaves; a profile with the net in the one-ply looks keeps them.
                    val at = if (p.learned == EvalSource.HAND) EvalSource.LEAVES else p.learned
                    when {
                        // The frozen baseline: the strength 10 of `feature/v0.10`'s first commit (ADR-049).
                        v == "v09" -> p.copy(learned = EvalSource.LEAVES, leafEvaluator = ::BaselineV09)

                        // v0.10's 10, ADR-060's baseline: its net alone (the ensemble's first) at the playout ends.
                        v == "v10" -> p.copy(net = LearnedNet.CURRENT, learned = EvalSource.LEAVES)

                        // An ensemble of saved nets (ADR-060): `avg(a,b,c)`, `lcb1(a,b,c)`, `min(a,b)`.
                        v.contains('(') -> p.copy(leafEvaluator = EnsembleEvaluator.parse(v), learned = at)

                        else -> p.copy(net = net(v), learned = at)
                    }
                }
            }
        }
        return p
    }

    /** A net name or an ensemble as a position evaluator (`inner=`). */
    private fun evaluator(v: String): (EngineData) -> PositionEvaluator =
        EnsembleEvaluator.parse(v) ?: net(v).let { n -> { data -> NetEvaluator(data, n) } }

    /** Splits [s] at [sep] outside parentheses (`AI_TEAMS=s7+net=avg(a,b):s7,…`). */
    fun splitTop(s: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        var depth = 0
        var start = 0
        for ((i, c) in s.withIndex()) {
            when {
                c == '(' -> depth++

                c == ')' -> depth--

                c == sep && depth == 0 -> {
                    out += s.substring(start, i)
                    start = i + 1
                }
            }
        }
        out += s.substring(start)
        return out
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
        val heroes: List<String>,
        val wh: FloatArray,
        val cards: List<String>,
        val wc: FloatArray,
    ) {
        fun toNet() = LearnedNet(inputs, h1, h2, w1, b1, w2, b2, w3, wl, b3, coinScale, heroes, wh, cards, wc)
    }

    /** The heroes and cards of the correction tables, in table order. */
    val HEROES: List<String> by lazy { TestData.engine.data.catalog.heroes.map { it.id }.sorted() }
    val CARDS: List<String> by lazy {
        TestData.engine.data.catalog.heroes.flatMap { h -> h.allCards.map { it.id } }.distinct().sorted()
    }
    private val HERO_INDEX by lazy { HEROES.withIndex().associate { (i, h) -> h to i } }
    private val CARD_INDEX by lazy { CARDS.withIndex().associate { (i, c) -> c to i } }

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
            o.writeUTF(n.heroes.joinToString(" "))
            for (x in n.wh) o.writeFloat(x)
            // Card ids can be long: in pieces, as DataOutput's strings stop at 64 KB.
            o.writeInt(n.cards.size)
            for (c in n.cards) o.writeUTF(c)
            for (x in n.wc) o.writeFloat(x)
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
        val w1 = arr(inputs * h1)
        val b1 = arr(h1)
        val w2 = arr(h1 * h2)
        val b2 = arr(h2)
        val w3 = arr(h2)
        val wl = arr(inputs)
        val b3 = i.readFloat()
        val heroes = i.readUTF().split(" ").filter { it.isNotEmpty() }
        val wh = arr(heroes.size * LearnedNet.HERO_COLS)
        val cards = List(i.readInt()) { i.readUTF() }
        val wc = arr(cards.size * LearnedNet.CARD_PLACES)
        NetData(inputs, h1, h2, w1, b1, w2, b2, w3, wl, b3, scale, schema, heroes, wh, cards, wc)
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
        val extras: Trainer.Extras,
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
        val perHero = FloatArray(st.heroes.size * Features.HERO.size)
        features.extract(st, x, perHero)
        val ctx = hand.context(st)
        val terms = ctx.termsRed()
        return Position(
            r.game, r.lineup, r.size, index, boundary, winner == Team.RED.name,
            ctx.scoreRed().toFloat(), FloatArray(Term.entries.size) { terms[it].toFloat() }, x,
            extras(st, perHero),
        )
    }

    /** What a position gives the correction tables: Red's heroes first, their numbers, each team's cards. */
    private fun extras(st: GameState, perHero: FloatArray): Trainer.Extras {
        val order = st.heroes.indices.sortedBy { if (st.heroes[it].team == Team.RED) 0 else 1 }
        val size = Features.HERO.size
        val v = FloatArray(order.size * size)
        for ((o, i) in order.withIndex()) perHero.copyInto(v, o * size, i * size, (i + 1) * size)
        fun cards(team: Team): ShortArray {
            val out = ArrayList<Short>()
            for (h in st.heroes) {
                if (h.team != team) continue
                for (c in h.cards) {
                    val place = LearnedNet.cardPlace(c)
                    if (place < 0) continue
                    val row = CARD_INDEX[c.id] ?: continue
                    out += (row * LearnedNet.CARD_PLACES + place).toShort()
                }
            }
            return out.toShortArray()
        }
        return Trainer.Extras(
            ByteArray(order.size) { HERO_INDEX.getValue(st.heroes[order[it]].id).toByte() },
            order.count { st.heroes[it].team == Team.RED },
            v,
            cards(Team.RED),
            cards(Team.BLUE),
        )
    }
}
