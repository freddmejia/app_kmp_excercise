package botix.dev.myweeklyroutinegymapp.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import botix.dev.myweeklyroutinegymapp.presentation.preview.PREVIEW_TODAY
import botix.dev.myweeklyroutinegymapp.presentation.preview.PREVIEW_WEEK_START
import botix.dev.myweeklyroutinegymapp.presentation.preview.PreviewSurface
import botix.dev.myweeklyroutinegymapp.presentation.preview.previewDaysWithSessions
import botix.dev.myweeklyroutinegymapp.presentation.preview.previewWeek
import kotlinx.datetime.LocalDate
import myweeklyroutinegymapp.shared.generated.resources.Res
import myweeklyroutinegymapp.shared.generated.resources.common_cancel
import myweeklyroutinegymapp.shared.generated.resources.common_go
import myweeklyroutinegymapp.shared.generated.resources.date_full
import myweeklyroutinegymapp.shared.generated.resources.day_fri
import myweeklyroutinegymapp.shared.generated.resources.day_mon
import myweeklyroutinegymapp.shared.generated.resources.day_sat
import myweeklyroutinegymapp.shared.generated.resources.day_sun
import myweeklyroutinegymapp.shared.generated.resources.day_thu
import myweeklyroutinegymapp.shared.generated.resources.day_tue
import myweeklyroutinegymapp.shared.generated.resources.day_wed
import myweeklyroutinegymapp.shared.generated.resources.month_apr
import myweeklyroutinegymapp.shared.generated.resources.month_aug
import myweeklyroutinegymapp.shared.generated.resources.month_dec
import myweeklyroutinegymapp.shared.generated.resources.month_feb
import myweeklyroutinegymapp.shared.generated.resources.month_jan
import myweeklyroutinegymapp.shared.generated.resources.month_jul
import myweeklyroutinegymapp.shared.generated.resources.month_jun
import myweeklyroutinegymapp.shared.generated.resources.month_mar
import myweeklyroutinegymapp.shared.generated.resources.month_may
import myweeklyroutinegymapp.shared.generated.resources.month_nov
import myweeklyroutinegymapp.shared.generated.resources.month_oct
import myweeklyroutinegymapp.shared.generated.resources.month_sep
import myweeklyroutinegymapp.shared.generated.resources.week_range_cross_month
import myweeklyroutinegymapp.shared.generated.resources.week_range_cross_year
import myweeklyroutinegymapp.shared.generated.resources.week_range_same_month
import myweeklyroutinegymapp.shared.generated.resources.week_select_date
import myweeklyroutinegymapp.shared.generated.resources.week_today
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** One tap of the week arrows, and the width of the strip. */
const val WEEK: Long = 7L

/**
 * The Monday that opens [day]'s week.
 *
 * Epoch day 0 was a Thursday, so adding 3 rotates the cycle to put Monday at zero.
 * The remainder is floored rather than truncated because Kotlin's `%` keeps the sign
 * of the dividend, which would throw pre-1970 dates into the following week.
 *
 * Monday-first is a choice, not a law — some locales start on Sunday. It is one
 * constant here if that ever needs to follow the device.
 */
fun weekStartOf(day: Long): Long = day - (((day + 3) % WEEK) + WEEK) % WEEK

/** The seven days of [day]'s week, Monday first. */
fun weekOf(day: Long): List<Long> = weekStartOf(day).let { start -> (start..start + 6).toList() }

/**
 * The week selector: one calendar week, Monday to Sunday.
 *
 * Moving between weeks is the arrows' job alone. Tapping a day only chooses within
 * the week on screen, so the row never shifts under the finger that is aiming at it —
 * the cell you meant to press is still the cell you press.
 *
 * The arrows are therefore the short hop, and the calendar the long one: anything
 * further than a week or two away is faster to pick by date than to step to. They sit
 * on the strip's own row, flanking the cells they move.
 *
 * The date above names [selectedDay] in full, because the cells themselves show no
 * month or year. Tapping it returns to [today]; the caret beside it opens the calendar.
 *
 * Shared by the log and activity screens. It takes plain values rather than either
 * screen's UI state so neither owns it, and both cannot drift into slightly different
 * week arithmetic.
 *
 * [markedDays] get a dot — the days something was logged.
 *
 * [trailing] fills the far end of the date row, opposite the date itself, centred
 * against it. It is where a screen puts its one creating control, and all three screens
 * with a strip now use it: Log puts New log there, Activities puts New, Weight puts
 * Log/Replace. Level with the date on every tab, so switching tabs does not move the
 * button — a control that lands somewhere different on each screen has to be found
 * again each time.
 *
 * For two of the three the date is also the action's *subject*: the log and the reading
 * are written against the selected day. A new activity is not — it belongs to the
 * catalogue — and it sits here for the alignment alone.
 *
 * The slot is still empty by default, and a strip with nothing in it renders exactly as
 * it did before the slot existed.
 */
