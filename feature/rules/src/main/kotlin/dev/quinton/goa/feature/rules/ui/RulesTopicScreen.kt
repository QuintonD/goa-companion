package dev.quinton.goa.feature.rules.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.rules.RulesTopic
import dev.quinton.goa.feature.rules.RulesText
import dev.quinton.goa.feature.rules.RulesTopicUiState

/** One quick-reference topic: an optional labelled summary, then the verbatim rulebook passages. */
@Composable
fun RulesTopicScreen(state: RulesTopicUiState, onBack: () -> Unit) {
    val title = (state as? RulesTopicUiState.Ready)?.topic?.title ?: "Rules"
    GoaScreen(title = title, onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                RulesTopicUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                RulesTopicUiState.NotFound -> EmptyState(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    title = "Topic not found",
                    message = "This rules topic is not in the bundled reference.",
                    modifier = Modifier.align(Alignment.Center),
                )

                is RulesTopicUiState.Ready -> TopicContent(state.topic)
            }
        }
    }
}

@Composable
internal fun TopicContent(topic: RulesTopic) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item(key = "pages") {
            Text(
                "Rulebook ${RulesText.pages(topic.pages)} · quoted word for word",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        topic.summary?.let { summary -> item(key = "summary") { SummaryNote(summary) } }
        itemsIndexed(topic.excerpts, key = { i, _ -> "excerpt-$i" }) { i, excerpt ->
            if (i > 0 && excerpt.heading != null) {
                HorizontalDivider(Modifier.padding(bottom = Spacing.lg), color = GoaTheme.colors.engravedBorder)
            }
            ExcerptBlock(excerpt)
        }
        item(key = "source") {
            Text(
                "Source: the official Guards of Atlantis II rulebook, 2nd printing (printed page numbers).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xl),
            )
        }
    }
}
