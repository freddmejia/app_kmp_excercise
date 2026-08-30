# R8 rules for MyWeeklyRoutineGymApp — release build only.
#
# Applied on top of getDefaultProguardFile("proguard-android-optimize.txt")
# and every dependency's own bundled consumer rules. To see the merged result
# R8 actually ran with, read after a release build:
#
#   androidApp/build/outputs/mapping/release/configuration.txt
#
# This file is deliberately almost empty, and that is the point. A keep rule
# is an instruction *not* to remove something, so every rule added here makes
# the APK bigger. The way to keep the app small is to add none unless
# something demonstrably breaks without it.


# ---------------------------------------------------------------------------
# Why this app needs no keep rules of its own
# ---------------------------------------------------------------------------
# Verified against the first minified build, 2026-08-30:
#
#  * There is no reflection in the sources. Class.forName, ::class.java,
#    newInstance, getDeclared* and @Serializable return zero hits across
#    shared/src and androidApp/src.
#
#  * Koin builds everything from lambdas and constructor references —
#    single { }, factoryOf(::X), viewModelOf(::X), koinViewModel<T>(). Nothing
#    is constructed by name. Definitions are keyed on KClass objects, which R8
#    renames consistently on both the registration and the lookup side, which
#    is why Koin ships no consumer rules and needs none.
#
#  * SQLDelight generates plain Kotlin. There is no runtime mapping layer and
#    no schema class looked up by name.
#
#  * MainActivity and GymApplication are named in the merged manifest, and AGP
#    generates keep rules for manifest entry points automatically. Both survive
#    the build unrenamed — confirmed in mapping.txt.
#
#  * The libraries that do need rules — coroutines, Compose, lifecycle,
#    AndroidX, kotlinx-datetime — all ship their own consumer rules, merged in
#    automatically. Copying them here would be dead weight that silently rots
#    as those libraries update.
#
# If a release build ever dies with ClassNotFoundException, NoSuchMethodError
# or a Koin NoDefinitionFoundException, deobfuscate the stack trace against
# androidApp/build/outputs/mapping/release/mapping.txt *before* adding
# anything, then add the narrowest rule that fixes it. Never a package-wide
# -keep class botix.dev.** — that is how an APK quietly stops shrinking.


# ---------------------------------------------------------------------------
# Measured, rejected: -repackageclasses ''
# ---------------------------------------------------------------------------
# Flattening every class into the root package is the standard size tip, and
# it is wrong for this app. Measured on 2026-08-30, both builds otherwise
# identical:
#
#   without   dex 2,790,448   APK 1,808,550
#   with      dex 2,771,052   APK 1,841,306   (dex -19,396, APK +32,756)
#
# The dex really does get smaller, and the APK still grows by 32 KB, because
# an APK is a zip: the shared package prefixes that flattening deletes were
# compressing almost for free, and short unique names in the string pool
# compress worse than long repetitive ones. The uncompressed dex is not the
# number that ships.
#
# Left commented out rather than deleted so the next person does not re-derive
# it. Re-measure before trusting it — the answer depends on how deep the
# package tree is, so it can flip as the app grows.
#-repackageclasses ''


# ---------------------------------------------------------------------------
# Not applicable here: stripping logging
# ---------------------------------------------------------------------------
# The other standard tip is -assumenosideeffects on android.util.Log. This app
# calls no logger at all — android.util.Log, println and printStackTrace are
# all zero hits — so the rule would delete nothing. It becomes worth adding
# the day logging is introduced, and not before.


# ---------------------------------------------------------------------------
# Readable crash reports — deliberately OFF
# ---------------------------------------------------------------------------
# Line-number tables are a real chunk of dex, so they stay stripped while
# minimum size is the goal. mapping.txt is still written on every release
# build, so class and method names in a stack trace can be recovered; only the
# line numbers are gone.
#
# If crash reporting is added later and unreadable traces become the bigger
# problem, uncomment both lines together — the second stops the first from
# leaking original source file names into the APK:
#
#-keepattributes SourceFile,LineNumberTable
#-renamesourcefileattribute SourceFile
