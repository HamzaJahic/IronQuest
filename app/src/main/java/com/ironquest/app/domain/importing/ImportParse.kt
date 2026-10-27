package com.ironquest.app.domain.importing

/** One problem found in an imported file. [line] is 1-based in the original text; null = whole file. */
data class ImportIssue(val line: Int?, val problem: ImportProblem)

/** Either a parsed value or the list of problems that stopped it. */
sealed interface ImportParse<out T> {
    data class Ok<T>(val value: T) : ImportParse<T>
    data class Failed(val issues: List<ImportIssue>) : ImportParse<Nothing>
}
