package dev.quinton.goa.core.datastore.timer

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TimerStateStoreTest {
    private val dir: File = Files.createTempDirectory("timer-store").toFile()

    private fun store(name: String = "timer") =
        DataStoreTimerStateStore.forFile { File(dir, "$name.preferences_pb") }

    @Test
    fun `snapshot updates are atomic and clearable`() = runTest {
        val store = store()
        assertThat(store.currentSnapshotJson()).isNull()

        store.updateSnapshot { "{\"a\":1}" }
        assertThat(store.snapshotJson.first()).isEqualTo("{\"a\":1}")

        val result = store.updateSnapshot { old -> old + "x" }
        assertThat(result).isEqualTo("{\"a\":1}x")

        store.updateSnapshot { null }
        assertThat(store.currentSnapshotJson()).isNull()
    }

    @Test
    fun `presets and selection persist`() = runTest {
        val store = store("presets")
        assertThat(store.presetsJson.first()).isNull()

        store.writePresets("[]")
        store.writeSelectedPresetId("relaxed")
        assertThat(store.presetsJson.first()).isEqualTo("[]")
        assertThat(store.selectedPresetId.first()).isEqualTo("relaxed")

        store.writePresets(null)
        store.writeSelectedPresetId(null)
        assertThat(store.presetsJson.first()).isNull()
        assertThat(store.selectedPresetId.first()).isNull()
    }
}
