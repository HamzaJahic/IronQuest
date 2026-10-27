package com.ironquest.app.ui.kit

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ironquest.app.ui.theme.IronTheme

/** Section heading ("Stats", "Appearance"). Bold upper-cases it. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        IronTheme.style.cased(text),
        style = IronTheme.type.sectionHead,
        color = IronTheme.colors.ink,
        modifier = modifier.padding(top = 28.dp, bottom = 8.dp).semantics { heading() },
    )
}
