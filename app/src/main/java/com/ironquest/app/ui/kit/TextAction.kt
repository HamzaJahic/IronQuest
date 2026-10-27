package com.ironquest.app.ui.kit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ironquest.app.ui.theme.IronTheme
import com.ironquest.app.ui.theme.ListTreatment

enum class ActionTone { INK, MUTED, DANGER }

/** A plain text button in body type: "Edit", "Clear", "Retry", "Delete" ([ActionTone.DANGER]). */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ActionTone = ActionTone.INK,
    strong: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = IronTheme.colors
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(
            text,
            style = if (strong) IronTheme.type.bodyStrong else IronTheme.type.body,
            color = when {
                !enabled -> colors.line
                tone == ActionTone.DANGER -> colors.danger
                tone == ActionTone.MUTED -> colors.muted
                else -> colors.ink
            },
        )
    }
}

/** A muted, full-width "+ …" link row: "+ Log something else", "+ Add set", "+ Add round". */
@Composable
fun AddLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        style = IronTheme.type.body,
        color = IronTheme.colors.muted,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

/** One action in a [RowActions] line. */
data class RowAction(val label: String, val onClick: () -> Unit, val destructive: Boolean = false)

/** The line of text actions under a card ("Edit  Share  Delete"); indented on tile styles to line up with the tile. */
@Composable
fun RowActions(actions: List<RowAction>, modifier: Modifier = Modifier) {
    val indent = if (IronTheme.style.listTreatment == ListTreatment.TILES) 8.dp else 0.dp
    Row(modifier.padding(horizontal = indent)) {
        actions.forEach { action ->
            TextAction(action.label, action.onClick, tone = if (action.destructive) ActionTone.DANGER else ActionTone.INK)
        }
    }
}
