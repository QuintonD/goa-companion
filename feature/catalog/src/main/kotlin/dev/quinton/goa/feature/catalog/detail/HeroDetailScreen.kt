package dev.quinton.goa.feature.catalog.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.quinton.goa.core.designsystem.component.ComplexityStars
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaOutlinedButton
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.HeroSymbol
import dev.quinton.goa.core.designsystem.component.LARGE_FONT_SCALE
import dev.quinton.goa.core.designsystem.component.LookArt
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.component.assetUrl
import dev.quinton.goa.core.designsystem.component.rememberOfficialBitmap
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.catalog.DeckGroupKind
import dev.quinton.goa.core.domain.catalog.DeckLayout
import dev.quinton.goa.core.domain.catalog.HANDICAP_EXPLANATION
import dev.quinton.goa.core.domain.catalog.HeroPilotStat
import dev.quinton.goa.core.domain.catalog.StartingCards
import dev.quinton.goa.core.domain.catalog.attackProfile
import dev.quinton.goa.core.domain.catalog.complexityName
import dev.quinton.goa.core.domain.catalog.elo
import dev.quinton.goa.core.domain.catalog.handicapConflict
import dev.quinton.goa.core.domain.catalog.kindLabel
import dev.quinton.goa.core.domain.catalog.label
import dev.quinton.goa.core.domain.catalog.percent
import dev.quinton.goa.core.domain.catalog.plural
import dev.quinton.goa.core.domain.catalog.tierNumeral
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.catalog.Ruling
import dev.quinton.goa.core.navigation.HeroDetailRoute
import dev.quinton.goa.feature.catalog.list.CardPlateRow
import dev.quinton.goa.feature.catalog.ui.LargePlate
import dev.quinton.goa.feature.catalog.ui.MetaPill
import dev.quinton.goa.feature.catalog.ui.SharedTitlePlate
import dev.quinton.goa.feature.catalog.ui.UnverifiedBadge
import dev.quinton.goa.feature.catalog.ui.overlapUp
import kotlinx.coroutines.launch

@Composable
fun HeroDetailScreen(
    state: HeroDetailUiState,
    onBack: () -> Unit,
    onCardClick: (heroId: String, cardId: String) -> Unit,
    modifier: Modifier = Modifier,
    onLearnNumbers: ((heroId: String) -> Unit)? = null,
    /** "Log a match with …" on the Overview's empty record: a new match with this hero in a seat. */
    onLogMatch: ((heroId: String) -> Unit)? = null,
    /**
     * Where the page opens: [HeroDetailRoute.DECK], [HeroDetailRoute.FAQ], or [HeroDetailRoute.SPELLBOOK] (the Deck
     * scrolled to the spellbook, once; back and rotation keep where you are). Null: the Overview.
     */
    initialSection: String? = null,
) {
    val ready = state as? HeroDetailUiState.Ready
    var section by rememberSaveable { mutableStateOf(HeroSection.opening(initialSection)) }
    GoaScreen(title = ready?.hero?.name.orEmpty(), onBack = onBack, modifier = modifier) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                HeroDetailUiState.Loading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center).semantics { contentDescription = "Loading hero" },
                )

                is HeroDetailUiState.NotFound -> EmptyState(
                    icon = Icons.Outlined.PersonSearch,
                    title = "Unknown hero",
                    message = "This hero isn't in the catalog bundled with this build.",
                    modifier = Modifier.align(Alignment.Center),
                )

                is HeroDetailUiState.Ready -> HeroPage(
                    state,
                    section = section,
                    onSelect = { section = it },
                    openAtSpellbook = initialSection == HeroDetailRoute.SPELLBOOK,
                    onCardClick = onCardClick,
                    onLearnNumbers = onLearnNumbers,
                    onLogMatch = onLogMatch,
                )
            }
        }
    }
}

/**
 * Overview · Deck · FAQ as true tabs: each shows only its part, keeps its own scroll, and the tab you are on is the
 * selected one. The Overview has the art and the profile; the Deck and FAQ open with a compact identity, so the cards
 * and their colour controls come first.
 */
