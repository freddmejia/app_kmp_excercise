package botix.dev.myweeklyroutinegymapp.data

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Dispatcher for database work. `Dispatchers.IO` is not visible from commonMain, so
 * each platform supplies its own; repositories take it as a constructor default,
 * which also lets tests substitute a deterministic one.
 */
internal expect val ioDispatcher: CoroutineDispatcher
