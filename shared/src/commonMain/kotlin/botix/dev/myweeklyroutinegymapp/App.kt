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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import botix.dev.myweeklyroutinegymapp.presentation.activity.ActivityCategoryScreen
import botix.dev.myweeklyroutinegymapp.presentation.activity.ActivityListScreen
import botix.dev.myweeklyroutinegymapp.presentation.bodyweight.BodyWeightScreen
import botix.dev.myweeklyroutinegymapp.presentation.common.LocalSnackbarHostState
import botix.dev.myweeklyroutinegymapp.presentation.theme.GymIcons
import botix.dev.myweeklyroutinegymapp.presentation.theme.GymTheme
import botix.dev.myweeklyroutinegymapp.presentation.workout.MuscleCatalogScreen
import botix.dev.myweeklyroutinegymapp.presentation.workout.WorkoutLogScreen
import myweeklyroutinegymapp.shared.generated.resources.Res
import myweeklyroutinegymapp.shared.generated.resources.activities_title
import myweeklyroutinegymapp.shared.generated.resources.categories_title
import myweeklyroutinegymapp.shared.generated.resources.common_back
import myweeklyroutinegymapp.shared.generated.resources.log_title
import myweeklyroutinegymapp.shared.generated.resources.muscles_title
import myweeklyroutinegymapp.shared.generated.resources.tab_activities
import myweeklyroutinegymapp.shared.generated.resources.tab_log
import myweeklyroutinegymapp.shared.generated.resources.tab_weight
import myweeklyroutinegymapp.shared.generated.resources.weight_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The three top-level tabs.
 *
 * A tab is not a screen: it is a *section*, and each one owns a small stack of routes
 * (see [Route]). The icon comes from the app's own set rather than a font emoji, so it
 * takes the navigation bar's tint like any other vector.
 */
private enum class Destination(val label: StringResource, val icon: ImageVector) {
    WORKOUT(Res.string.tab_log, GymIcons.Dumbbell),
    ACTIVITIES(Res.string.tab_activities, GymIcons.Catalogue),
    WEIGHT(Res.string.tab_weight, GymIcons.Scale),
}

/**
 * Every screen the app can show, and which tab it belongs to.
 *
 * Still no navigation library: these routes take no arguments, have no deep links and
 * nest exactly one level deep, so a nav graph would add a dependency to model a list.
 * What matters is that this list is the *single* source of truth for where the app is.
 * The catalogue screens used to be booleans held beside a selected-tab index, which is
 * two pieces of state that can disagree — the bar could say "Activities" while the
 * muscle catalogue was on screen. Now the bar reads its selection off the back stack,
 * so it cannot drift from what is actually rendered.
 *
 * Introduce a real nav graph when a route gains an argument or a deep link.
 */
private enum class Route(val destination: Destination, val title: StringResource) {
    WORKOUT_LOG(Destination.WORKOUT, Res.string.log_title),

    /** A detail of the log, not a peer of it: it edits what the log can record. */
    MUSCLE_CATALOG(Destination.WORKOUT, Res.string.muscles_title),

    ACTIVITY_LIST(Destination.ACTIVITIES, Res.string.activities_title),

    /** Same shape: a catalogue that edits one field of the screen behind it. */
    ACTIVITY_CATEGORIES(Destination.ACTIVITIES, Res.string.categories_title),

    BODY_WEIGHT(Destination.WEIGHT, Res.string.weight_title),
}

/** Where a tab lands when it is selected from the bar. */
private val Destination.startRoute: Route
    get() = when (this) {
        Destination.WORKOUT -> Route.WORKOUT_LOG
        Destination.ACTIVITIES -> Route.ACTIVITY_LIST
        Destination.WEIGHT -> Route.BODY_WEIGHT
    }

/**
 * Survives process death as a list of ordinals.
 *
 * Enums have no built-in `Saver` in Compose Multiplatform — storing the constants
 * directly would restore as null and drop the user back on the log screen after the
 * system killed the app in the background.
 */
private val RouteStackSaver: Saver<List<Route>, Any> = listSaver(
    save = { stack -> stack.map { it.ordinal } },
    restore = { saved -> saved.map { Route.entries[it] } },
)

/**
 * Shared UI root for both platforms.
 *
 * There is no `KoinContext` wrapper: as of Koin 4.1 `startKoin()` installs the Compose
 * context itself, and wrapping again is deprecated. Koin must therefore be started
 * *before* this composes — `GymApplication` does it on Android, `startKoinIos()`
 * called from `iOSApp.swift` does it on iOS.
 *
 * Not previewable: the screens resolve real dependencies. Preview their `…Content`
 * composables instead, which take their state as a parameter — wrapped in [GymTheme],
 * which is what gives them the app's colours outside of a running app.
 */
// TopAppBar is still experimental in the Kotlin/Native side of material3, the same
// way DatePicker is (see WeekStrip) — the Android artifact stabilised it first.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    GymTheme {
        var backStack by rememberSaveable(stateSaver = RouteStackSaver) {
            mutableStateOf(listOf(Route.WORKOUT_LOG))
        }
        val currentRoute = backStack.last()
        // One queue for the whole app, so a message raised on any screen lands in the
        // same place and two of them cannot overlap.
        val snackbarHostState = remember { SnackbarHostState() }

        // Selecting a tab resets that section to its root rather than restoring where
        // you last were inside it: the catalogues are edit-then-leave screens, and
        // coming back to the app on one of them — instead of on the log you actually
        // use — is the wrong default.
        fun select(destination: Destination) {
            backStack = listOf(destination.startRoute)
        }

        fun push(route: Route) {
            backStack = backStack + route
        }

        // Never empties: something has to be on screen.
        fun pop() {
            backStack = backStack.dropLast(1).ifEmpty { listOf(Route.WORKOUT_LOG) }
        }

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
                    title = { Text(stringResource(currentRoute.title)) },
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
                        NavigationBarItem(
                            // Derived, not stored. The bar has no idea of its own
                            // about which tab is current; it asks the back stack.
                            selected = destination == currentRoute.destination,
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
                            label = { Text(stringResource(destination.label)) },
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
                    when (currentRoute) {
                        Route.WORKOUT_LOG ->
                            WorkoutLogScreen(onManageMuscles = { push(Route.MUSCLE_CATALOG) })

                        Route.MUSCLE_CATALOG -> MuscleCatalogScreen()

                        Route.ACTIVITY_LIST ->
                            ActivityListScreen(onManageCategories = { push(Route.ACTIVITY_CATEGORIES) })

                        Route.ACTIVITY_CATEGORIES -> ActivityCategoryScreen()

                        Route.BODY_WEIGHT -> BodyWeightScreen()
                    }
                }
            }
        }
    }
}
