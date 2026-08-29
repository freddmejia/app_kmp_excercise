package botix.dev.myweeklyroutinegymapp.presentation.preview

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityCategory
import botix.dev.myweeklyroutinegymapp.domain.model.ActivityCategoryUsage
import botix.dev.myweeklyroutinegymapp.domain.model.CategoryVolume
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightChart
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightEntry
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightPoint
import botix.dev.myweeklyroutinegymapp.domain.model.BodyWeightSummary
import botix.dev.myweeklyroutinegymapp.domain.model.ChartMode
import botix.dev.myweeklyroutinegymapp.domain.model.MeasurementType
import botix.dev.myweeklyroutinegymapp.domain.model.Muscle
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleGroup
import botix.dev.myweeklyroutinegymapp.domain.model.MusclePickerItem
import botix.dev.myweeklyroutinegymapp.domain.model.MuscleSection
import botix.dev.myweeklyroutinegymapp.domain.model.SessionDetail
import botix.dev.myweeklyroutinegymapp.domain.model.SessionEntry
import botix.dev.myweeklyroutinegymapp.domain.model.SessionSummary
import botix.dev.myweeklyroutinegymapp.domain.model.SessionTotals
import botix.dev.myweeklyroutinegymapp.domain.model.UntrainedMuscle
import botix.dev.myweeklyroutinegymapp.domain.model.WeightTrend
import botix.dev.myweeklyroutinegymapp.domain.model.WorkoutSession
import botix.dev.myweeklyroutinegymapp.presentation.common.LocalSnackbarHostState
import botix.dev.myweeklyroutinegymapp.presentation.theme.GymTheme

/**
 * The sample data and the wrapper every `@Preview` in this module is built on.
 *
 * ### Why the fixtures are shared rather than inline
 * Almost every screen renders the same catalogue from a different angle — the log
 * picks activities, the editor ticks their muscles, the catalogue lists the groups
 * those muscles belong to. Built per file, "Bench press" would be muscle 2 in one
 * preview and muscle 7 in the next, and a preview that disagrees with the one beside
 * it about what the data *is* stops being a reference for anything. One set of ids
 * here means the previews compose into a coherent picture of one user's app.
 *
 * ### Why this lives in commonMain and not a test source set
 * `@Preview` is rendered by the IDE from the main source set; a preview in
 * `commonTest` is invisible to it. Everything here is therefore `internal` — the
 * `androidApp` and iOS binaries cannot reach it, and nothing outside the previews
 * references it, so it is dead code the compiler is free to drop.
 */

/** Epoch millis. Fixed, because a preview that moves is a preview you cannot compare. */
private const val PREVIEW_CREATED_AT = 1_756_000_000_000L

/** Wednesday, 26 August 2026 — mid-week, so the strip has days either side of it. */
internal const val PREVIEW_TODAY = 20_691L

/** Monday, 24 August 2026. The week is Monday-first; see `weekStartOf`. */
internal const val PREVIEW_WEEK_START = 20_689L

/** The seven days the strip draws around [PREVIEW_TODAY]. */
internal val previewWeek: List<Long> = (PREVIEW_WEEK_START..PREVIEW_WEEK_START + 6).toList()

// --- Wrapper ----------------------------------------------------------------

/**
 * Wraps a preview in the app's theme and the ambients its screens assume.
 *
 * [LocalSnackbarHostState] has no default — it throws, deliberately, so that a screen
 * raising a message outside the [androidx.compose.material3.Scaffold] fails loudly
 * rather than swallowing it. Every `…Content` that calls `MessageSnackbar` therefore
 * needs one provided here, or its preview renders as a stack trace.
 *
 * The scheme is left to `GymTheme`'s own default so that `@PreviewLightDark` — which
 * works by flipping the preview's ui mode, which is what `isSystemInDarkTheme()`
 * reads — actually produces two different pictures.
 */
