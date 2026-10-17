package dev.quinton.goa

import android.Manifest
import android.os.Build
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry

/*
 * Shared helpers for the end-to-end "game night" tests.
 *
 * State reset: app/build.gradle.kts runs instrumented tests through the AndroidX Test
 * Orchestrator with `clearPackageData=true`, so every test method gets its own process and
 * starts with an empty Room database and empty DataStore files (a fresh install). Tests never
 * clear data themselves.
 */

typealias AppRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

const val TIMEOUT = 10_000L

/** Lets the timer start without the runtime notification dialog (Android 13+). */
fun grantNotifications() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.uiAutomation.grantRuntimePermission(
        instrumentation.targetContext.packageName,
        Manifest.permission.POST_NOTIFICATIONS,
    )
}

fun hasClickLabel(label: String): SemanticsMatcher = SemanticsMatcher("onClick label = '$label'") {
    it.config.getOrNull(SemanticsActions.OnClick)?.label == label
}

fun hasClickLabelStartingWith(prefix: String): SemanticsMatcher = SemanticsMatcher(
    "onClick label starts with '$prefix'",
) {
    it.config.getOrNull(SemanticsActions.OnClick)?.label?.startsWith(prefix) == true
}

fun hasRole(role: Role): SemanticsMatcher = SemanticsMatcher("role = $role") {
    it.config.getOrNull(SemanticsProperties.Role) == role
}

fun hasStateDescription(value: String): SemanticsMatcher = SemanticsMatcher("stateDescription = '$value'") {
    it.config.getOrNull(SemanticsProperties.StateDescription) == value
}

fun hasCustomAction(label: String): SemanticsMatcher = SemanticsMatcher("custom action '$label'") { node ->
    node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == label } == true
}

/** Excludes the bottom navigation items ("Players" is also an inner tab label). */
fun notBottomNav(): SemanticsMatcher = SemanticsMatcher("not bottom nav") {
    it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("nav_") != true
}

val SemanticsNode.descriptions: List<String>
    get() = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

val SemanticsNode.texts: List<String>
    get() = config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

fun AppRule.exists(matcher: SemanticsMatcher, unmerged: Boolean = false): Boolean =
    onAllNodes(matcher, unmerged).fetchSemanticsNodes().isNotEmpty()

fun AppRule.waitFor(matcher: SemanticsMatcher, timeout: Long = TIMEOUT, unmerged: Boolean = false) {
    try {
        waitUntil(timeout) { exists(matcher, unmerged) }
    } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
        throw AssertionError("Timed out waiting for ${matcher.description}\n${screenDump()}", e)
    }
}

/** [waitUntil] that reports [what] and the screen when it times out. */
fun AppRule.waitUntilOrDump(what: String, timeout: Long = TIMEOUT, condition: () -> Boolean) {
    try {
        waitUntil(timeout, condition)
    } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
        throw AssertionError("Timed out waiting until $what\n${screenDump()}", e)
    }
}

/** Merged semantics tree of every window, for failure messages. */
fun AppRule.screenDump(): String = runCatching {
    onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().indices.joinToString("\n") { i ->
        onAllNodes(androidx.compose.ui.test.isRoot())[i].printToString(maxDepth = Int.MAX_VALUE)
    }
}.getOrElse { "(no tree: ${it.message})" }

fun AppRule.waitForText(text: String, substring: Boolean = false, timeout: Long = TIMEOUT) =
    waitFor(hasText(text, substring = substring), timeout)

fun AppRule.waitForDescription(text: String, substring: Boolean = false, timeout: Long = TIMEOUT) =
    waitFor(hasContentDescription(text, substring = substring), timeout)

fun AppRule.waitGone(matcher: SemanticsMatcher, timeout: Long = TIMEOUT) {
    waitUntilOrDump("${matcher.description} is gone", timeout) { !exists(matcher) }
}

/** Bottom navigation: "catalog", "timer", "draft" or "players". */
fun AppRule.openTab(tab: String) {
    onNodeWithTagSafe("nav_$tab").performClick()
    waitForIdle()
}

private fun AppRule.onNodeWithTagSafe(tag: String): SemanticsNodeInteraction {
    waitFor(androidx.compose.ui.test.hasTestTag(tag))
    return onNode(androidx.compose.ui.test.hasTestTag(tag))
}

/**
 * Brings the first node matching [matcher] on screen: lazy containers are scrolled until it is
 * composed, plain scrolling columns scroll it into view.
 */
fun AppRule.reveal(matcher: SemanticsMatcher): SemanticsNodeInteraction {
    if (!exists(matcher)) {
        val containers = onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().size
        for (i in 0 until containers) {
            runCatching { onAllNodes(hasScrollToIndexAction())[i].performScrollToNode(matcher) }
            if (exists(matcher)) break
        }
    }
    waitFor(matcher)
    val node = onAllNodes(matcher).onFirst()
    runCatching { node.performScrollTo() }
    return node
}

