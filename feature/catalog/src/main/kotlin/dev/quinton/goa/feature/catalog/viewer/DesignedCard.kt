package dev.quinton.goa.feature.catalog.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.cardtext.CardTextView
import dev.quinton.goa.core.designsystem.cardtext.GameIcons
import dev.quinton.goa.core.designsystem.component.cardColor
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.feature.catalog.detail.printedValues
import dev.quinton.goa.feature.catalog.ui.InitiativeBadge
import dev.quinton.goa.feature.catalog.ui.kindLabel

/**
 * Our own rendering of a card for when no official image is bundled: card-coloured frame,
 * name, initiative, primary action, values and the printed text with inline icons.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesignedCard(card: Card, isHandicap: Boolean, modifier: Modifier = Modifier) {
    val accent = cardColor(card.color)
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .background(Brush.verticalGradient(0f to accent.copy(alpha = 0.28f), 0.45f to accent.copy(alpha = 0.04f)))
            .border(2.dp, accent.copy(alpha = 0.8f), shape)
            .padding(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    card.kindLabel(isHandicap).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
                Text(card.name, style = MaterialTheme.typography.headlineSmall)
            }
            InitiativeBadge(card.initiative, accent = accent, size = 48.dp)
        }
        Spacer(Modifier.height(Spacing.lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            card.primary?.let { primary ->
                val icon = GameIcons.forPrimary(primary)
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.18f))
                        .border(1.dp, accent, CircleShape)
                        .semantics { contentDescription = "Primary action ${icon.label}" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon.vector, contentDescription = null, tint = accent, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(Spacing.lg))
            }
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                card.printedValues().forEach { value ->
                    Row(
                        Modifier.clearAndSetSemantics { contentDescription = value.spoken },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(value.icon.vector, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(value.printed, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
        if (card.text.isNotBlank()) {
            Spacer(Modifier.height(Spacing.lg))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f))
                    .padding(Spacing.md),
            ) {
                CardTextView(card.text, iconTint = accent)
            }
        }
    }
}