@Composable
private fun HeroPage(
    state: HeroDetailUiState.Ready,
    section: HeroSection,
    onSelect: (HeroSection) -> Unit,
    openAtSpellbook: Boolean,
    onCardClick: (heroId: String, cardId: String) -> Unit,
    onLearnNumbers: ((heroId: String) -> Unit)?,
    onLogMatch: ((heroId: String) -> Unit)?,
) {
    val overview = rememberLazyListState()
    val deck = rememberLazyListState()
    val faq = rememberLazyListState()
    val listState = when (section) {
        HeroSection.OVERVIEW -> overview
        HeroSection.DECK -> deck
        HeroSection.FAQ -> faq
    }
    val scope = rememberCoroutineScope()
    val navHeight = with(LocalDensity.current) { SectionBarHeight.roundToPx() }
    // The sticky bar: the tabs, and on the Deck the jump bar under them. A jump lands just under it.
    var barHeight by remember { mutableIntStateOf(navHeight) }
    val trivia = onLearnNumbers != null
    var tier by rememberSaveable { mutableStateOf<Int?>(null) }
    var pickedPart by rememberSaveable { mutableStateOf<String?>(null) }
    val tiered = remember(state, tier) { state.deck.onlyTier(tier) }
    // The colour filter offers the parts the tier leaves; one it removes falls back to all colours.
    val parts = remember(tiered) { deckParts(tiered) }
    val part = pickedPart?.takeIf { key -> parts.any { it.key == key } }
    val shown = remember(state, tiered, part) { state.copy(deck = tiered.onlyPart(part)) }
    val deckKeys = remember(shown, trivia) { deckTabKeys(shown, trivia) }
    var opened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (opened) return@LaunchedEffect
        opened = true
        if (openAtSpellbook) deckKeys.indexOf(EXTRAS_KEY).takeIf { it >= 0 }?.let { deck.scrollToItem(it, -navHeight) }
    }
    // A changed filter brings the cards' head back under the sticky bar when the list was scrolled past it.
    val filter: (() -> Unit) -> Unit = { change ->
        change()
        val head = deckKeys.indexOf(DECK_KEY)
        if (head >= 0 && deck.firstVisibleItemIndex > head) scope.launch { deck.scrollToItem(head, -barHeight) }
    }
    val select: (HeroSection) -> Unit = { next ->
        if (next != section) {
            // Each tab keeps its own scroll: one you come back to is where you left it.
            onSelect(next)
        } else {
            scope.launch { listState.animateScrollToItem(0) } // Tapped again: back to its top.
        }
    }
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = Spacing.xxl)) {
        if (section == HeroSection.OVERVIEW) {
            item(key = HEADER_KEY) { HeroHeader(state.hero, state.productName) }
        } else {
            item(key = HEADER_KEY) { CompactHeroHeader(state.hero) }
        }
        stickyHeader(key = NAV_KEY) {
            Column(Modifier.background(MaterialTheme.colorScheme.background).onSizeChanged { barHeight = it.height }) {
                HeroSectionBar(section, faqCount = state.hero.faq.size, onSelect = select)
                if (section == HeroSection.DECK) {
                    DeckFilterBar(
                        parts = parts,
                        part = part,
                        onPart = { key -> filter { pickedPart = key } },
                        tiers = deckTiers(state.deck),
                        tier = tier,
                        onTier = { t -> filter { tier = t } },
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    )
                    Box(Modifier.fillMaxWidth().height(1.dp).background(GoaTheme.colors.engravedBorder))
                }
            }
        }
        when (section) {
            HeroSection.OVERVIEW -> overviewContent(
                state,
                onCardClick = { onCardClick(state.hero.id, it.id) },
                onLogMatch = onLogMatch?.let { log -> { log(state.hero.id) } },
            )

            HeroSection.DECK -> deckContent(
                shown,
                deckCount(state, shown.deck, tier, parts.firstOrNull { it.key == part }?.label),
                onCardClick = { onCardClick(state.hero.id, it.id) },
                onLearnNumbers = onLearnNumbers?.let { learn -> { learn(state.hero.id) } },
            )

            HeroSection.FAQ -> faqContent(state.hero)
        }
    }
}

/** The Deck tab's items in order, by key, as [deckContent] emits them: the jump row finds its stops here. */
internal fun deckTabKeys(state: HeroDetailUiState.Ready, trivia: Boolean): List<String> = buildList {
    add(HEADER_KEY)
    add(NAV_KEY)
    add(DECK_KEY)
    state.deck.groups.forEach { add(groupKey(it.kind)) }
    if (trivia) add("deck-trivia")
    if (state.deck.handicap != null) add(HANDICAP_KEY)
    if (state.deck.extras.isNotEmpty()) add(EXTRAS_KEY)
}

