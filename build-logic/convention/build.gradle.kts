import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "botix.dev.myweeklyroutinegymapp.buildlogic"

kotlin {
    compilerOptions {
        // Matches the app and shared modules. A convention plugin compiled for a newer
        // JVM than the build it configures fails at apply time, not at compile time.
        jvmTarget = JvmTarget.JVM_11
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    // compileOnly, not implementation: these plugins are already on the consuming
    // build's classpath. Bundling them would put two copies of AGP in one classloader.
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("appProperties") {
            id = "myweeklyroutinegymapp.app-properties"
            implementationClass = "AppPropertiesConventionPlugin"
        }
    }
}
