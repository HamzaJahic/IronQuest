package com.ironquest.app.domain.food

import java.util.Locale

/**
 * Local food search. Every query word must match a word of the name or an alias: exact (3) beats
 * prefix (2) beats substring (1); the first query word matching the first name word adds 2.
 * Ties: shorter name first, then alphabetical. Words are lower-cased and crudely singularised so
 * "eggs" finds "Egg" and "tomato" finds "Tomatoes".
 */
object FoodSearchRanker {
    private val NON_WORD = Regex("[^\\p{L}\\p{N}%]+")

    fun words(text: String): List<String> = text.lowercase(Locale.ROOT).split(NON_WORD).filter { it.isNotEmpty() }.map(::singular)

    fun singular(word: String): String = when {
        word.length > 4 && word.endsWith("ies") -> word.dropLast(3) + "y"
        word.length > 4 && (word.endsWith("oes") || word.endsWith("ches") || word.endsWith("shes")) -> word.dropLast(2)
        word.length > 3 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
        else -> word
    }

    /** Null when some query word matches nothing. */
    fun score(queryWords: List<String>, nameWords: List<String>, aliasWords: List<String>): Int? {
        if (queryWords.isEmpty()) return null
        val candidates = nameWords + aliasWords
        var total = 0
        queryWords.forEach { queryWord ->
            val best = candidates.maxOfOrNull { candidate ->
                when {
                    candidate == queryWord -> 3
                    candidate.startsWith(queryWord) -> 2
                    candidate.contains(queryWord) -> 1
                    else -> 0
                }
            } ?: 0
            if (best == 0) return null
            total += best
        }
        if (nameWords.firstOrNull() == queryWords.first()) total += 2
        return total
    }

    fun <T> rank(query: String, items: List<T>, name: (T) -> String, aliases: (T) -> List<String>, limit: Int): List<T> {
        val queryWords = words(query)
        if (queryWords.isEmpty()) return emptyList()
        return items
            .mapNotNull { item ->
                val itemName = name(item)
                score(queryWords, words(itemName), aliases(item).flatMap(::words))?.let { points -> Triple(item, points, itemName) }
            }
            .sortedWith(compareByDescending<Triple<T, Int, String>> { it.second }.thenBy { it.third.length }.thenBy { it.third.lowercase(Locale.ROOT) })
            .take(limit)
            .map { it.first }
    }
}