@Composable
internal fun PreviewSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    GymTheme {
        CompositionLocalProvider(
            LocalSnackbarHostState provides remember { SnackbarHostState() },
        ) {
            Surface(
                modifier = modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
            ) {
                content()
            }
        }
    }
}

// --- Categories -------------------------------------------------------------

/**
 * The three the app ships, which is also one of each shape it can log: sets and
 * muscles, and distance and time twice over. A fixture holding only strength
 * categories would let every preview of the log flow agree with itself and with
 * nothing else.
 */
internal val previewCategories: List<ActivityCategory> = listOf(
    ActivityCategory(
        id = 1,
        name = "Gym",
        measurementType = MeasurementType.STRENGTH,
        createdAt = PREVIEW_CREATED_AT,
        seedKey = "gym",
    ),
    ActivityCategory(
        id = 2,
        name = "Bicycle",
        measurementType = MeasurementType.DISTANCE_TIME,
        createdAt = PREVIEW_CREATED_AT + 1,
        seedKey = "bicycle",
    ),
    // No seed key: the user's own, which is the row that can be renamed and deleted
    // without the ledger having an opinion.
    ActivityCategory(
        id = 3,
        name = "Mobility",
        measurementType = MeasurementType.STRENGTH,
        createdAt = PREVIEW_CREATED_AT + 2,
    ),
)

internal val previewCategoryUsage: List<ActivityCategoryUsage> = listOf(
    ActivityCategoryUsage(previewCategories[0], sessionCount = 6),
    ActivityCategoryUsage(previewCategories[1], sessionCount = 2),
    // Zero is the interesting row: it is the one that can be deleted without stripping
    // the label off anything, and the subtitle has to read sensibly at zero.
    ActivityCategoryUsage(previewCategories[2], sessionCount = 0),
)

/** Absent means "not trained that week" — there is no zero row. */
internal val previewCategoryVolume: Map<Long, CategoryVolume> = mapOf(
    1L to CategoryVolume(
        categoryId = 1,
        effectiveSets = 14,
        warmupSets = 3,
        distinctDays = 2,
        sessionCount = 3,
    ),
    2L to CategoryVolume(
        categoryId = 2,
        effectiveSets = 0,
        warmupSets = 0,
        distinctDays = 1,
        sessionCount = 1,
        distanceMeters = 8_400,
        durationSeconds = 2_640,
    ),
)

// --- Muscle catalogue -------------------------------------------------------

internal val previewMuscleGroups: List<MuscleGroup> = listOf(
    MuscleGroup(id = 1, name = "Chest", createdAt = PREVIEW_CREATED_AT),
    MuscleGroup(id = 2, name = "Back", createdAt = PREVIEW_CREATED_AT + 1),
    MuscleGroup(id = 3, name = "Legs", createdAt = PREVIEW_CREATED_AT + 2),
)

/** Every group ships a "General" leaf, so every fixture group has one too. */
internal val previewMusclesByGroup: Map<Long, List<Muscle>> = mapOf(
    1L to listOf(
        Muscle(id = 1, groupId = 1, name = "General", createdAt = PREVIEW_CREATED_AT),
        Muscle(id = 2, groupId = 1, name = "Upper chest", createdAt = PREVIEW_CREATED_AT + 1),
        Muscle(id = 3, groupId = 1, name = "Lower chest", createdAt = PREVIEW_CREATED_AT + 2),
    ),
    2L to listOf(
        Muscle(id = 4, groupId = 2, name = "General", createdAt = PREVIEW_CREATED_AT + 3),
        Muscle(id = 5, groupId = 2, name = "Lats", createdAt = PREVIEW_CREATED_AT + 4),
        // Archived: the catalogue draws it differently, and the picker leaves it out.
        Muscle(
            id = 6,
            groupId = 2,
            name = "Lower back",
            isArchived = true,
            createdAt = PREVIEW_CREATED_AT + 5,
        ),
    ),
    3L to listOf(
        Muscle(id = 7, groupId = 3, name = "General", createdAt = PREVIEW_CREATED_AT + 6),
        Muscle(id = 8, groupId = 3, name = "Quadriceps", createdAt = PREVIEW_CREATED_AT + 7),
        Muscle(id = 9, groupId = 3, name = "Hamstrings", createdAt = PREVIEW_CREATED_AT + 8),
    ),
)

