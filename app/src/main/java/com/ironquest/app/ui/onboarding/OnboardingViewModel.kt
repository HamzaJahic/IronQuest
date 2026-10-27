package com.ironquest.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironquest.app.domain.onboarding.OnboardingTopic
import com.ironquest.app.domain.repository.OnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Seen tips for one screen's [OnboardingGate]; the rules themselves live in `domain/onboarding`. */
@HiltViewModel
class OnboardingViewModel @Inject constructor(private val repository: OnboardingRepository) : ViewModel() {
    val seen: StateFlow<Map<OnboardingTopic, Int>> = repository.observeSeen().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.currentSeen())

    fun finish(topic: OnboardingTopic) = repository.markSeen(topic)

    fun skipAll() = repository.markAllSeen()
}
