package com.ironquest.app.ui.kit

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ironquest.app.R
import com.ironquest.app.ui.theme.IronTheme
import com.ironquest.app.ui.theme.TitleCase
import java.util.Locale

/**
 * Dialog on a `raised` background with a serif/section-head title and plain buttons. [content]
 * replaces [text] when the dialog needs fields. [destructive] colours the confirm button `deload`.
 */
@Composable
fun IronDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    text: String? = null,
    dismissLabel: String = stringResource(R.string.common_cancel),
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    showDismiss: Boolean = true,
    onDelete: (() -> Unit)? = null,
    deleteLabel: String = stringResource(R.string.common_delete),
    content: (@Composable () -> Unit)? = null,
) {
    val colors = IronTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.raised,
        titleContentColor = colors.ink,
        textContentColor = colors.ink,
        // Bold's section head is an 11 sp label; a dialog title needs the bigger button face.
        title = {
            val bold = IronTheme.style.titleCase == TitleCase.UPPERCASE
            Text(if (bold) title.uppercase(Locale.getDefault()) else title, style = if (bold) IronTheme.type.button else IronTheme.type.sectionHead)
        },
        text = when {
            content != null -> content
            text != null -> {
                { Text(text, style = IronTheme.type.body, color = colors.muted) }
            }
            else -> null
        },
        confirmButton = {
            DialogButton(confirmLabel, onConfirm, enabled = confirmEnabled, destructive = destructive)
        },
        dismissButton = if (showDismiss || onDelete != null) {
            {
                Row {
                    if (onDelete != null) DialogButton(deleteLabel, onDelete, destructive = true)
                    if (showDismiss) DialogButton(dismissLabel, onDismiss, muted = true)
                }
            }
        } else {
            null
        },
    )
}

/** A dialog text button: `accent` by default, `muted` for Cancel, `deload` for Delete. */
@Composable
fun DialogButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
    muted: Boolean = false,
) {
    val colors = IronTheme.colors
    TextButton(onClick = onClick, enabled = enabled) {
        Text(
            text,
            style = IronTheme.type.button,
            color = when {
                !enabled -> colors.line
                destructive -> colors.danger
                muted -> colors.muted
                else -> colors.accent
            },
        )
    }
}

/** Snackbar host: `ink` bar with `paper` text, so it reads on every palette. */
@Composable
fun IronSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val colors = IronTheme.colors
    SnackbarHost(hostState, modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = IronTheme.shapes.tile,
            containerColor = colors.ink,
            contentColor = colors.paper,
            actionColor = colors.paper,
        )
    }
}

/** Shown by the Meal, Plan and Workout-log editors when leaving with unsaved edits. */
@Composable
fun DiscardChangesDialog(onDiscard: () -> Unit, onKeepEditing: () -> Unit) {
    IronDialog(
        title = stringResource(R.string.kit_discard_title),
        text = stringResource(R.string.kit_discard_text),
        confirmLabel = stringResource(R.string.kit_discard_confirm),
        onConfirm = onDiscard,
        dismissLabel = stringResource(R.string.kit_discard_keep_editing),
        onDismiss = onKeepEditing,
        destructive = true,
    )
}
