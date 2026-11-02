package dev.quinton.goa.server.game

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.online.HealthInfo
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.server.game.http.gameServerModule
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import java.nio.file.Paths
import org.junit.Test

class HealthTest {
    private val config = GameServerConfig(Paths.get("unused"), Paths.get("unused"), rulesBuild = "test")

    @Test
    fun answersWithAndWithoutThePublicMount() = testApplication {
        application { gameServerModule(config) }
        for (path in listOf("/v1/health", "/game/v1/health")) {
            val health = OnlineProtocol.json.decodeFromString(HealthInfo.serializer(), client.get(path).bodyAsText())
            assertThat(health.ok).isTrue()
            assertThat(health.protocol).isEqualTo(OnlineProtocol.VERSION)
            assertThat(health.rulesBuild).isEqualTo("test")
        }
    }
}
