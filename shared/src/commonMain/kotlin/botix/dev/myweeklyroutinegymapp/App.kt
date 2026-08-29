package botix.dev.myweeklyroutinegymapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import botix.dev.myweeklyroutinegymapp.domain.usecase.workout.SyncCatalogSeedsUseCase
import botix.dev.myweeklyroutinegymapp.presentation.activity.ActivityCategoryScreen
import botix.dev.myweeklyroutinegymapp.presentation.activity.CategoryEditorScreen
import botix.dev.myweeklyroutinegymapp.presentation.bodyweight.BodyWeightEntryScreen
import botix.dev.myweeklyroutinegymapp.presentation.bodyweight.BodyWeightScreen
import botix.dev.myweeklyroutinegymapp.presentation.common.LocalSnackbarHostState
import botix.dev.myweeklyroutinegymapp.presentation.preferences.PreferencesScreen
import botix.dev.myweeklyroutinegymapp.presentation.theme.GymIcons
import botix.dev.myweeklyroutinegymapp.presentation.theme.GymTheme
import botix.dev.myweeklyroutinegymapp.presentation.workout.MuscleCatalogScreen
import botix.dev.myweeklyroutinegymapp.presentation.workout.MuscleEditorScreen
import botix.dev.myweeklyroutinegymapp.presentation.workout.WorkoutEntryScreen
import botix.dev.myweeklyroutinegymapp.presentation.workout.WorkoutLogScreen
import myweeklyroutinegymapp.shared.generated.resources.Res
import myweeklyroutinegymapp.shared.generated.resources.categories_title
import myweeklyroutinegymapp.shared.generated.resources.category_edit_title
import myweeklyroutinegymapp.shared.generated.resources.category_new_title
import myweeklyroutinegymapp.shared.generated.resources.common_back
import myweeklyroutinegymapp.shared.generated.resources.log_edit
import myweeklyroutinegymapp.shared.generated.resources.log_new
import myweeklyroutinegymapp.shared.generated.resources.log_title
import myweeklyroutinegymapp.shared.generated.resources.muscle_edit
import myweeklyroutinegymapp.shared.generated.resources.muscle_edit_group
import myweeklyroutinegymapp.shared.generated.resources.muscle_new
import myweeklyroutinegymapp.shared.generated.resources.muscle_new_group
import myweeklyroutinegymapp.shared.generated.resources.muscle_seed_general
import myweeklyroutinegymapp.shared.generated.resources.muscles_title
import myweeklyroutinegymapp.shared.generated.resources.preferences_title
import myweeklyroutinegymapp.shared.generated.resources.tab_activities
import myweeklyroutinegymapp.shared.generated.resources.tab_log
import myweeklyroutinegymapp.shared.generated.resources.tab_preferences
import myweeklyroutinegymapp.shared.generated.resources.tab_weight
import myweeklyroutinegymapp.shared.generated.resources.weight_entry_edit
import myweeklyroutinegymapp.shared.generated.resources.weight_entry_new
import myweeklyroutinegymapp.shared.generated.resources.weight_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The three top-level tabs.
 *
 * A tab is not a screen: it is a *section*, and each one owns a small stack of
 * screens (see [Screen]). The icon comes from the app's own set rather than a font emoji, so it
 * takes the navigation bar's tint like any other vector.
 */
private enum class Destination(val label: StringResource, val icon: ImageVector) {
    /**
     * The log. It is the app.
     *
     * There was an Activities tab beside it, holding a catalogue of named things you
     * created before you could log them. It went in two steps: first the tab was
     * absorbed here, then the catalogue itself was removed, because a session already
     * carries everything the names were standing in for — the day, the kind of
     * training, and the muscles worked with their sets.
     *
     * What is left to configure lives in Preferences: the categories that say what
     * kind of training exists, and the muscles that can be logged against.
     */
    WORKOUT(Res.string.tab_log, GymIcons.Dumbbell),
    WEIGHT(Res.string.tab_weight, GymIcons.Scale),