internal fun groupKey(kind: DeckGroupKind) = "deck-$kind"

internal const val HEADER_KEY = "header"
internal const val NAV_KEY = "nav"
internal const val DECK_KEY = "deck-title"
internal const val HANDICAP_KEY = "handicap"
internal const val EXTRAS_KEY = "extras"
internal const val FAQ_KEY = "faq-title"

private val padded = Modifier.padding(horizontal = Spacing.screen)

/**
 * The Overview, what the hero does first: the ratings, then the five starting cards with their text (the hero's
 * abilities at level 1, each opening its card); then your table's record with the hero (one line while there is
 * none), the lore and where the data comes from.
 */
private fun LazyListScope.overviewContent(
    state: HeroDetailUiState.Ready,
    onCardClick: (Card) -> Unit,
    onLogMatch: (() -> Unit)?,
) {
    val hero = state.hero
    item(key = "profile") { StatPlate(hero.stats, padded.padding(top = Spacing.md)) }
    val starting = StartingCards.of(hero)
    if (starting.isNotEmpty()) {
        item(key = "starting") {
            Column(padded, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionHeader("Starting cards")
                starting.forEach { card ->
                    CardPlateRow(card, kind = card.kindLabel(), text = card.text, onClick = { onCardClick(card) })
                }
            }
        }
    }
    item(key = "stats") {
        Column(padded) {
            SectionHeader("Your table")
            if (state.stats.hasGames) {
                PersonalStatsCard(state.stats)
            } else {
                NoGamesRow(hero.name, onLogMatch)
            }
        }
    }
    hero.lore?.takeIf { it.isNotBlank() }?.let { lore ->
        item(key = "lore") {
            Column(padded) {
                SectionHeader("Lore")
                LoreBlock(lore)
            }
        }
    }
    item(key = "sources") { DataSourceLine(hero, padded.padding(top = Spacing.lg)) }
}

/**
 * One count everywhere: the deck's own cards, the Handicap card named apart ("18 cards + Handicap"), as the card
 * viewer counts them; narrowed, what the filters leave, named by them ("Red · Tier II · 2 cards").
 */
internal fun deckCount(state: HeroDetailUiState.Ready, shown: DeckLayout, tier: Int?, part: String?): String =
    if (tier == null && part == null) {
        plural(state.hero.cards.size, "card") + if (state.hero.handicap != null) " + Handicap" else ""
    } else {
        (listOfNotNull(part, tier?.let { "Tier ${tierNumeral(it)}" }) + plural(shown.shownCount(), "card"))
            .joinToString(" · ")
    }

/** The Deck under its jump bar: the cards by colour, the Handicap card and any extras; with a [tier], only those. */
private fun LazyListScope.deckContent(
    state: HeroDetailUiState.Ready,
    count: String,
    onCardClick: (Card) -> Unit,
    onLearnNumbers: (() -> Unit)?,
) {
    val hero = state.hero
    item(key = DECK_KEY) {
        Text(
            count,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = padded.padding(top = Spacing.sm),
        )
    }
    state.deck.groups.forEach { group ->
        item(key = groupKey(group.kind)) { DeckGroupSection(group, onCardClick, padded) }
    }
    if (onLearnNumbers != null) {
        item(key = "deck-trivia") { LearnNumbersButton(hero.name, onLearnNumbers, padded) }
    }
    state.deck.handicap?.let { handicap ->
        item(key = HANDICAP_KEY) {
            Column(padded) {
                SectionHeader("Handicap")
                GoaPlate(Modifier.fillMaxWidth()) {
                    Text(
                        HANDICAP_EXPLANATION, // Rulebook p.20.
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    )
                    handicapConflict(hero, handicap)?.let { note ->
                        Text(
                            note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = Spacing.sm),
                        )
                    }
                    DeckCardGrid(listOf(handicap), onCardClick, isHandicap = true)
                }
            }
        }
    }
    if (state.deck.extras.isNotEmpty()) {
        item(key = EXTRAS_KEY) { ExtrasSection(state.deck, onCardClick, padded) }
    }
}

