package botix.dev.myweeklyroutinegymapp.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The app's colour system: one electric-blue accent on a cool neutral base.
 *
 * **Every raw colour in the app is private to this file.** Screens read
 * `MaterialTheme.colorScheme` and nothing else, so re-skinning the app is a change
 * here and nowhere else. A `Color(0xFF…)` appearing in a screen is a bug — the one
 * legitimate literal elsewhere is `Color.Transparent`, which is an absence of paint
 * rather than a choice of one.
 *
 * ### Why one accent
 * Electric blue carries every "this is live / this is selected" signal: the chosen day
 * in the week strip, the outlined today cell, the selected tab's pill. A second brand
 * hue competing for that job would mean none of them read as *the* accent. `tertiary`
 * is a cyan from the same cool family rather than a contrasting hue, so a component
 * that reaches for it by default cannot drag a stray colour into the palette.
 *
 * ### Why the dark scheme is not the light one inverted
 * At tone 40 the accent (`#0F51D6`) is legible on white and nearly invisible on black;
 * at tone 80 (`#A9C4FF`) the reverse. So the accent swaps tone between schemes while
 * keeping its hue — the app looks like itself in both, and neither mode is the one
 * that "works less well".
 *
 * ### Contrast
 * Every on-colour pair here was checked against WCAG 2.1: 4.5:1 or better for text
 * (the weakest is 4.26:1, for `outline`, which paints borders rather than glyphs) and
 * well past 3:1 for the icon and indicator pairs the navigation bar uses. The surfaces
 * ladder from `surfaceContainerLowest` to `Highest`, so a card can sit on a screen
 * that sits on a background without any of the three needing a border to separate it.
 */

// ---------------------------------------------------------------------------
// The accent ramp. Tone numbers follow Material 3: 10 is near-black, 90 near-white.
// ---------------------------------------------------------------------------
private val Blue10 = Color(0xFF00174B)
private val Blue20 = Color(0xFF002A78)
private val Blue30 = Color(0xFF00399E)
private val Blue40 = Color(0xFF0F51D6)
private val Blue80 = Color(0xFFA9C4FF)
private val Blue90 = Color(0xFFDBE2FF)

// Cool steel, for the second-rank surfaces that must not compete with the accent.
private val Steel10 = Color(0xFF111C2B)
private val Steel20 = Color(0xFF263141)
private val Steel30 = Color(0xFF3C4758)
private val Steel40 = Color(0xFF545F71)
private val Steel80 = Color(0xFFBCC7DC)
private val Steel90 = Color(0xFFD8E2F9)

// Cyan: the same cool family, reserved for the rare M3 component that wants a third.
private val Cyan10 = Color(0xFF001E2E)
private val Cyan20 = Color(0xFF003549)
private val Cyan30 = Color(0xFF004C6B)
private val Cyan40 = Color(0xFF00658F)
private val Cyan80 = Color(0xFF8ECFF8)
private val Cyan90 = Color(0xFFC8E6FF)

// Red is a signal, not a brand colour, so it stays the Material baseline.
private val Red10 = Color(0xFF410002)
private val Red20 = Color(0xFF690005)
private val Red30 = Color(0xFF93000A)
private val Red40 = Color(0xFFBA1A1A)
private val Red80 = Color(0xFFFFB4AB)
private val Red90 = Color(0xFFFFDAD6)

// Neutrals, tinted very slightly blue so black never looks brown beside the accent.
private val White = Color(0xFFFFFFFF)
private val Black = Color(0xFF000000)
private val NeutralLightSurface = Color(0xFFF7F9FC)
private val NeutralLightOn = Color(0xFF12161C)
private val NeutralLightVariant = Color(0xFFDFE2EB)
private val NeutralLightVariantOn = Color(0xFF43474E)
private val NeutralDarkSurface = Color(0xFF0E1116)
private val NeutralDarkOn = Color(0xFFE2E5EC)
private val NeutralDarkVariant = Color(0xFF42474F)
private val NeutralDarkVariantOn = Color(0xFFC2C7D1)

