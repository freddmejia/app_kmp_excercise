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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import myweeklyroutinegymapp.shared.generated.resources.week_pick_date
import myweeklyroutinegymapp.shared.generated.resources.week_range_cross_month
import myweeklyroutinegymapp.shared.generated.resources.week_range_cross_year
import myweeklyroutinegymapp.shared.generated.resources.week_range_same_month
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
 * further than a week or two away is faster to pick by date than to step to.
 *
 * Shared by the log and activity screens. It takes plain values rather than either
 * screen's UI state so neither owns it, and both cannot drift into slightly different
 * week arithmetic.
 *
 * [markedDays] get a dot — the days something was logged.
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
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { onStepWeek(false) },
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) { Text("‹") }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    selectedDay.asFullDate(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Offered only when it would do something. A "Today" button while
                    // already on today is a control that cannot be pressed usefully.
                    if (selectedDay != today) {
                        TextButton(
                            onClick = onGoToToday,
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) { Text(stringResource(Res.string.week_today)) }
                    }
                    // Labelled, not bare. The calendar emoji has a date drawn into its
                    // artwork ("July 17" on Noto), which sitting directly under a live
                    // date reads as data rather than decoration.
                    TextButton(
                        onClick = { showDatePicker = true },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text(stringResource(Res.string.week_pick_date)) }
                }
            }

            OutlinedButton(
                onClick = { onStepWeek(true) },
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) { Text("›") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            visibleDays.forEach { day ->
                DayCell(
                    day = day,
                    isSelected = day == selectedDay,
                    isToday = day == today,
                    isMarked = day in markedDays,
                    onClick = { onSelectDay(day) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showDatePicker) {
        DayPickerDialog(
            initialDay = selectedDay,
            onDismiss = { showDatePicker = false },
            onPick = { day ->
                onSelectDay(day)
                showDatePicker = false
            },
        )
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
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDay * MILLIS_PER_DAY,
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { pickerState.selectedDateMillis?.let { onPick(it / MILLIS_PER_DAY) } },
                // The picker allows a cleared selection; confirming that would jump
                // to the epoch rather than doing nothing.
                enabled = pickerState.selectedDateMillis != null,
            ) { Text(stringResource(Res.string.common_go)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
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
