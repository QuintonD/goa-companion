package dev.quinton.goa.server.game.e2e

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.CreateRoom
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.OnlineRoutes
import dev.quinton.goa.core.online.RoomCreated
import dev.quinton.goa.core.online.RoomStatus
import dev.quinton.goa.core.online.RoomView
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.feature.board.play.online.decodeServerFrame
import dev.quinton.goa.feature.board.play.online.encodeClientFrame
import dev.quinton.goa.server.game.room.RandomGames
import dev.quinton.goa.server.game.room.TestRules
import java.io.File
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * How many rooms fit: `main` in its own JVM at -Xmx512m, as compose.yaml runs it, with bots on real sockets.
 * Opt-in, as it takes minutes: `gradlew :server:game:test --tests "*LoadE2eTest*" -Pgoa.e2e.load=true`.
 * Bots here are the wire and nothing more (a decoder and a random answer): the numbers are the server's, not
 * a hundred drivers' on one machine. `-Pgoa.e2e.load.bots=rooms,bigRooms,minMillis,maxMillis,answersPerRoom`.
 * The numbers go to `server/game/build/e2e-load.txt` as they come, so a run cut short still says something.
 */
class LoadE2eTest {
    private class Numbers {
        val latency = ConcurrentLinkedQueue<Long>()
        val frameBytes = ConcurrentLinkedQueue<Int>()
        val answers = AtomicInteger()
        val refused = AtomicInteger()
        val closes = AtomicInteger()
        val finished = AtomicInteger()
        val failures = ConcurrentLinkedQueue<String>()
        val decodeNanos = AtomicLong()
    }

    /** A bot in the room: its ticket was sent at [sentAt], and the ack closes the measure. */
    private inner class Bot(
        val token: String,
        val hero: String,
        val base: () -> String,
        val numbers: Numbers,
        val scope: CoroutineScope,
        val random: Random,
        val minMillis: Long,
        val maxMillis: Long,
        val answerLimit: Int,
        val roomAnswers: AtomicInteger,
    ) : WebSocket.Listener {
        private val partial = StringBuilder()
        private val sendLock = Any()
        private var pendingSend: CompletableFuture<*> = CompletableFuture.completedFuture(null)
        private val sentAt = ConcurrentHashMap<Long, Long>()

        @Volatile
        var socket: WebSocket? = null

        @Volatile
        var lastTicket = 0L

        @Volatile
        var gameId: String? = null
        var done = CompletableFuture<Unit>()

        suspend fun connect() {
            val url = URI(base().replaceFirst("http", "ws") + OnlineRoutes.PLAY)
            val opened = jdkHttp.newWebSocketBuilder().buildAsync(url, this).await()
            socket = opened
            say(
                ClientMessage.Hello(
                    OnlineProtocol.VERSION,
                    token,
                    "load/1",
                    TestRules.engine.data.catalog.dataVersion,
                ),
            )
        }

        fun say(message: ClientMessage) {
            val text = encodeClientFrame(message)
            synchronized(sendLock) {
                pendingSend = pendingSend.handle { _, _ -> socket?.sendText(text, true)?.join() }
            }
        }

        override fun onText(socket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            partial.append(data)
            if (last) {
                val raw = partial.toString()
                partial.clear()
                numbers.frameBytes += raw.length
                val began = System.nanoTime()
                val frame = decodeServerFrame(raw)
                numbers.decodeNanos.addAndGet(System.nanoTime() - began)
                on(frame)
            }
            socket.request(1)
            return null
        }

        private fun on(frame: ServerMessage) {
            when (frame) {
                is ServerMessage.Ack -> {
                    val at = sentAt.remove(frame.ticket)
                    if (at != null) numbers.latency += System.nanoTime() - at
                    if (!frame.accepted) numbers.refused.incrementAndGet()
                }

                is ServerMessage.GameStart -> gameId = frame.gameId

                is ServerMessage.Table -> ask(frame.decision)

                is ServerMessage.End -> {
                    numbers.finished.incrementAndGet()
                    done.complete(Unit)
                }

                is ServerMessage.Refused -> numbers.failures += "refused: $frame"

                else -> Unit
            }
        }

        private fun ask(question: Decision?) {
            if (question == null || question.options.isEmpty() || question.seat != hero) return
            if (question.id == lastTicket) return
            lastTicket = question.id
            scope.launch {
                delay(random.nextLong(minMillis, maxMillis))
                val game = gameId ?: return@launch
                if (lastTicket != question.id) return@launch
                val choice: Choice = RandomGames.choose(question, random)
                sentAt[question.id] = System.nanoTime()
                say(ClientMessage.Answer(game, question.id, choice))
                numbers.answers.incrementAndGet()
                if (roomAnswers.incrementAndGet() >= answerLimit) done.complete(Unit)
            }
        }

        override fun onClose(socket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            numbers.closes.incrementAndGet()
            if (statusCode != WebSocket.NORMAL_CLOSURE) numbers.failures += "closed $statusCode $reason"
            return null
        }

        override fun onError(socket: WebSocket, error: Throwable) {
            numbers.closes.incrementAndGet()
            numbers.failures += "socket error: ${error.javaClass.simpleName}"
        }
    }

