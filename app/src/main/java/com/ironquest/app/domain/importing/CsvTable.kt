package com.ironquest.app.domain.importing

/** One data row; [line] is where the row starts in the original text. */
data class CsvRow(val line: Int, val cells: Map<String, String>) {
    /** Trimmed cell, or "" when the column is missing. */
    operator fun get(column: String): String = cells[column]?.trim().orEmpty()
}

data class CsvTable(val columns: List<String>, val rows: List<CsvRow>)
