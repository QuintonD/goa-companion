package dev.quinton.goa.feature.rules.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaCard
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.presentation.rules.RulesText
import dev.quinton.goa.core.presentation.rules.RulesUiState
import dev.quinton.goa.core.presentation.rules.TopicItem

/** Rules tab: the level & defeat ladder first, then searchable quick-reference topics. */
@Composable
fun RulesScreen(
    state: RulesUiState,
    onQueryChange: (String) -> Unit,
    onOpenLadder: () -> Unit,
    onOpenTopic: (String) -> Unit,
    onBack: () -> Unit,
) {
    GoaScreen(title = "Rules", onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                RulesUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                RulesUiState.Unavailable -> EmptyState(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    title = "Rules unavailable",
                    message = RulesText.UNAVAILABLE,
                    modifier = Modifier.align(Alignment.Center),
                )

                is RulesUiState.Ready -> RulesList(state, onQueryChange, onOpenLadder, onOpenTopic)
            }
        }
    }
}

@Composable
private fun RulesList(
    state: RulesUiState.Ready,
    onQueryChange: (String) -> Unit,
    onOpenLadder: () -> Unit,
    onOpenTopic: (String) -> Unit,
) {
    val showLadder = state.showLadder
    LazyColumn(
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "search") { SearchField(state.query, onQueryChange) }
        if (showLadder) {
            item(key = "ladder") { LadderCard(onOpenLadder) }
        }
        item(key = "topics-header") {
            SectionHeader(state.topicsHeader)
        }
        if (state.isEmpty) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = RulesText.EMPTY_TITLE,
                    message = RulesText.EMPTY_MESSAGE,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        items(state.topics, key = { it.id }) { topic -> TopicRow(topic) { onOpenTopic(topic.id) } }
        item(key = "source") {
            Text(
                RulesText.LIST_SOURCE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.lg),
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("Search rules") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
private fun LadderCard(onClick: () -> Unit) {
    GoaCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.MilitaryTech,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(scaledIconSize(32.dp)),
            )
            Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                Text(
                    RulesText.LADDER_TITLE,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    RulesText.LADDER_SUBTITLE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PageTag(RulesText.LADDER_PAGES)
        }
    }
}

@Composable
private fun TopicRow(topic: TopicItem, onClick: () -> Unit) {
    GoaCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "${topic.title}, ${RulesText.spokenPages(topic.pages)}"
        },
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    topic.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
                )
                Text(
                    RulesText.pages(topic.pages),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
