package com.ironquest.app.ui.kit

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.ironquest.app.ui.theme.IronTheme

/** Screen title in the style's title font; Bold upper-cases it. [trailing] sits top-right (e.g. a LevelMark). */
@Composable
fun PageTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            IronTheme.style.cased(text),
            style = IronTheme.type.pageTitle,
            color = IronTheme.colors.ink,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing?.invoke(this)
    }
}