    /**
     * The catalogues, and anything else that shapes the app rather than records what
     * was done in it. Last, because it is the tab you visit least — the order of a
     * navigation bar is a claim about how often each section is wanted.
     */
    PREFERENCES(Res.string.tab_preferences, GymIcons.Sliders),
}

/**
 * Every screen the app can show, which tab it belongs to, and what the app bar calls
 * it while it is up.
 *
 * Still no navigation library. These routes nest exactly one level deep and have no
 * deep links, and the one argument any of them takes is a single `Long`, so a nav
 * graph would be a dependency and a URL grammar bought to model a list. What matters
 * is that this list is the *single* source of truth for where the app is: the
 * catalogue screens used to be booleans held beside a selected-tab index, which is two
 * pieces of state that can disagree — the bar could say "Activities" while the muscle
 * catalogue was on screen. The bar now reads its selection off the back stack, so it
 * cannot drift from what is actually rendered.
 *
 * A sealed interface rather than the enum it was, because [BodyWeightEntry] carries
 * the day it is editing. That is also what lets the bar name a screen without asking
 * it: a route that knows whether it opened onto an existing reading knows whether the
 * title should say new or edit.
 *
 * Introduce a real nav graph when a route gains a deep link, or when the arguments
 * outgrow what [ScreenStackSaver] can flatten.
 */
private sealed interface Screen {
    val destination: Destination
    val title: StringResource

    data object WorkoutLog : Screen {
        override val destination get() = Destination.WORKOUT
        override val title get() = Res.string.log_title
    }

    /**
     * The muscle catalogue.
     *
     * It sat under the Log tab, reached by a button in that screen's header, which put
     * a piece of configuration inside the screen it configures. It is settings, and it
     * lives with the other catalogue now.
     */
    data object MuscleCatalog : Screen {
        override val destination get() = Destination.PREFERENCES
        override val title get() = Res.string.muscles_title
    }

    /**
     * One name: a muscle group or a leaf inside one, new or renamed.
     *
     * Four cases and one form. [isGroup] says which half of the catalogue is being
     * written; a zero id is something that does not exist yet, which ids from
     * `AUTOINCREMENT` can never be. [groupId] is still carried when editing a leaf,
     * because the form names the group the leaf belongs to.
     */
    data class MuscleEditor(
        val groupId: Long,
        val muscleId: Long,
        val isGroup: Boolean,
    ) : Screen {
        override val destination get() = Destination.PREFERENCES
        override val title
            get() = when {
                isGroup && groupId == 0L -> Res.string.muscle_new_group
                isGroup -> Res.string.muscle_edit_group
                muscleId == 0L -> Res.string.muscle_new
                else -> Res.string.muscle_edit
            }
    }

    /** The other catalogue, and for the same reason under the same tab. */
    data object ActivityCategories : Screen {
        override val destination get() = Destination.PREFERENCES
        override val title get() = Res.string.categories_title
    }

    /** One category, new or edited. Zero is the id of one that does not exist yet. */
    data class CategoryEditor(val categoryId: Long) : Screen {
        override val destination get() = Destination.PREFERENCES
        override val title
            get() = if (categoryId == 0L) {
                Res.string.category_new_title
            } else {
                Res.string.category_edit_title
            }
    }

    /** The section itself: a signpost to the two catalogues, and nothing else yet. */
    data object Preferences : Screen {
        override val destination get() = Destination.PREFERENCES
        override val title get() = Res.string.preferences_title
    }

    /**
     * One log, being written or filled in further.
     *
     * The **session id** is the argument. It used to be (activity, day), because a
     * session was found-or-created by that pair; now the row is created before this
     * route is pushed — either by the New-log flow or by tapping an existing row — and
     * a day may hold several logs of the same kind, so only the id identifies one.
     *
     * [existing] is what the caller already knew: whether this log was tapped in the
     * day's list, or made a second ago. See [BodyWeightEntry] for why it is passed
     * rather than read back out of the database.
     */
    data class LogEntry(
        val sessionId: Long,
        val epochDay: Long,
        val existing: Boolean,
    ) : Screen {
        override val destination get() = Destination.WORKOUT
        override val title get() = if (existing) Res.string.log_edit else Res.string.log_new
    }