@Composable
fun WeekStrip(
    selectedDay: Long,
    today: Long,
    visibleDays: List<Long>,
    markedDays: Set<Long>,
    onSelectDay: (Long) -> Unit,
    onStepWeek: (Boolean) -> Unit,
    onGoToToday: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The selected day, spelled out. The cells carry only a weekday and a number,
        // so this is the only thing on screen naming the month and year — browse two
        // weeks on and "Lun 7" is otherwise unplaceable. [today] keeps its own mark
        // in the strip (the outlined cell), which is what says where "now" is.
        Row(
            modifier = Modifier.fillMaxWidth(),
            // The date at one end of the row, the day's action at the other.
            // SpaceBetween rather than a spacer, so a strip with no trailing content
            // leaves the date exactly where it has always been.
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The date and its caret travel together: they are two targets on one
            // subject, and a gap opening between them would read as two controls.
            //
            // Weighted, and `fill = false`, so it takes its natural width while there
            // is room and gives way when there is not. A Row measures its unweighted
            // children first and hands each of them everything still unclaimed, so
            // without this the date — first in the row, and as long as the locale
            // makes it — would be measured against the full width and [trailing] would
            // get whatever survived. At a large font scale that is nothing: the button
            // would be measured to zero and vanish from the tree, no pixels and no tap
            // target, which is exactly how the New-log button disappeared out of the
            // old header Row. `fill = false` keeps today's layout unchanged; the date
            // now wraps under pressure instead of starving the control beside it.
            Row(
                modifier = Modifier.weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selectedDay.asFullDate(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    // Reads as the selection, acts as "go home": tapping it returns to
                    // today. Always enabled — pressing it while already on today is a
                    // no-op, which is quieter than a button that appears and disappears
                    // under the finger as the selection moves.
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(
                            onClickLabel = stringResource(Res.string.week_today),
                            role = Role.Button,
                            onClick = onGoToToday,
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
                // A separate target, not part of the date: one tap goes to today, the
                // other opens the calendar, and they must not be reachable by the same
                // press. The caret is the affordance the emoji used to carry, minus the
                // artwork — the calendar glyph has a date drawn into it, which sitting
                // beside a live date read as data.
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(
                            onClickLabel = stringResource(Res.string.week_select_date),
                            role = Role.Button,
                            onClick = { showDatePicker = true },
                        )
                        .size(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("\u25BE", style = MaterialTheme.typography.titleSmall)
                }
            }

            trailing()
        }

        // The arrows sit on the row they act on. They step the week the cells show,
        // so reading them above the strip made them look like they belonged to the
        // date instead — which is the one thing they do not move.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeekArrow(glyph = "\u2039", onClick = { onStepWeek(false) })

            // Today and behind, never ahead.
            //
            // A training log records what happened. A cell for next Thursday invites a
            // session on a day that has not occurred, and every frequency, streak and
            // "untrained this week" figure then counts a workout nobody did. The week
            // is still the unit — this trims the tail of the current one and leaves a
            // past week whole, which is why it is a filter here rather than a change
            // to weekOf().
            //
            // The cells that remain keep weight(1f), so a Saturday's six cells are
            // wider than a past week's seven rather than leaving a gap where Sunday
            // would have been. The row is a row of days, not a fixed grid.
            visibleDays.filter { it <= today }.forEach { day ->
                DayCell(
                    day = day,
                    isSelected = day == selectedDay,
                    isToday = day == today,
                    isMarked = day in markedDays,
                    onClick = { onSelectDay(day) },
                    modifier = Modifier.weight(1f),
                )
            }

            // Disabled on the week holding today: stepping forward from it would land
            // on a week with nothing left to show, and an arrow that empties the strip
            // is worse than one that does not move.
            WeekArrow(
                glyph = "\u203A",
                onClick = { onStepWeek(true) },
                enabled = visibleDays.none { it >= today },
            )
        }
    }

    if (showDatePicker) {
        DayPickerDialog(
            initialDay = selectedDay,
            today = today,
            onDismiss = { showDatePicker = false },
            onPick = { day ->
                onSelectDay(day)
                showDatePicker = false
            },
        )
    }
}

