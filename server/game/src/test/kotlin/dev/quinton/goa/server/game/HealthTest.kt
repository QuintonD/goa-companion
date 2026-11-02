package dev.quinton.goa.server.game

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.online.HealthInfo
import dev.quinton.goa.core.online.OnlineProtocol
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import org.junit.Test

class HealthTest {
    @Test
    fun answersWithAndWithoutThePublicMount() = serverTest {
        for (path in listOf("/v1/health", "/game/v1/health")) {
            val health = HealthInfo.serializer().parse(http.get(path).bodyAsText())
            assertThat(health.ok).isTrue()
            assertThat(health.protocol).isEqualTo(OnlineProtocol.VERSION)
            assertThat(health.rulesBuild).isEqualTo("test")
            assertThat(health.status).isEqualTo("open")
            assertThat(health.maxRooms).isEqualTo(GameServerConfig.DEFAULT_MAX_ROOMS)
        }
    }

    @Test
    fun countsOpenRoomsAndRunningGames() = serverTest {
        room(account().token)
        rooms.extraRunning = 2
        assertThat(HealthInfo.serializer().parse(http.get("/v1/health").bodyAsText()).rooms).isEqualTo(3)
    }
}
