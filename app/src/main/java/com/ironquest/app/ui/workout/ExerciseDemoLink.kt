package com.ironquest.app.ui.workout

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ironquest.app.R
import com.ironquest.app.domain.model.ExerciseDefinition
import com.ironquest.app.domain.model.ExerciseDemo
import com.ironquest.app.ui.catalog.exerciseName
import com.ironquest.app.ui.theme.IronTheme

/**
 * "How to do it ›" under an exercise's name: opens a YouTube search for the movement (the YouTube
 * app if installed, otherwise the browser). Needs no network code of our own.
 */
@Composable
fun HowToLink(exercise: ExerciseDefinition, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val howToLabel = stringResource(R.string.workout_how_to_cd, exerciseName(exercise))
    var failed by remember { mutableStateOf(false) }
    TextButton(
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ExerciseDemo.youtubeSearchUrl(exercise.name)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (notFound: ActivityNotFoundException) {
                failed = true
            }
        },
        modifier = modifier.semantics { contentDescription = howToLabel },
    ) {
        Text(
            stringResource(if (failed) R.string.workout_how_to_failed else R.string.workout_how_to_link),
            style = IronTheme.type.secondary,
            color = if (failed) IronTheme.colors.muted else IronTheme.colors.ink,
        )
    }
    LaunchedEffect(failed) { if (failed) kotlinx.coroutines.delay(3000).also { failed = false } }
}
