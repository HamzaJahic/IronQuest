package com.ironquest.app.ui.kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ironquest.app.R
import com.ironquest.app.ui.theme.IronMotion
import com.ironquest.app.ui.theme.IronTheme

/**
 * A section heading that folds its [content] away. The folded header can show a one-line
 * [summary] (current value) so the page stays scannable. Expansion state belongs to the caller.
 */
@Composable
fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    // Read only inside graphicsLayer: the turn redraws one layer, it never recomposes or re-measures.
    val angle = animateFloatAsState(if (expanded) 90f else 0f, tween(IronMotion.SHORT_MS), label = "chevron")
    val toggleLabel = stringResource(if (expanded) R.string.kit_section_collapse_label else R.string.kit_section_expand_label, title)
    val stateText = stringResource(if (expanded) R.string.kit_section_expanded else R.string.kit_section_collapsed)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = toggleLabel, role = Role.Button, onClick = onToggle)
                .padding(top = 24.dp, bottom = 10.dp)
                .semantics {
                    heading()
                    stateDescription = stateText
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(IronTheme.style.cased(title), style = type.sectionHead, color = colors.ink)
            if (!expanded && summary != null) {
                Text(summary, style = type.secondary, color = colors.muted, modifier = Modifier.weight(1f).padding(start = 12.dp), maxLines = 1)
            } else {
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
            Text("›", style = type.body, color = colors.muted, modifier = Modifier.graphicsLayer { rotationZ = angle.value })
        }
        // Instant on purpose: an animated height inside the You tab's scroll column moved the
        // whole page for ~300 ms (and two sections animate at once when one opens another closes).
        if (expanded) Column(Modifier.fillMaxWidth()) { content() }
    }
}
