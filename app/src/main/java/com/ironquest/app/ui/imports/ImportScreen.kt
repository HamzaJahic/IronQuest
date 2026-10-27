package com.ironquest.app.ui.imports

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironquest.app.R
import com.ironquest.app.domain.importing.ImportKind
import com.ironquest.app.domain.onboarding.OnboardingScreen
import com.ironquest.app.domain.onboarding.OnboardingTarget
import com.ironquest.app.ui.format.formatTimeOfDay
import com.ironquest.app.ui.kit.BulletList
import com.ironquest.app.ui.kit.IronSnackbarHost
import com.ironquest.app.ui.kit.IronTextField
import com.ironquest.app.ui.kit.PrimaryButton
import com.ironquest.app.ui.kit.RowGroup
import com.ironquest.app.ui.kit.RuledRow
import com.ironquest.app.ui.kit.ScreenTop
import com.ironquest.app.ui.kit.SecondaryButton
import com.ironquest.app.ui.kit.SectionHeader
import com.ironquest.app.ui.kit.TextAction
import com.ironquest.app.ui.kit.ToggleChip
import com.ironquest.app.ui.onboarding.OnboardingGate
import com.ironquest.app.ui.onboarding.onboardingAnchor
import com.ironquest.app.ui.text.resolve
import com.ironquest.app.ui.theme.IronTheme
import kotlinx.coroutines.launch

/** MIME types offered by the file picker; CSVs are labelled inconsistently across apps. */
private val CSV_MIME_TYPES = arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")

@Composable
fun ImportScreen(
    onBack: () -> Unit,
    /** Import written; pop back and show [message] there. */
    onDone: (message: String) -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val currentOnDone by rememberUpdatedState(onDone)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ImportEvent.Done -> currentOnDone(event.message.resolve(context.resources))
                is ImportEvent.Message -> snackbar.showSnackbar(event.text.resolve(context.resources))
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                when (val source = readCsvFile(context, uri)) {
                    is CsvSource.Loaded -> viewModel.onAction(ImportAction.CsvLoaded(source.text, source.fileName))
                    is CsvSource.Failed -> viewModel.onAction(ImportAction.LoadFailed(source.problem))
                }
            }
        }
    }

    fun copy(label: String, text: String, confirmation: String) {
        copyToClipboard(context, label, text)
        scope.launch { snackbar.showSnackbar(confirmation) }
    }

    ImportContent(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onAction = viewModel::onAction,
        onCopyPrompt = {
            val resources = context.resources
            copy(
                resources.getString(R.string.import_clip_label_prompt),
                ImportCopy.aiPrompt(state.kind).resolve(resources),
                ImportCopy.promptCopied(state.kind).resolve(resources),
            )
        },
        onCopyErrors = {
            val resources = context.resources
            copy(
                resources.getString(R.string.import_clip_label_error),
                ImportCopy.errorReport(state.kind, state.issues).resolve(resources),
                resources.getString(R.string.import_error_copied),
            )
        },
        onPickFile = {
            try {
                picker.launch(CSV_MIME_TYPES)
            } catch (notFound: ActivityNotFoundException) {
                scope.launch { snackbar.showSnackbar(context.getString(R.string.import_no_file_picker)) }
            }
        },
        onPaste = {
            val text = readClipboardText(context)
            if (text == null) {
                scope.launch { snackbar.showSnackbar(context.getString(R.string.import_clipboard_empty)) }
            } else {
                viewModel.onAction(ImportAction.CsvLoaded(text, fileName = null))
            }
        },
    )
    OnboardingGate(OnboardingScreen.IMPORT, emptySet())
}

