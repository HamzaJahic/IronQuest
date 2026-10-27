package com.ironquest.app.ui.kit

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import com.ironquest.app.ui.theme.IronTheme

/** A bottom sheet on `raised` with the style's sheet corners. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IronSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = IronTheme.colors.raised,
        shape = IronTheme.shapes.sheet,
        content = content,
    )
}