    data object BodyWeight : Screen {
        override val destination get() = Destination.WEIGHT
        override val title get() = Res.string.weight_title
    }

    /**
     * One day's reading, being written or corrected.
     *
     * The day is the argument because a day holds at most one reading — see
     * `BodyWeightEntryViewModel`. [existing] is not derived from it: finding out
     * whether the day already has a reading is a database read, and the title would
     * flip from "New" to "Edit" a frame after the screen opened. The caller already
     * knows — it was looking at that very row, or at the strip's own week — so it
     * says.
     */
    data class BodyWeightEntry(val epochDay: Long, val existing: Boolean) : Screen {
        override val destination get() = Destination.WEIGHT
        override val title
            get() = if (existing) Res.string.weight_entry_edit else Res.string.weight_entry_new
    }
}

/** Where a tab lands when it is selected from the bar. */
private val Destination.startScreen: Screen
    get() = when (this) {
        Destination.WORKOUT -> Screen.WorkoutLog
        Destination.WEIGHT -> Screen.BodyWeight
        Destination.PREFERENCES -> Screen.Preferences
    }

/**
 * Survives process death as a flat list of `Long`s, four per screen: a tag, and three
 * slots of argument.
 *
 * Compose Multiplatform has no built-in `Saver` for either an enum or a sealed type —
 * storing the objects themselves restores as null and drops the user back on the log
 * screen after the system kills the app in the background. Fixed-width slots rather
 * than a variable-length encoding, because the alternative is a parser: two routes
 * carry arguments now and they already disagree about how many. A third shape, or an
 * argument that is not a number, is the moment to reach for a real navigation library
 * instead of widening this again.
 */
private const val TAG_WORKOUT_LOG = 0L
private const val TAG_MUSCLE_CATALOG = 1L
private const val TAG_ACTIVITY_CATEGORIES = 3L
private const val TAG_BODY_WEIGHT = 4L
private const val TAG_BODY_WEIGHT_ENTRY = 5L
private const val TAG_LOG_ENTRY = 6L
private const val TAG_PREFERENCES = 7L
private const val TAG_MUSCLE_EDITOR = 8L
private const val TAG_CATEGORY_EDITOR = 9L

private fun Screen.saved(): List<Long> = when (this) {
    Screen.WorkoutLog -> listOf(TAG_WORKOUT_LOG, 0L, 0L, 0L)
    Screen.MuscleCatalog -> listOf(TAG_MUSCLE_CATALOG, 0L, 0L, 0L)
    Screen.ActivityCategories -> listOf(TAG_ACTIVITY_CATEGORIES, 0L, 0L, 0L)
    Screen.BodyWeight -> listOf(TAG_BODY_WEIGHT, 0L, 0L, 0L)
    is Screen.BodyWeightEntry ->
        listOf(TAG_BODY_WEIGHT_ENTRY, epochDay, if (existing) 1L else 0L, 0L)
    is Screen.LogEntry ->
        listOf(TAG_LOG_ENTRY, sessionId, epochDay, if (existing) 1L else 0L)
    Screen.Preferences -> listOf(TAG_PREFERENCES, 0L, 0L, 0L)
    is Screen.MuscleEditor ->
        listOf(TAG_MUSCLE_EDITOR, groupId, muscleId, if (isGroup) 1L else 0L)
    is Screen.CategoryEditor -> listOf(TAG_CATEGORY_EDITOR, categoryId, 0L, 0L)
}

