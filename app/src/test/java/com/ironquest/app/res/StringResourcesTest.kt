package com.ironquest.app.res

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Guards the string resources: unique names, positional placeholders, plurals, translations in step. */
class StringResourcesTest {

    private val resDir = File("src/main/res")
    private val placeholder = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfx]""")

    private data class Entry(val file: File, val name: String, val text: String)

    private fun entries(dir: File): List<Entry> = dir.listFiles { file -> file.name.startsWith("strings") && file.extension == "xml" }.orEmpty().flatMap { file ->
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = document.getElementsByTagName("string")
        val plurals = document.getElementsByTagName("plurals")
        (0 until strings.length).map { index ->
            val element = strings.item(index) as Element
            Entry(file, element.getAttribute("name"), element.textContent)
        } + (0 until plurals.length).flatMap { index ->
            val element = plurals.item(index) as Element
            val items = element.getElementsByTagName("item")
            (0 until items.length).map { itemIndex ->
                val item = items.item(itemIndex) as Element
                Entry(file, element.getAttribute("name") + "#" + item.getAttribute("quantity"), item.textContent)
            }
        }
    }

    private val defaults by lazy { entries(File(resDir, "values")) }

    @Test
    fun `names are unique across the default files`() {
        val duplicates = defaults.groupBy { it.name }.filterValues { it.size > 1 }.keys
        assertTrue("Duplicate string names: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `strings with two or more placeholders number them`() {
        val unnumbered = defaults.filter { entry ->
            val found = placeholder.findAll(entry.text).toList()
            found.size >= 2 && found.any { match -> match.groups[1] == null }
        }.map { it.name }
        assertTrue("Use %1\$s, %2\$d… in: $unnumbered", unnumbered.isEmpty())
    }

    @Test
    fun `plurals have one and other`() {
        val pluralNames = defaults.filter { '#' in it.name }.map { it.name.substringBefore('#') }.toSet()
        val missing = pluralNames.filter { name ->
            defaults.none { it.name == "$name#one" } || defaults.none { it.name == "$name#other" }
        }
        assertTrue("Plurals need one and other: $missing", missing.isEmpty())
    }

    @Test
    fun `translations use the same placeholders as English`() {
        val english = defaults.associateBy { it.name }
        val mismatches = resDir.listFiles { file -> file.isDirectory && file.name.startsWith("values-") }.orEmpty()
            .flatMap { dir -> entries(dir) }
            .filter { translated ->
                val source = english[translated.name] ?: return@filter true // a translation of a string that doesn't exist
                placeholder.findAll(translated.text).map { it.value }.sorted().toList() !=
                    placeholder.findAll(source.text).map { it.value }.sorted().toList()
            }
            .map { "${it.file.parentFile.name}/${it.name}" }
        assertTrue("Placeholder mismatch or unknown name: $mismatches", mismatches.isEmpty())
    }

    @Test
    fun `values with edge whitespace are wrapped in double quotes`() {
        val value = Regex("""<(string|item)\b[^>]*>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
        val bad = resDir.walkTopDown().filter { it.isFile && it.name.startsWith("strings") && it.extension == "xml" }.flatMap { file ->
            value.findAll(file.readText()).mapNotNull { match ->
                val raw = match.groupValues[2]
                val quoted = raw.startsWith("\"") && raw.endsWith("\"")
                if (!quoted && raw != raw.trim()) "${file.name}: '${raw.trim().take(30)}'" else null
            }
        }.toList()
        assertTrue("Wrap in double quotes (aapt2 trims edge spaces): $bad", bad.isEmpty())
    }
}
