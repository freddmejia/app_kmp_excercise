package botix.dev.myweeklyroutinegymapp

import android.app.Application
import botix.dev.myweeklyroutinegymapp.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

/**
 * Starts Koin once for the process.
 *
 * It has to happen here rather than in MainActivity: the graph holds a single
 * database, and starting it per-activity would rebuild that on every launch.
 */
class GymApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidLogger()
            // The application context, so the database outlives any screen.
            androidContext(this@GymApplication)
        }
    }
}
