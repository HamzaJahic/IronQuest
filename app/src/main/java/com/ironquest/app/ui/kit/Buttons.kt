package com.ironquest.app.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ironquest.app.ui.theme.ButtonVariant
import com.ironquest.app.ui.theme.IronTheme
import java.util.Locale

/**
 * Full-width main action. Diary: rounded `accent` fill. Soft: `accent` pill. Bold: square block,
 * upper-case label on the left and an arrow on the right ("START →").
 */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = IronTheme.colors
    val bold = IronTheme.style.buttonVariant == ButtonVariant.BLOCK_ARROW
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = if (bold) 52.dp else 48.dp),
        shape = IronTheme.shapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.onAccent,
            disabledContainerColor = colors.line,
            disabledContentColor = colors.muted,
        ),
        contentPadding = PaddingValues(horizontal = if (bold) 16.dp else 24.dp, vertical = 12.dp),
    ) {
        if (bold) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text.uppercase(Locale.getDefault()), style = IronTheme.type.button)
                Text("→", style = IronTheme.type.button)
            }
        } else {
            Text(text, style = IronTheme.type.button)
        }
    }
}

/** Outlined companion to [PrimaryButton]: `ink` border and text, same shape. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = IronTheme.colors
    val bold = IronTheme.style.buttonVariant == ButtonVariant.BLOCK_ARROW
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = IronTheme.shapes.button,
        border = BorderStroke(if (bold) 1.5.dp else 1.dp, if (enabled) colors.ink else colors.line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink, disabledContentColor = colors.muted),
    ) {
        Text(IronTheme.style.cased(text), style = IronTheme.type.button)
    }
}
