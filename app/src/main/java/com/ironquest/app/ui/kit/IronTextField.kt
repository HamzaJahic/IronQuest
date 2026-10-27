package com.ironquest.app.ui.kit

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.ironquest.app.ui.theme.IronTheme

/** The app's text field: outlined, body text, `tile` corners, full width unless [modifier] sets a width. */
@Composable
fun IronTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorText: String? = null,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = isError,
        supportingText = supportingText(isError, errorText),
        placeholder = placeholderText(placeholder),
        singleLine = singleLine,
        minLines = minLines,
        textStyle = IronTheme.type.body,
        shape = IronTheme.shapes.tile,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
    )
}

/** The same field over a [TextFieldValue], for callers that control the selection (pre-selected numbers). */
@Composable
fun IronTextField(
    label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = isError,
        supportingText = supportingText(isError, errorText),
        singleLine = true,
        textStyle = IronTheme.type.body,
        shape = IronTheme.shapes.tile,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
    )
}

// if-expressions (not ?.let) so the lambdas are inferred as @Composable.
private fun supportingText(isError: Boolean, errorText: String?): (@Composable () -> Unit)? = if (isError && errorText != null) {
    { Text(errorText) }
} else {
    null
}

private fun placeholderText(placeholder: String?): (@Composable () -> Unit)? = if (placeholder != null) {
    { Text(placeholder) }
} else {
    null
}
