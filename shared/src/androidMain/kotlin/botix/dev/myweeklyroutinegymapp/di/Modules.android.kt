package botix.dev.myweeklyroutinegymapp.di

import botix.dev.myweeklyroutinegymapp.db.DriverFactory
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The driver needs a Context. It resolves the *application* context that
 * `androidContext(...)` was started with, never an Activity — the database outlives
 * any screen.
 */
actual val platformModule: Module = module {
    single { DriverFactory(androidContext()) }
}