/** The catalogue as the pickers see it: leaves only, each carrying its group's name. */
internal val previewMuscleSections: List<MuscleSection> = previewMuscleGroups.map { group ->
    MuscleSection(
        groupId = group.id,
        groupName = group.name,
        muscles = previewMusclesByGroup.getValue(group.id)
            .filter { !it.isArchived }
            .map { muscle ->
                MusclePickerItem(
                    muscleId = muscle.id,
                    muscleName = muscle.name,
                    groupId = group.id,
                    groupName = group.name,
                    // Varied, because the picker orders by it.
                    usageCount = muscle.id * 3,
                )
            },
    )
}

/** What an activity's own muscle list looks like: one group, not the whole catalogue. */
internal val previewScopedMuscleSections: List<MuscleSection> =
    previewMuscleSections.take(1)

internal val previewUntrainedMuscles: List<UntrainedMuscle> = listOf(
    UntrainedMuscle(muscleId = 5, muscleName = "Lats", groupName = "Back"),
    UntrainedMuscle(muscleId = 9, muscleName = "Hamstrings", groupName = "Legs"),
)

// --- Sessions ---------------------------------------------------------------

internal val previewSessionEntries: List<SessionEntry> = listOf(
    SessionEntry(
        id = 1,
        sessionId = 10,
        muscleId = 1,
        muscleName = "General",
        groupId = 1,
        groupName = "Chest",
        effectiveSets = 6,
        warmupSets = 2,
    ),
    SessionEntry(
        id = 2,
        sessionId = 10,
        muscleId = 2,
        muscleName = "Upper chest",
        groupId = 1,
        groupName = "Chest",
        effectiveSets = 8,
        warmupSets = 1,
    ),
)

internal val previewSessionDetail: SessionDetail = SessionDetail(
    session = WorkoutSession(
        id = 10,
        categoryId = 1,
        epochDay = PREVIEW_TODAY,
        createdAt = PREVIEW_CREATED_AT,
    ),
    entries = previewSessionEntries,
    totals = SessionTotals(
        effectiveSets = previewSessionEntries.sumOf { it.effectiveSets },
        warmupSets = previewSessionEntries.sumOf { it.warmupSets },
    ),
)

/** A session started and left: the card says so rather than showing a bare zero. */
internal val previewEmptySessionDetail: SessionDetail = SessionDetail(
    session = previewSessionDetail.session,
    entries = emptyList(),
    totals = SessionTotals(effectiveSets = 0, warmupSets = 0),
)

/**
 * Two Gym logs on one day — which is the point: a day holds as many logs of a kind as
 * were recorded, told apart by what is in them rather than by a name.
 */
internal val previewStrengthSession: SessionSummary = SessionSummary(
    sessionId = 10,
    categoryId = 1,
    categoryName = "Gym",
    measurementType = MeasurementType.STRENGTH,
    epochDay = PREVIEW_TODAY,
    notes = "Felt strong, added a set on incline.",
    effectiveSets = 14,
    warmupSets = 3,
    muscleCount = 2,
)

internal val previewDistanceSession: SessionSummary = SessionSummary(
    sessionId = 11,
    categoryId = 2,
    categoryName = "Bicycle",
    measurementType = MeasurementType.DISTANCE_TIME,
    epochDay = PREVIEW_TODAY,
    effectiveSets = 0,
    warmupSets = 0,
    muscleCount = 0,
    distanceMeters = 8_400,
    durationSeconds = 2_640,
)

