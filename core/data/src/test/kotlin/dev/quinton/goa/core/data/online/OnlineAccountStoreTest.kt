package dev.quinton.goa.core.data.online

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.backup.BackupDocument
import dev.quinton.goa.core.datastore.online.OnlineAccountStore
import dev.quinton.goa.feature.board.play.online.OnlineIdentity
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardCache
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.w3c.dom.Element

/**
 * The online account outlives the process and stays on the device: docs/ONLINE.md says the recovery code is what
 * moves it, and a token in a backup would keep working on two phones.
 */
@RunWith(RobolectricTestRunner::class)
class OnlineAccountStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file get() = OnlineAccountStore.file(context)

    private fun <T> withStore(block: suspend (DataStoreOnlineIdentityStore) -> T): T = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val store = OnlineAccountStore(PreferenceDataStoreFactory.create(scope = scope) { file })
        try {
            block(DataStoreOnlineIdentityStore(store))
        } finally {
            // As a process dying does: only the file is left.
            scope.coroutineContext.job.cancelAndJoin()
        }
    }

    @Test
    fun `the account survives the process dying, and clear removes it`() {
        val identity = OnlineIdentity("acc", "secret-token", "Ann")
        assertThat(withStore { it.load() }).isNull()
        withStore { it.save(identity) }
        assertThat(withStore { it.load() }).isEqualTo(identity)
        withStore { it.clear() }
        assertThat(withStore { it.load() }).isNull()
    }

    @Test
    fun `the account's file is in the directory Android never backs up, outside the settings`() {
        assertThat(file.parentFile).isEqualTo(context.noBackupFilesDir)
        assertThat(file.path).doesNotContain(File(context.filesDir, "datastore").path)
    }

    /** Every rule of a backup rules file, as (domain, path). */
    private fun rules(name: String): List<Triple<String, String, String>> {
        val xml = repoFile("app/src/main/res/xml/$name")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml)
        val nodes = doc.getElementsByTagName("*")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .filter { it.tagName == "include" || it.tagName == "exclude" }
            .map { Triple(it.tagName, it.getAttribute("domain"), it.getAttribute("path")) }
    }

    @Test
    fun `no backup rule reaches the account's file, in the cloud or on a device transfer`() {
        val onlyThese = setOf(Triple("include", "database", "."), Triple("include", "file", "datastore/"))
        assertThat(rules("backup_rules.xml").toSet()).isEqualTo(onlyThese)
        assertThat(rules("data_extraction_rules.xml").toSet()).isEqualTo(onlyThese)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `the app's backup file has no place for it, and nothing that fills one reads it`() {
        // The whole file's shape: a new top-level field would have to name the account to carry it.
        val fields = BackupDocument.serializer().descriptor.elementNames.toList()
        assertThat(fields).containsNoneOf("token", "online", "onlineAccount", "identity", "recoveryCode")
        // BackupContributor is the only other way in (its `extras`).
        val sources = repoDirs("core/data/src/main", "feature", "app/src/main").flatMap { dir ->
            dir.walkTopDown().filter { it.extension == "kt" && "/build/" !in it.invariantSeparatorsPath }.toList()
        }
        val contributors = sources.filter { ": BackupContributor" in it.readText() }
        assertThat(contributors).isNotEmpty()
        contributors.forEach { source ->
            val text = source.readText()
            listOf("OnlineAccountStore", "OnlineIdentity", "core.data.online").forEach { name ->
                assertThat(text).doesNotContain(name)
            }
        }
    }

    @Test
    fun `the leaderboard's copy lives outside the backed-up settings too, and survives a new instance`() = runBlocking {
        val cache: LeaderboardCache = FileLeaderboardCache(context)
        assertThat(cache.read()).isNull()
        cache.write("""{"schema":1}""")
        assertThat(FileLeaderboardCache(context).read()).isEqualTo("""{"schema":1}""")
        val copies = context.filesDir.walkTopDown().filter { it.isFile && it.readText().contains("schema") }.toList()
        assertThat(copies).isNotEmpty()
        copies.forEach { assertThat(it.path).doesNotContain(File(context.filesDir, "datastore").path) }
        cache.write("""{"schema":2}""")
        assertThat(cache.read()).isEqualTo("""{"schema":2}""")
        assertThat(context.filesDir.listFiles().orEmpty().map { it.name }.filter { it.endsWith(".part") }).isEmpty()
    }

    private fun repoRoot(): File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private fun repoFile(path: String) = File(repoRoot(), path)

    private fun repoDirs(vararg paths: String) = paths.map(::repoFile)
}
