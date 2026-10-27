package com.ironquest.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ironquest.app.R
import com.ironquest.app.ui.theme.IronTheme
import com.ironquest.app.ui.theme.TitleCase

/**
 * A tip: title, body, step dots and Skip / Back / Next ("Done" on the last step). It has no window of
 * its own: show it inside [Spotlight], which handles back press. [onBack] is null on the first step,
 * [onSkipAll] only on the first step of the first tip of a visit. [demo] sits above the title.
 */
@Composable
fun TipCard(
    title: String,
    body: String,
    stepIndex: Int,
    stepCount: Int,
    onNext: () -> Unit,
    onBack: (() -> Unit)?,
    onSkip: () -> Unit,
    onSkipAll: (() -> Unit)?,
    modifier: Modifier = Modifier,
    demo: (@Composable () -> Unit)? = null,
) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    val isLast = stepIndex == stepCount - 1
    Column(
        modifier
            .fillMaxWidth()
            .clip(IronTheme.shapes.sheet)
            .background(colors.raised)
            .semantics { paneTitle = title }
            .padding(start = 24.dp, end = 12.dp, top = 24.dp, bottom = 8.dp),
    ) {
        if (demo != null) {
            Box(Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 16.dp), contentAlignment = Alignment.Center) { demo() }
        }
        // Bold's section head is an 11 sp label; a card title needs the bigger button face (as IronDialog).
        val bold = IronTheme.style.titleCase == TitleCase.UPPERCASE
        Text(IronTheme.style.cased(title), style = if (bold) type.button else type.sectionHead, color = colors.ink, modifier = Modifier.padding(end = 12.dp))
        Spacer(Modifier.height(8.dp))
        Text(body, style = type.body, color = colors.ink, modifier = Modifier.padding(end = 12.dp))
        if (stepCount > 1) {
            Spacer(Modifier.height(16.dp))
            StepDots(stepIndex, stepCount)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (!isLast) DialogButton(stringResource(R.string.onboarding_skip), onSkip, muted = true)
            Spacer(Modifier.weight(1f))
            if (onBack != null) DialogButton(stringResource(R.string.onboarding_back), onBack, muted = true)
            DialogButton(stringResource(if (isLast) R.string.onboarding_done else R.string.onboarding_next), onNext)
        }
        if (onSkipAll != null) DialogButton(stringResource(R.string.onboarding_skip_all), onSkipAll, muted = true)
    }
}

/** One dot per step, like the workout page dots: current in `ink`, the rest in `line`. */
@Composable
private fun StepDots(stepIndex: Int, stepCount: Int) {
    val colors = IronTheme.colors
    val description = stringResource(R.string.onboarding_step_cd, stepIndex + 1, stepCount)
    Row(
        Modifier.padding(end = 12.dp).clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(stepCount) { index ->
            val current = index == stepIndex
            Box(Modifier.size(if (current) 8.dp else 6.dp).clip(CircleShape).background(if (current) colors.ink else colors.line))
        }
    }
}