/** The FAQ: the official rulings for this hero, each opening to its answer and source. */
private fun LazyListScope.faqContent(hero: Hero) {
    item(key = FAQ_KEY) { SectionHeader("FAQ rulings", modifier = padded) }
    if (hero.faq.isEmpty()) {
        item(key = "faq-empty") {
            Text(
                "No official rulings recorded for this hero.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = padded,
            )
        }
    } else {
        itemsIndexed(hero.faq, key = { i, _ -> "faq-$i" }) { _, ruling ->
            FaqItem(ruling, padded.padding(bottom = Spacing.sm))
        }
    }
}

/**
 * The Deck's and FAQ's head: the deck symbol, the name on its title plate and the identity line, without the art,
 * so the cards start high on the screen.
 */
@Composable
private fun CompactHeroHeader(hero: Hero) {
    val largeText = LocalDensity.current.fontScale > LARGE_FONT_SCALE
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hero.symbol != null) {
            HeroSymbol(
                hero.id,
                hero.symbol,
                modifier = Modifier.padding(end = Spacing.sm).size(scaledIconSize(COMPACT_SYMBOL, 1.3f)),
                contentDescription = "${hero.name}'s deck symbol",
            )
        }
        Column(Modifier.weight(1f)) {
            HeroNamePlate(hero, Modifier.widthIn(max = HEADER_PLATE_MAX_WIDTH).fillMaxWidth())
            HeroIdentityLine(hero, Modifier.padding(start = 2.dp, top = 4.dp), stacked = largeText)
        }
    }
}

/** Opens card trivia on this hero: an unobtrusive tonal action under the deck. */
@Composable
private fun LearnNumbersButton(heroName: String, onClick: () -> Unit, modifier: Modifier) {
    GoaOutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm)
            .heightIn(min = 48.dp),
    ) {
        Icon(Icons.Outlined.Quiz, contentDescription = null, modifier = Modifier.size(scaledIconSize(18.dp)))
        Spacer(Modifier.width(Spacing.sm))
        Text("Learn $heroName's card numbers")
    }
}

/**
 * The hero's art edge to edge, fading into the canvas, as the page's banner. Only Tigerclaw has their
 * dashboard's art (p.16); for every other hero the portrait is what looked best: a card's art is often
 * a scene without the hero, and a blurred backdrop muddied the portrait. It is cropped from nearer the
 * top, where the faces are.
 */
@Composable
private fun HeroHeaderArt(hero: Hero) {
    val background = MaterialTheme.colorScheme.background
    val art = LookArt.dashboardArt(hero.id) ?: hero.portrait
    var loaded by remember(art) { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = HEADER_ART_MAX_HEIGHT)
            .aspectRatio(HEADER_ART_RATIO)
            .semantics { contentDescription = "${hero.name} artwork" },
    ) {
        if (!loaded) {
            // The sigil on the medallion: while it loads, if it fails, and for a hero without art.
            HeroPortrait(hero.id, null, null, Modifier.fillMaxSize(), RectangleShape)
        }
        if (art != null) {
            AsyncImage(
                model = assetUrl(art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = HEADER_ART_ALIGNMENT,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { loaded = true },
                onError = { loaded = false },
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.55f to Color.Transparent, 1f to background),
            ),
        )
    }
}

