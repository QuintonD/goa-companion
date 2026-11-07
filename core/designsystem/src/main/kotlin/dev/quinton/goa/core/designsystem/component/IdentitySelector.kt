package dev.quinton.goa.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.theme.Spacing

/**
 * Who this phone plays as, one row everywhere it matters (Online, Card trivia, Group sync): the player's mark
 * ([avatar], given its size), "Playing as Ann", the scope it counts in under it ("Online", "Group · Friday nights",
 * "Guest practice"), and a chevron when a tap changes it. Read only, with no chevron, where it can't be changed here
 * (no [onClick]). One quiet plate and one TalkBack node ("Playing as Ann, you. Group · Friday nights").
 */
@Composable
fun IdentitySelector(
    title: String,
    scope: String,
    avatar: @Composable (Dp) -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String = "Change player",
    spoken: String = "$title. $scope",
    tag: (@Composable () -> Unit)? = null,
) {
    val click = if (onClick != null) {
        Modifier.clip(PlateShape).clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    GoaPlate(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(click)
            .clearAndSetSemantics {
                contentDescription = spoken
                if (onClick != null) {
                    role = Role.Button
                    onClick(label = onClickLabel) {
                        onClick()
                        true
                    }
                }
            },
        brackets = false,
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            avatar(scaledIconSize(IDENTITY_AVATAR, maxScale = 1.5f))
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (tag != null) {
                        Spacer(Modifier.width(Spacing.sm))
                        tag()
                    }
                }
                Text(
                    scope,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onClick != null) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.sm).size(scaledIconSize(24.dp)),
                )
            }
        }
    }
}

/** The mark's size in an [IdentitySelector]: big enough for the most-played hero badge. */
private val IDENTITY_AVATAR = 40.dp
