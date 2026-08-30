// Included build, not buildSrc.
//
// buildSrc would work, but any edit to it invalidates the whole build's configuration
// cache and re-runs configuration for every module. An included build is compiled once
// and only its consumers rebuild, which is why the Android convention-plugin pattern
// uses this shape.
dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }

    // One catalog for the whole build. Without this the convention plugins would pin
    // their own AGP and Kotlin versions and silently drift from the app's.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
