import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.Properties

/**
 * Turns `app.properties` into a Kotlin object the shared module can read.
 *
 * ### Why a generated object and not BuildConfig
 * `BuildConfig` is an Android application/library feature. The `shared` module is an
 * `com.android.kotlin.multiplatform.library`, whose extension exposes no `buildConfig`
 * at all in AGP 9.0.1 — and iOS has no such concept in any version. Since
 * `DriverFactory` needs this flag on *both* platforms, the flag has to live in
 * `commonMain`, which means generating Kotlin rather than Java.
 *
 * The app module still gets a real `BuildConfig` field from the same file, so the two
 * cannot disagree — see [AppPropertiesConventionPlugin].
 *
 * The file is read inside the task action rather than at configuration time, so the
 * configuration cache stays valid and editing `app.properties` re-runs only this task.
 */
@CacheableTask
abstract class GenerateAppConfig : DefaultTask() {

    /**
     * PathSensitivity.NONE: the contents decide the output, the location does not. With
     * RELATIVE this would re-run whenever the build moved on disk.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val propertiesFile: RegularFileProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val properties = Properties().apply {
            propertiesFile.get().asFile.inputStream().use { load(it) }
        }

        // Absent means encrypted. Failing open here would mean a typo in a key name, or
        // a missing file on a fresh clone, silently shipping an unencrypted database —
        // the flag is allowed to turn encryption off, but only by saying so out loud.
        val encrypted = properties.readBoolean(KEY_DATABASE_ENCRYPTED, default = true)

        val pkg = packageName.get()
        val target = outputDirectory.get().asFile.resolve(pkg.replace('.', '/')).apply { mkdirs() }

        target.resolve("AppConfig.kt").writeText(
            """
            |package $pkg
            |
            |// GENERATED FROM app.properties -- DO NOT EDIT.
            |// Regenerate with :shared:generateAppConfig, or edit app.properties at the
            |// repository root and build.
            |internal object AppConfig {
            |
            |    /**
            |     * Whether the SQLite database is opened through SQLCipher.
            |     *
            |     * Read from `$KEY_DATABASE_ENCRYPTED` in app.properties, defaulting to
            |     * true when the key or the file is missing.
            |     */
            |    const val DATABASE_AVAILABLE_ENCRYPTED: Boolean = $encrypted
            |}
            |
            """.trimMargin(),
        )
    }

    /**
     * Only "true"/"false" are accepted, case-insensitively and whitespace-trimmed.
     *
     * [String.toBoolean] would map "yes", "1" and a typo like "ture" all to false, which
     * for a security switch means a misspelling quietly disables encryption.
     */
    private fun Properties.readBoolean(key: String, default: Boolean): Boolean {
        val raw = getProperty(key)?.trim() ?: return default
        return when (raw.lowercase()) {
            "true" -> true
            "false" -> false
            else -> error(
                "app.properties: '$key' must be true or false, but was '$raw'. " +
                    "Refusing to guess, because guessing wrong here ships an " +
                    "unencrypted database.",
            )
        }
    }

    internal companion object {
        const val KEY_DATABASE_ENCRYPTED: String = "database_available_encrypted"
    }
}
