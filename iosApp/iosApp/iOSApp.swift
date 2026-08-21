import SwiftUI
import Shared

@main
struct iOSApp: App {

    /// Starts the shared Koin graph once, before any Compose view exists.
    ///
    /// `App.init()` rather than `ContentView`'s lifecycle: the graph owns a single
    /// database, and a SwiftUI view body can be evaluated more than once.
    init() {
        KoinIosKt.startKoinIos()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
