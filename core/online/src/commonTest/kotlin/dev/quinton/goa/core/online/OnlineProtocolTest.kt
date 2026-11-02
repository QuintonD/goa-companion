package dev.quinton.goa.core.online

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.GameLength
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OnlineProtocolTest {
    private val json = OnlineProtocol.json

    @Test
    fun aClientMessageSurvivesTheWire() {
        val sent: ClientMessage = ClientMessage.Answer("g1", 7, Choice.Pass)
        val text = json.encodeToString(ClientMessage.serializer(), sent)
        assertEquals(sent, json.decodeFromString(ClientMessage.serializer(), text))
        assertTrue("\"type\":\"game.answer\"" in text, text)
    }

    @Test
    fun aServerMessageSurvivesTheWire() {
        val sent: ServerMessage = ServerMessage.Refused(Refusal.ROOMS_FULL, retryAfterSeconds = 60)
        val text = json.encodeToString(ServerMessage.serializer(), sent)
        assertEquals(sent, json.decodeFromString(ServerMessage.serializer(), text))
    }

    @Test
    fun aNewerServersExtraFieldsAreIgnored() {
        val text = """{"type":"pong","nonce":1,"serverTime":2,"somethingNew":true}"""
        assertEquals(ServerMessage.Pong(1, 2), json.decodeFromString(ServerMessage.serializer(), text))
    }

    @Test
    fun aLobbySurvivesTheWire() {
        val room = RoomView(
            code = "ABCD2345",
            hostAccountId = "a1",
            mapId = "forgotten-island",
            length = GameLength.QUICK,
            teamSize = 2,
            ranked = true,
            seats = emptyList(),
            status = RoomStatus.OPEN,
            expiresAt = 9,
        )
        val text = json.encodeToString(RoomView.serializer(), room)
        assertEquals(room, json.decodeFromString(RoomView.serializer(), text))
    }
}
