package com.ironquest.app.domain.onboarding

/** Where an onboarding gate lives: one per stateful screen (Week and Month are separate screens here). */
enum class OnboardingScreen {
    TODAY,
    WORKOUT,
    LOG,
    LOG_EDITOR,
    FOOD_DAY,
    FOOD_WEEK,
    FOOD_MONTH,
    MEALS,
    MEAL_DETAIL,
    MEAL_EDITOR,
    MEAL_BUILDER,
    INGREDIENTS,
    PLANS,
    PLAN_EDITOR,
    PROGRAMS,
    IMPORT,
    YOU,
}

/** What a screen currently knows, so contextual tips wait for their moment. */
enum class OnboardingFact {
    /** A dialog, sheet or menu of the screen's own is open: never cover it. */
    BLOCKED,
    GOALS_VISIBLE,
    OTHER_DAYS_VISIBLE,

    /** Other days show their "Do next" action (hidden while a workout is unfinished). */
    DO_NEXT_VISIBLE,
    SET_LOGGED,
    SUPERSET_VISIBLE,
    DAY_SHEET_OPEN,
    SUPPLEMENTS_VISIBLE,

    /** The tracking mode shows the plan's checklist. */
    FOOD_PLAN_SHOWN,

    /** Plan mode: the "Log something else" link is the way to add food. */
    FOOD_PICKER_SHOWN,

    /** Log-as-I-go or both: the "Log food" link is the way to add food. */
    FOOD_BUILDER_SHOWN,
    RECENTS_VISIBLE,
    AMOUNT_ROW_OPEN,
    CREATE_FOOD_VISIBLE,
}

/** The control a step is about. Only coach marks use it; a missing target falls back to a centred card. */
enum class OnboardingTarget {
    BOTTOM_BAR,
    TODAY_START,
    TODAY_SUMMARY,
    TODAY_GOALS,
    TODAY_OTHER_DAYS,
    TODAY_DO_NEXT,
    TODAY_FOOD_LINE,
    WORKOUT_NUMBERS,
    WORKOUT_LOG_BUTTON,
    WORKOUT_OVERLOAD,
    WORKOUT_HOW_TO,
    WORKOUT_FOOTER,
    WORKOUT_LOGGED_SETS,
    WORKOUT_MENU,
    SUPERSET_ROUNDS,
    SUPERSET_BUTTON,
    LOG_MONTH,
    LOG_ADD_WORKOUT,
    LOG_WORKOUT_ACTIONS,
    LOG_EDITOR_SAME_AS_LAST,
    LOG_EDITOR_SAVE,
    FOOD_DATE,
    FOOD_MODES,
    FOOD_CHECKLIST,
    FOOD_LOG_OTHER,
    FOOD_LOG_FOOD,
    FOOD_WATER,
    FOOD_SUPPLEMENTS,
    FOOD_LINKS,
    FOOD_TRACKING_LINK,
    FOOD_WEEK_BARS,
    FOOD_WEEK_MACRO,
    FOOD_MONTH_GRID,
    MEALS_SEARCH,
    MEALS_ARCHIVED,
    MEALS_IMPORT,
    MEAL_DETAIL_LOG,
    MEAL_EDITOR_MACROS,
    MEAL_EDITOR_ESTIMATE,
    INGREDIENTS_SEARCH,
    INGREDIENTS_APPLY,
    PLANS_ACTIVE,
    PLANS_ACTIONS,
    PLANS_IMPORT,
    PLAN_EDITOR_SLOT,
    PLAN_EDITOR_TARGETS,
    PROGRAMS_ACTIVE,
    PROGRAMS_ACTIONS,
    PROGRAMS_IMPORT,
    IMPORT_COPY_PROMPT,
    IMPORT_PASTE,
    IMPORT_COPY_ERROR,
    YOU_LEVEL,
    YOU_PLANS,
    YOU_GOALS,
    YOU_APPEARANCE,
    YOU_BACKUP,
    YOU_FOOD_TRACKING,
    BUILDER_SEARCH,
    BUILDER_RECENTS,
    BUILDER_DONE,
    BUILDER_UNITS,
    BUILDER_CREATE,
}

/** One page of a tip. [requires] hides just this step (e.g. no supplements yet → skip the supplements step). */
data class OnboardingStep(val target: OnboardingTarget?, val requires: OnboardingFact? = null)

