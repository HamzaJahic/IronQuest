package com.ironquest.app.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavBackStackEntry

/**
 * The app's only motion durations. Snappy by rule: nothing a user waits for runs longer than
 * [SCREEN_FADE_MS]; tab switches don't animate at all. Animate through `graphicsLayer` (deferred
 * reads), never by animating layout size inside a scrolling column.
 */
object IronMotion {
    /** Fade-in of a pushed screen. 0 = no navigation animation at all. */
    const val SCREEN_FADE_MS = 120

    /** Fade-out is shorter than fade-in, so the old screen never lingers. */
    const val SCREEN_FADE_OUT_MS = 90

    /** Small UI feedback (chevrons, icon swaps). */
    const val SHORT_MS = 120

    /**
     * Wait before a tip shows, so the pushed-screen fade ([SCREEN_FADE_MS]) and the first data
     * emission are done and a tip never pops over a half-loaded screen.
     */
    const val TIP_DELAY_MS = 350

    /**
     * How long a coach mark waits for its control to report where it is before it shows the card
     * centred instead (the control may be scrolled away, or not on this screen at all). Slow
     * devices need a few frames after a tab switch.
     */
    const val TIP_ANCHOR_WAIT_MS = 500

    /** One loop of a tip's demo (XP filling, a page swipe, a superset round). */
    const val DEMO_LOOP_MS = 2600

    /**
     * Navigation transitions. [isTab] tells whether a route is a bottom-bar tab; switching
     * between two tabs is instant, everything else gets a short fade.
     */
    fun enter(isTab: (String?) -> Boolean): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (SCREEN_FADE_MS == 0 || isTabSwitch(isTab)) EnterTransition.None else fadeIn(tween(SCREEN_FADE_MS))
    }

    fun exit(isTab: (String?) -> Boolean): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (SCREEN_FADE_MS == 0 || isTabSwitch(isTab)) ExitTransition.None else fadeOut(tween(SCREEN_FADE_OUT_MS))
    }

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(isTab: (String?) -> Boolean): Boolean = isTab(initialState.destination.route) && isTab(targetState.destination.route)
}
