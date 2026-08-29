package botix.dev.myweeklyroutinegymapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * Koin is started in [GymApplication], not here: the graph owns a single database,
 * and starting it per-activity would rebuild that on every launch and on every
 * configuration change.
 *
 * There is deliberately no `@Preview` of [App] any more — it resolves real
 * dependencies, and the preview renderer never runs `Application.onCreate`, so Koin
 * would not be started and the preview would fail. Preview `BodyWeightContent`
 * instead, which takes its state as a parameter.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }
}
