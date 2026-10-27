package com.ironquest.app.domain.importing

/** Shared cell parsers. They return null for "invalid"; callers turn that into an [ImportIssue]. */
internal object Cells {
    private val NUMBER = Regex("^(-?\\d+(?:[.,]\\d+)?)\\s*[a-zA-Z%]*$") // not UI text: CSV format

    /** "30", "30.5", "30,5", "30g", "250 kcal" → number. Blank → [blank]. */
    fun number(text: String, blank: Double? = null): Double? {
        if (text.isBlank()) return blank
        val match = NUMBER.matchEntire(text.trim()) ?: return null
        return match.groupValues[1].replace(',', '.').toDoubleOrNull()
    }

    fun int(text: String): Int? = number(text)?.takeIf { it % 1.0 == 0.0 }?.toInt()

    /** "7:30", "07:30", "7:30 pm", "19.30" → minutes after midnight. */
    fun timeOfDay(text: String): Int? {
        val match = Regex("^(\\d{1,2})[:.](\\d{2})\\s*([ap]\\.?m\\.?)?$", RegexOption.IGNORE_CASE) // not UI text: CSV format
            .matchEntire(text.trim()) ?: return null
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        val suffix = match.groupValues[3].lowercase().replace(".", "")
        if (minute > 59) return null
        when (suffix) {
            "am" -> {
                if (hour !in 1..12) return null
                if (hour == 12) hour = 0
            }
            "pm" -> {
                if (hour !in 1..12) return null
                if (hour != 12) hour += 12
            }
            else -> if (hour > 23) return null
        }
        return hour * 60 + minute
    }
}
