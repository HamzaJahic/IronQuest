package com.ironquest.app.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ironquest.app.domain.onboarding.OnboardingFact
import com.ironquest.app.domain.onboarding.OnboardingRules
import com.ironquest.app.domain.onboarding.OnboardingScreen
import com.ironquest.app.domain.onboarding.OnboardingTopic
import com.ironquest.app.ui.kit.Spotlight
import com.ironquest.app.ui.kit.TipCard
import com.ironquest.app.ui.theme.IronMotion
import com.ironquest.app.ui.theme.findActivity
import kotlinx.coroutines.delay

/**
 * Hosts the tips for one screen. Call it once in the stateful XxxScreen (never in XxxContent),
 * with the facts that screen knows, and only once its state has loaded: the facts of the first
 * composition count as "already there at the start of the visit". Shows nothing until the rules
 * pick a topic.
 */
@Composable
fun OnboardingGate(
    screen: OnboardingScreen,
    facts: Set<OnboardingFact>,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val seen by viewModel.seen.collectAsState()
    // Per-visit memory survives rotation, not a new visit (a new back-stack entry gets new state).
    // Stored as names, so the saved state is a plain list of strings.
    var shownThisVisit by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var factsAtVisitStart by rememberSaveable { mutableStateOf(facts.map { fact -> fact.name }) }
    // Bottom tabs restore their saved state, so leaving a screen must end the visit by hand.
    // A rotation stops the screen too, but it is the same visit.
    val activity = LocalContext.current.findActivity()
    var left by rememberSaveable { mutableStateOf(false) }
    val leaving = { if (activity?.isChangingConfigurations != true) left = true }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { leaving() }
    DisposableEffect(Unit) { onDispose { leaving() } }
    if (left) {
        left = false
        shownThisVisit = emptyList()
        factsAtVisitStart = facts.map { fact -> fact.name }
    }
    val topic = OnboardingRules.next(
        screen,
        seen,
        facts,
        factsAtVisitStart = OnboardingFact.entries.filter { fact -> fact.name in factsAtVisitStart }.toSet(),
        shownThisVisit = OnboardingTopic.entries.filter { shown -> shown.name in shownThisVisit }.toSet(),
    ) ?: return
    var ready by remember(topic) { mutableStateOf(false) }
    LaunchedEffect(topic) {
        delay(IronMotion.TIP_DELAY_MS.toLong())
        ready = true
    }
    if (!ready) return
    val visibleSteps = OnboardingRules.stepsFor(topic, facts)
    OnboardingPresenter(
        topic = topic,
        // Equal steps share their requirement, so filtering by equality keeps duplicates together.
        stepIndices = topic.steps.indices.filter { index -> topic.steps[index] in visibleSteps },
        offerSkipAll = shownThisVisit.isEmpty(),
        onFinish = {
            viewModel.finish(topic)
            shownThisVisit = shownThisVisit + topic.name
        },
        onSkipAll = {
            viewModel.skipAll()
            shownThisVisit = shownThisVisit + topic.name
        },
    )
}

/**
 * The look of a tip: a coach mark ([Spotlight] around the step's [OnboardingTarget]) with the
 * [TipCard] beside it, plus a small demo where a step has one ([onboardingDemo]). A step with no
 * target, or whose control isn't on screen, gets a centred card. [stepIndices] are positions in
 * [OnboardingTopic.steps], so copy lookups stay stable when a step is hidden.
 */
@Composable
private fun OnboardingPresenter(
    topic: OnboardingTopic,
    stepIndices: List<Int>,
    offerSkipAll: Boolean,
    onFinish: () -> Unit,
    onSkipAll: () -> Unit,
) {
    var position by rememberSaveable(topic) { mutableIntStateOf(0) }
    // Facts can change while a tip is open (a hidden step can drop out), so stay within range.
    val current = position.coerceIn(0, stepIndices.lastIndex)
    val target = topic.steps[stepIndices[current]].target
    val anchors = LocalOnboardingAnchors.current
    DisposableEffect(anchors, target) {
        anchors?.wanted?.value = target
        onDispose { if (anchors?.wanted?.value == target) anchors?.wanted?.value = null }
    }
    // A control reports its bounds a frame or two after it is wanted. Until then keep the previous
    // step on screen, so the card doesn't flash in the centre; give up after a short wait (the
    // control may be scrolled away or not composed).
    val hole = target?.let { anchors?.bounds?.get(it) }
    var waited by remember(target) { mutableStateOf(false) }
    LaunchedEffect(target) {
        delay(IronMotion.TIP_ANCHOR_WAIT_MS.toLong())
        waited = true
    }
    val ready = target == null || anchors == null || hole != null || waited
    var lastShown by remember(topic) { mutableStateOf<ShownStep?>(null) }
    val shown = if (ready) ShownStep(current, hole) else lastShown
    SideEffect { if (ready) lastShown = shown }
    if (shown == null) return
    val shownPosition = shown.position.coerceIn(0, stepIndices.lastIndex)
    val stepIndex = stepIndices[shownPosition]
    Spotlight(hole = shown.hole, onDismiss = onFinish) {
        TipCard(
            title = stringResource(OnboardingCopy.title(topic, stepIndex)),
            body = stringResource(OnboardingCopy.body(topic, stepIndex)),
            stepIndex = shownPosition,
            stepCount = stepIndices.size,
            onNext = { if (shownPosition == stepIndices.lastIndex) onFinish() else position = shownPosition + 1 },
            onBack = if (shownPosition > 0) {
                { position = shownPosition - 1 }
            } else {
                null
            },
            onSkip = onFinish,
            onSkipAll = if (shownPosition == 0 && offerSkipAll) onSkipAll else null,
            demo = onboardingDemo(topic, stepIndex),
        )
    }
}

/** The step a coach mark currently shows, and its hole in screen coordinates. */
private data class ShownStep(val position: Int, val hole: Rect?)
