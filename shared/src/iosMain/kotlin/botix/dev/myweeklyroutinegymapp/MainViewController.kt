package botix.dev.myweeklyroutinegymapp

import androidx.compose.ui.window.ComposeUIViewController

/**
 * Wraps the shared UI for UIKit.
 *
 * Koin is not started here — `iOSApp.swift` calls `initKoinIos()` first, so the graph
 * exists once per process rather than once per view controller.
 */
fun MainViewController() = ComposeUIViewController { App() }
