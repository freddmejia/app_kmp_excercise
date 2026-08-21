package botix.dev.myweeklyroutinegymapp.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * `Dispatchers.IO` is internal on Kotlin/Native in kotlinx-coroutines 1.10.2, so
 * Native uses the default pool instead.
 *
 * Acceptable here: SQLite calls through the native driver are short and local, and
 * the pool is sized to the CPU count rather than being a single shared main thread.
 * Revisit if a genuinely long-running query appears — that would want its own
 * dispatcher rather than a slice of the shared compute pool.
 */
internal actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