/** The four shapes of card the day list can render. */
internal val previewDayLog: List<SessionSummary> = listOf(
    previewStrengthSession,
    previewDistanceSession,
    // Started and left. It has to keep its row: it was created deliberately, and a
    // hidden one could never be reopened or deleted.
    SessionSummary(
        sessionId = 12,
        categoryId = 1,
        categoryName = "Gym",
        measurementType = MeasurementType.STRENGTH,
        epochDay = PREVIEW_TODAY,
        effectiveSets = 0,
        warmupSets = 0,
        muscleCount = 0,
    ),
    // Its category was deleted after it was logged. The sets survive and the row
    // renders with a fallback name — the one case where categoryName is null.
    SessionSummary(
        sessionId = 13,
        categoryId = null,
        categoryName = null,
        measurementType = MeasurementType.STRENGTH,
        epochDay = PREVIEW_TODAY,
        effectiveSets = 6,
        warmupSets = 1,
        muscleCount = 1,
    ),
)

/**
 * What each of [previewDayLog]'s logs worked — the pills on the cards.
 *
 * Only the two strength logs with entries appear: a ride has no muscles, and one
 * started and left has none yet, and both of those render a line of their own rather
 * than an empty row of pills.
 */
internal val previewDayMuscles: Map<Long, List<String>> = mapOf(
    10L to listOf("Upper chest", "Triceps"),
    13L to listOf("Lats"),
)

/** Two of the seven visible days have something logged. */
internal val previewDaysWithSessions: Set<Long> = setOf(PREVIEW_WEEK_START, PREVIEW_TODAY)

// --- Body weight ------------------------------------------------------------

/** A fortnight of every-other-day readings, drifting down with real day-to-day noise. */
private val previewRawWeights = listOf(
    84.2, 84.0, 84.4, 83.6, 83.9, 83.1, 83.4, 82.8, 82.9, 82.3, 82.6, 81.9, 82.1, 81.4,
)

/**
 * Raw and smoothed as two genuinely different series.
 *
 * The screen draws the raw points faintly behind a moving average, so a fixture whose
 * two series are the same list would make the chart look right while proving nothing —
 * the smoothing is computed here for the same reason the real one is computed at all.
 */
internal val previewChart: BodyWeightChart = run {
    val raw = previewRawWeights.mapIndexed { index, kg ->
        BodyWeightPoint(
            epochDay = PREVIEW_TODAY - (previewRawWeights.lastIndex - index) * 2L,
            weightKg = kg,
        )
    }
    val smoothed = raw.mapIndexed { index, point ->
        val window = raw.subList(maxOf(0, index - 2), index + 1)
        point.copy(weightKg = window.sumOf { it.weightKg } / window.size)
    }
    BodyWeightChart(
        raw = raw,
        smoothed = smoothed,
        summary = BodyWeightSummary(
            currentKg = raw.last().weightKg,
            changeKg = raw.last().weightKg - raw.first().weightKg,
            changePercent = (raw.last().weightKg - raw.first().weightKg) / raw.first().weightKg * 100,
            trend = WeightTrend.FALLING,
            ratePerWeekKg = -0.7,
            minKg = previewRawWeights.min(),
            maxKg = previewRawWeights.max(),
            sampleCount = raw.size,
        ),
        mode = ChartMode.DAILY,
    )
}

/** Newest first, which is the order the history list renders. */
internal val previewWeightHistory: List<BodyWeightEntry> = listOf(
    BodyWeightEntry(id = 5, epochDay = PREVIEW_TODAY, weightKg = 81.4, note = "Before breakfast"),
    BodyWeightEntry(id = 4, epochDay = PREVIEW_TODAY - 2, weightKg = 82.1),
    BodyWeightEntry(id = 3, epochDay = PREVIEW_TODAY - 4, weightKg = 81.9, note = "Post-travel"),
    BodyWeightEntry(id = 2, epochDay = PREVIEW_TODAY - 6, weightKg = 82.6),
    BodyWeightEntry(id = 1, epochDay = PREVIEW_TODAY - 8, weightKg = 82.3),
)

internal val previewWeekWeights: Map<Long, BodyWeightEntry> =
    previewWeightHistory.filter { it.epochDay in previewWeek }.associateBy { it.epochDay }