/** Daylight: near-white surfaces, the accent at tone 40 so it holds white text. */
private val GymLightColors: ColorScheme = lightColorScheme(
    primary = Blue40,
    onPrimary = White,
    primaryContainer = Blue90,
    onPrimaryContainer = Blue10,
    inversePrimary = Blue80,

    secondary = Steel40,
    onSecondary = White,
    secondaryContainer = Steel90,
    onSecondaryContainer = Steel10,

    tertiary = Cyan40,
    onTertiary = White,
    tertiaryContainer = Cyan90,
    onTertiaryContainer = Cyan10,

    error = Red40,
    onError = White,
    errorContainer = Red90,
    onErrorContainer = Red10,

    background = NeutralLightSurface,
    onBackground = NeutralLightOn,
    surface = NeutralLightSurface,
    onSurface = NeutralLightOn,
    surfaceVariant = NeutralLightVariant,
    onSurfaceVariant = NeutralLightVariantOn,
    surfaceTint = Blue40,

    // The elevation ladder. Lowest is the only pure white in the light scheme, which
    // is what makes a dialog or a raised card read as being *above* the screen.
    surfaceBright = White,
    surfaceDim = Color(0xFFD8DCE4),
    surfaceContainerLowest = White,
    surfaceContainerLow = Color(0xFFF1F4FA),
    surfaceContainer = Color(0xFFEBEEF5),
    surfaceContainerHigh = Color(0xFFE5E9F0),
    surfaceContainerHighest = Color(0xFFDFE3EA),

    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C6CF),
    scrim = Black,
    inverseSurface = Color(0xFF2B3038),
    inverseOnSurface = Color(0xFFF1F4FA),
)

/**
 * Night: a near-black neutral base with the accent lifted to tone 80.
 *
 * The base is `#0E1116` rather than pure black — on an OLED black the elevation ladder
 * above it becomes impossible to see, and every raised surface would then need an
 * outline to exist at all.
 */
private val GymDarkColors: ColorScheme = darkColorScheme(
    primary = Blue80,
    onPrimary = Blue20,
    primaryContainer = Blue30,
    onPrimaryContainer = Blue90,
    inversePrimary = Blue40,

    secondary = Steel80,
    onSecondary = Steel20,
    secondaryContainer = Steel30,
    onSecondaryContainer = Steel90,

    tertiary = Cyan80,
    onTertiary = Cyan20,
    tertiaryContainer = Cyan30,
    onTertiaryContainer = Cyan90,

    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,

    background = NeutralDarkSurface,
    onBackground = NeutralDarkOn,
    surface = NeutralDarkSurface,
    onSurface = NeutralDarkOn,
    surfaceVariant = NeutralDarkVariant,
    onSurfaceVariant = NeutralDarkVariantOn,
    surfaceTint = Blue80,

    surfaceBright = Color(0xFF34383F),
    surfaceDim = NeutralDarkSurface,
    surfaceContainerLowest = Color(0xFF090B0F),
    surfaceContainerLow = Color(0xFF14181E),
    surfaceContainer = Color(0xFF181C22),
    surfaceContainerHigh = Color(0xFF232830),
    surfaceContainerHighest = Color(0xFF2E333B),

    outline = Color(0xFF8C919B),
    outlineVariant = NeutralDarkVariant,
    scrim = Black,
    inverseSurface = NeutralDarkOn,
    inverseOnSurface = Color(0xFF2B3038),
)

/**
 * The colours Material 3 has no role for.
 *
 * Right now that is one pair: the red revealed *behind* a row being swiped away. M3
 * offers `error` and `errorContainer`, and neither reads as a destructive background in
 * both schemes — `error` is a true red in light but a pale salmon in dark, and
 * `errorContainer` is the reverse. A screen cannot choose between them either, because
 * `colorScheme` does not say which scheme it is.
 *
 * So the choice is made here, once, where both schemes are in view. Screens read
 * `LocalGymAccents.current` and still name no colour of their own.
 */
@Immutable
data class GymAccents(
    /** Behind a swipe-to-delete row: unmistakably red in either scheme. */
    val destructive: Color,
    /** The trash icon on top of it. 6.5:1 in light, 7.2:1 in dark. */
    val onDestructive: Color,
)

private val LightAccents = GymAccents(destructive = Red40, onDestructive = White)

// Tone 30 rather than the light scheme's 40: on a near-black base the brighter red
// glows, and the icon on top of it has nowhere left to go for contrast.
private val DarkAccents = GymAccents(destructive = Red30, onDestructive = Red90)

/**
 * Set by [GymTheme]. Static because it changes only when the whole scheme does — a
 * read of it should not subscribe the reader to recomposition.
 */
val LocalGymAccents = staticCompositionLocalOf { LightAccents }

/**
 * Wraps the whole app.
 *
 * [darkTheme] follows the device by default and is a parameter only so a preview or a
 * screenshot test can pin one scheme. There is deliberately no dynamic-colour branch:
 * dynamic colour is Android-only, so it would leave iOS looking like a different
 * product, and it hands the accent — the thing every "selected" and "live" state in
 * this app is built on — to whatever the user has set as a wallpaper.
 */
@Composable
fun GymTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalGymAccents provides if (darkTheme) DarkAccents else LightAccents,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) GymDarkColors else GymLightColors,
            content = content,
        )
    }
}