/**
 * One week back or forward.
 *
 * Sized rather than left to [OutlinedButton]'s own minimum, which is wide enough that
 * two of them plus seven cells stop fitting a phone in portrait. The M3 touch target
 * still measures 48.dp around it, so the circle is small without the tap being small.
 */
@Composable
private fun WeekArrow(
    glyph: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(glyph, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun DayCell(
    day: Long,
    isSelected: Boolean,
    isToday: Boolean,
    isMarked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        // Today keeps an outline even when it is not the selection, so the strip
        // still says where "now" is after you have browsed away from it.
        border = if (isToday && !isSelected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                day.asWeekdayLabel(),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                day.asDayOfMonth(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
            // A dot rather than a count: the strip answers "did I train" at a glance,
            // and the detail is one tap away.
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (isMarked) LocalContentColor.current else Color.Transparent),
            )
        }
    }
}

/** Milliseconds in a day, for the date picker's UTC-based contract. */
private const val MILLIS_PER_DAY = 86_400_000L

/**
 * The year [today] falls in, for the picker's year list.
 *
 * Goes through the same `LocalDate` conversion every formatter here uses rather than
 * dividing by 365 — the approximation is wrong on roughly one day in four.
 */
private fun todayYear(today: Long): Int = LocalDate.fromEpochDays(today.toInt()).year

/**
 * Jumping to an arbitrary year, month and day.
 *
 * [rememberDatePickerState] speaks UTC milliseconds at the start of a day, and an
 * epoch day is a plain count of days, so the conversion is a straight multiply. That
 * looks like a time-zone bug and is not: nothing here is an instant. Routing it
 * through the device zone is what *would* break it, shifting the picked date by a day
 * for anyone west of UTC.
 */
// Experimental on the Kotlin/Native side of material3 even though the Android
// artifact has stabilised it, so the opt-in is required for the iOS target to build.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPickerDialog(
    initialDay: Long,
    today: Long,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDay * MILLIS_PER_DAY,
        // The strip stops at today, and this must stop there too — otherwise the
        // calendar is a way round the rule, landing the user on a day the strip
        // then refuses to draw.
        //
        // Both halves are needed: the day grid greys out future dates, and the year
        // list stops at this year, because picking 2031 from the year list would
        // otherwise move the grid somewhere every cell is disabled.
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis / MILLIS_PER_DAY <= today

            override fun isSelectableYear(year: Int): Boolean = year <= todayYear(today)
        },
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        // Both buttons go in the confirm slot, as one full-width row.
        //
        // Material puts them together at the end, which reads as a pair of equals and
        // makes the destructive-ish choice (leaving) the neighbour of the one you
        // usually want. Pushed apart, each end of the row means something: leave on
        // the left, commit on the right, and the thumb cannot hit one while reaching
        // for the other. `dismissButton` is left null because filling it would add a
        // third button after this row.
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
                TextButton(
                    onClick = { pickerState.selectedDateMillis?.let { onPick(it / MILLIS_PER_DAY) } },
                    // The picker allows a cleared selection; confirming that would jump
                    // to the epoch rather than doing nothing.
                    enabled = pickerState.selectedDateMillis != null,
                ) { Text(stringResource(Res.string.common_go)) }
            }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

/**
 * Epoch day -> text, in the presentation layer on purpose.
 *
 * The domain deals in epoch days and keeps no dependency on a date library
 * (see `SystemClock`), so formatting belongs here alongside every other piece of
 * user-facing wording.
 *
 * These are `@Composable` because the names are translated: weekday and month come
 * from string resources, and the order of the parts is itself a resource, so a locale
 * can put the month before the day without touching this code.
 */
private fun Long.toLocalDate(): LocalDate = LocalDate.fromEpochDays(this)

fun Long.asDayOfMonth(): String = toLocalDate().day.toString()

/**
 * Indexed off the enum's ordinal rather than its name.
 *
 * `dayOfWeek.name` is the English constant — taking three letters of it produced
 * "Mon"/"Tue" by accident of the enum being written in English, which no other
 * language would survive.
 */
private fun weekdayRes(date: LocalDate): StringResource = when (date.dayOfWeek.ordinal) {
    0 -> Res.string.day_mon
    1 -> Res.string.day_tue
    2 -> Res.string.day_wed
    3 -> Res.string.day_thu
    4 -> Res.string.day_fri
    5 -> Res.string.day_sat
    else -> Res.string.day_sun
}

