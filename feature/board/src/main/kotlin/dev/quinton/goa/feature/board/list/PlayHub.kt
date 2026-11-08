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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.BevelText
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.changedHere
import dev.quinton.goa.feature.board.ui.PlateSurface

/**
 * One of the Play tab's entries: a painted scene (the app's own art, `third_party/ASSETS.md`) framed in the slate
 * plate, its name engraved over the scene, then, in bronze, its state ([status], or [progress]: "1 of 50 positions")
 * and what it is. The whole plate is one button; TalkBack reads its name, its state and what it is.
 */
@Composable
internal fun ArtPlate(
    @DrawableRes art: Int,
    title: String,
    text: String,
    onClick: () -> Unit,
    artHeight: Dp,
    modifier: Modifier = Modifier,
    status: String? = null,
    enabled: Boolean = true,
    artAlignment: Alignment = Alignment.Center,
    progress: (@Composable () -> Unit)? = null,
) {
    PlateSurface(
        onClick = onClick,
        enabled = enabled,
        // One choice of several side by side: the steel edge without the section's brackets, so the scene is the
        // ornament and six tiles do not repeat four corner fittings each (DESIGN.md "Framing levels").
        brackets = false,
        modifier = modifier.semantics { if (!enabled) stateDescription = "Not available" },
    ) {
        Column(Modifier.padding(ART_INSET)) {
            Box(
                Modifier
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    if (status != null) {
                        Text(
                            status,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = with(MaterialTheme.colorScheme) { if (enabled) primary else onSurfaceVariant },
                        )
                    }
                    progress?.invoke()
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (enabled) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** The game against the computer saved on this phone: its line-up, who plays it, and Resume. */
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
                            "Game in progress" + (if (names.isNotEmpty()) ": $names. " else ". ") + resume.summary
                    },
            ) {
                Text(
                    if (resume.savedAt > 0) {
                        "GAME IN PROGRESS · " +
                            BoardText.changedHere(resume.savedAt, now).removePrefix("Changed ").uppercase()
                    } else {
                        "GAME IN PROGRESS"
                    },
                    style = GoaTextStyles.overline,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                if (resume.red.isNotEmpty() || resume.blue.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        itemVerticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = Spacing.xs),
                    ) {
                        LineupHeroes(Team.RED, resume.red, size = 26.dp)
                        Text(
                            "vs",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        LineupHeroes(Team.BLUE, resume.blue, size = 26.dp)
                    }
                }
                Text(
                    resume.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp),
                )
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
