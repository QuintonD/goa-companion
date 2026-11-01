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
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.quinton.goa.core.designsystem.component.ComplexityStars
import dev.quinton.goa.core.designsystem.component.EmptyState
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
import dev.quinton.goa.core.domain.catalog.HANDICAP_EXPLANATION
import dev.quinton.goa.core.domain.catalog.HeroPilotStat
import dev.quinton.goa.core.domain.catalog.attackProfile
import dev.quinton.goa.core.domain.catalog.complexityName
import dev.quinton.goa.core.domain.catalog.elo
import dev.quinton.goa.core.domain.catalog.handicapConflict
import dev.quinton.goa.core.domain.catalog.label
import dev.quinton.goa.core.domain.catalog.percent
import dev.quinton.goa.core.domain.catalog.plural
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.catalog.Ruling
import dev.quinton.goa.core.navigation.HeroDetailRoute
import dev.quinton.goa.feature.catalog.ui.LargePlate
import dev.quinton.goa.feature.catalog.ui.MetaPill
import dev.quinton.goa.feature.catalog.ui.SharedTitlePlate
import dev.quinton.goa.feature.catalog.ui.UnverifiedBadge
import dev.quinton.goa.feature.catalog.ui.overlapUp

@Composable
fun HeroDetailScreen(
    state: HeroDetailUiState,
    onBack: () -> Unit,
    onCardClick: (heroId: String, cardId: String) -> Unit,
    modifier: Modifier = Modifier,
    onLearnNumbers: ((heroId: String) -> Unit)? = null,
    /** [HeroDetailRoute.SPELLBOOK]: open at the spellbook (once; back and rotation keep where you are). */
    initialSection: String? = null,
) {
    val listState = rememberLazyListState()
    var opened by rememberSaveable { mutableStateOf(false) }
    val ready = state as? HeroDetailUiState.Ready
    LaunchedEffect(ready != null) {
        if (ready == null || opened) return@LaunchedEffect
        opened = true
        if (initialSection == HeroDetailRoute.SPELLBOOK) {
            extrasItemIndex(ready, onLearnNumbers != null)?.let { listState.scrollToItem(it) }
        }
    }
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

                is HeroDetailUiState.Ready -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = Spacing.xxl),
                ) {
                    heroDetailContent(
                        state,
                        onCardClick = { onCardClick(state.hero.id, it.id) },
                        onLearnNumbers = onLearnNumbers?.let { learn -> { learn(state.hero.id) } },
                    )
                }
            }
        }
    }
}

/**
 * Where [heroDetailContent] puts the extras (Gydion's spellbook): after the header, profile, stats,
 * deck title, the deck's groups, the trivia button and the Handicap card. Null without extras.
 */
internal fun extrasItemIndex(state: HeroDetailUiState.Ready, trivia: Boolean): Int? {
    if (state.deck.extras.isEmpty()) return null
    return FIXED_ITEMS + state.deck.groups.size + (if (trivia) 1 else 0) + (if (state.deck.handicap != null) 1 else 0)
}

/** header, profile, stats, deck-title. */
private const val FIXED_ITEMS = 4

private fun LazyListScope.heroDetailContent(
    state: HeroDetailUiState.Ready,
    onCardClick: (Card) -> Unit,
    onLearnNumbers: (() -> Unit)?,
) {
    val hero = state.hero
    val padded = Modifier.padding(horizontal = Spacing.screen)
    item(key = "header") { HeroHeader(hero, state.productName) }
    item(key = "profile") { StatPlate(hero.stats, padded.padding(top = Spacing.md)) }
    item(key = "stats") {
        Column(padded) {
            SectionHeader("Your table")
            PersonalStatsCard(state.stats)
        }
    }
    item(key = "deck-title") {
        SectionHeader(
            "Deck",
            modifier = padded.semantics(mergeDescendants = true) { heading() },
            trailing = {
                Text(
                    plural(hero.cards.size, "card"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
    state.deck.groups.forEach { group ->
        item(key = "deck-${group.kind}") { DeckGroupSection(group, onCardClick, padded) }
    }
    if (onLearnNumbers != null) {
        item(key = "deck-trivia") { LearnNumbersButton(hero.name, onLearnNumbers, padded) }
    }
    state.deck.handicap?.let { handicap ->
        item(key = "handicap") {
            Column(padded) {
                SectionHeader("Handicap")
                GoaPlate(Modifier.fillMaxWidth()) {
                    Text(
                        HANDICAP_EXPLANATION, // Rulebook p.20.
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    )
                    handicapConflict(hero, handicap)?.let { note ->
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
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
        item(key = "extras") { ExtrasSection(state.deck, onCardClick, padded) }
    }
    item(key = "faq-title") { SectionHeader("FAQ rulings", modifier = padded) }
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
        itemsIndexed(hero.faq, key = { i, _ ->
            "faq-$i"
        }) { _, ruling -> FaqItem(ruling, padded.padding(bottom = Spacing.sm)) }
    }
    hero.lore?.takeIf { it.isNotBlank() }?.let { lore ->
        item(key = "lore") {
            Column(padded) {
                SectionHeader("Lore")
                LoreBlock(lore)
            }
        }
    }
    item(key = "sources") {
        Column(padded) {
            SectionHeader("Sources")
            SourcesBlock(hero)
        }
    }
}

/** Opens card trivia on this hero: an unobtrusive tonal action under the deck. */
@Composable
private fun LearnNumbersButton(heroName: String, onClick: () -> Unit, modifier: Modifier) {
    OutlinedButton(
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
                    Text(complexityName(it).orEmpty(), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(hero.roles.joinToString(" · ") { it.label }, style = MaterialTheme.typography.bodySmall)
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
                Text(word, style = MaterialTheme.typography.bodySmall)
                if (i < words.lastIndex) {
                    Text(
                        " ·",
                        style = MaterialTheme.typography.bodySmall,
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
        Text("Verified", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonalStatsCard(stats: HeroPersonalStats) {
    GoaPlate(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.lg)) {
        if (!stats.hasGames) {
            Text("No games logged with this hero yet", style = MaterialTheme.typography.titleSmall)
            Text(
                "Log a match in the Players tab to see Elo, win rate and who plays this hero best.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            return@GoaPlate
        }
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
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        if (note != null) {
            Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                style = MaterialTheme.typography.labelSmall,
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
                    style = MaterialTheme.typography.labelSmall,
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
        Text(
            lore,
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.md),
        )
    }
}

@Composable
private fun SourcesBlock(hero: Hero) {
    if (hero.sources.isEmpty()) {
        Text(
            "No sources recorded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    hero.sources.forEach { source ->
        Column(Modifier.padding(bottom = Spacing.sm).semantics(mergeDescendants = true) {}) {
            Text(source.name, style = MaterialTheme.typography.bodyMedium)
            val meta = listOfNotNull(source.url, source.retrieved?.let { "retrieved $it" }).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Text(
        "Game content and art © Wolff Designa. Private, non-commercial build.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm),
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
private val HEADER_PLATE_MAX_WIDTH = 440.dp
private val HERO_PLATE_NAME = GoaTextStyles.plateTitle.copy(fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = 0.sp)
private const val MOTION_MS = 250
private const val MAX_PILOTS = 3
