package com.ironquest.app.data.settings

import com.ironquest.app.domain.onboarding.OnboardingTopic

/** Prefs ↔ seen versions. Pure, so the "never throw on old data" rule has a test. */
object OnboardingSeenCodec {
    // not UI text: SharedPreferences key
    fun key(topic: OnboardingTopic) = "seen_${topic.name.lowercase()}"

    /** Missing topics count as unseen; unknown keys and non-Int values are ignored. */
    fun decode(stored: Map<String, *>): Map<OnboardingTopic, Int> = OnboardingTopic.entries.mapNotNull { topic -> (stored[key(topic)] as? Int)?.let { version -> topic to version } }.toMap()
}
