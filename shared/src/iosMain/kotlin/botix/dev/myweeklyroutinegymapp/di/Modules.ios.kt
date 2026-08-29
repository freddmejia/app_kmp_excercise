package botix.dev.myweeklyroutinegymapp.di

import botix.dev.myweeklyroutinegymapp.db.DriverFactory
import org.koin.core.module.Module
import org.koin.dsl.module

/** iOS resolves the database path itself, so the factory needs nothing injected. */
actual val platformModule: Module = module {
    single { DriverFactory() }
}