private fun monthRes(date: LocalDate): StringResource = when (date.month.ordinal) {
    0 -> Res.string.month_jan
    1 -> Res.string.month_feb
    2 -> Res.string.month_mar
    3 -> Res.string.month_apr
    4 -> Res.string.month_may
    5 -> Res.string.month_jun
    6 -> Res.string.month_jul
    7 -> Res.string.month_aug
    8 -> Res.string.month_sep
    9 -> Res.string.month_oct
    10 -> Res.string.month_nov
    else -> Res.string.month_dec
}

@Composable
fun Long.asWeekdayLabel(): String = stringResource(weekdayRes(toLocalDate()))

@Composable
fun Long.asFullDate(): String {
    val date = toLocalDate()
    return stringResource(
        Res.string.date_full,
        stringResource(weekdayRes(date)),
        date.day.toString(),
        stringResource(monthRes(date)),
        date.year.toString(),
    )
}

/** "17–23 Aug 2026", for a header that names the whole week rather than one day. */
@Composable
fun weekLabelOf(day: Long): String {
    val start = weekStartOf(day)
    val startDate = start.toLocalDate()
    val endDate = (start + 6).toLocalDate()
    val startMonth = stringResource(monthRes(startDate))
    val endMonth = stringResource(monthRes(endDate))
    // The month is repeated only when the week straddles two, and the year only when
    // it straddles two of those — the common case stays short.
    return when {
        startDate.year != endDate.year -> stringResource(
            Res.string.week_range_cross_year,
            startDate.day.toString(), startMonth, startDate.year.toString(),
            endDate.day.toString(), endMonth, endDate.year.toString(),
        )

        startDate.month != endDate.month -> stringResource(
            Res.string.week_range_cross_month,
            startDate.day.toString(), startMonth,
            endDate.day.toString(), endMonth, endDate.year.toString(),
        )

        else -> stringResource(
            Res.string.week_range_same_month,
            startDate.day.toString(), endDate.day.toString(), startMonth, endDate.year.toString(),
        )
    }
}

// --- Previews ---------------------------------------------------------------

/**
 * The strip as the log and weight screens use it: no trailing content, so the date row
 * looks exactly as it did before that slot existed.
 *
 * [PREVIEW_TODAY] is a Wednesday, so this is also the shape the current week always
 * takes — three cells, not seven, and the forward arrow disabled. Thursday onward is
 * not drawn: a training log records what happened, and a cell for Saturday invites a
 * session on a day that has not occurred.
 */
