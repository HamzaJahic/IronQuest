package com.ironquest.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.ironquest.app.domain.repository.AppSettingsRepository
import com.ironquest.app.domain.repository.OnboardingRepository
import com.ironquest.app.notifications.ReminderIntents
import com.ironquest.app.ui.navigation.IronQuestNavHost
import com.ironquest.app.ui.navigation.Routes
import com.ironquest.app.ui.theme.IronTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appSettings: AppSettingsRepository

    @Inject lateinit var onboarding: OnboardingRepository

    /** Route requested by a notification tap; the NavHost consumes it. */
    private val openRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only on a fresh start: after rotation the back stack is already restored.
        if (savedInstanceState == null) openRoute.value = intent?.getStringExtra(ReminderIntents.EXTRA_OPEN_ROUTE)
        // Automation (baseline profile, measure-smoothness.sh) taps through screens a tip would cover.
        // Harmless if another app sends it: the worst case is hidden tips.
        if (intent?.getBooleanExtra(EXTRA_SKIP_ONBOARDING, false) == true) {
            onboarding.markAllSeen()
            appSettings.finishFoodSetup()
        }
        // Transparent bars; icon contrast follows the theme (SystemBarIcons in the nav host).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        // Read once, synchronously: a recreated activity keeps its restored back stack either way.
        val startRoute = if (appSettings.isFoodSetupPending()) Routes.FOOD_SETUP else Routes.TODAY
        setContent {
            // Initial value read synchronously, so the first frame already has the user's look.
            // Remembered: a new flow per recomposition re-registered the prefs listener each time.
            val appearanceFlow = remember { appSettings.observeAppearance() }
            val initialAppearance = remember { appSettings.currentAppearance() }
            val appearance by appearanceFlow.collectAsState(initial = initialAppearance)
            IronTheme(appearance) {
                val route by openRoute.collectAsState()
                IronQuestNavHost(startRoute = startRoute, openRoute = route, onRouteOpened = { openRoute.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(ReminderIntents.EXTRA_OPEN_ROUTE)?.let { openRoute.value = it }
    }

    companion object {
        // not UI text: intent extra name
        const val EXTRA_SKIP_ONBOARDING = "com.ironquest.app.SKIP_ONBOARDING"
    }
}
