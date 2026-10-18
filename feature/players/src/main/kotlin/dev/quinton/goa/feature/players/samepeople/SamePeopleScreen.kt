package dev.quinton.goa.feature.players.samepeople

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.ConfirmDialog
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaCard
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.feature.players.common.LoadingState
import dev.quinton.goa.feature.players.common.plural
import dev.quinton.goa.feature.players.player.PlayedTogetherDialog

class SamePeopleActions(
    val onBack: () -> Unit = {},
    val onSamePerson: (String) -> Unit = {},
    val onDifferentPeople: (String) -> Unit = {},
    val onAskAgain: () -> Unit = {},
    val onOutcomeShown: () -> Unit = {},
)

/** "Same people?" (ADR-031): pairs of players that might be one person. */
@Composable
fun SamePeopleScreen(state: SamePeopleUiState, actions: SamePeopleActions, confirmKey: String? = null) {
    var confirming by rememberSaveable { mutableStateOf(confirmKey) }
    GoaScreen(title = "Same people?", onBack = actions.onBack) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))

            state.pairs.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                EmptyState(
                    icon = Icons.Outlined.People,
                    title = "No look-alikes",
                    message = "No two players have names this alike. To merge any two players, open one and " +
                        "use Merge into…",
                    action = if (state.differentCount > 0) {
                        { AskAgain(state.differentCount, actions.onAskAgain) }
                    } else {
                        null
                    },
                )
            }

            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.screen,
                    end = Spacing.screen,
                    top = padding.calculateTopPadding() + Spacing.sm,
                    bottom = padding.calculateBottomPadding() + Spacing.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item(key = "intro") {
                    Text(
                        "These names look alike and the two never played in the same match. If it's one person " +
                            "(say, a friend you added who then joined from their own phone), merge them: their " +
                            "matches and ratings become one, on every phone after the next sync.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.pairs, key = { it.key }) { pair ->
                    PairCard(
                        pair,
                        onSame = { confirming = pair.key },
                        onDifferent = { actions.onDifferentPeople(pair.key) },
                    )
                }
                if (state.differentCount > 0) {
                    item(key = "ask-again") { AskAgain(state.differentCount, actions.onAskAgain) }
                }
            }
        }
    }
    state.pairs.firstOrNull { it.key == confirming }?.let { pair ->
        val from = pair.other
        val into = pair.keep
        ConfirmDialog(
            title = "Merge ${from.player.name} into ${into.player.name}?",
            message = "${from.player.name} (${plural(from.games, "match", "matches")}) becomes " +
                "${into.player.name} (${plural(into.games, "match", "matches")}). Their matches and ratings count " +
                "as one player from now on, and the other phones follow at the next sync. ${from.player.name} " +
                "disappears from the lists. This can't be undone.",
            confirmLabel = "Merge",
            destructive = true,
            icon = Icons.Outlined.MergeType,
            onConfirm = {
                confirming = null
                actions.onSamePerson(pair.key)
            },
            onDismiss = { confirming = null },
        )
    }
    (state.outcome as? SamePeopleOutcome.PlayedTogether)?.let { refused ->
        PlayedTogetherDialog(refused.matches, onDismiss = actions.onOutcomeShown)
    }
}

@Composable
private fun PairCard(pair: SamePeoplePair, onSame: () -> Unit, onDifferent: () -> Unit) {
    GoaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.md)) {
        Side(pair.keep)
        HorizontalDivider(Modifier.padding(vertical = Spacing.xs), color = MaterialTheme.colorScheme.outlineVariant)
        Side(pair.other)
        FlowRow(
            Modifier.fillMaxWidth().padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            OutlinedButton(
                onClick = onDifferent,
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    contentDescription = "${pair.keep.player.name} and ${pair.other.player.name} are different people"
                },
            ) { Text("Different people") }
            Button(
                onClick = onSame,
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    contentDescription = "${pair.keep.player.name} and ${pair.other.player.name} are the same person"
                },
            ) { Text("Same person") }
        }
    }
}

@Composable
private fun Side(side: PairSide) {
    val summary = SamePeopleViewModel.summary(side)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${side.player.name}, $summary" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerAvatar(side.player, size = 40.dp)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                side.player.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AskAgain(count: Int, onAskAgain: () -> Unit) {
    TextButton(onClick = onAskAgain, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(
            if (count == 1) {
                "Ask again about the pair you said were different"
            } else {
                "Ask again about the $count pairs you said were different"
            },
        )
    }
}