private fun step(target: OnboardingTarget?, requires: OnboardingFact? = null) = OnboardingStep(target, requires)

/**
 * Every tip in the app. Declaration order is priority order. Words live in `ui/onboarding/OnboardingCopy`
 * and `strings_onboarding.xml`; this file only says what exists and when.
 *
 * A topic without [requires] is an intro: it shows on the screen's first visit. One with [requires] is
 * contextual: it shows when its situation first comes up. [after] makes a topic wait until another is
 * seen. Bump [version] when the feature changes enough that its tip should show again.
 */
enum class OnboardingTopic(
    val screens: Set<OnboardingScreen>,
    val steps: List<OnboardingStep>,
    val requires: Set<OnboardingFact> = emptySet(),
    val after: OnboardingTopic? = null,
    val version: Int = 1,
) {
    WELCOME(setOf(OnboardingScreen.TODAY), listOf(step(null), step(OnboardingTarget.BOTTOM_BAR), step(null))),
    TODAY(
        setOf(OnboardingScreen.TODAY),
        listOf(
            step(OnboardingTarget.TODAY_START),
            step(OnboardingTarget.TODAY_SUMMARY),
            step(OnboardingTarget.TODAY_OTHER_DAYS, requires = OnboardingFact.OTHER_DAYS_VISIBLE),
            step(OnboardingTarget.TODAY_DO_NEXT, requires = OnboardingFact.DO_NEXT_VISIBLE),
            step(OnboardingTarget.TODAY_FOOD_LINE),
        ),
        after = WELCOME,
        version = 2,
    ),
    GOALS(setOf(OnboardingScreen.TODAY), listOf(step(OnboardingTarget.TODAY_GOALS)), requires = setOf(OnboardingFact.GOALS_VISIBLE), after = TODAY),
    WORKOUT(
        setOf(OnboardingScreen.WORKOUT),
        listOf(
            step(OnboardingTarget.WORKOUT_NUMBERS),
            step(OnboardingTarget.WORKOUT_LOG_BUTTON),
            step(OnboardingTarget.WORKOUT_OVERLOAD),
            step(OnboardingTarget.WORKOUT_HOW_TO),
            step(OnboardingTarget.WORKOUT_FOOTER),
        ),
    ),
    WORKOUT_LOGGED_SET(
        setOf(OnboardingScreen.WORKOUT),
        listOf(step(OnboardingTarget.WORKOUT_LOGGED_SETS), step(OnboardingTarget.WORKOUT_MENU)),
        requires = setOf(OnboardingFact.SET_LOGGED),
        after = WORKOUT,
    ),
    SUPERSET(
        setOf(OnboardingScreen.WORKOUT),
        listOf(step(OnboardingTarget.SUPERSET_ROUNDS), step(OnboardingTarget.SUPERSET_BUTTON)),
        requires = setOf(OnboardingFact.SUPERSET_VISIBLE),
        after = WORKOUT,
    ),
    LOG(setOf(OnboardingScreen.LOG), listOf(step(OnboardingTarget.LOG_MONTH))),
    LOG_DAY(
        setOf(OnboardingScreen.LOG),
        listOf(step(OnboardingTarget.LOG_ADD_WORKOUT), step(OnboardingTarget.LOG_WORKOUT_ACTIONS)),
        requires = setOf(OnboardingFact.DAY_SHEET_OPEN),
        after = LOG,
    ),
    LOG_EDITOR(setOf(OnboardingScreen.LOG_EDITOR), listOf(step(OnboardingTarget.LOG_EDITOR_SAME_AS_LAST), step(OnboardingTarget.LOG_EDITOR_SAVE))),
    FOOD_DAY(
        setOf(OnboardingScreen.FOOD_DAY),
        listOf(
            step(OnboardingTarget.FOOD_CHECKLIST, requires = OnboardingFact.FOOD_PLAN_SHOWN),
            step(OnboardingTarget.FOOD_LOG_OTHER, requires = OnboardingFact.FOOD_PICKER_SHOWN),
            step(OnboardingTarget.FOOD_LOG_FOOD, requires = OnboardingFact.FOOD_BUILDER_SHOWN),
            step(OnboardingTarget.FOOD_WATER),
            step(OnboardingTarget.FOOD_SUPPLEMENTS, requires = OnboardingFact.SUPPLEMENTS_VISIBLE),
            step(OnboardingTarget.FOOD_DATE),
            step(OnboardingTarget.FOOD_MODES),
            step(OnboardingTarget.FOOD_LINKS),
            step(OnboardingTarget.FOOD_TRACKING_LINK),
        ),
        version = 2,
    ),
    FOOD_OVERVIEW(
        setOf(OnboardingScreen.FOOD_WEEK, OnboardingScreen.FOOD_MONTH),
        listOf(step(OnboardingTarget.FOOD_WEEK_BARS), step(OnboardingTarget.FOOD_WEEK_MACRO), step(OnboardingTarget.FOOD_MONTH_GRID)),
    ),
    MEAL_BUILDER(
        setOf(OnboardingScreen.MEAL_BUILDER),
        listOf(
            step(OnboardingTarget.BUILDER_SEARCH),
            step(OnboardingTarget.BUILDER_RECENTS, requires = OnboardingFact.RECENTS_VISIBLE),
            step(OnboardingTarget.BUILDER_DONE),
        ),
    ),
    MEAL_BUILDER_AMOUNT(
        setOf(OnboardingScreen.MEAL_BUILDER),
        listOf(step(OnboardingTarget.BUILDER_UNITS)),
        requires = setOf(OnboardingFact.AMOUNT_ROW_OPEN),
        after = MEAL_BUILDER,
    ),
    CUSTOM_FOOD(
        setOf(OnboardingScreen.MEAL_BUILDER),
        listOf(step(OnboardingTarget.BUILDER_CREATE)),
        requires = setOf(OnboardingFact.CREATE_FOOD_VISIBLE),
        after = MEAL_BUILDER,
    ),
    MEALS(setOf(OnboardingScreen.MEALS), listOf(step(OnboardingTarget.MEALS_SEARCH), step(OnboardingTarget.MEALS_ARCHIVED), step(OnboardingTarget.MEALS_IMPORT))),
    MEAL_DETAIL(setOf(OnboardingScreen.MEAL_DETAIL), listOf(step(OnboardingTarget.MEAL_DETAIL_LOG))),
    MEAL_EDITOR(setOf(OnboardingScreen.MEAL_EDITOR), listOf(step(OnboardingTarget.MEAL_EDITOR_MACROS), step(OnboardingTarget.MEAL_EDITOR_ESTIMATE))),
    INGREDIENTS(setOf(OnboardingScreen.INGREDIENTS), listOf(step(OnboardingTarget.INGREDIENTS_SEARCH), step(OnboardingTarget.INGREDIENTS_APPLY)), version = 2),
    PLANS(setOf(OnboardingScreen.PLANS), listOf(step(OnboardingTarget.PLANS_ACTIVE), step(OnboardingTarget.PLANS_ACTIONS), step(OnboardingTarget.PLANS_IMPORT))),
    PLAN_EDITOR(setOf(OnboardingScreen.PLAN_EDITOR), listOf(step(OnboardingTarget.PLAN_EDITOR_SLOT), step(OnboardingTarget.PLAN_EDITOR_TARGETS))),
    PROGRAMS(setOf(OnboardingScreen.PROGRAMS), listOf(step(OnboardingTarget.PROGRAMS_ACTIVE), step(OnboardingTarget.PROGRAMS_ACTIONS), step(OnboardingTarget.PROGRAMS_IMPORT))),
    IMPORT(setOf(OnboardingScreen.IMPORT), listOf(step(OnboardingTarget.IMPORT_COPY_PROMPT), step(OnboardingTarget.IMPORT_PASTE), step(OnboardingTarget.IMPORT_COPY_ERROR))),
    YOU(
        setOf(OnboardingScreen.YOU),
        listOf(
            step(OnboardingTarget.YOU_LEVEL),
            step(OnboardingTarget.YOU_PLANS),
            step(OnboardingTarget.YOU_FOOD_TRACKING),
            step(OnboardingTarget.YOU_GOALS),
            step(OnboardingTarget.YOU_APPEARANCE),
            step(OnboardingTarget.YOU_BACKUP),
        ),
        version = 2,
    ),
}
