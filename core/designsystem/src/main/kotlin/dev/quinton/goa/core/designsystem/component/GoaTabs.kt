package dev.quinton.goa.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing

/** A [GoaTabRow]'s height: content stuck under it (a hero page's deck filters) sits just below. */
val GoaTabRowHeight = 48.dp

/**
 * The app's one page-tab bar (hero page, Players, trivia leaderboard): the tabs spread evenly across the width, the
 * selected one in the app's selected look (a bronze wash, a bold bronze word and a bronze rule under it), the others in
 * quiet words, an engraved line under the bar. With large text that no longer fits it scrolls sideways (faded edges),
 * never cutting a word. TalkBack hears each as a tab, selected or not.
 */
@Composable
fun GoaTabRow(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val rule = GoaTheme.colors.selection
    val wash = GoaTheme.colors.selectionFill
    val scroll = rememberScrollState()
    Column(modifier.fillMaxWidth().background(colors.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = GoaTabRowHeight)
                .fadingHorizontalScroll(scroll, gutter = Spacing.sm)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    Modifier
                        .widthIn(min = TAB_MIN_WIDTH)
                        .heightIn(min = GoaTabRowHeight)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                        .drawBehind {
                            if (selected) {
                                val h = UNDERLINE.toPx()
                                val inset = TAB_TINT_INSET.toPx()
                                drawRoundRect(
                                    wash,
                                    topLeft = Offset(0f, inset),
                                    size = Size(size.width, size.height - inset - h),
                                    cornerRadius = CornerRadius(TAB_TINT_CORNER.toPx()),
                                )
                                drawRect(rule, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
                            }
                        }
                        .padding(horizontal = Spacing.md),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (selected) colors.primary else colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(vertical = Spacing.sm),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(GoaTheme.colors.engravedBorder))
    }
}

private val UNDERLINE = 3.dp
private val TAB_MIN_WIDTH = 72.dp
private val TAB_TINT_INSET = 6.dp
private val TAB_TINT_CORNER = 8.dp
