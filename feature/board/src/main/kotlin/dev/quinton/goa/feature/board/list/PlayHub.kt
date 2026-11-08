package dev.quinton.goa.feature.board.list

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.BevelText
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.changedHere
import dev.quinton.goa.feature.board.ui.PlateSurface

/**
 * One of the Play tab's entries: a painted scene (the app's own art, `third_party/ASSETS.md`) filling the slate plate,
 * its name engraved over the scene's foot. State is a badge on the scene, never a sentence: [badge] (the puzzles'
 * solved count) at its top end, a greyed scene with "Unavailable" when the build can't serve it. The whole plate is
 * one button; TalkBack reads its name and [state].
 */
@Composable
internal fun ArtPlate(
    @DrawableRes art: Int,
    title: String,
    onClick: () -> Unit,
    artHeight: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    artAlignment: Alignment = Alignment.Center,
    state: String? = null,
    badge: (@Composable () -> Unit)? = null,
) {
    PlateSurface(
        onClick = onClick,
        enabled = enabled,
        // One choice of several side by side: the steel edge without the section's brackets, so the scene is the
        // ornament and six tiles do not repeat four corner fittings each (DESIGN.md "Framing levels").
        brackets = false,
        modifier = modifier.semantics {
            when {
                !enabled -> stateDescription = "Not available"
                state != null -> stateDescription = state
            }
        },
    ) {
        Box(
            Modifier
                .padding(ART_INSET)
                .fillMaxWidth()
                .height(artHeight)
                .clip(ArtShape),
        ) {
            Image(
                painterResource(art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = artAlignment,
                colorFilter = if (enabled) null else GREY,
                modifier = Modifier.matchParentSize(),
            )
            // The scene darkens toward its foot, where the name is engraved.
            Box(Modifier.matchParentSize().background(SCRIM))
            val corner = Modifier.align(Alignment.TopEnd).padding(Spacing.sm)
            when {
                !enabled -> ArtBadge(corner) { BadgeText("Unavailable") }
                badge != null -> ArtBadge(corner.clearAndSetSemantics {}) { badge() }
            }
            // The engraved name grows with large text only so far (1.5x), so it stays on the scene.
            val scale = LocalDensity.current.fontScale
            val size = TITLE_SP * scale.coerceAtMost(TITLE_MAX_SCALE) / scale
            BevelText(
                title,
                style = GoaTextStyles.plateTitle.copy(fontSize = size.sp, lineHeight = (size * TITLE_LEAD).sp),
                maxLines = 2,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        }
    }
}

/** A small dark pill on a scene's corner: state at a glance ("✓ 2/72", "Unavailable"). */
@Composable
private fun ArtBadge(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier
            .clip(BadgeShape)
            .background(BADGE)
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun BadgeText(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = BADGE_INK)
}

/** The puzzles' badge: a teal tick and solved / total ("✓ 2/72"). */
@Composable
internal fun SolvedBadge(solved: Int, total: Int) {
    Icon(
        Icons.Outlined.CheckCircle,
        contentDescription = null,
        tint = BADGE_TICK,
        modifier = Modifier.size(scaledIconSize(16.dp, 1.4f)),
    )
    Spacer(Modifier.width(4.dp))
    BadgeText("$solved/$total")
}

/**
 * The game against the computer saved on this phone: its line-up and Resume. Who you play, the computer's strength
 * and when it was saved are for TalkBack; the board shows them once you are back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ResumeCard(resume: ResumeUi, now: Long, onResume: () -> Unit, modifier: Modifier = Modifier) {
    val names = listOf(resume.red, resume.blue).filter { it.isNotEmpty() }
        .joinToString(" vs ") { team -> team.joinToString(", ") { it.name } }
    // Large text: the button under the words, so they keep the width.
    val stacked = LocalDensity.current.fontScale > LARGE_TEXT
    PlateSurface(
        brackets = true,
        border = BorderStroke(1.dp, RESUME_EDGE),
        modifier = modifier.fillMaxWidth(),
    ) {
        FlowRow(
            Modifier.padding(start = Spacing.md, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
            maxItemsInEachRow = if (stacked) 1 else 2,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {
                        contentDescription =
                            "Game in progress" + (if (names.isNotEmpty()) ": $names. " else ". ") + resume.summary +
                            (if (resume.savedAt > 0) ". " + BoardText.changedHere(resume.savedAt, now) else "")
                    },
            ) {
                if (resume.red.isNotEmpty() || resume.blue.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        LineupHeroes(Team.RED, resume.red, size = LINEUP)
                        Text(
                            "vs",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        LineupHeroes(Team.BLUE, resume.blue, size = LINEUP)
                    }
                }
                // Only a save that can't be read says so in words: the line-up and Resume say the rest.
                if (resume.unreadable) {
                    Text(
                        resume.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            GoaButton(
                onClick = onResume,
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                modifier = Modifier.heightIn(min = 48.dp).then(if (stacked) Modifier.fillMaxWidth() else Modifier),
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (resume.unreadable) "Open" else "Resume")
            }
        }
    }
}

private val ART_INSET = 4.dp
private val ArtShape = RoundedCornerShape(6.dp)
private const val TITLE_SP = 19f
private const val TITLE_MAX_SCALE = 1.5f
private const val TITLE_LEAD = 1.2f
private val SCRIM = Brush.verticalGradient(
    0f to Color.Transparent,
    0.45f to Color.Transparent,
    1f to Color(0xD9070B10),
)
private val GREY = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
private val RESUME_EDGE = Color(0xFF3FC1B0)
private const val LARGE_TEXT = 1.3f
private val LINEUP = 32.dp
private val BadgeShape = RoundedCornerShape(50)
private val BADGE = Color(0xCC070B10)
private val BADGE_INK = Color(0xFFF3E7CF)
private val BADGE_TICK = Color(0xFF3FC1B0)
