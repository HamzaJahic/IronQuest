package com.ironquest.app.ui.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ironquest.app.R
import com.ironquest.app.domain.importing.CsvExport
import com.ironquest.app.domain.importing.ImportKind
import com.ironquest.app.domain.model.Program
import com.ironquest.app.domain.model.TrackingType
import com.ironquest.app.domain.onboarding.OnboardingFact
import com.ironquest.app.domain.onboarding.OnboardingScreen
import com.ironquest.app.domain.onboarding.OnboardingTarget
import com.ironquest.app.domain.repository.ProgramException
import com.ironquest.app.domain.repository.ProgramRepository
import com.ironquest.app.ui.catalog.exerciseName
import com.ironquest.app.ui.catalog.programName
import com.ironquest.app.ui.catalog.programNameText
import com.ironquest.app.ui.catalog.templateName
import com.ironquest.app.ui.kit.BulletList
import com.ironquest.app.ui.kit.IronDialog
import com.ironquest.app.ui.kit.IronSnackbarHost
import com.ironquest.app.ui.kit.RowAction
import com.ironquest.app.ui.kit.RowActions
import com.ironquest.app.ui.kit.RowGroup
import com.ironquest.app.ui.kit.RuledRow
import com.ironquest.app.ui.kit.ScreenTop
import com.ironquest.app.ui.kit.SecondaryButton
import com.ironquest.app.ui.onboarding.OnboardingGate
import com.ironquest.app.ui.onboarding.onboardingAnchor
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.resolve
import com.ironquest.app.ui.text.uiText
import com.ironquest.app.ui.theme.IronTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class ProgramsUiState(
    val isLoading: Boolean = true,
    val programs: List<Program> = emptyList(),
    /** The built-in program was deleted; Programs offers to bring it back. */
    val builtInHidden: Boolean = false,
)

@HiltViewModel
class ProgramsViewModel @Inject constructor(private val repository: ProgramRepository) : ViewModel() {

    val uiState: StateFlow<ProgramsUiState> = combine(repository.observePrograms(), repository.observeBuiltInHidden()) { programs, hidden ->
        ProgramsUiState(isLoading = false, programs = programs, builtInHidden = hidden)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgramsUiState())

    private val _messages = Channel<UiText>(Channel.BUFFERED)
    val messages: Flow<UiText> = _messages.receiveAsFlow()

    private val writeMutex = Mutex()

    fun activate(id: Long?) = write { repository.setActiveProgram(id) }

    /** null = the built-in program. */
    fun delete(id: Long?) = write { repository.deleteProgram(id) }

    fun restoreBuiltIn() = write { repository.restoreBuiltIn() }

    fun say(text: UiText) {
        _messages.trySend(text)
    }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                writeMutex.withLock { block() }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _messages.send(
                    when {
                        error is ProgramException -> ProgramCopy.problem(error.problem)
                        error.message != null -> UiText.Verbatim(error.message.orEmpty())
                        else -> uiText(R.string.programs_update_failed)
                    },
                )
            }
        }
    }
}

@Composable
fun ProgramsScreen(
    onBack: () -> Unit,
    onImport: () -> Unit,
    /** One-shot message from the import screen; shown once. */
    message: String? = null,
    onMessageShown: () -> Unit = {},
    viewModel: ProgramsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it.resolve(resources)) } }
    LaunchedEffect(message) {
        if (message != null) {
            onMessageShown()
            snackbar.showSnackbar(message)
        }
    }
    var confirmDelete by remember { mutableStateOf<Program?>(null) }
    val colors = IronTheme.colors
    val context = LocalContext.current

    Scaffold(
        containerColor = colors.paper,
        topBar = { ScreenTop(stringResource(R.string.programs_title), onBack) },
        snackbarHost = { IronSnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                BulletList(
                    stringResource(R.string.programs_hint_rotation),
                    stringResource(R.string.programs_hint_history),
                    style = IronTheme.type.secondary,
                    color = colors.muted,
                )
            }
            items(state.programs, key = { it.id ?: -1L }) { program ->
                ProgramCard(
                    program = program,
                    // One coach-mark hole per step: the active program (else the first) stands for all.
                    isAnchor = program == (state.programs.firstOrNull { it.isActive } ?: state.programs.first()),
                    onActivate = { viewModel.activate(program.id) },
                    onShare = {
                        val ok = shareText(context, programNameText(program).resolve(context.resources), CsvExport.program(program.templates))
                        if (!ok) viewModel.say(uiText(R.string.programs_share_failed))
                    },
                    onDelete = { confirmDelete = program },
                )
            }
            if (state.programs.isEmpty() && !state.isLoading) {
                item { Text(stringResource(R.string.programs_empty), style = IronTheme.type.body, color = colors.muted) }
            }
            item {
                SecondaryButton(ImportCopy.title(ImportKind.PROGRAM).resolve(), onClick = onImport, modifier = Modifier.fillMaxWidth().onboardingAnchor(OnboardingTarget.PROGRAMS_IMPORT))
            }
            if (state.builtInHidden) {
                item {
                    SecondaryButton(stringResource(R.string.programs_restore_built_in), onClick = viewModel::restoreBuiltIn, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    confirmDelete?.let { program ->
        IronDialog(
            title = stringResource(R.string.programs_delete_title, programName(program)),
            text = stringResource(if (program.isBuiltIn) R.string.programs_delete_text_built_in else R.string.programs_delete_text),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = {
                viewModel.delete(program.id)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
            destructive = true,
        )
    }
    if (!state.isLoading) OnboardingGate(OnboardingScreen.PROGRAMS, if (confirmDelete != null) setOf(OnboardingFact.BLOCKED) else emptySet())
}

@Composable
private fun ProgramCard(program: Program, isAnchor: Boolean, onActivate: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    RowGroup {
        RuledRow {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Column(if (isAnchor) Modifier.onboardingAnchor(OnboardingTarget.PROGRAMS_ACTIVE) else Modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(programName(program), style = type.bodyStrong, color = colors.ink)
                    if (program.isActive) Text(stringResource(R.string.programs_active), style = type.secondary, color = colors.accent)
                }
                program.templates.forEach { template ->
                    Text(templateName(template), style = type.bodyStrong, color = colors.ink, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    BulletList(
                        template.exercises.map { exercise ->
                            val name = exerciseName(exercise.exercise)
                            val range = stringResource(R.string.common_range, exercise.repRange.min, exercise.repRange.max)
                            val detail = if (exercise.exercise.trackingType == TrackingType.DURATION) {
                                stringResource(R.string.programs_exercise_sets_minutes, exercise.targetSets, range, stringResource(R.string.unit_min))
                            } else {
                                stringResource(R.string.programs_exercise_sets_reps, exercise.targetSets, range)
                            }
                            buildAnnotatedString {
                                append(name)
                                withStyle(SpanStyle(color = colors.muted)) { append(detail) }
                            }
                        },
                        spacing = 4,
                    )
                }
            }
        }
        RowActions(
            listOfNotNull(
                RowAction(stringResource(R.string.programs_make_active), onActivate).takeUnless { program.isActive },
                RowAction(stringResource(R.string.common_share), onShare),
                RowAction(stringResource(R.string.common_delete), onDelete, destructive = true),
            ),
            if (isAnchor) Modifier.onboardingAnchor(OnboardingTarget.PROGRAMS_ACTIONS) else Modifier,
        )
    }
}
