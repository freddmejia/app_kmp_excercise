import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.util.Properties

/**
 * Publishes `app.properties` into the build, as a Kotlin object for multiplatform code
 * and as a `BuildConfig` field for the Android app.
 *
 * Apply with `id("myweeklyroutinegymapp.app-properties")`. The file lives at the
 * repository root and is read from `rootProject`, so every module sees the same values
 * no matter where it sits in the tree.
 *
 * ### Two outputs, one source
 * A Kotlin multiplatform library cannot emit `BuildConfig` — AGP's KMP library extension
 * has no such DSL, and iOS has no equivalent. So `shared` gets a generated `AppConfig`
 * object in `commonMain`, which Android, iOS and the host tests all compile against.
 *
 * The Android application module *can* emit `BuildConfig`, and does, from this same
 * file. That is duplication on purpose: the app module and anything reading it stay in
 * the idiom Android developers expect, while the shared code gets something that works
 * on a platform where `BuildConfig` does not exist. Both are generated from one read of
 * one file, so they cannot drift.
 */
class AppPropertiesConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        val propertiesFile: Provider<RegularFile> =
            rootProject.layout.projectDirectory.file(APP_PROPERTIES).let { providers.provider { it } }

        // --- multiplatform: a generated Kotlin object in commonMain -----------------
        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            val generate: TaskProvider<GenerateAppConfig> =
                tasks.register<GenerateAppConfig>("generateAppConfig") {
                    group = "build"
                    description = "Generates AppConfig.kt from $APP_PROPERTIES."
                    this.propertiesFile.set(propertiesFile)
                    packageName.set(GENERATED_PACKAGE)
                    outputDirectory.set(layout.buildDirectory.dir("generated/appconfig/kotlin"))
                }

            extensions.configure<KotlinMultiplatformExtension> {
                // Wiring the TaskProvider itself, not its output path: that carries the
                // task dependency with it, so compileKotlin* cannot start before the
                // file exists. Passing a bare File here is the classic way to get a
                // build that works locally and fails on a clean CI checkout.
                sourceSets.getByName("commonMain").kotlin.srcDir(generate)
            }
        }

        // --- android application: a real BuildConfig field --------------------------
        pluginManager.withPlugin("com.android.application") {
            extensions.configure<ApplicationExtension> {
                buildFeatures.buildConfig = true
                defaultConfig.buildConfigField(
                    "boolean",
                    BUILD_CONFIG_FIELD,
                    readDatabaseEncrypted(rootProject).toString(),
                )
            }
        }
    }

    /**
     * Read at configuration time, which is the only time `buildConfigField` can accept a
     * value. The file is registered with the value source below so the configuration
     * cache is invalidated when it changes rather than serving a stale flag.
     */
    private fun readDatabaseEncrypted(rootProject: Project): Boolean {
        val file = rootProject.layout.projectDirectory.file(APP_PROPERTIES).asFile
        if (!file.exists()) return true

        val raw = Properties()
            .apply { file.inputStream().use { load(it) } }
            .getProperty(GenerateAppConfig.KEY_DATABASE_ENCRYPTED)
            ?.trim()
            ?: return true

        return when (raw.lowercase()) {
            "true" -> true
            "false" -> false
            else -> error(
                "app.properties: '${GenerateAppConfig.KEY_DATABASE_ENCRYPTED}' must be " +
                    "true or false, but was '$raw'.",
            )
        }
    }

    private companion object {
        const val APP_PROPERTIES = "app.properties"
        const val GENERATED_PACKAGE = "botix.dev.myweeklyroutinegymapp.config"
        const val BUILD_CONFIG_FIELD = "DATABASE_AVAILABLE_ENCRYPTED"
    }
}