private fun screenOf(tag: Long, arg0: Long, arg1: Long, arg2: Long): Screen = when (tag) {
    TAG_MUSCLE_CATALOG -> Screen.MuscleCatalog
    TAG_ACTIVITY_CATEGORIES -> Screen.ActivityCategories
    TAG_BODY_WEIGHT -> Screen.BodyWeight
    TAG_BODY_WEIGHT_ENTRY -> Screen.BodyWeightEntry(epochDay = arg0, existing = arg1 == 1L)
    TAG_LOG_ENTRY ->
        Screen.LogEntry(sessionId = arg0, epochDay = arg1, existing = arg2 == 1L)
    TAG_PREFERENCES -> Screen.Preferences
    TAG_MUSCLE_EDITOR ->
        Screen.MuscleEditor(groupId = arg0, muscleId = arg1, isGroup = arg2 == 1L)
    TAG_CATEGORY_EDITOR -> Screen.CategoryEditor(categoryId = arg0)
    // Including TAG_WORKOUT_LOG, and anything a future version of the app wrote that
    // this one does not know: the log is where the app opens, so an unreadable stack
    // degrades to the start rather than to a crash.
    else -> Screen.WorkoutLog
}

private val ScreenStackSaver: Saver<List<Screen>, Any> = listSaver(
    save = { stack -> stack.flatMap { it.saved() } },
    restore = { flat ->
        flat.chunked(4).map { (tag, arg0, arg1, arg2) -> screenOf(tag, arg0, arg1, arg2) }
    },
)

/**
 * Shared UI root for both platforms.
 *
 * There is no `KoinContext` wrapper: as of Koin 4.1 `startKoin()` installs the Compose
 * context itself, and wrapping again is deprecated. Koin must therefore be started
 * *before* this composes — `GymApplication` does it on Android, `startKoinIos()`
 * called from `iOSApp.swift` does it on iOS.
 *
 * **This is the one composable in the app with no `@Preview`, and it cannot have one.**
 * Every branch of the `when` below resolves a ViewModel from Koin, and every one of
 * those reaches a repository and a SQLDelight driver — a preview of this would need a
 * database, not a fixture. Faking the graph well enough to render it would be building
 * a second app to look at.
 *
 * What *is* previewable is everything it composes: each screen's stateless `…Content`
 * half, which takes its state as a parameter. Those previews live at the bottom of each
 * screen's own file and share the fixtures and the theme-and-ambients wrapper in
 * `presentation/preview/PreviewFixtures.kt`. Between them they cover every screen in
 * this `when`; what they cannot cover is the chrome around it — the app bar's title and
 * back arrow, and the tab row — which are only ever real on a device.
 */
/**
 * Puts the shipped catalogues — categories and muscles — in place, and keeps their
 * names in the device's language. See [SyncCatalogSeedsUseCase] for what that means;
 * this is only about *when*.
 *
 * ### Why the key is a translated string
 * The obvious key is `Unit` — run once per composition. That is right for the install
 * case and wrong for the language case: `getString` resolves against whatever language
 * is current *when it runs*, so a sync that never runs again can only ever have
 * written the language the app first started in.
 *
 * Both platforms do usually recompose from scratch after a language change, which
 * would make `Unit` enough by accident. Keying on one of the seeded names instead
 * makes it enough on purpose: the key is a string that changes exactly when the
 * translations do, so the effect re-runs when — and only when — there is a
 * retranslation to perform. Any seeded name would do; this is the one that appears
 * in all seven muscle groups.
 *
 * The work itself is idempotent and writes nothing when there is nothing to change,
 * so an extra run costs a handful of indexed reads.
 */
@Composable
private fun SyncCatalogSeeds() {
    val sync = koinInject<SyncCatalogSeedsUseCase>()
    val languageKey = stringResource(Res.string.muscle_seed_general)
    LaunchedEffect(languageKey) { sync() }
}