/** The name and title on the cards' title plate: two texts, so it breaks between them when it must. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroNamePlate(hero: Hero, modifier: Modifier = Modifier) {
    val ink = GoaTheme.colors.onParchment
    SharedTitlePlate(rememberOfficialBitmap(LookArt.TITLE_PLATE), LargePlate, modifier) {
        FlowRow(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() },
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        ) {
            Text(hero.name, style = HERO_PLATE_NAME, color = ink, textAlign = TextAlign.Center)
            Text(hero.title, style = HERO_PLATE_NAME, color = ink, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroHeader(hero: Hero, productName: String) {
    val largeText = LocalDensity.current.fontScale > LARGE_FONT_SCALE
    Column {
        HeroHeaderArt(hero)
        Column(Modifier.overlapUp(HEADER_OVERLAP).padding(horizontal = Spacing.screen)) {
            Row {
                // The deck symbol, as on the back of the hero's cards and their dashboard (p.6).
                if (hero.symbol != null) {
                    HeroSymbol(
                        hero.id,
                        hero.symbol,
                        modifier = Modifier.padding(end = Spacing.sm).size(scaledIconSize(HEADER_SYMBOL, 1.4f)),
                        contentDescription = "${hero.name}'s deck symbol",
                    )
                }
                Column(Modifier.weight(1f).padding(top = Spacing.sm)) {
                    HeroNamePlate(hero, Modifier.widthIn(max = HEADER_PLATE_MAX_WIDTH).fillMaxWidth())
                    if (!largeText) HeroIdentityLine(hero, Modifier.padding(start = 2.dp, top = 6.dp))
                }
            }
            if (largeText) HeroIdentityLine(hero, Modifier.padding(top = Spacing.sm), stacked = true)
            if (hero.aliases.isNotEmpty()) {
                Text(
                    "Also known as ${hero.aliases.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            FlowRow(
                Modifier.padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                MetaPill(productName)
                // App-derived from the Ranged subtype of the deck's Attack cards (DOMAIN.md); kept
                // apart from the official roles (the rulebook calls no-range attacks "Non-Ranged", p.13).
                hero.attackProfile().pill?.let { MetaPill(it) }
                if (hero.printing != 2) MetaPill("Printing ${hero.printing}")
                if (hero.verified) VerifiedPill() else UnverifiedBadge()
            }
        }
    }
}

/**
 * Stars, the complexity's name and the official roles, flowing line to line. [stacked], for large text: the
 * stars and the name, then the roles as one sentence, so a separator doesn't dangle at each line's end.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroIdentityLine(hero: Hero, modifier: Modifier = Modifier, stacked: Boolean = false) {
    val words = listOfNotNull(hero.complexity?.let(::complexityName)) + hero.roles.map { it.label }
    if (stacked) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                hero.complexity?.let {
                    ComplexityStars(it, starSize = 14.dp, modifier = Modifier.padding(end = Spacing.sm))
                    Text(complexityName(it).orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(hero.roles.joinToString(" · ") { it.label }, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        hero.complexity?.let { ComplexityStars(it, starSize = 14.dp, modifier = Modifier.padding(end = 2.dp)) }
        words.forEachIndexed { i, word ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(word, style = MaterialTheme.typography.bodyMedium)
                if (i < words.lastIndex) {
                    Text(
                        " ·",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
    }
}

@Composable
private fun VerifiedPill() {
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = "Verified data" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Verified,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text("Verified", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
    }
}

/** No match logged with the hero yet: one line, and "Log a match" (with the hero in a seat) beside it. */
@Composable
private fun NoGamesRow(heroName: String, onLogMatch: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "No games logged with $heroName yet",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (onLogMatch != null) {
            GoaOutlinedButton(
                onClick = onLogMatch,
                modifier = Modifier.padding(start = Spacing.md).heightIn(min = 48.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(scaledIconSize(18.dp)))
                Spacer(Modifier.width(Spacing.sm))
                Text("Log a match")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonalStatsCard(stats: HeroPersonalStats) {
    GoaPlate(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.lg)) {
        val rating = stats.rating
        val usage = stats.usage
        val games = usage?.matches ?: rating?.games ?: stats.log.games
        val wins = usage?.wins ?: rating?.wins ?: stats.log.wins
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            maxItemsInEachRow = 3,
        ) {
            val metricModifier = Modifier.weight(1f)
            Metric(
                "Elo",
                rating?.takeIf { it.games > 0 }?.let { elo(it.value) } ?: "—",
                rating?.takeIf { it.provisional && it.games > 0 }?.let { "provisional" },
                metricModifier,
            )
            Metric("Games", games.toString(), "${wins}W · ${games - wins}L", metricModifier)
            Metric("Win rate", if (games > 0) percent(wins.toDouble() / games) else "—", null, metricModifier)
            usage?.let {
                Metric("Pick rate", percent(it.pickRate), null, metricModifier)
                Metric("Ban rate", percent(it.banRate), plural(it.bans, "ban"), metricModifier)
            }
        }
        PilotsBlock(stats)
    }
}

@Composable
private fun Metric(label: String, value: String, note: String?, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PilotsBlock(stats: HeroPersonalStats) {
    val best = stats.log.bestPilots
    val pilots = best.ifEmpty { stats.log.allPilots }
    if (pilots.isEmpty()) return
    Spacer(Modifier.height(Spacing.lg))
    Text(
        if (best.isNotEmpty()) "Best with this hero" else "Played by",
        style = MaterialTheme.typography.titleSmall,
    )
    if (best.isEmpty()) {
        Text(
            "Rankings start at ${stats.log.minGames} games per player.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    pilots.take(MAX_PILOTS).forEachIndexed { index, pilot -> PilotRow(index + 1, pilot, ranked = best.isNotEmpty()) }
}

@Composable
private fun PilotRow(rank: Int, pilot: HeroPilotStat, ranked: Boolean) {
    val name = pilot.player?.name ?: "Unknown player"
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(top = Spacing.sm)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ranked) {
            Text(
                "$rank",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(24.dp),
            )
        }
        PlayerAvatar(pilot.player, size = 36.dp)
        Spacer(Modifier.width(Spacing.md))
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(percent(pilot.winRate), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "${pilot.wins}–${pilot.games - pilot.wins}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics {
                    contentDescription =
                        "${plural(pilot.wins, "win")}, ${plural(pilot.games - pilot.wins, "loss", "losses")}"
                },
            )
        }
    }
}

