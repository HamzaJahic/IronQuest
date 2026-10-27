package com.ironquest.app.ui.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import com.ironquest.app.domain.onboarding.OnboardingTarget

/**
 * Where the controls a coach mark points at are on screen. Only the [wanted] target reports its
 * bounds, so the rest of the app pays nothing while no tip is open.
 */
class OnboardingAnchors {
    val wanted = mutableStateOf<OnboardingTarget?>(null)

    /** Screen coordinates (the coach mark is its own window, and so are sheets). */
    val bounds = mutableStateMapOf<OnboardingTarget, Rect>()
}

val LocalOnboardingAnchors = staticCompositionLocalOf<OnboardingAnchors?> { null }

/**
 * Marks the control a tip step talks about ([OnboardingTarget]). A no-op unless that step is open;
 * then it scrolls the control into view and reports where it is. Only one composed control may
 * carry a target at a time (list screens mark their first row).
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.onboardingAnchor(target: OnboardingTarget): Modifier = composed {
    val anchors = LocalOnboardingAnchors.current
    if (anchors == null || anchors.wanted.value != target) return@composed Modifier
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(requester) { requester.bringIntoView() }
    DisposableEffect(anchors, target) { onDispose { anchors.bounds.remove(target) } }
    Modifier
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { coordinates ->
            // boundsInRoot clips to the scrolled viewport, so a control scrolled away reports an empty rect.
            val inRoot = coordinates.boundsInRoot()
            val topLeft = coordinates.findRootCoordinates().localToScreen(inRoot.topLeft)
            anchors.bounds[target] = Rect(topLeft, inRoot.size)
        }
}
