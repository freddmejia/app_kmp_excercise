package botix.dev.myweeklyroutinegymapp.di

/**
 * iOS entry point for starting Koin, called from `iOSApp.swift`.
 *
 * A dedicated no-argument function rather than exposing [initKoin] directly: that one
 * takes a `KoinAppDeclaration` lambda, which Swift would have to satisfy with a
 * closure over Kotlin types.
 *
 * Named `startKoinIos` and not `initKoinIos` on purpose. Kotlin/Native renames
 * exported functions beginning with `init` to `doInit…` in the Objective-C header,
 * because `init` is a reserved method family there — the Swift call site would read
 * `doInitKoinIos()`, which looks like a typo. Avoiding the prefix keeps the name the
 * same on both sides.
 *
 * Must run before the first Compose view is created, since the composition resolves
 * dependencies immediately.
 */
fun startKoinIos() {
    initKoin()
}
