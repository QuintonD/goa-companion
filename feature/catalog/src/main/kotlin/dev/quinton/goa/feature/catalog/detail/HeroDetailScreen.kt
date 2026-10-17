package dev.quinton.goa.feature.catalog.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.ComplexityStars
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.catalog.Ruling
import dev.quinton.goa.feature.catalog.model.HeroPilotStat
import dev.quinton.goa.feature.catalog.model.attackProfile
import dev.quinton.goa.feature.catalog.ui.MetaPill
import dev.quinton.goa.feature.catalog.ui.SectionTitle
import dev.quinton.goa.feature.catalog.ui.UnverifiedBadge
import dev.quinton.goa.feature.catalog.ui.complexityName
import dev.quinton.goa.feature.catalog.ui.elo
import dev.quinton.goa.feature.catalog.ui.label
import dev.quinton.goa.feature.catalog.ui.percent
import dev.quinton.goa.feature.catalog.ui.plural

@Composable
fun HeroDetailScreen(
    state: HeroDetailUiState,
    onBack: () -> Unit,
    onCardClick: (heroId: String, cardId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val headerGone by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val title = (state as? HeroDetailUiState.Ready)?.hero?.name?.takeIf { headerGone }.orEmpty()
    GoaScreen(title = title, onBack = onBack, modifier = modifier) { padding ->
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
                    heroDetailContent(state, onCardClick = { onCardClick(state.hero.id, it.id) })
                }
            }
        }
    }
}

private fun LazyListScope.heroDetailContent(state: HeroDetailUiState.Ready, onCardClick: (Card) -> Unit) {
    val hero = state.hero
    val padded = Modifier.padding(horizontal = Spacing.screen)
    item(key = "header") { HeroHeader(hero, state.productName) }
    item(key = "profile") {
        Column(padded) {
            SectionTitle("Profile")
            StatBars(hero.stats)
        }
    }
    item(key = "stats") {
        Column(padded) {
            SectionTitle("Your table")
            PersonalStatsCard(state.stats)
        }
    }
    item(key = "deck-title") {
        SectionTitle(
            "Deck",
            modifier = padded,
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
    state.deck.handicap?.let { handicap ->
        item(key = "handicap") {
            Column(padded) {
                SectionTitle("Handicap")
                Text(
                    // Rulebook p.20.
                    "A weaker version of a basic card (usually Gold). With 5, 7 or 9 players, every hero on the " +
                        "larger team replaces one of its basic cards with it. With 3 players, the player controlling " +
                        "two heroes replaces the Gold card of one of them with it. In even-count games, the most " +
                        "experienced players may use it to even out skill.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
                CardTileGrid(listOf(handicap), columns = 2, onCardClick = onCardClick, isHandicap = true)
            }
        }
    }
    if (state.deck.extras.isNotEmpty()) {
        item(key = "extras") { ExtrasSection(state.deck, onCardClick, padded) }
    }
    item(key = "faq-title") { SectionTitle("FAQ rulings", modifier = padded) }
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
                SectionTitle("Lore")
                LoreBlock(lore)
            }
        }
    }
    item(key = "sources") {
        Column(padded) {
            SectionTitle("Sources")
            SourcesBlock(hero)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroHeader(hero: Hero, productName: String) {
    val background = MaterialTheme.colorScheme.background
    Column {
        Box(Modifier.fillMaxWidth().height(HEADER_ART_HEIGHT)) {
            HeroPortrait(
                heroId = hero.id,
                portrait = hero.portrait,
                contentDescription = "${hero.name} artwork",
                shape = RectangleShape,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.45f to Color.Transparent, 1f to background),
                ),
            )
        }
        Column(Modifier.padding(horizontal = Spacing.screen).padding(top = Spacing.xs)) {
            Text(hero.name, style = MaterialTheme.typography.headlineMedium)
            Text(
                hero.title,
                style = MaterialTheme.typography.titleMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hero.aliases.isNotEmpty()) {
                Text(
                    "Also known as ${hero.aliases.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            FlowRow(
                Modifier.padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                hero.complexity?.let { level ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ComplexityStars(level, starSize = 18.dp)
                        complexityName(level)?.let {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                MetaPill(productName)
                // App-derived from the Ranged subtype of the deck's Attack cards (DOMAIN.md); kept
                // apart from the official role chips because "Melee" is also an official role name.
                hero.attackProfile().pill?.let { MetaPill(it) }
                if (hero.printing != 2) MetaPill("Printing ${hero.printing}")
                if (hero.verified) VerifiedPill() else UnverifiedBadge()
            }
            val roles = hero.roles.map { it.label }
            if (roles.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(top = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    roles.forEach { RolePill(it) }
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

@Composable
private fun RolePill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
            .padding(horizontal = Spacing.md, vertical = 6.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonalStatsCard(stats: HeroPersonalStats) {
    val shape = MaterialTheme.shapes.medium
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, GoaTheme.colors.engravedBorder, shape)
            .padding(Spacing.lg),
    ) {
        if (!stats.hasGames) {
            Text("No games logged with this hero yet", style = MaterialTheme.typography.titleSmall)
            Text(
                "Log a match in the Players tab to see Elo, win rate and who plays this hero best.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            return@Column
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
                        "${pilot.wins} wins, ${pilot.games - pilot.wins} losses"
                },
            )
        }
    }
}

@Composable
private fun FaqItem(ruling: Ruling, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(ruling.question) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(MOTION_MS), label = "faqChevron")
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, GoaTheme.colors.engravedBorder, shape),
    ) {
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

private val HEADER_ART_HEIGHT = 280.dp
private const val MOTION_MS = 250
private const val MAX_PILOTS = 3
