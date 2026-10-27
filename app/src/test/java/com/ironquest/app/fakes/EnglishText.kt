package com.ironquest.app.fakes

import com.ironquest.app.R
import com.ironquest.app.ui.text.UiText
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Resolves a [UiText] to its English text in a plain JVM test, from `res/values/strings*.xml`
 * (ids are mapped back to names through the generated `R.string` / `R.plurals` fields).
 * Plurals use the English rule: `one` when the count is 1, else `other`.
 */
object EnglishText {
    private val byName: Map<String, String> by lazy {
        stringFiles().flatMap { file ->
            val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            (0 until strings.length).map { (strings.item(it) as Element).let { element -> element.getAttribute("name") to unescape(element.textContent) } }
        }.toMap()
    }

    /** Plural name to its `one` / `other` text. */
    private val pluralsByName: Map<String, Map<String, String>> by lazy {
        stringFiles().flatMap { file ->
            val plurals = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("plurals")
            (0 until plurals.length).map { index ->
                val plural = plurals.item(index) as Element
                val items = plural.getElementsByTagName("item")
                plural.getAttribute("name") to (0 until items.length).associate { position ->
                    val item = items.item(position) as Element
                    item.getAttribute("quantity") to unescape(item.textContent)
                }
            }
        }.toMap()
    }

    private fun stringFiles(): List<File> = File("src/main/res/values").listFiles { file -> file.name.startsWith("strings") && file.extension == "xml" }.orEmpty().toList()

    private val nameById: Map<Int, String> by lazy {
        R.string::class.java.fields.associate { field -> field.getInt(null) to field.name }
    }

    private val pluralNameById: Map<Int, String> by lazy {
        R.plurals::class.java.fields.associate { field -> field.getInt(null) to field.name }
    }

    /** aapt2 trims unquoted edge whitespace, so strings that need it are written in double quotes; the quotes are not text. */
    private fun unquote(text: String) = if (text.length >= 2 && text.startsWith('"') && text.endsWith('"')) text.substring(1, text.length - 1) else text

    /** Also drops the bidi isolates (U+2066-2069, written as `\u206x` in the XML): invisible formatting, so English text has none. */
    private fun unescape(text: String) = unquote(text).replace("\\n", "\n").replace("\\\"", "\"").replace("\\'", "'").replace("\\@", "@").replace("\\?", "?").replace("%%", "%").replace(Regex("\\\\u206[6-9]"), "")

    fun resolve(text: UiText): String = when (text) {
        is UiText.Verbatim -> text.text
        is UiText.Joined -> text.items.joinToString(resolveId(text.separator)) { resolve(it) }
        is UiText.Res -> String.format(resolveId(text.id).replace("%%", "%%"), *text.args.map(::arg).toTypedArray())
        is UiText.Plural -> {
            val forms = pluralsByName.getValue(pluralNameById.getValue(text.id))
            val template = forms.getValue(if (text.count == 1) "one" else "other")
            String.format(template, *text.args.map(::arg).toTypedArray())
        }
    }

    /** The English text of a plain string resource (no arguments). */
    fun string(id: Int): String = resolveId(id)

    private fun resolveId(id: Int): String = byName.getValue(nameById.getValue(id))

    private fun arg(value: Any): Any = if (value is UiText) resolve(value) else value
}
