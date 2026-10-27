package com.ironquest.app.ui.kit

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ironquest.app.R
import com.ironquest.app.ui.theme.IronTheme

/**
 * Top row of a pushed screen: "‹ Back" on the left, optional [actions] (icon buttons, text links)
 * on the right. Pads for the status bar itself. Put a [PageTitle] below it.
 */
@Composable
fun BackHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backLabel: String = stringResource(R.string.common_back),
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            .padding(start = 8.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.kit_back_header_back, backLabel), style = IronTheme.type.body, color = IronTheme.colors.muted)
        }
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/** [BackHeader] plus the screen's [PageTitle], for a Scaffold `topBar`. */
@Composable
fun ScreenTop(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backLabel: String = stringResource(R.string.common_back),
    actions: @Composable RowScope.() -> Unit = {},
) {
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth()) {
        BackHeader(onBack = onBack, backLabel = backLabel, actions = actions)
        PageTitle(title, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp))
    }
}

/** A small text action for [BackHeader] ("Import", "New"). */
@Composable
fun HeaderAction(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(
            IronTheme.style.cased(text),
            style = IronTheme.type.bodyStrong,
            color = if (enabled) IronTheme.colors.ink else IronTheme.colors.muted,
        )
    }
}
