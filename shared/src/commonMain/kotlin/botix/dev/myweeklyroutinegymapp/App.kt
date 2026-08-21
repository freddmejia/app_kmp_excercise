package botix.dev.myweeklyroutinegymapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import botix.dev.myweeklyroutinegymapp.presentation.activity.ActivityCategoryScreen
import botix.dev.myweeklyroutinegymapp.presentation.activity.ActivityListScreen
import botix.dev.myweeklyroutinegymapp.presentation.bodyweight.BodyWeightScreen
import botix.dev.myweeklyroutinegymapp.presentation.workout.MuscleCatalogScreen
import botix.dev.myweeklyroutinegymapp.presentation.workout.WorkoutLogScreen
import myweeklyroutinegymapp.shared.generated.resources.Res
import myweeklyroutinegymapp.shared.generated.resources.tab_activities
import myweeklyroutinegymapp.shared.generated.resources.tab_log
import myweeklyroutinegymapp.shared.generated.resources.tab_weight
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The three feature screens, one per top-level tab.
 *
 * Tab state rather than a navigation library: these are peers with no hierarchy, no
 * arguments and no deep links, so a nav graph would add a dependency and a back stack
 * that nothing here needs. Introduce one when a screen gains a detail route.
 */
private enum class Destination(val label: StringResource, val symbol: String) {
    WORKOUT(Res.string.tab_log, "🏋"),
    ACTIVITIES(Res.string.tab_activities, "📋"),
    WEIGHT(Res.string.tab_weight, "⚖"),
}

/**
 * Shared UI root for both platforms.
 *
 * There is no `KoinContext` wrapper: as of Koin 4.1 `startKoin()` installs the Compose
 * context itself, and wrapping again is deprecated. Koin must therefore be started
 * *before* this composes — `GymApplication` does it on Android, `startKoinIos()`
 * called from `iOSApp.swift` does it on iOS.
 *
 * Not previewable: the screens resolve real dependencies. Preview their `…Content`
 * composables instead, which take their state as a parameter.
 */
@Composable
fun App() {
    MaterialTheme {
        // Saved as an ordinal: enums have no built-in Saver in Compose Multiplatform,
        // so storing the value directly would not survive process death.
        var selectedIndex by rememberSaveable { mutableStateOf(0) }
        // The muscle catalogue is a detail of the Log tab, not a peer of it, so it
        // is a flag on that tab rather than a fourth destination.
        var showMuscleCatalog by rememberSaveable { mutableStateOf(false) }
        // Same shape for categories on the Activities tab: a catalogue that edits
        // one field of the screen behind it, not a destination of its own.
        var showCategories by rememberSaveable { mutableStateOf(false) }
        val destination = Destination.entries[selectedIndex]

        Scaffold(
            modifier = Modifier.fillMaxSize().safeContentPadding(),
            bottomBar = {
                NavigationBar {
                    Destination.entries.forEachIndexed { index, entry ->
                        NavigationBarItem(
                            selected = index == selectedIndex,
                            onClick = { selectedIndex = index },
                            icon = { Text(entry.symbol) },
                            label = { Text(stringResource(entry.label)) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                when (destination) {
                    Destination.WORKOUT ->
                        if (showMuscleCatalog) {
                            MuscleCatalogScreen(onBack = { showMuscleCatalog = false })
                        } else {
                            WorkoutLogScreen(onManageMuscles = { showMuscleCatalog = true })
                        }
                    Destination.ACTIVITIES ->
                        if (showCategories) {
                            ActivityCategoryScreen(onBack = { showCategories = false })
                        } else {
                            ActivityListScreen(onManageCategories = { showCategories = true })
                        }
                    Destination.WEIGHT -> BodyWeightScreen()
                }
            }
        }
    }
}