@PreviewLightDark
@Composable
private fun WeekStripPreview() {
    PreviewSurface {
        WeekStrip(
            selectedDay = PREVIEW_TODAY,
            today = PREVIEW_TODAY,
            visibleDays = previewWeek,
            markedDays = previewDaysWithSessions,
            onSelectDay = {},
            onStepWeek = {},
            onGoToToday = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

/**
 * A past week, whole.
 *
 * The current week is trimmed at today, so seven cells only ever appear once the week
 * is behind you — and this is the preview that proves the trim is a filter on the tail
 * rather than a smaller strip. The forward arrow is live here and disabled in
 * [WeekStripPreview], which is the other half of the same rule.
 */
@Preview
@Composable
private fun WeekStripPastWeekPreview() {
    PreviewSurface {
        WeekStrip(
            selectedDay = PREVIEW_WEEK_START - 7,
            today = PREVIEW_TODAY,
            visibleDays = (PREVIEW_WEEK_START - 7..PREVIEW_WEEK_START - 1).toList(),
            markedDays = previewDaysWithSessions,
            onSelectDay = {},
            onStepWeek = {},
            onGoToToday = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

/**
 * Browsed away from today, which is the state the outlined cell exists for: the
 * selection has moved to Friday and today is still findable behind its border.
 */
@Preview
@Composable
private fun WeekStripBrowsedPreview() {
    PreviewSurface {
        WeekStrip(
            selectedDay = PREVIEW_WEEK_START + 4,
            today = PREVIEW_TODAY,
            visibleDays = previewWeek,
            markedDays = previewDaysWithSessions,
            onSelectDay = {},
            onStepWeek = {},
            onGoToToday = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** With the [trailing] slot filled, the way all three day-strip screens fill it. */
@Preview
@Composable
private fun WeekStripWithTrailingPreview() {
    PreviewSurface {
        WeekStrip(
            selectedDay = PREVIEW_TODAY,
            today = PREVIEW_TODAY,
            visibleDays = previewWeek,
            markedDays = previewDaysWithSessions,
            onSelectDay = {},
            onStepWeek = {},
            onGoToToday = {},
            modifier = Modifier.padding(16.dp),
            trailing = { Button(onClick = {}) { Text("New log") } },
        )
    }
}

/**
 * The squeeze this row has to survive: the longest button label in the app, a narrow
 * screen and a large font scale, all at once.
 *
 * **What to look for is that the button is still there.** An unweighted date would be
 * measured against the full width first and leave the button nothing, and a `Row` does
 * not clip a child it cannot fit — it measures it to zero and the control leaves the
 * tree entirely. The date wrapping to two lines here is the trade working.
 */
@Preview(name = "Narrow", widthDp = 320)
@Preview(name = "Narrow + 200% font", widthDp = 320, fontScale = 2f)
@Composable
private fun WeekStripSqueezedPreview() {
    PreviewSurface {
        WeekStrip(
            selectedDay = PREVIEW_TODAY,
            today = PREVIEW_TODAY,
            visibleDays = previewWeek,
            markedDays = previewDaysWithSessions,
            onSelectDay = {},
            onStepWeek = {},
            onGoToToday = {},
            modifier = Modifier.padding(16.dp),
            trailing = { Button(onClick = {}) { Text("Nouvelle séance") } },
        )
    }
}

@Preview
@Composable
private fun WeekArrowPreview() {
    PreviewSurface {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WeekArrow(glyph = "‹", onClick = {})
            WeekArrow(glyph = "›", onClick = {})
        }
    }
}

/**
 * All four cell states at once, which is the only way to check they stay distinct:
 * selected, today-but-not-selected, marked, and plain. Selected-and-today is the
 * fifth — the border drops away there, deliberately, because the fill already says it.
 */
@PreviewLightDark
@Composable
private fun DayCellPreview() {
    PreviewSurface {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DayCell(
                day = PREVIEW_WEEK_START,
                isSelected = true,
                isToday = false,
                isMarked = true,
                onClick = {},
                modifier = Modifier.weight(1f),
            )
            DayCell(
                day = PREVIEW_WEEK_START + 1,
                isSelected = false,
                isToday = true,
                isMarked = false,
                onClick = {},
                modifier = Modifier.weight(1f),
            )
            DayCell(
                day = PREVIEW_WEEK_START + 2,
                isSelected = false,
                isToday = false,
                isMarked = true,
                onClick = {},
                modifier = Modifier.weight(1f),
            )
            DayCell(
                day = PREVIEW_WEEK_START + 3,
                isSelected = false,
                isToday = false,
                isMarked = false,
                onClick = {},
                modifier = Modifier.weight(1f),
            )
            DayCell(
                day = PREVIEW_TODAY,
                isSelected = true,
                isToday = true,
                isMarked = true,
                onClick = {},
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview(heightDp = 640)
@Composable
private fun DayPickerDialogPreview() {
    PreviewSurface {
        DayPickerDialog(initialDay = PREVIEW_TODAY, today = PREVIEW_TODAY, onDismiss = {}, onPick = {})
    }
}

/**
 * The date formatters, and specifically [weekLabelOf]'s three branches — the month is
 * repeated only when the week straddles two and the year only when it straddles two of
 * those, so the three have to be read together to see that the short case stays short.
 *
 * They are `@Composable` because every part of them is translated: switching this
 * preview's locale is the fastest check that a translation still reads as a date.
 */
@Preview(name = "en")
@Preview(name = "es", locale = "es")
@Composable
private fun DateLabelsPreview() {
    // Monday-start weeks: within August, across August into September, across the year.
    val sameMonth = PREVIEW_WEEK_START
    val crossMonth = 20_696L
    val crossYear = 20_815L
    PreviewSurface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(PREVIEW_TODAY.asFullDate(), style = MaterialTheme.typography.titleSmall)
            // A Row rather than a joined string: asWeekdayLabel is @Composable, so it
            // cannot be called from inside joinToString's plain lambda.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                previewWeek.forEach { day ->
                    Text(
                        day.asWeekdayLabel() + " " + day.asDayOfMonth(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text(weekLabelOf(sameMonth), style = MaterialTheme.typography.bodyMedium)
            Text(weekLabelOf(crossMonth), style = MaterialTheme.typography.bodyMedium)
            Text(weekLabelOf(crossYear), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
