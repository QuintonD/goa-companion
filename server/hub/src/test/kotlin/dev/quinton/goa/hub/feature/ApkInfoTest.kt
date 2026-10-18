package dev.quinton.goa.hub.feature

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkInfoTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `a file that isn't an APK has no version, and nothing throws`() {
        val file = tmp.newFile("goa.apk").toPath()
        Files.write(file, ByteArray(100) { 7 })
        assertThat(ApkInfo.read(file)).isEqualTo(ApkInfo(null, null))
        assertThat(ApkInfo.read(tmp.root.toPath().resolve("missing.apk"))).isEqualTo(ApkInfo(null, null))
    }

    /** Against a real build: `GOA_TEST_APK=…/app-release.apk` (skipped when unset). */
    @Test
    fun `a real APK's manifest gives its versionName and versionCode`() {
        val path = System.getenv("GOA_TEST_APK")?.let { Paths.get(it) }
        assumeTrue(path != null && Files.isRegularFile(path))
        val info = ApkInfo.read(path!!)
        assertThat(info.versionName).matches("\\d+\\.\\d+\\.\\d+.*")
        assertThat(info.versionCode).isAtLeast(1L)
        println("APK version: $info")
    }
}
