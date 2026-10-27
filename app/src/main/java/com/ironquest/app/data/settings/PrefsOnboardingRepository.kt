package com.ironquest.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.ironquest.app.domain.onboarding.OnboardingTopic
import com.ironquest.app.domain.repository.OnboardingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** SharedPreferences `onboarding`: one Int per topic, the version seen (see [OnboardingSeenCodec]). */
@Singleton
class PrefsOnboardingRepository @Inject constructor(
    @ApplicationContext context: Context,
) : OnboardingRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun observeSeen(): Flow<Map<OnboardingTopic, Int>> = changes().map { currentSeen() }.distinctUntilChanged()

    /** Emits once now and on every preference change. */
    private fun changes(): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    override fun currentSeen(): Map<OnboardingTopic, Int> = OnboardingSeenCodec.decode(prefs.all)

    override fun markSeen(topic: OnboardingTopic) {
        prefs.edit().putInt(OnboardingSeenCodec.key(topic), topic.version).apply()
    }

    override fun markAllSeen() {
        val editor = prefs.edit()
        OnboardingTopic.entries.forEach { topic -> editor.putInt(OnboardingSeenCodec.key(topic), topic.version) }
        editor.apply()
    }

    override fun resetAll() {
        prefs.edit().clear().apply()
    }

    private companion object {
        // not UI text: SharedPreferences file name
        const val PREFS = "onboarding"
    }
}
