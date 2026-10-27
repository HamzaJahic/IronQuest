package com.ironquest.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ironquest.app.ui.theme.IronTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * A Monday-first month: narrow weekday initials, then one square [day] cell per date. The blanks
 * before the 1st and after the last day stay empty. [day] gets the weighted modifier to apply.
 */
@Composable
fun MonthGrid(
    month: YearMonth,
    modifier: Modifier = Modifier,
    day: @Composable (date: LocalDate, cellModifier: Modifier) -> Unit,
) {
    val leadingBlanks = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
    val weeks = (leadingBlanks + month.lengthOfMonth() + 6) / 7
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { dayOfWeek ->
                Text(
                    dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = IronTheme.type.label,
                    color = IronTheme.colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        repeat(weeks) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val dayNumber = week * 7 + column - leadingBlanks + 1
                    if (dayNumber in 1..month.lengthOfMonth()) {
                        day(month.atDay(dayNumber), Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

/**
 * One day of a [MonthGrid]: [fill] background, an `ink` ring on today, bold number when [emphasized].
 * Future days are not tappable. The whole cell is the touch target.
 */
@Composable
fun DayCell(
    date: LocalDate,
    fill: Color,
    contentColor: Color,
    isToday: Boolean,
    isFuture: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = isToday,
) {
    val colors = IronTheme.colors
    val shape = IronTheme.shapes.chip
    Box(
        modifier
            .aspectRatio(1f)
            .clickable(enabled = !isFuture, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(fill)
                .then(if (isToday) Modifier.border(1.5.dp, colors.ink, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = IronTheme.type.body.let { style -> if (emphasized) style.copy(fontWeight = FontWeight.Bold) else style },
                color = contentColor,
            )
        }
    }
}
