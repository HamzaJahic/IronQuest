package com.ironquest.app.ui.text

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Text that a ViewModel or a pure text builder hands to the UI: a resource plus its arguments,
 * resolved where a Context is at hand. Arguments may themselves be [UiText].
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText

    /** [items] separated by the string resource [separator] (", " / " · "); the separator is translatable. */
    data class Joined(val items: List<UiText>, @StringRes val separator: Int) : UiText

    /** Text that is already content (a meal name, a system error message); never app copy. */
    data class Verbatim(val text: String) : UiText
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

fun joinedText(items: List<UiText>, @StringRes separator: Int): UiText = UiText.Joined(items, separator)

/** [args] default to the count itself, the usual "%d meals" case. */
fun uiPlural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText = UiText.Plural(id, count, if (args.isEmpty()) listOf(count) else args.toList())

fun UiText.resolve(resources: Resources): String = when (this) {
    is UiText.Res -> resources.getString(id, *resolvedArgs(args, resources))
    is UiText.Plural -> resources.getQuantityString(id, count, *resolvedArgs(args, resources))
    is UiText.Joined -> items.joinToString(resources.getString(separator)) { it.resolve(resources) }
    is UiText.Verbatim -> text
}

@Composable
fun UiText.resolve(): String {
    LocalConfiguration.current // re-resolve when the app language changes
    return resolve(LocalContext.current.resources)
}

private fun resolvedArgs(args: List<Any>, resources: Resources): Array<Any> = args.map { argument -> if (argument is UiText) argument.resolve(resources) else argument }.toTypedArray()
