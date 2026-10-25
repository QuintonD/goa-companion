package dev.quinton.goa.web.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A browser history in memory: entries with their hash and the app's state, a cursor, and scroll. */
private class FakeHistory(startHash: String = "") : HistoryPort {
    val entries = mutableListOf<Pair<String, HistoryEntry?>>(startHash to null)
    var index = 0
    var scroll = 0.0
    private var listener: () -> Unit = {}

    override fun hash() = entries[index].first

    override fun entry() = entries[index].second

    override fun push(entry: HistoryEntry, hash: String) {
        while (entries.size > index + 1) entries.removeAt(entries.lastIndex)
        entries += hash to entry
        index++
    }

    override fun replace(entry: HistoryEntry, hash: String) {
        entries[index] = hash to entry
    }

    override fun back() {
        if (index > 0) {
            index--
            listener()
        }
    }

    fun forward() {
        index++
        listener()
    }

    /** The person types a new hash into the address bar. */
    fun type(hash: String) {
        push(HistoryEntry("typed", -1, Tab.HEROES), hash)
        entries[index] = hash to null
        listener()
    }

    override fun onPop(listener: () -> Unit) {
        this.listener = listener
    }

    override fun scrollY() = scroll
}

class RouterTest {
    private var keys = 0

    private fun router(history: FakeHistory) = Router(history) { "k${keys++}" }

    @Test
    fun startsOnTheHashAndNormalisesIt() {
        val history = FakeHistory("")
        val router = router(history)
        assertEquals(Route.Catalog, router.route)
        assertEquals("#/heroes", history.hash())
        assertEquals(1, history.entries.size)
    }

    @Test
    fun aColdDeepLinkGoesUpToItsParentWithoutLeavingTheApp() {
        val history = FakeHistory("#/heroes/arien")
        val router = router(history)
        assertTrue(router.canGoBack)
        router.back()
        assertEquals(Route.Catalog, router.route)
        assertEquals(1, history.entries.size) // replaced, not pushed
        assertFalse(router.canGoBack)
    }

    @Test
    fun backReturnsThroughHistoryOnTheSameTab() {
        val history = FakeHistory("#/heroes")
        val router = router(history)
        router.navigate(Route.HeroDetail("arien"))
        router.navigate(Route.HeroDetail("brogan"))
        router.back()
        assertEquals(Route.HeroDetail("arien"), router.route)
        assertEquals(1, history.index)
    }

    @Test
    fun backAfterATabSwitchGoesUpNotToTheOtherTab() {
        val history = FakeHistory("#/heroes")
        val router = router(history)
        router.navigate(Route.HeroDetail("arien"))
        router.selectTab(Tab.TIMER)
        router.selectTab(Tab.HEROES)
        assertEquals(Route.HeroDetail("arien"), router.route) // the tab reopens where it was
        router.back()
        assertEquals(Route.Catalog, router.route)
        assertEquals(Tab.HEROES, router.tab)
    }

    @Test
    fun theCurrentTabReturnsToItsRoot() {
        val router = router(FakeHistory("#/players"))
        router.navigate(Route.Matches)
        router.selectTab(Tab.PLAYERS)
        assertEquals(Route.Players, router.route)
        val before = router.scrollTarget.id
        router.selectTab(Tab.PLAYERS)
        assertEquals(before + 1, router.scrollTarget.id) // at the root: scroll to the top
        assertEquals(0.0, router.scrollTarget.y)
    }

    @Test
    fun sharedScreensStayOnTheTabThatOpenedThem() {
        val router = router(FakeHistory("#/draft"))
        router.navigate(Route.Settings)
        assertEquals(Tab.DRAFT, router.tab)
        router.back()
        assertEquals(Route.Draft, router.route)
    }

    @Test
    fun browserBackRestoresTheTabAndScrollOfThatEntry() {
        val history = FakeHistory("#/heroes")
        val router = router(history)
        history.scroll = 640.0
        router.navigate(Route.HeroDetail("widget"))
        assertEquals(0.0, router.scrollTarget.y)
        router.selectTab(Tab.TIMER)
        history.back()
        history.back()
        assertEquals(Route.Catalog, router.route)
        assertEquals(Tab.HEROES, router.tab)
        assertEquals(640.0, router.scrollTarget.y)
        history.forward()
        assertEquals(Route.HeroDetail("widget"), router.route)
    }

    @Test
    fun goingUpToAListRestoresWhereItWasScrolled() {
        val history = FakeHistory("#/heroes")
        val router = router(history)
        history.scroll = 900.0
        router.navigate(Route.HeroDetail("widget"))
        router.selectTab(Tab.TIMER)
        router.selectTab(Tab.HEROES)
        history.scroll = 0.0
        router.back()
        assertEquals(900.0, router.scrollTarget.y)
    }

    @Test
    fun aTypedHashBecomesAnEntryOfTheApp() {
        val history = FakeHistory("#/heroes")
        val router = router(history)
        history.type("#/players/ann")
        assertEquals(Route.PlayerDetail("ann"), router.route)
        assertEquals(Tab.PLAYERS, router.tab)
        assertTrue(history.entry() != null)
    }

    @Test
    fun retainedStateLivesAsLongAsItsEntry() {
        val history = FakeHistory("#/heroes")
        val router = router(history)
        router.navigate(Route.Settings)
        val form = router.retain("form") { mutableListOf("draft") }
        router.navigate(Route.About)
        history.back()
        assertSame(form, router.retain("form") { mutableListOf() })
        history.back()
        router.navigate(Route.Settings) // a new visit: a new entry, fresh state
        assertNotSame(form, router.retain("form") { mutableListOf() })
    }

    @Test
    fun navigatingToTheCurrentScreenOnlyScrollsUp() {
        val history = FakeHistory("#/heroes")
        val router = router(history)
        router.navigate(Route.Catalog)
        assertEquals(1, history.entries.size)
    }
}
