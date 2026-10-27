package com.ironquest.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ironquest.app.R
import com.ironquest.app.domain.model.WorkoutBlock
import com.ironquest.app.domain.onboarding.OnboardingTarget
import com.ironquest.app.domain.workout.isBlockDone
import com.ironquest.app.ui.onboarding.LocalOnboardingAnchors
import com.ironquest.app.ui.onboarding.onboardingAnchor
import com.ironquest.app.ui.theme.IronTheme
import kotlinx.coroutines.launch

@Composable
internal fun WorkoutPager(
    state: ActiveWorkoutUiState,
    onBack: () -> Unit,
    onSessionAction: (SessionAction) -> Unit,
    onAction: (WorkoutAction) -> Unit,
    onOpenDialog: (DialogTarget) -> Unit,
    onPageShown: (Int) -> Unit = {},
    onMenuOpenChange: (Boolean) -> Unit = {},
) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    val blocks = state.blocks
    val anchors = LocalOnboardingAnchors.current
    val pager = rememberPagerState(initialPage = state.initialPage.coerceIn(0, (blocks.size - 1).coerceAtLeast(0))) { blocks.size }
    val scope = rememberCoroutineScope()
    // Remembered so the pages can skip recomposition. Reads pageCount at call time, not a
    // captured blocks.lastIndex, so it stays right if the block list ever changes.
    val onNextPage: () -> Unit = remember(pager, scope) {
        { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(pager.pageCount - 1)) } }
    }
    val onFinish: () -> Unit = remember(onAction) { { onAction(WorkoutAction.Finish) } }
    val currentOnPageShown by rememberUpdatedState(onPageShown)
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { page -> currentOnPageShown(page) }
    }
    // "Exercise 2 of 5" while every page is one exercise; with supersets a page can hold several.
    val progressRes = if (blocks.any { it is WorkoutBlock.Superset }) R.string.workout_header_page_progress else R.string.workout_header_exercise_progress

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.workout_back_today), style = type.body, color = colors.muted)
            }
            Spacer(Modifier.weight(1f))
            Text(
                IronTheme.style.cased(stringResource(progressRes, pager.currentPage + 1, blocks.size)),
                style = type.secondary,
                color = colors.muted,
                modifier = Modifier.padding(end = if (state.loggedSets > 0) 0.dp else 12.dp),
            )
            // Nothing to stop or restart until a set is logged (the session doesn't exist yet).
            if (state.loggedSets > 0) SessionMenu(enabled = !state.isResetting, onAction = onSessionAction, onOpenChange = onMenuOpenChange)
        }
        PageDots(state, pager.currentPage)

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
            beyondViewportPageCount = 1,
        ) { page ->
            // Only the page on screen can be a coach-mark target; its prefetched neighbours can't.
            val isCurrent by remember(pager, page) { derivedStateOf { pager.currentPage == page } }
            CompositionLocalProvider(LocalOnboardingAnchors provides anchors.takeIf { isCurrent }) {
                val block = blocks[page]
                val group = state.exercisesOf(block)
                val isLast = page == blocks.lastIndex
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    when (block) {
                        is WorkoutBlock.Single -> group.firstOrNull()?.let { exercise ->
                            SinglePage(exercise, isLast, state.canComplete, onAction, onOpenDialog, onNextPage)
                        }
                        is WorkoutBlock.Superset -> if (group.size == block.exercises.size) {
                            SupersetPage(block, group, isLast, state.canComplete, onAction, onOpenDialog, onNextPage)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        Footer(state, onFinish = onFinish)
    }
}

/** "⋯" in the header: Restart workout / Stop workout. */
@Composable
private fun SessionMenu(enabled: Boolean, onAction: (SessionAction) -> Unit, onOpenChange: (Boolean) -> Unit) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    var open by remember { mutableStateOf(false) }
    val currentOnOpenChange by rememberUpdatedState(onOpenChange)
    LaunchedEffect(open) { currentOnOpenChange(open) }
    val optionsLabel = stringResource(R.string.workout_options_cd)
    Box(Modifier.onboardingAnchor(OnboardingTarget.WORKOUT_MENU)) {
        TextButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.semantics { contentDescription = optionsLabel }) {
            Text("⋯", style = type.bodyStrong, color = colors.muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.raised) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.workout_menu_restart), style = type.body, color = colors.ink) },
                onClick = {
                    open = false
                    onAction(SessionAction.RESTART)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.workout_menu_stop), style = type.body, color = colors.danger) },
                onClick = {
                    open = false
                    onAction(SessionAction.STOP)
                },
            )
        }
    }
}

/** One dot per page: current in `ink`, finished in `accent`, the rest in `line`. */
@Composable
private fun PageDots(state: ActiveWorkoutUiState, current: Int) {
    val colors = IronTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
    ) {
        state.blocks.forEachIndexed { i, block ->
            val done = state.exercisesOf(block).isBlockDone()
            val color = when {
                i == current -> colors.ink
                done -> colors.accent
                else -> colors.line
            }
            Box(Modifier.size(if (i == current) 8.dp else 6.dp).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun Footer(state: ActiveWorkoutUiState, onFinish: () -> Unit) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    Column(Modifier.fillMaxWidth().background(colors.paper).navigationBarsPadding().onboardingAnchor(OnboardingTarget.WORKOUT_FOOTER)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pluralStringResource(R.plurals.workout_footer_status_count, state.targetSets, state.loggedSets, state.targetSets, state.sessionXp),
                style = type.secondary,
                color = colors.muted,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onFinish, enabled = state.canComplete) {
                Text(
                    IronTheme.style.cased(stringResource(if (state.isCompleting) R.string.workout_finishing else R.string.workout_finish)),
                    style = type.button,
                    color = if (state.canComplete) colors.accent else colors.muted,
                )
            }
        }
    }
}