    private class Params(text: String?) {
        private val values = text?.split(',')?.map { it.toLong() }.orEmpty()
        val rooms = values.getOrElse(0) { 20 }.toInt()
        val bigRooms = values.getOrElse(1) { 3 }.toInt()
        val minMillis = values.getOrElse(2) { 300 }
        val maxMillis = values.getOrElse(3) { 1_000 }
        val answersPerRoom = values.getOrElse(4) { 300 }.toInt()
    }

    private suspend fun Host.post(path: String, token: String?, body: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI(base + path))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json")
        token?.let { request.header("Authorization", "Bearer $it") }
        return jdkHttp.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString()).await()
    }

    private suspend fun Host.account(name: String): String {
        val response = post(OnlineRoutes.ACCOUNTS, null, """{"name":"$name"}""")
        check(response.statusCode() == CREATED) { "No account: ${response.body()}" }
        return OnlineProtocol.json.decodeFromString(
            dev.quinton.goa.core.online.AccountCreated.serializer(),
            response.body(),
        ).token
    }

    /** One room at a table size, its seats filled and started over the same sockets the bots then play on. */
    private suspend fun Host.fillRoom(
        n: Int,
        size: Int,
        params: Params,
        numbers: Numbers,
        scope: CoroutineScope,
    ): List<Bot> {
        val heroes = TestRules.engine.playableHeroes.sorted().shuffled(Random(n)).take(size * 2)
        val tokens = heroes.indices.map { account("Bot $n $it") }
        val map = if (size >= 4) ACROSS_THE_RIVER else FORGOTTEN_ISLAND
        val length = if (size >= 4) GameLength.QUICK else GameLength.LONG
        val body = OnlineProtocol.json.encodeToString(
            CreateRoom.serializer(),
            CreateRoom(map, length, size, ranked = true),
        )
        val made = post(OnlineRoutes.ROOMS, tokens.first(), body)
        check(made.statusCode() == CREATED) { "No room: ${made.body()}" }
        val code = OnlineProtocol.json.decodeFromString(RoomCreated.serializer(), made.body()).code
        val roomAnswers = AtomicInteger()
        val seen = HashSet<String>()
        val bots = tokens.zip(heroes).mapIndexed { i, (token, hero) ->
            Bot(
                token,
                hero,
                { base },
                numbers,
                scope,
                Random(n * SEED_STEP + i),
                params.minMillis,
                params.maxMillis,
                params.answersPerRoom,
                roomAnswers,
            )
        }
        for (bot in bots) {
            val joined = post(OnlineRoutes.join(code), bot.token, "")
            check(joined.statusCode() == OK) { "Couldn't join: ${joined.body()}" }
            val view = OnlineProtocol.json.decodeFromString(RoomView.serializer(), joined.body())
            // The only seat taken since the last look at the room is this bot's: join seats whoever comes in.
            val seat = view.seats.first { it.accountId != null && it.accountId !in seen }
            seen += checkNotNull(seat.accountId)
            bot.connect()
            bot.say(ClientMessage.PickSeat(code, seat.team, seat.index, bot.hero))
        }
        delay(SETTLE_MILLIS)
        bots.forEach { it.say(ClientMessage.Ready(code, true)) }
        delay(SETTLE_MILLIS)
        bots.first().say(ClientMessage.Start(code))
        withTimeoutOrNull(WAIT_MILLIS) {
            while (bots.any { it.gameId == null }) delay(SETTLE_MILLIS)
        } ?: error("Room $n didn't start: ${RoomStatus.OPEN}")
        return bots
    }

    /** To the file as well: Gradle shows a test's output only when it has ended. */
    private fun say(line: String) {
        println(line)
        File("build/e2e-load.txt").appendText("$line\n")
    }

    private fun percentile(sorted: List<Long>, p: Double): Long =
        if (sorted.isEmpty()) 0 else sorted[((sorted.size - 1) * p).toInt()]

    private fun report(host: ServerProcess, numbers: Numbers, wallMillis: Long, cpuAtStart: Long, label: String) {
        val latency = numbers.latency.toList().sorted()
        val bytes = numbers.frameBytes.toList()
        val cpu = host.cpuMillis - cpuAtStart
        say(
            "load: $label: ${numbers.answers.get()} answers in ${wallMillis / MILLIS_PER_SECOND} s, " +
                "${numbers.answers.get() * MILLIS_PER_SECOND / wallMillis.coerceAtLeast(1)} answers/s, " +
                "ack p50 ${percentile(latency, P50) / NANOS_PER_MILLI} ms, " +
                "p99 ${percentile(latency, P99) / NANOS_PER_MILLI} ms, " +
                "max ${(latency.lastOrNull() ?: 0) / NANOS_PER_MILLI} ms; " +
                "server cpu ${cpu / MILLIS_PER_SECOND} s = " +
                "${cpu * PERCENT / wallMillis.coerceAtLeast(1)}% of one core; " +
                "frames ${bytes.size}, mean ${bytes.sum() / bytes.size.coerceAtLeast(1)} B, " +
                "max ${bytes.maxOrNull() ?: 0} B, total ${bytes.sumOf { it.toLong() } / BYTES_PER_MB} MB; " +
                "refused ${numbers.refused.get()}, closes ${numbers.closes.get()}, finished ${numbers.finished.get()}",
        )
    }

    @Test
    fun whatFitsIn512Megabytes(): Unit = runBlocking {
        assumeTrue("Opt-in: -Pgoa.e2e.load=true", System.getProperty("goa.e2e.load") == "true")
        File("build/e2e-load.txt").delete()
        val params = Params(System.getProperty("goa.e2e.load.bots"))
        val pool = Executors.newFixedThreadPool(BOT_THREADS) { task ->
            Thread(task, "load-bot").apply { isDaemon = true }
        }
        val scope = CoroutineScope(SupervisorJob() + pool.asCoroutineDispatcher())
        val cap = params.rooms + params.bigRooms + 1
        ServerProcess(env = mapOf("GOA_GAME_MAX_ROOMS" to cap.toString())).use { host ->
            val numbers = Numbers()
            val started = host.startProcess()
            say("load: server up in $started ms; ${host.heapAfterGc().lines().drop(1).joinToString(" ")}")
            val filling = Semaphore(FILLING_AT_ONCE)
            val rooms = ArrayList<List<Bot>>()
            val jobs = ArrayList<Job>()
            repeat(params.rooms + params.bigRooms) { n ->
                val size = if (n < params.rooms) 2 else BIG_TABLE
                jobs += launch(scope.coroutineContext) {
                    val bots = filling.withPermit { host.fillRoom(n, size, params, numbers, scope) }
                    synchronized(rooms) { rooms += bots }
                }
            }
            jobs.joinAll()
            say("load: ${rooms.size} rooms running; health ${host.health()}; ${numbers.failures.toList().distinct()}")
            check(rooms.size == params.rooms + params.bigRooms) { "Rooms that never started: $numbers" }

            val began = System.currentTimeMillis()
            val cpu = host.cpuMillis
            val sampler = launch(scope.coroutineContext) {
                while (isActive) {
                    delay(HEAP_SAMPLE_MILLIS)
                    say(
                        "load: at ${(System.currentTimeMillis() - began) / MILLIS_PER_SECOND} s, " +
                            "${numbers.answers.get()} answers, heap " +
                            host.heapAfterGc().lines().drop(1).first().trim(),
                    )
                }
            }
            val allBots = rooms.flatten()
            withTimeoutOrNull(LOAD_LIMIT_MILLIS) {
                allBots.map { launch(scope.coroutineContext) { it.done.await() } }.joinAll()
            }
            sampler.cancel()
            val wall = System.currentTimeMillis() - began
            report(host, numbers, wall, cpu, "${params.rooms} of 2v2 + ${params.bigRooms} of 5v5")
            say("load: heap after GC with every room running: ${heapLine(host)}")
            val failures = numbers.failures.toList()
            say("load: failures ${failures.size} ${failures.distinct().take(FAILURES_SHOWN)}")

            // Every game replays from its log when the server comes back.
            val running = host.health().rooms
            val answersOnDisk = countAnswers(host)
            host.kill()
            val restart = host.startProcess()
            val logged = host.output.lastOrNull { "Restored" in it }.orEmpty().substringAfter("RoomCore - ")
            say("load: restart $restart ms, $running rooms, $answersOnDisk answers on disk: $logged")
            say("load: heap after the restart: ${heapLine(host)}")
        }
        pool.shutdownNow()
        pool.awaitTermination(1, TimeUnit.SECONDS)
    }

    /** The largest table, on the other map, to a rated end, through the drivers: too long for the normal run. */
    @Test
    fun aWholeFiveASideGameCounts(): Unit = runBlocking {
        assumeTrue("Opt-in: -Pgoa.e2e.load=true", System.getProperty("goa.e2e.load") == "true")
        World().use { world ->
            val people = world.signedUp(*Array(BIG_TABLE * 2) { "Player $it" })
            val room = openRoom(people, BIG_TABLE, ACROSS_THE_RIVER)
            room.pickAndReady(lineup(BIG_TABLE * 2, Random(BIG_TABLE)))
            val gameId = room.started()
            val players = people.map { Player(it, gameId) }
            playToEnd(players, SOAK_LIMIT_MILLIS)
            val truth = world.log.truth(gameId)
            say("soak: 5v5 ${truth.answers.size} answers, ${truth.events.size} events, ${truth.status}")
            assertThat(truth.status).isEqualTo("finished")
            assertThat(truth.answers.filter { it.origin == "clock" }).isEmpty()
            val match = world.store.matches().single { it.id == gameId }
            assertThat(match.participants).hasSize(BIG_TABLE * 2)
            assertThat(match.winner).isEqualTo(truth.positions.last().spectator.winner)
            val scan = WireScan(truth)
            people.forEach { person ->
                assertThat(scan.scan(person.connector.wire, room.heroOf(person))).isEmpty()
                assertThat(rebuiltLog(person.tables(gameId))).isEqualTo(truth.events)
            }
            players.forEach { player ->
                assertThat(player.seen?.over?.youWon).isEqualTo(match.winner == room.teamOf(player.person))
                assertThat(player.seen?.online?.rated).isTrue()
            }
        }
    }

    private fun heapLine(host: ServerProcess): String =
        host.heapAfterGc().lines().drop(1).joinToString(" ").take(HEAP_SHOWN)

    private fun countAnswers(host: Host): Long =
        java.sql.DriverManager.getConnection("jdbc:sqlite:${host.dir.resolve("games.sqlite").toAbsolutePath()}")
            .use { db ->
                db.createStatement().use { s ->
                    s.executeQuery("SELECT COUNT(*) FROM answers").use { rows ->
                        rows.next()
                        rows.getLong(1)
                    }
                }
            }

    private companion object {
        const val OK = 200
        const val CREATED = 201
        const val BIG_TABLE = 5
        const val SEED_STEP = 100
        const val SETTLE_MILLIS = 150L
        const val FILLING_AT_ONCE = 4
        const val BOT_THREADS = 8
        const val HEAP_SAMPLE_MILLIS = 60_000L
        const val LOAD_LIMIT_MILLIS = 12 * 60_000L
        const val SOAK_LIMIT_MILLIS = 10 * 60_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val NANOS_PER_MILLI = 1_000_000L
        const val BYTES_PER_MB = 1_048_576L
        const val PERCENT = 100
        const val P50 = 0.5
        const val P99 = 0.99
        const val HEAP_SHOWN = 400
        const val FAILURES_SHOWN = 5
    }
}
