package com.ironquest.app.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironquest.app.R
import com.ironquest.app.domain.model.LevelUpEvent
import com.ironquest.app.domain.nutrition.MealPlan
import com.ironquest.app.domain.onboarding.OnboardingFact
import com.ironquest.app.domain.onboarding.OnboardingScreen
import com.ironquest.app.domain.onboarding.OnboardingTarget
import com.ironquest.app.ui.imports.shareText
import com.ironquest.app.ui.kit.HeaderAction
import com.ironquest.app.ui.kit.IronDialog
import com.ironquest.app.ui.kit.IronSnackbarHost
import com.ironquest.app.ui.kit.IronSwitch
import com.ironquest.app.ui.kit.RowAction
import com.ironquest.app.ui.kit.RowActions
import com.ironquest.app.ui.kit.RowGroup
import com.ironquest.app.ui.kit.RuledRow
import com.ironquest.app.ui.kit.ScreenTop
import com.ironquest.app.ui.kit.reachedLine
import com.ironquest.app.ui.onboarding.OnboardingGate
import com.ironquest.app.ui.onboarding.onboardingAnchor
import com.ironquest.app.ui.text.resolve
import com.ironquest.app.ui.theme.IronTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun PlansScreen(
    onBack: () -> Unit,
    onEditPlan: (Long) -> Unit,
    onNewPlan: () -> Unit,
    onImport: () -> Unit,
    /** XP text from the plan editor that just saved (e.g. "+25 XP"); shown once. */
    message: String? = null,
    /** Level-up caused by that save; shown once. */
    editorLevelUp: LevelUpEvent? = null,
    onResultShown: () -> Unit = {},
    viewModel: PlansViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            // Level-ups are a one-line snackbar now (no dialogs).
            snackbar.showSnackbar(event.text().resolve(resources))
        }
    }
    LaunchedEffect(message, editorLevelUp) {
        if (message != null || editorLevelUp != null) {
            onResultShown()
            val line = listOfNotNull(message, editorLevelUp?.reachedLine()?.resolve(resources)).joinToString(resources.getString(R.string.common_dot_separator))
            if (line.isNotEmpty()) snackbar.showSnackbar(line)
        }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onShare: (MealPlan) -> Unit = { plan ->
        scope.launch {
            val csv = viewModel.exportCsv(plan.id)
            val shared = csv != null && shareText(context, plan.name, csv)
            if (!shared) snackbar.showSnackbar(resources.getString(R.string.food_plans_share_failed))
        }
    }
    val onDuplicate: (Long) -> Unit = { planId ->
        viewModel.duplicate(planId) { name -> resources.getString(R.string.food_plan_copy_name, name) }
    }
    var dialogOpen by remember { mutableStateOf(false) }
    PlansContent(
        state,
        snackbar,
        onBack,
        onEditPlan,
        onNewPlan,
        onImport,
        viewModel::setActive,
        onDuplicate,
        onShare,
        viewModel::delete,
        onDialogOpenChange = { open -> dialogOpen = open },
    )
    if (!state.isLoading) OnboardingGate(OnboardingScreen.PLANS, if (dialogOpen) setOf(OnboardingFact.BLOCKED) else emptySet())
}

@Composable
private fun PlansContent(
    state: PlansUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onEditPlan: (Long) -> Unit,
    onNewPlan: () -> Unit,
    onImport: () -> Unit,
    onSetActive: (Long, Boolean) -> Unit,
    onDuplicate: (Long) -> Unit,
    onShare: (MealPlan) -> Unit,
    onDelete: (Long) -> Unit,
    onDialogOpenChange: (Boolean) -> Unit,
) {
    val colors = IronTheme.colors
    var confirmDelete by remember { mutableStateOf<MealPlan?>(null) }
    val currentOnDialogOpenChange by rememberUpdatedState(onDialogOpenChange)
    LaunchedEffect(confirmDelete != null) { currentOnDialogOpenChange(confirmDelete != null) }
    Scaffold(
        containerColor = colors.paper,
        topBar = {
            ScreenTop(stringResource(R.string.food_plans_title), onBack) {
                HeaderAction(stringResource(R.string.common_import), onImport, modifier = Modifier.onboardingAnchor(OnboardingTarget.PLANS_IMPORT))
                HeaderAction(stringResource(R.string.common_new), onNewPlan)
            }
        },
        snackbarHost = { IronSnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.isLoading && state.plans.isEmpty()) {
                item {
                    Text(stringResource(R.string.food_plans_empty), style = IronTheme.type.body, color = colors.muted)
                }
            }
            items(state.plans, key = { it.id }) { plan ->
                PlanCard(
                    plan = plan,
                    // One coach-mark hole per step: the first plan stands for all of them.
                    isAnchor = plan == state.plans.first(),
                    onActiveChange = { onSetActive(plan.id, it) },
                    onEdit = { onEditPlan(plan.id) },
                    onDuplicate = { onDuplicate(plan.id) },
                    onShare = { onShare(plan) },
                    onDelete = { confirmDelete = plan },
                )
            }
        }
    }
    confirmDelete?.let { plan ->
        IronDialog(
            title = stringResource(R.string.food_plans_delete_title, plan.name),
            text = stringResource(R.string.food_plans_delete_text),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = {
                onDelete(plan.id)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
            destructive = true,
        )
    }
}

@Composable
private fun PlanCard(
    plan: MealPlan,
    isAnchor: Boolean,
    onActiveChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    RowGroup {
        RuledRow(onClick = onEdit) {
            Column(Modifier.weight(1f)) {
                Text(plan.name, style = type.bodyStrong, color = colors.ink)
                Text(
                    listOfNotNull(
                        stringResource(R.string.food_plan_active).takeIf { plan.isActive },
                        pluralStringResource(R.plurals.food_plan_meal_count, plan.slots.size, plan.slots.size),
                        plan.targets.summaryLine(),
                    ).joinToString(stringResource(R.string.common_dot_separator)),
                    style = type.secondary,
                    color = colors.muted,
                )
            }
            val switchLabel = stringResource(R.string.food_plan_active_switch_cd, plan.name)
            IronSwitch(
                plan.isActive,
                onActiveChange,
                Modifier.semantics { contentDescription = switchLabel }.then(if (isAnchor) Modifier.onboardingAnchor(OnboardingTarget.PLANS_ACTIVE) else Modifier),
            )
        }
        RowActions(
            listOf(
                RowAction(stringResource(R.string.common_edit), onEdit),
                RowAction(stringResource(R.string.common_duplicate), onDuplicate),
                RowAction(stringResource(R.string.common_share), onShare),
                RowAction(stringResource(R.string.common_delete), onDelete, destructive = true),
            ),
            if (isAnchor) Modifier.onboardingAnchor(OnboardingTarget.PLANS_ACTIONS) else Modifier,
        )
    }
}
