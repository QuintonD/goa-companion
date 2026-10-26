package dev.quinton.goa.tools.catalogimport

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Test

/**
 * `official/SOURCES.md` is shown in both apps' About and published with the public web app
 * (ADR-043): its notice must say whose content it is and on what terms, and the shipped file must
 * be the one the template writes.
 */
class SourcesNoticeTest {
    private fun generated(): String {
        val dir = createTempDirectory("sources").toFile()
        try {
            Output(dir).writeSources(ImportResult(emptyList(), emptyList(), emptyList(), emptySet(), emptyMap()))
            return File(dir, "official/SOURCES.md").readText()
        } finally {
            dir.deleteRecursively()
        }
    }

    /** The lines before the first table: the notice and the encoding note. */
    private fun notice(text: String) = text.lineSequence().takeWhile { !it.startsWith("|") }.toList()

    @Test
    fun theNoticeNamesTheOwnerAndThePublicNonCommercialUse() {
        val notice = notice(generated()).joinToString(" ")

        assertThat(notice).contains("Content © Wolff Designa, who keep all rights")
        assertThat(notice).contains("free, non-commercial fan companion")
        assertThat(notice).contains("public web app, ADR-043")
        assertThat(notice).contains("not affiliated with or endorsed by Wolff Designa")
        assertThat(notice).doesNotContain("private")
    }

    @Test
    fun theShippedFileHasTheTemplatesNotice() {
        val shipped = File("../../app/src/main/assets/official/SOURCES.md")

        assertThat(notice(shipped.readText())).isEqualTo(notice(generated()))
    }
}
