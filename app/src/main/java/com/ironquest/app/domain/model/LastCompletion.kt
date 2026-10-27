package com.ironquest.app.domain.model

/** The most recently completed workout (by completion time): which day, and when. */
data class LastCompletion(val templateId: String, val completedAtMillis: Long)