@Composable
private fun ImportContent(
    state: ImportUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onAction: (ImportAction) -> Unit,
    onCopyPrompt: () -> Unit,
    onCopyErrors: () -> Unit,
    onPickFile: () -> Unit,
    onPaste: () -> Unit,
) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    var showFormat by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        containerColor = colors.paper,
        topBar = { ScreenTop(ImportCopy.title(state.kind).resolve(), onBack) },
        snackbarHost = { IronSnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.kind.siblings) { kind ->
                    ToggleChip(ImportCopy.label(kind).resolve(), kind == state.kind, { onAction(ImportAction.KindChange(kind)) })
                }
            }

            SectionHeader(stringResource(R.string.import_get_header))
            BulletList(
                stringResource(R.string.import_step_copy),
                ImportCopy.pasteStep(state.kind).resolve(),
                stringResource(R.string.import_step_save),
            )
            PrimaryButton(stringResource(R.string.import_copy_prompt), onClick = onCopyPrompt, modifier = Modifier.onboardingAnchor(OnboardingTarget.IMPORT_COPY_PROMPT))
            TextAction(stringResource(if (showFormat) R.string.import_hide_format else R.string.import_show_format), { showFormat = !showFormat })
            if (showFormat) {
                Text(
                    state.kind.example,
                    style = type.secondary,
                    color = colors.ink,
                    softWrap = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }

            SectionHeader(stringResource(R.string.import_load_header))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(stringResource(R.string.import_choose_file), onClick = onPickFile, enabled = !state.isImporting, modifier = Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.import_paste), onClick = onPaste, enabled = !state.isImporting, modifier = Modifier.weight(1f).onboardingAnchor(OnboardingTarget.IMPORT_PASTE))
            }
            state.fileName?.let { name ->
                Text(stringResource(R.string.import_loaded, name.resolve()), style = type.secondary, color = colors.muted)
            }

            if (state.issues.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(IronTheme.shapes.tile)
                        .background(colors.deloadBg)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(stringResource(R.string.import_failed_title), style = type.bodyStrong, color = colors.danger)
                    BulletList(*state.issues.map { ImportCopy.issue(it).resolve() }.toTypedArray(), style = type.secondary)
                    SecondaryButton(stringResource(R.string.import_copy_error), onClick = onCopyErrors, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).onboardingAnchor(OnboardingTarget.IMPORT_COPY_ERROR))
                    Text(
                        stringResource(R.string.import_copy_error_hint),
                        style = type.secondary,
                        color = colors.muted,
                    )
                }
            }

            if (state.parsed != null) {
                TargetPanel(
                    state,
                    onNameChange = { onAction(ImportAction.NameChange(it)) },
                    onTargetProgram = { onAction(ImportAction.TargetProgram(it)) },
                    onTargetDay = { onAction(ImportAction.TargetDay(it)) },
                )
                SectionHeader(stringResource(R.string.import_preview_header))
                val timeContext = LocalContext.current
                BulletList(*state.previewLines { formatTimeOfDay(it, timeContext) }.map { it.resolve() }.toTypedArray(), style = type.secondary)
                state.blocker?.let { blocker ->
                    Text(blocker.resolve(), style = type.secondary, color = colors.hold)
                }
                PrimaryButton(
                    stringResource(if (state.isImporting) R.string.import_importing else R.string.common_import),
                    onClick = { onAction(ImportAction.Import) },
                    enabled = state.canImport,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun TargetPanel(
    state: ImportUiState,
    onNameChange: (String) -> Unit,
    onTargetProgram: (Long?) -> Unit,
    onTargetDay: (String) -> Unit,
) {
    if (!state.needsName && !state.isDayImport && !state.isExerciseImport) return
    if (state.isDayImport) {
        SectionHeader(stringResource(R.string.import_add_to_header))
        RowGroup {
            TargetRow(stringResource(R.string.import_new_program), selected = state.targetProgramId == null) { onTargetProgram(null) }
            state.programs.forEach { program ->
                TargetRow(program.name, selected = state.targetProgramId == program.id) { onTargetProgram(program.id) }
            }
        }
    }
    if (state.isExerciseImport && state.dayTargets.isNotEmpty()) {
        SectionHeader(stringResource(R.string.import_add_to_day_header))
        RowGroup {
            state.dayTargets.forEach { day ->
                TargetRow(day.label.resolve(), selected = state.targetTemplateId == day.templateId) { onTargetDay(day.templateId) }
            }
        }
    }
    if (state.needsName) {
        IronTextField(
            stringResource(if (state.kind == ImportKind.MEAL_PLAN) R.string.import_plan_name else R.string.import_program_name),
            state.name,
            onNameChange,
            modifier = Modifier.padding(top = 12.dp),
            capitalization = KeyboardCapitalization.Words,
        )
    }
}

@Composable
private fun TargetRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = IronTheme.colors
    RuledRow(Modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton)) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = colors.accent, unselectedColor = colors.muted),
        )
        Text(label, style = IronTheme.type.body, color = colors.ink)
    }
}
