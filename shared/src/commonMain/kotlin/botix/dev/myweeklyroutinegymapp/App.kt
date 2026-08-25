package botix.dev.myweeklyroutinegymapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import myweeklyroutinegymapp.shared.generated.resources.tab_activities
import myweeklyroutinegymapp.shared.generated.resources.tab_log
import myweeklyroutinegymapp.shared.generated.resources.tab_weight
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
private enum class Route(val destination: Destination) {
    WORKOUT_LOG(Destination.WORKOUT),

    /** A detail of the log, not a peer of it: it edits what the log can record. */
    MUSCLE_CATALOG(Destination.WORKOUT),

    ACTIVITY_LIST(Destination.ACTIVITIES),

    /** Same shape: a catalogue that edits one field of the screen behind it. */
    ACTIVITY_CATEGORIES(Destination.ACTIVITIES),

    BODY_WEIGHT(Destination.WEIGHT),
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
            // safeDrawing, not safeContent. safeContent is safeDrawing *plus*
            // safeGestures, and safeGestures carries the back-swipe exclusion strips
            // down the left and right edges — roughly 24.dp a side on a gesture-nav
            // device. That is invisible padding applied to every screen, outside any
            // margin a screen sets for itself, so a screen asking for 10.dp side
            // margins silently got 34.dp and no amount of editing the screen fixed it.
            //
            // Dropping it costs nothing here: the exclusion zones intercept *swipes*,
            // not taps, and nothing in this app is dragged horizontally. Status bar,
            // navigation bar and display cutout are all still honoured.
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
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

                        Route.MUSCLE_CATALOG -> MuscleCatalogScreen(onBack = { pop() })

                        Route.ACTIVITY_LIST ->
                            ActivityListScreen(onManageCategories = { push(Route.ACTIVITY_CATEGORIES) })

                        Route.ACTIVITY_CATEGORIES -> ActivityCategoryScreen(onBack = { pop() })

                        Route.BODY_WEIGHT -> BodyWeightScreen()
                    }
                }
            }
        }
    }
}
