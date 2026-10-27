package com.ironquest.app.ui.text

import androidx.annotation.StringRes
import com.ironquest.app.R

/** "Couldn't <do X>: <system message>"; [template] takes the message as %1$s, "unknown error" when there is none. */
fun failedText(@StringRes template: Int, error: Exception): UiText = uiText(template, error.message?.let { UiText.Verbatim(it) } ?: uiText(R.string.common_unknown_error))