fun AppRule.tap(matcher: SemanticsMatcher) {
    reveal(matcher).performClick()
    waitForIdle()
}

/** Clicks the clickable node showing exactly [text]. */
fun AppRule.tapText(text: String, substring: Boolean = false) =
    tap(hasText(text, substring = substring) and hasClickAction() and !hasSetTextAction())

/** Clicks the button labelled [label], whether the label is its text or its content description. */
fun AppRule.tapLabel(label: String) = tap((hasText(label) or hasContentDescription(label)) and hasClickAction())

fun AppRule.tapDescription(text: String, substring: Boolean = false) =
    tap(hasContentDescription(text, substring = substring) and hasClickAction())

fun AppRule.typeInto(label: String, value: String) {
    reveal(hasSetTextAction() and hasText(label)).performTextInput(value)
    waitForIdle()
}

/** Creates a player through Players → Add player → Save and waits for the leaderboard row. */
fun AppRule.createPlayerViaProfile(name: String) {
    openTab("players")
    tapLabel("Add player")
    waitForText("Name")
    typeInto("Name", name)
    tapText("Save")
    waitForDescription("$name, no games yet", substring = true)
}

/** Match editor: puts [hero] and [pilot] in the first seat still without a hero (Red's come first). */
fun AppRule.fillNextSeat(hero: String, pilot: String) {
    tap(hasContentDescription("Hero: Unknown hero. Change hero") and hasClickAction())
    waitForText("Search heroes")
    onNode(hasSetTextAction() and hasText("Search heroes")).performTextInput(hero)
    tap(hasText(hero) and hasRole(Role.RadioButton))
    waitForDescription("Hero: $hero. Change hero")

    tap(hasContentDescription("Pilot: none. Change pilot") and hasClickAction())
    waitForText("Pilot for", substring = true)
    tap(hasText(pilot) and hasRole(Role.RadioButton))
    waitGone(hasText("Pilot for", substring = true))
}

/** Leaderboard row content descriptions, e.g. "Rank 1, Ana, rating 1220, provisional, 1 wins 0 losses". */
fun AppRule.leaderboardRows(): List<String> = onAllNodes(hasClickLabel("Open profile"))
    .fetchSemanticsNodes()
    .mapNotNull { node -> node.descriptions.firstOrNull { RowPattern.containsMatchIn(it) } }

/** The pilots named on a "Who won?" winner button ("Red won: Ana, Cal"); empty when none are seated. */
fun AppRule.winnerPilots(team: String): Set<String> {
    val prefix = "$team won"
    waitFor(hasContentDescription(prefix, substring = true) and hasRole(Role.RadioButton))
    val description = onNode(hasContentDescription(prefix, substring = true) and hasRole(Role.RadioButton))
        .fetchSemanticsNode().descriptions.first { it.startsWith(prefix) }
    return description.removePrefix(prefix).removePrefix(": ").split(", ").filter { it.isNotBlank() }.toSet()
}

/** One line of "Result saved" → Rating changes: "Ana, won, rating +16, now 1216, rank 1". */
data class RatingChange(val name: String, val won: Boolean, val delta: Int, val now: Int, val raw: String)

private val ChangePattern = Regex("""^(.+?), (won|lost), rating ([+−±])(\d+), now (\d+)""")

/** Waits until all [count] players' rating changes are computed on "Result saved", keyed by name. */
fun AppRule.ratingChanges(count: Int): Map<String, RatingChange> {
    fun read() = onAllNodes(hasContentDescription(", rating ", substring = true)).fetchSemanticsNodes()
        .flatMap { it.descriptions }
        .mapNotNull { raw ->
            ChangePattern.find(raw)?.let { m ->
                val size = m.groupValues[4].toInt()
                val delta = if (m.groupValues[3] == "−") -size else if (m.groupValues[3] == "+") size else 0
                RatingChange(m.groupValues[1], m.groupValues[2] == "won", delta, m.groupValues[5].toInt(), raw)
            }
        }
        .associateBy { it.name }
    waitUntilOrDump("$count rating changes") { read().size == count }
    return read()
}

data class RatingRow(val name: String, val rating: Int?, val provisional: Boolean, val raw: String)

private val RowPattern = Regex("""^(?:Rank \d+, )?(.+?), (?:rating (\d+)|no games yet)""")

fun parseRow(raw: String): RatingRow {
    val m = requireNotNull(RowPattern.find(raw)) { "Unexpected leaderboard row: $raw" }
    return RatingRow(m.groupValues[1], m.groupValues[2].toIntOrNull(), raw.contains(", provisional"), raw)
}
