package com.ironquest.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ironquest.app.R
import com.ironquest.app.domain.importing.ImportKind
import com.ironquest.app.domain.model.LevelUpEvent
import com.ironquest.app.domain.onboarding.OnboardingTarget
import com.ironquest.app.ui.food.FoodScreen
import com.ironquest.app.ui.food.MealDetailScreen
import com.ironquest.app.ui.food.MealDetailViewModel
import com.ironquest.app.ui.food.MealEditorScreen
import com.ironquest.app.ui.food.MealEditorViewModel
import com.ironquest.app.ui.food.MealLibraryScreen
import com.ironquest.app.ui.food.PlanEditorScreen
import com.ironquest.app.ui.food.PlanEditorViewModel
import com.ironquest.app.ui.food.PlansScreen
import com.ironquest.app.ui.food.builder.MealBuilderScreen
import com.ironquest.app.ui.food.builder.MealBuilderViewModel
import com.ironquest.app.ui.food.message
import com.ironquest.app.ui.food.xpChangedText
import com.ironquest.app.ui.imports.ImportScreen
import com.ironquest.app.ui.imports.ImportViewModel
import com.ironquest.app.ui.imports.ProgramsScreen
import com.ironquest.app.ui.kit.IronIcons
import com.ironquest.app.ui.kit.reachedLine
import com.ironquest.app.ui.log.LogScreen
import com.ironquest.app.ui.onboarding.LocalOnboardingAnchors
import com.ironquest.app.ui.onboarding.OnboardingAnchors
import com.ironquest.app.ui.onboarding.onboardingAnchor
import com.ironquest.app.ui.setup.FoodSetupScreen
import com.ironquest.app.ui.setup.FoodSetupViewModel
import com.ironquest.app.ui.setup.SetupExit
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.joinedText
import com.ironquest.app.ui.text.resolve
import com.ironquest.app.ui.theme.IronMotion
import com.ironquest.app.ui.theme.IronTheme
import com.ironquest.app.ui.theme.ListTreatment
import com.ironquest.app.ui.theme.SystemBarIcons
import com.ironquest.app.ui.today.TodayScreen
import com.ironquest.app.ui.workout.ActiveWorkoutScreen
import com.ironquest.app.ui.workout.ActiveWorkoutViewModel
import com.ironquest.app.ui.workout.WorkoutLogEditorScreen
import com.ironquest.app.ui.workout.WorkoutLogEditorViewModel
import com.ironquest.app.ui.you.YouScreen
import kotlinx.coroutines.flow.first

/** SavedStateHandle keys for one-shot results handed to the screen we pop back to. Consumed (nulled) once shown. */
private const val KEY_MESSAGE = "food_message"
private const val KEY_LEVEL_UP = "food_level_up"

/** Bottom-bar tabs. */
private enum class TopLevel(val route: String, @StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    Today(Routes.TODAY, R.string.nav_tab_today, Icons.Outlined.Home, Icons.Filled.Home),
    Log(Routes.LOG, R.string.nav_tab_log, IronIcons.EventNoteOutlined, IronIcons.EventNote),
    Food(Routes.FOOD, R.string.nav_tab_food, IronIcons.RestaurantOutlined, IronIcons.Restaurant),
    You(Routes.YOU, R.string.nav_tab_you, Icons.Outlined.Person, Icons.Filled.Person),
}

