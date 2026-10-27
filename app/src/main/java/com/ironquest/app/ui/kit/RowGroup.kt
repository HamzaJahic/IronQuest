package com.ironquest.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ironquest.app.ui.theme.IronTheme
import com.ironquest.app.ui.theme.ListTreatment

/** A group of [RuledRow]s: no container for Diary/Bold, one `raised` tile for Soft. */
@Composable
fun RowGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (IronTheme.style.listTreatment == ListTreatment.TILES) {
        Column(
            modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clip(IronTheme.shapes.tile)
                .background(IronTheme.colors.raised)
                .padding(vertical = 4.dp),
            content = content,
        )
    } else {
        Column(modifier.fillMaxWidth(), content = content)
    }
}