// TopAppBar is still experimental in the Kotlin/Native side of material3, the same
// way DatePicker is (see WeekStrip) — the Android artifact stabilised it first.
// BackHandler is experimental in Compose Multiplatform 1.11 for the same reason it
// exists at all: the multiplatform contract around a "back" that iOS expresses as an
// edge swipe and Android as a gesture or a button is still settling. What it compiles
// to on Android is the OnBackPressedDispatcher, which is not experimental.
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun App() {
    GymTheme {
        SyncCatalogSeeds()

        var backStack by rememberSaveable(stateSaver = ScreenStackSaver) {
            mutableStateOf(listOf<Screen>(Screen.WorkoutLog))
        }
        val currentScreen = backStack.last()
        // One queue for the whole app, so a message raised on any screen lands in the
        // same place and two of them cannot overlap.
        val snackbarHostState = remember { SnackbarHostState() }

        // Selecting a tab resets that section to its root rather than restoring where
        // you last were inside it: the catalogues are edit-then-leave screens, and
        // coming back to the app on one of them — instead of on the log you actually
        // use — is the wrong default.
        fun select(destination: Destination) {
            backStack = listOf(destination.startScreen)
        }

        fun push(screen: Screen) {
            backStack = backStack + screen
        }

        // Never empties: something has to be on screen.
        fun pop() {
            backStack = backStack.dropLast(1).ifEmpty { listOf(Screen.WorkoutLog) }
        }

        // The system back gesture pops this stack.
        //
        // It did not, and that was the oldest open item in the logs: swiping back from
        // an editor closed the *app*, because Android's back went to the activity while
        // the stack that actually decided what was on screen lived here and never heard
        // about it. Every screen therefore had exactly one way out — the arrow in the
        // app bar — and the gesture people reach for first threw away what they were
        // doing.
        //
        // Enabled only on a pushed route, so back at a tab root still leaves the app.
        // That is the platform's behaviour and worth keeping: intercepting it there
        // would make the app impossible to leave by the ordinary means.
        //
        // It is the same `pop()` the arrow calls, deliberately. Two ways out that ran
        // different code would drift, and the arrow is the one with test coverage.
        BackHandler(enabled = backStack.size > 1) { pop() }

        Scaffold(
            // The app fills the window, insets and all, and the two bars pay for the
            // strips they cover. It used to be padded as a whole with
            // safeDrawingPadding(), which kept content out of the system bars but left
            // the strips themselves unpainted by anything Compose owns — and *that* is
            // where the two platforms parted company. Android painted them from the
            // window background in themes.xml; iOS has no such thing, so the status
            // bar and the home indicator came out in the system default instead of the
            // app's own surface, and the same screen looked like two different apps.
            //
            // Now the bars extend under them (see topBar and NavigationBar, both of
            // which take those insets themselves) and the result is identical on both.
            //
            // imePadding stays on the whole Scaffold: safeDrawing carried the keyboard
            // inset too, and content still has to rise above the keyboard. It is only
            // the *system bar* half of safeDrawing that moved into the bars.
            modifier = Modifier.fillMaxSize().imePadding(),
            // What is left for the body: the horizontal insets, which are the display
            // cutout in landscape. Vertically the body is bounded by the two bars, and
            // Scaffold measures those, so asking for the vertical insets here as well
            // would inset the same strip twice.
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
            topBar = {
                TopAppBar(
                    // The screen names itself in one place for every screen, at a
                    // fixed height, instead of each one drawing its own headline at
                    // whatever offset its content happened to start at.
                    title = { Text(stringResource(currentScreen.title)) },
                    navigationIcon = {
                        // Only on a pushed route, and it is the one way back: iOS has
                        // no system back at all, and the Android gesture is not wired
                        // to this stack. Nothing draws in this slot at the root, so
                        // the title sits where it would anyway.
                        if (backStack.size > 1) {
                            IconButton(onClick = { pop() }) {
                                Icon(
                                    imageVector = GymIcons.ArrowLeft,
                                    contentDescription = stringResource(Res.string.common_back),
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        // Named rather than defaulted, and named to the same role the
                        // navigation bar takes: the two frame the screen, so they have
                        // to be the same step above it. M3's own default for this bar
                        // is `surface` — the screen's own colour — which would leave
                        // the top of the app flat while the bottom of it is raised.
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        // Muted like an unselected tab: this is a control, but it is
                        // not the thing on the screen to look at.
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            },
            // Above the navigation bar rather than over it: the tab row stays reachable
            // while a message is on screen.
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                // Container colour left to M3, which takes `surfaceContainer` from the
                // theme — the bar then sits a step above the screen behind it in both
                // schemes without naming a colour here.
                NavigationBar {
                    Destination.entries.forEach { destination ->
                        // Derived, not stored. The bar has no idea of its own about
                        // which tab is current; it asks the back stack. Hoisted to a
                        // local because the label reads it too.
                        val selected = destination == currentScreen.destination
                        NavigationBarItem(
                            selected = selected,
                            onClick = { select(destination) },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    // Null on purpose: the label below is already
                                    // read out, and a description here would make
                                    // every tab announce itself twice.
                                    contentDescription = null,
                                )
                            },
                            label = {
                                Text(
                                    stringResource(destination.label),
                                    // Weight as well as colour. The bar already tints
                                    // the selected item and puts a pill behind its
                                    // icon, but both of those are colour — and colour
                                    // alone is the one signal that does not survive a
                                    // greyscale display, a colour-vision difference,
                                    // or bright sunlight. The weight says the same
                                    // thing in a second channel.
                                    fontWeight = if (selected) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    },
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                // The M3 pill, filled with the accent itself rather
                                // than a tinted container. In the light scheme the
                                // pale `primaryContainer` against the bar's own
                                // `surfaceContainer` is 1.1:1 — a selection you
                                // cannot see. Filled with `primary` it is 5.7:1, and
                                // the selected tab reads across the room.
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                // Sits *inside* that pill, so it is the accent's
                                // on-colour, not the accent.
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                // Sits *below* the pill, on the bar, so here the
                                // accent is the foreground.
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                // Muted, but still 8:1 on the bar — "not selected"
                                // must not mean "not readable".
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
                    when (currentScreen) {
                        // Two ways in, one destination. Tapping a row opens the log
                        // it belongs to; pressing New log picks a category, creates
                        // the session, and opens that. The screen itself never
                        // creates anything either way.
                        Screen.WorkoutLog -> WorkoutLogScreen(
                            onOpenEntry = { sessionId, epochDay, existing ->
                                push(Screen.LogEntry(sessionId, epochDay, existing))
                            },
                        )

                        // A strength log writes as it is filled in, so this leaves by
                        // the back arrow with nothing to commit; a distance log leaves
                        // when it is saved. Both land back on the day's list.
                        is Screen.LogEntry -> WorkoutEntryScreen(
                            sessionId = currentScreen.sessionId,
                            epochDay = currentScreen.epochDay,
                            onDone = { pop() },
                        )

                        Screen.Preferences -> PreferencesScreen(
                            onOpenMuscles = { push(Screen.MuscleCatalog) },
                            onOpenCategories = { push(Screen.ActivityCategories) },
                        )

                        Screen.MuscleCatalog -> MuscleCatalogScreen(
                            onEditGroup = { groupId ->
                                push(Screen.MuscleEditor(groupId, muscleId = 0L, isGroup = true))
                            },
                            onEditMuscle = { groupId, muscleId ->
                                push(Screen.MuscleEditor(groupId, muscleId, isGroup = false))
                            },
                        )

                        is Screen.MuscleEditor -> MuscleEditorScreen(
                            groupId = currentScreen.groupId,
                            muscleId = currentScreen.muscleId,
                            isGroup = currentScreen.isGroup,
                            onDone = { pop() },
                        )

                        Screen.ActivityCategories -> ActivityCategoryScreen(
                            onEdit = { categoryId -> push(Screen.CategoryEditor(categoryId)) },
                        )

                        is Screen.CategoryEditor -> CategoryEditorScreen(
                            categoryId = currentScreen.categoryId,
                            onDone = { pop() },
                        )

                        Screen.BodyWeight -> BodyWeightScreen(
                            onOpenEntry = { epochDay, existing ->
                                push(Screen.BodyWeightEntry(epochDay, existing))
                            },
                        )

                        // Saving is the end of the screen, so it leaves the same way
                        // the back arrow would: there is one way off it, and the list
                        // behind is already showing what was written.
                        is Screen.BodyWeightEntry -> BodyWeightEntryScreen(
                            epochDay = currentScreen.epochDay,
                            onDone = { pop() },
                        )
                    }
                }
            }
        }
    }
}
