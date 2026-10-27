package com.ironquest.app.domain.importing

/** Collects issues while parsing rows, capped so a broken file doesn't produce a wall of text. */
internal class IssueCollector {
    val issues = mutableListOf<ImportIssue>()
    val isEmpty: Boolean get() = issues.isEmpty()

    fun add(line: Int?, problem: ImportProblem) {
        if (issues.size < MAX_ISSUES) issues += ImportIssue(line, problem)
    }

    fun requireColumns(table: CsvTable, required: List<String>): Boolean {
        val missing = required.filter { it !in table.columns }
        if (missing.isNotEmpty()) {
            add(line = null, ImportProblem.MissingColumns(missing, table.columns.take(10).map { it.take(30) }))
        }
        return missing.isEmpty()
    }

    companion object {
        const val MAX_ISSUES = 15
    }
}