@Composable
fun IronQuestNavHost(
    navController: NavHostController = rememberNavController(),
    /** [Routes.FOOD_SETUP] while the first-launch setup is pending, else [Routes.TODAY]. */
    startRoute: String = Routes.TODAY,
    /** Top-level route requested from outside (notification tap), or null. */
    openRoute: String? = null,
    onRouteOpened: () -> Unit = {},
) {
    // Bumped on every notification-driven open of Food; the screen resets to the Day view when it changes.
    // Saveable because FoodScreen keeps its "seen" value in rememberSaveable; both must survive rotation.
    var foodDayRequest by rememberSaveable { mutableIntStateOf(0) }
    // Messages cross screens as resolved text (a SavedStateHandle can't hold a UiText).
    val resources = LocalContext.current.resources
    // A reminder tap jumps to a top-level tab exactly like a bottom-bar tap.
    LaunchedEffect(openRoute) {
        val route = openRoute?.let(Routes::fromLegacy) ?: return@LaunchedEffect
        navController.currentBackStackEntryFlow.first() // wait until the graph is set
        // First-launch setup comes first; the tap's target is dropped rather than stacked over it.
        if (navController.currentDestination?.route == Routes.FOOD_SETUP) {
            onRouteOpened()
            return@LaunchedEffect
        }
        if (TopLevel.entries.any { it.route == route }) {
            if (route == Routes.FOOD) foodDayRequest++
            navController.navigateTopLevel(route)
            // restoreState may bring back a pushed screen (Meals, plan editor…); a reminder
            // tap must land on the tab's root (idempotent if already there).
            navController.popBackStack(route, inclusive = false)
        }
        onRouteOpened()
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TopLevel.entries.any { it.route == currentRoute }
    val colors = IronTheme.colors
    SystemBarIcons(darkBehindStatusBar = colors.isDark, darkBehindNavigationBar = colors.isDark)
    // Around the Scaffold, so the bottom bar can be a coach-mark target too.
    val anchors = remember { OnboardingAnchors() }
    CompositionLocalProvider(LocalOnboardingAnchors provides anchors) {
        Scaffold(
            containerColor = colors.paper,
            contentColor = colors.ink,
            // Screens own their insets: top-level ones pad for the status bar, the workout
            // screen pads its own header and footer. Avoids double-applied insets.
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (showBottomBar) {
                    IronBottomBar(currentRoute, onSelect = { route ->
                        // Re-selecting the current tab is a no-op (avoids a pointless pop + restore).
                        if (currentRoute != route) navController.navigateTopLevel(route)
                    })
                }
            },
        ) { padding ->
            // Navigation's default is a 700 ms cross-fade on every change, which made the app feel
            // slow. Tabs switch instantly; pushed screens fade in IronMotion.SCREEN_FADE_MS.
            val isTab: (String?) -> Boolean = { route -> TopLevel.entries.any { it.route == route } }
            NavHost(
                navController = navController,
                startDestination = startRoute,
                enterTransition = IronMotion.enter(isTab),
                exitTransition = IronMotion.exit(isTab),
                popEnterTransition = IronMotion.enter(isTab),
                popExitTransition = IronMotion.exit(isTab),
            ) {
                composable(Routes.TODAY) { entry ->
                    TodayScreen(
                        contentPadding = padding,
                        onStartWorkout = { templateId -> navController.navigateFrom(entry, Routes.workout(templateId)) },
                        onOpenPrograms = { navController.navigateFrom(entry, Routes.PROGRAMS) },
                        onOpenFood = {
                            if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) navController.navigateTopLevel(Routes.FOOD)
                        },
                    )
                }
                composable(Routes.FOOD_SETUP) { entry ->
                    FoodSetupScreen(
                        onExit = { next, _ -> navController.leaveSetup(entry, next) },
                        onBack = { navController.popIfResumed(entry) },
                    )
                }
                composable(
                    route = Routes.FOOD_SETTINGS,
                    arguments = listOf(navArgument(FoodSetupViewModel.ARG_FROM_SETTINGS) { type = NavType.BoolType; defaultValue = true }),
                ) { entry ->
                    FoodSetupScreen(
                        onExit = { _, _ -> navController.popIfOnTop(entry) },
                        onBack = { navController.popIfResumed(entry) },
                    )
                }
                composable(Routes.YOU) { entry ->
                    YouScreen(
                        contentPadding = padding,
                        onOpenPrograms = { navController.navigateFrom(entry, Routes.PROGRAMS) },
                        onOpenPlans = { navController.navigateFrom(entry, Routes.PLANS) },
                        onOpenFoodSettings = { navController.navigateFrom(entry, Routes.FOOD_SETTINGS) },
                    )
                }
                composable(Routes.PROGRAMS) { entry ->
                    val message by entry.savedStateHandle.getStateFlow<String?>(KEY_MESSAGE, null).collectAsStateWithLifecycle()
                    ProgramsScreen(
                        message = message,
                        onMessageShown = { entry.savedStateHandle[KEY_MESSAGE] = null },
                        onBack = { navController.popIfResumed(entry) },
                        onImport = { navController.navigateFrom(entry, Routes.importCsv(ImportKind.PROGRAM)) },
                    )
                }
                composable(
                    route = Routes.IMPORT_PATTERN,
                    arguments = listOf(navArgument(ImportViewModel.ARG_KIND) { type = NavType.StringType }),
                ) { entry ->
                    ImportScreen(
                        onBack = { navController.popIfResumed(entry) },
                        onDone = { message ->
                            if (navController.currentBackStackEntry?.id == entry.id) {
                                navController.previousBackStackEntry?.savedStateHandle?.set(KEY_MESSAGE, message)
                            }
                            navController.popIfOnTop(entry)
                        },
                    )
                }
                composable(Routes.LOG) { entry ->
                    LogScreen(
                        contentPadding = padding,
                        onAddWorkout = { templateId, date -> navController.navigateFrom(entry, Routes.workoutLog(0, templateId, date)) },
                        onEditWorkout = { log -> navController.navigateFrom(entry, Routes.workoutLog(log.workoutId, log.templateId, log.date)) },
                    )
                }
                composable(
                    route = Routes.WORKOUT_LOG_PATTERN,
                    arguments = listOf(
                        navArgument(WorkoutLogEditorViewModel.ARG_WORKOUT_ID) { type = NavType.LongType },
                        navArgument(WorkoutLogEditorViewModel.ARG_TEMPLATE_ID) { type = NavType.StringType },
                        navArgument(WorkoutLogEditorViewModel.ARG_DATE) { type = NavType.LongType },
                    ),
                ) { entry ->
                    WorkoutLogEditorScreen(
                        onBack = { navController.popIfResumed(entry) },
                        onDone = { navController.popIfOnTop(entry) },
                    )
                }
                composable(
                    route = Routes.WORKOUT_PATTERN,
                    arguments = listOf(navArgument(ActiveWorkoutViewModel.ARG_TEMPLATE_ID) { type = NavType.StringType }),
                ) {
                    // popBackStack(route) is idempotent: a double tap on "Back to Today" can't pop the hub itself.
                    ActiveWorkoutScreen(onExit = { navController.popBackStack(Routes.TODAY, inclusive = false) })
                }
                composable(Routes.FOOD) { entry ->
                    val message by entry.savedStateHandle.getStateFlow<String?>(KEY_MESSAGE, null).collectAsStateWithLifecycle()
                    FoodScreen(
                        message = message,
                        onMessageShown = { entry.savedStateHandle[KEY_MESSAGE] = null },
                        contentPadding = padding,
                        onOpenMeals = { navController.navigateFrom(entry, Routes.MEALS) },
                        onOpenPlans = { navController.navigateFrom(entry, Routes.PLANS) },
                        onOpenMeal = { navController.navigateFrom(entry, Routes.mealDetail(it)) },
                        onOpenBuilder = { date, logId -> navController.navigateFrom(entry, Routes.mealBuilder(date, logId)) },
                        onOpenFoodSettings = { navController.navigateFrom(entry, Routes.FOOD_SETTINGS) },
                        dayRequest = foodDayRequest,
                    )
                }
                composable(Routes.MEALS) { entry ->
                    val message by entry.savedStateHandle.getStateFlow<String?>(KEY_MESSAGE, null).collectAsStateWithLifecycle()
                    MealLibraryScreen(
                        message = message,
                        onMessageShown = { entry.savedStateHandle[KEY_MESSAGE] = null },
                        onBack = { navController.popIfResumed(entry) },
                        onOpenMeal = { navController.navigateFrom(entry, Routes.mealDetail(it)) },
                        onNewMeal = { navController.navigateFrom(entry, Routes.mealEdit(null)) },
                        onImport = { navController.navigateFrom(entry, Routes.importCsv(ImportKind.MEALS)) },
                    )
                }
                composable(
                    route = Routes.MEAL_DETAIL_PATTERN,
                    arguments = listOf(navArgument(MealDetailViewModel.ARG_MEAL_ID) { type = NavType.StringType }),
                ) { entry ->
                    MealDetailScreen(
                        onBack = { navController.popIfResumed(entry) },
                        onEdit = { navController.navigateFrom(entry, Routes.mealEdit(it)) },
                    )
                }
                composable(
                    route = Routes.MEAL_EDIT_PATTERN,
                    arguments = listOf(navArgument(MealEditorViewModel.ARG_MEAL_ID) { type = NavType.StringType }),
                ) { entry ->
                    MealEditorScreen(
                        onBack = { navController.popIfResumed(entry) },
                        onDone = { navController.popIfOnTop(entry) },
                        // The detail screen of a deleted meal is stale too: go back to the list
                        // (or to Food when the editor was reached from a Day-view meal).
                        onDeleted = { outcome ->
                            if (navController.currentBackStackEntry?.id != entry.id) return@MealEditorScreen
                            // Hand the outcome to the screen we land on; it shows it once.
                            val target = navController.entryOrNull(Routes.MEALS) ?: navController.entryOrNull(Routes.FOOD)
                            target?.savedStateHandle?.set(KEY_MESSAGE, outcome.message().resolve(resources))
                            if (!navController.popBackStack(Routes.MEALS, inclusive = false)) {
                                navController.popBackStack(Routes.FOOD, inclusive = false)
                            }
                        },
                    )
                }
                composable(
                    route = Routes.MEAL_BUILDER_PATTERN,
                    arguments = listOf(
                        navArgument(MealBuilderViewModel.ARG_DATE) { type = NavType.LongType },
                        navArgument(MealBuilderViewModel.ARG_LOG_ID) { type = NavType.LongType },
                    ),
                ) { entry ->
                    MealBuilderScreen(
                        onBack = { navController.popIfResumed(entry) },
                        onFinished = { award ->
                            // Food shows one snackbar: the XP change and the level line together.
                            if (navController.currentBackStackEntry?.id == entry.id) {
                                val parts = listOfNotNull(
                                    award.xpDelta.takeIf { it != 0 }?.let(::xpChangedText),
                                    award.levelUp?.reachedLine(),
                                )
                                if (parts.isNotEmpty()) {
                                    val text: UiText = joinedText(parts, R.string.common_dot_separator)
                                    navController.previousBackStackEntry?.savedStateHandle?.set(KEY_MESSAGE, text.resolve(resources))
                                }
                            }
                            navController.popIfOnTop(entry)
                        },
                    )
                }
                composable(Routes.PLANS) { entry ->
                    val message by entry.savedStateHandle.getStateFlow<String?>(KEY_MESSAGE, null).collectAsStateWithLifecycle()
                    val levels by entry.savedStateHandle.getStateFlow<IntArray?>(KEY_LEVEL_UP, null).collectAsStateWithLifecycle()
                    PlansScreen(
                        message = message,
                        editorLevelUp = levels?.let { LevelUpEvent(it[0], it[1], emptyList()) },
                        onResultShown = {
                            entry.savedStateHandle[KEY_MESSAGE] = null
                            entry.savedStateHandle[KEY_LEVEL_UP] = null
                        },
                        onBack = { navController.popIfResumed(entry) },
                        onEditPlan = { navController.navigateFrom(entry, Routes.planEdit(it)) },
                        onNewPlan = { navController.navigateFrom(entry, Routes.planEdit(null)) },
                        onImport = { navController.navigateFrom(entry, Routes.importCsv(ImportKind.MEAL_PLAN)) },
                    )
                }
                composable(
                    route = Routes.PLAN_EDIT_PATTERN,
                    arguments = listOf(navArgument(PlanEditorViewModel.ARG_PLAN_ID) { type = NavType.StringType }),
                ) { entry ->
                    PlanEditorScreen(
                        onBack = { navController.popIfResumed(entry) },
                        onDone = { award ->
                            // The Plans screen shows the XP change from re-evaluating today's bonuses.
                            if (navController.currentBackStackEntry?.id == entry.id) {
                                navController.previousBackStackEntry?.savedStateHandle?.let { handle ->
                                    if (award.xpDelta != 0) handle[KEY_MESSAGE] = xpChangedText(award.xpDelta).resolve(resources)
                                    award.levelUp?.let { handle[KEY_LEVEL_UP] = intArrayOf(it.previousLevel, it.newLevel) }
                                }
                            }
                            navController.popIfOnTop(entry)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Ends the first-launch setup: Today becomes the root, then the plan import or editor is pushed on
 * top so back lands on Today. Only acts while [entry] is still on top (a double tap can't run it twice).
 */
private fun NavHostController.leaveSetup(entry: NavBackStackEntry, next: SetupExit) {
    if (currentBackStackEntry?.id != entry.id) return
    navigate(Routes.TODAY) { popUpTo(Routes.FOOD_SETUP) { inclusive = true } }
    when (next) {
        SetupExit.TODAY -> Unit
        SetupExit.IMPORT_PLAN -> navigate(Routes.importCsv(ImportKind.MEAL_PLAN))
        SetupExit.NEW_PLAN -> navigate(Routes.planEdit(null))
    }
}

private fun NavHostController.entryOrNull(route: String): NavBackStackEntry? = runCatching { getBackStackEntry(route) }.getOrNull()

/**
 * Pops [entry] only while it is the resumed destination, so a double tap on Back (or a
 * Saved event racing a Back tap) can't pop the screen underneath as well.
 */
private fun NavHostController.popIfResumed(entry: NavBackStackEntry) {
    if (isResumedTop(entry)) popBackStack()
}

/** Navigates only from the resumed entry, so a double tap can't push the same screen twice. */
private fun NavHostController.navigateFrom(entry: NavBackStackEntry, route: String) {
    if (isResumedTop(entry)) navigate(route)
}

/**
 * Checks the LIVE top entry by id, not the captured [entry] object: a tab restore
 * (saveState/restoreState) creates a new NavBackStackEntry with the same id while the
 * screen's composition — and any callbacks it remembered — survives, so the captured
 * object can be a popped entry that never resumes again.
 */
private fun NavHostController.isResumedTop(entry: NavBackStackEntry): Boolean {
    val top = currentBackStackEntry
    return top?.id == entry.id && top.lifecycle.currentState == Lifecycle.State.RESUMED
}

/** For VM-driven exits: pop [entry] if it's still on top, whatever its lifecycle state. */
private fun NavHostController.popIfOnTop(entry: NavBackStackEntry) {
    if (currentBackStackEntry?.id == entry.id) popBackStack()
}

/** Same options as the bottom bar: one copy of each tab, its state saved and restored. Double taps are safe (launchSingleTop). */
private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        // Today, not the graph's start: after the first-launch setup the start destination is gone from the stack.
        popUpTo(Routes.TODAY) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Four tabs: outlined icon when unselected, filled when selected; indicator per style. */
@Composable
private fun IronBottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    val colors = IronTheme.colors
    val treatment = IronTheme.style.listTreatment
    // Diary: no indicator, ink icon. Bold: heavy accent block. Soft: tinted pill.
    val indicator = when (treatment) {
        ListTreatment.RULED_DASHED -> Color.Transparent
        ListTreatment.RULED_SOLID -> colors.accent
        ListTreatment.TILES -> colors.raised
    }
    val selectedIcon = when (treatment) {
        ListTreatment.RULED_SOLID -> colors.onAccent
        ListTreatment.TILES -> colors.accent
        ListTreatment.RULED_DASHED -> colors.ink
    }
    Column(Modifier.onboardingAnchor(OnboardingTarget.BOTTOM_BAR)) {
        HorizontalDivider(thickness = if (treatment == ListTreatment.RULED_SOLID) 2.dp else 1.dp, color = if (treatment == ListTreatment.RULED_SOLID) colors.ink else colors.line)
        NavigationBar(containerColor = colors.paper, tonalElevation = 0.dp) {
            TopLevel.entries.forEach { dest ->
                val selected = currentRoute == dest.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(dest.route) },
                    icon = { Icon(if (selected) dest.selectedIcon else dest.icon, contentDescription = null) },
                    label = {
                        Text(
                            stringResource(dest.label),
                            style = IronTheme.type.label.let { if (selected) it.copy(fontWeight = FontWeight.Bold) else it },
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = selectedIcon,
                        indicatorColor = indicator,
                        selectedTextColor = colors.ink,
                        unselectedIconColor = colors.muted,
                        unselectedTextColor = colors.muted,
                    ),
                )
            }
        }
    }
}
