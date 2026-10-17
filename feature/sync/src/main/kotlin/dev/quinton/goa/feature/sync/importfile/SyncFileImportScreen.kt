package dev.quinton.goa.feature.sync.importfile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.GoaCard
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.sync.SyncFileProblem
import dev.quinton.goa.feature.sync.SyncText

class SyncFileImportActions(
    val onBack: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onOpenGroup: () -> Unit = {},
)

@Composable
fun SyncFileImportScreen(state: SyncFileImportUiState, actions: SyncFileImportActions) {
    GoaScreen(title = "Import sync file", onBack = actions.onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            when (val stage = state.stage) {
                ImportStage.Reading, ImportStage.Importing -> Box(
                    Modifier.fillMaxWidth().padding(Spacing.xl),
                    Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                is ImportStage.Failed -> {
                    Title("Can't import this file")
                    Text(
                        stage.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    if (stage.problem == SyncFileProblem.NOT_IN_A_GROUP) {
                        Button(onClick = actions.onOpenGroup, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Group sync")
                        }
                    }
                    OutlinedButton(onClick = actions.onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Close")
                    }
                }

                is ImportStage.Ready -> {
                    Title("From “${stage.groupName}”")
                    GoaCard(Modifier.fillMaxWidth()) {
                        Text(
                            "${stage.matches} matches and ${stage.players} players in the file.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "On this phone: ${SyncText.summary(stage.changes)}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(
                        "Merging works like a sync: anything new is added, and where both have the same match or " +
                            "player, the later edit wins. Matches deleted on the other phone are deleted here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = actions.onConfirm,
                        enabled = !stage.changes.isEmpty,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text(if (stage.changes.isEmpty) "Nothing to merge" else "Merge into this phone") }
                }

                is ImportStage.Done -> {
                    Title("Merged")
                    Text(
                        SyncText.summary(stage.changes),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Button(onClick = actions.onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
}
