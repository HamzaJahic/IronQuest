package com.ironquest.app.domain.model

import java.net.URLEncoder

/** Where "How to" sends the user. No media is hosted or bundled: it is a YouTube search. */
object ExerciseDemo {
    private const val BASE = "https://www.youtube.com/results?search_query=" // not UI text

    /** Search for a demonstration of [exerciseName], e.g. "Barbell Row exercise form". */
    fun youtubeSearchUrl(exerciseName: String): String = BASE + URLEncoder.encode("${exerciseName.trim()} exercise form", "UTF-8") // not UI text
}
