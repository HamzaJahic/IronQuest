package com.ironquest.app.domain.importing

/**
 * Small RFC 4180 reader that tolerates what AI tools and spreadsheets usually produce:
 * a UTF-8 BOM, ```csv fences, CRLF, `;` or tab delimiters, blank lines, and headers in any
 * case with spaces ("Protein (g)" → `protein_g`).
 */
object Csv {
    /** Whole-file cap; anything larger is not a hand-made plan. */
    const val MAX_CHARS = 512 * 1024
    const val MAX_ROWS = 500

    fun read(text: String, aliases: Map<String, String> = emptyMap()): ImportParse<CsvTable> {
        if (text.length > MAX_CHARS) return fail(line = null, ImportProblem.FileTooLarge(MAX_CHARS / 1024))
        val cleaned = text.removePrefix("\uFEFF") // not UI text: byte order mark
            .lines()
            // AI chats often wrap the file in a Markdown code fence.
            .map { if (it.trimStart().startsWith("```")) "" else it }
            .joinToString("\n")
        val records = try {
            split(cleaned, detectDelimiter(cleaned))
        } catch (syntax: CsvSyntaxException) {
            return fail(syntax.line, ImportProblem.UnclosedQuote)
        }
        val nonBlank = records.filter { record -> record.cells.any { it.isNotBlank() } }
        if (nonBlank.isEmpty()) return fail(line = null, ImportProblem.EmptyFile)
        val header = nonBlank.first()
        val columns = header.cells.map { normalizeHeader(it, aliases) }
        if (columns.all { it.isEmpty() }) return fail(header.line, ImportProblem.HeaderMissing)
        val dup = columns.filter { it.isNotEmpty() }.groupBy { it }.filterValues { it.size > 1 }.keys.firstOrNull()
        if (dup != null) return fail(header.line, ImportProblem.DuplicateColumn(dup))
        val body = nonBlank.drop(1)
        if (body.isEmpty()) return fail(line = null, ImportProblem.NoDataRows)
        if (body.size > MAX_ROWS) return fail(line = null, ImportProblem.TooManyRows(body.size, MAX_ROWS))
        val rows = body.map { record ->
            CsvRow(
                line = record.line,
                cells = columns.withIndex()
                    .filter { it.value.isNotEmpty() }
                    .associate { (columnIndex, name) -> name to record.cells.getOrElse(columnIndex) { "" } },
            )
        }
        return ImportParse.Ok(CsvTable(columns.filter { it.isNotEmpty() }, rows))
    }

    /** "Protein (g)" → "protein_g", then mapped through [aliases]. */
    fun normalizeHeader(raw: String, aliases: Map<String, String>): String {
        val key = raw.trim().lowercase()
            .replace(Regex("[()\\[\\]]"), " ")
            .trim()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        return aliases[key] ?: key
    }

    private data class Record(val line: Int, val cells: List<String>)

    private class CsvSyntaxException(val line: Int) : Exception()

    /** Picks the delimiter that occurs most often (outside quotes) in the first non-blank line. */
    private fun detectDelimiter(text: String): Char {
        val first = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return ','
        var inQuotes = false
        val counts = mutableMapOf(',' to 0, ';' to 0, '\t' to 0)
        for (char in first) {
            if (char == '"') inQuotes = !inQuotes
            if (!inQuotes && char in counts) counts[char] = counts.getValue(char) + 1
        }
        return counts.maxBy { it.value }.takeIf { it.value > 0 }?.key ?: ','
    }

    private fun split(text: String, delimiter: Char): List<Record> {
        val records = mutableListOf<Record>()
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var line = 1
        var recordLine = 1
        var inQuotes = false
        var quoteLine = 0
        var position = 0
        while (position < text.length) {
            val char = text[position]
            when {
                // not UI text: CSV format
                inQuotes && char == '"' && text.getOrNull(position + 1) == '"' -> {
                    cell.append('"')
                    position++
                }
                inQuotes && char == '"' -> inQuotes = false
                inQuotes -> {
                    if (char == '\n') line++
                    if (char != '\r') cell.append(char)
                }
                char == '"' && cell.isBlank() -> {
                    cell.setLength(0)
                    inQuotes = true
                    quoteLine = line
                }
                char == delimiter -> {
                    cells += cell.toString()
                    cell.setLength(0)
                }
                char == '\r' -> Unit
                char == '\n' -> {
                    cells += cell.toString()
                    cell.setLength(0)
                    records += Record(recordLine, cells.toList())
                    cells.clear()
                    line++
                    recordLine = line
                }
                else -> cell.append(char)
            }
            position++
        }
        if (inQuotes) throw CsvSyntaxException(quoteLine)
        if (cell.isNotEmpty() || cells.isNotEmpty()) {
            cells += cell.toString()
            records += Record(recordLine, cells.toList())
        }
        return records
    }

    private fun fail(line: Int?, problem: ImportProblem) = ImportParse.Failed(listOf(ImportIssue(line, problem)))
}