@Composable
private fun FaqItem(ruling: Ruling, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(ruling.question) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(MOTION_MS), label = "faqChevron")
    GoaPlate(modifier.fillMaxWidth(), brackets = false, contentPadding = PaddingValues()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(
                    onClickLabel = if (expanded) "Hide answer" else "Show answer",
                    role = Role.Button,
                ) { expanded = !expanded }
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(Spacing.md))
            Text(ruling.question, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = null,
                modifier = Modifier.rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(MOTION_MS)) + fadeIn(tween(MOTION_MS)),
            exit = shrinkVertically(tween(MOTION_MS)) + fadeOut(tween(MOTION_MS)),
        ) {
            Column(Modifier.padding(start = 48.dp, end = Spacing.lg, bottom = Spacing.lg)) {
                Text(ruling.answer, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Source: ${ruling.source}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun LoreBlock(lore: String) {
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .width(2.dp)
                .heightIn(min = 24.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(1.dp)),
        )
        // A quotation from the game's world: the serif the game prints in (Crimson), italic as quoted.
        Text(
            lore,
            style = GoaTextStyles.printed.copy(fontSize = 19.sp, lineHeight = 27.sp),
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = Spacing.md),
        )
    }
}

/**
 * Where the hero's data comes from, as one quiet line: "Data: Stats of Atlantis · Verified" (or Unverified, always
 * shown, AGENTS.md). The links, commits and dates are in About → Data sources, not on a player's page.
 */
@Composable
private fun DataSourceLine(hero: Hero, modifier: Modifier = Modifier) {
    val names = hero.sources.map { it.name }.distinct()
    val source = when (names.size) {
        0 -> "none recorded"
        1 -> names.first()
        else -> "${names.first()} + ${names.size - 1} more"
    }
    val tone = if (hero.verified) GoaTheme.colors.verified else GoaTheme.colors.unverified
    val status = if (hero.verified) "Verified" else "Unverified"
    val line = buildAnnotatedString {
        append("Data: $source · ")
        withStyle(SpanStyle(color = tone, fontWeight = FontWeight.SemiBold)) { append(status) }
    }
    Text(
        line,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "Data: $source, $status" },
    )
}

/** The header's art: as wide as the page, about twice as wide as tall, and no taller when held sideways. */
private const val HEADER_ART_RATIO = 2f
private val HEADER_ART_MAX_HEIGHT = 240.dp

/** Crops toward the top of the art: 0 is its middle, -1 its top. */
private val HEADER_ART_ALIGNMENT = BiasAlignment(0f, -0.5f)

/** How far the symbol and the title plate run up over the fading art. */
private val HEADER_OVERLAP = 54.dp
private val HEADER_SYMBOL = 66.dp
private val COMPACT_SYMBOL = 44.dp
private val HEADER_PLATE_MAX_WIDTH = 440.dp
private val HERO_PLATE_NAME = GoaTextStyles.plateTitle.copy(fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = 0.sp)
private const val MOTION_MS = 250
private const val MAX_PILOTS = 3
