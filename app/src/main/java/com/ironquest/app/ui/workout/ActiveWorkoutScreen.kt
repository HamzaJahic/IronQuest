package com.ironquest.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironquest.app.R
import com.ironquest.app.domain.onboarding.OnboardingFact
import com.ironquest.app.domain.onboarding.OnboardingScreen
import com.ironquest.app.ui.goals.GoalCopy
import com.ironquest.app.ui.kit.IronDialog
import com.ironquest.app.ui.kit.PageTitle
import com.ironquest.app.ui.kit.PrimaryButton
import com.ironquest.app.ui.kit.Stamp
import com.ironquest.app.ui.kit.reachedLine
import com.ironquest.app.ui.kit.stampText
import com.ironquest.app.ui.onboarding.OnboardingGate
import com.ironquest.app.ui.text.resolve
import com.ironquest.app.ui.theme.IronTheme

/** A queued celebration: [text] on the stamp, then [line] as a one-line snackbar once it settles. */
private data class StampUi(val id: Long, val text: String, val line: String)

/** What a number dialog edits: the big weight / reps number of a set, or a whole logged set. */
internal sealed interface DialogTarget {
    val exerciseId: String
    val index: Int

    data class Weight(override val exerciseId: String, override val index: Int, val initial: String) : DialogTarget
    data class Amount(override val exerciseId: String, override val index: Int, val initial: String, val cardio: Boolean) : DialogTarget
    data class LoggedSetEdit(
        override val exerciseId: String,
        override val index: Int,
        val weight: String,
        val amount: String,
        val cardio: Boolean,
    ) : DialogTarget
}

/** The header menu's destructive actions, confirmed in a dialog first. */
internal enum class SessionAction { RESTART, STOP }

@Composable
fun ActiveWorkoutScreen(
    onExit: () -> Unit,
    viewModel: ActiveWorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    // Level-ups show as stamps, not dialogs; shown one at a time.
    val stamps = remember { mutableStateListOf<StampUi>() }
    var settledLine by remember { mutableStateOf<StampUi?>(null) }
    var dialog by remember { mutableStateOf<DialogTarget?>(null) }
    var confirm by remember { mutableStateOf<SessionAction?>(null) }
    // Reported by the pager once it is composed, so the tips wait for the real first page.
    var shownPage by remember { mutableStateOf<Int?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is WorkoutEvent.LevelUp -> stamps += StampUi(System.nanoTime(), event.event.stampText().resolve(resources), event.event.reachedLine().resolve(resources))
                is WorkoutEvent.GoalReached -> stamps += StampUi(
                    System.nanoTime(),
                    GoalCopy.stamp(event.type).resolve(resources),
                    resources.getString(R.string.workout_goal_stamp_line, GoalCopy.label(event.type).resolve(resources), event.exerciseName.resolve(resources), event.bonusXp),
                )
                is WorkoutEvent.Message -> snackbarHostState.showSnackbar(event.text.resolve(resources))
                WorkoutEvent.Stopped -> onExit()
            }
        }
    }
    LaunchedEffect(settledLine?.id) {
        settledLine?.let { snackbarHostState.showSnackbar(it.line) }
    }

    // One instance for the screen's life: a new callback per emission made every composed
    // pager page recompose on each -/+ tap.
    val onAction: (WorkoutAction) -> Unit = remember(viewModel) { viewModel::onAction }
    val onOpenDialog: (DialogTarget) -> Unit = remember { { target: DialogTarget -> dialog = target } }

    Box(Modifier.fillMaxSize().background(IronTheme.colors.paper)) {
        val victory = state.victory
        when {
            victory != null -> SummaryPage(state, victory, onDone = onExit)
            state.isLoading -> Unit // local DB: draw nothing rather than flash a spinner
            state.notFound -> NotFound(onBack = onExit)
            // Keyed so a restart starts the pager over on the first page.
            else -> key(state.sessionKey) {
                WorkoutPager(
                    state = state,
                    onBack = onExit,
                    onSessionAction = { confirm = it },
                    onAction = onAction,
                    onOpenDialog = onOpenDialog,
                    onPageShown = { page -> shownPage = page },
                    onMenuOpenChange = { open -> menuOpen = open },
                )
            }
        }

        stamps.firstOrNull()?.let { stamp ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                key(stamp.id) {
                    Stamp(stamp.text, onSettled = {
                        stamps.remove(stamp)
                        settledLine = stamp
                    })
                }
            }
        }
        SnackbarHost(
            snackbarHostState,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 64.dp),
        )
    }

    val page = shownPage
    if (!state.isLoading && !state.notFound && page != null) {
        val blocked = dialog != null || confirm != null || menuOpen
        val facts = state.onboardingFacts(page)
        OnboardingGate(OnboardingScreen.WORKOUT, if (blocked) facts + OnboardingFact.BLOCKED else facts)
    }

    dialog?.let { target ->
        NumberDialog(
            target = target,
            onDismiss = { dialog = null },
            onSave = { weight, amount ->
                dialog = null
                when (target) {
                    is DialogTarget.Weight -> onAction(WorkoutAction.WeightChange(target.exerciseId, target.index, weight))
                    is DialogTarget.Amount -> onAction(WorkoutAction.AmountChange(target.exerciseId, target.index, amount))
                    is DialogTarget.LoggedSetEdit -> onAction(WorkoutAction.EditSet(target.exerciseId, target.index, weight, amount))
                }
            },
            onDelete = {
                dialog = null
                onAction(WorkoutAction.DeleteSet(target.exerciseId, target.index))
            },
        )
    }

    confirm?.let { action ->
        val restart = action == SessionAction.RESTART
        IronDialog(
            title = stringResource(if (restart) R.string.workout_restart_title else R.string.workout_stop_title),
            text = pluralStringResource(
                if (restart) R.plurals.workout_restart_text_count else R.plurals.workout_stop_text_count,
                state.loggedSets,
                state.loggedSets,
            ),
            confirmLabel = stringResource(if (restart) R.string.workout_restart_confirm else R.string.workout_stop_confirm),
            destructive = true,
            onConfirm = {
                confirm = null
                onAction(if (action == SessionAction.RESTART) WorkoutAction.Restart else WorkoutAction.Stop)
            },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun NotFound(onBack: () -> Unit) {
    val colors = IronTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp, start = 20.dp, end = 20.dp),
    ) {
        PageTitle(stringResource(R.string.workout_not_found_title))
        Text(
            stringResource(R.string.workout_not_found_body),
            style = IronTheme.type.body,
            color = colors.muted,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        PrimaryButton(stringResource(R.string.workout_back_to_today), onClick = onBack)
    }
}
