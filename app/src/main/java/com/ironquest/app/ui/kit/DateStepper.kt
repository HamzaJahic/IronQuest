package com.ironquest.app.ui.kit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ironquest.app.R
import com.ironquest.app.ui.theme.IronTheme

/** "‹  Friday, 9 October  ›": steps a day/week/month; the forward arrow is disabled at the present. */
@Composable
fun DateStepper(
    label: String,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    unit: String,
    modifier: Modifier = Modifier,
    /** Makes the label tappable (to pick a date). */
    onLabelClick: (() -> Unit)? = null,
) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    val pickLabel = stringResource(R.string.kit_date_stepper_pick_label, unit)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.kit_date_stepper_previous_cd, unit), tint = colors.ink)
        }
        Text(
            label,
            style = type.secondary,
            color = if (onLabelClick != null) colors.ink else colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .then(if (onLabelClick != null) Modifier.clickable(onClickLabel = pickLabel, role = Role.Button, onClick = onLabelClick) else Modifier)
                .then(if (onLabelClick != null) Modifier.padding(vertical = 12.dp) else Modifier),
        )
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.kit_date_stepper_next_cd, unit), tint = if (canGoForward) colors.ink else colors.line)
        }
    }
}
