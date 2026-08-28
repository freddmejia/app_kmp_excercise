package botix.dev.myweeklyroutinegymapp.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The app's icon set, drawn in `commonMain` so both platforms get identical artwork.
 *
 * ### Why these are hand-built vectors and not Material Icons
 * `material-icons-core` is not on this project's classpath (see `shared/build.gradle.kts`)
 * and the extended set was deprecated in Compose Multiplatform 1.7. Adding either
 * would pull a dependency in to draw three glyphs — and neither ships a dumbbell,
 * which is the one icon this app most needs. `ImageVector` is already in the
 * `compose.ui` artifact the app depends on, so this file costs nothing.
 *
 * What it replaces is worse than either: the tabs used emoji (🏋 📋 ⚖), which are
 * font glyphs. They are rendered by the platform, so they differ between Android and
 * iOS, ignore the tint the navigation bar sets for a selected item, and land on
 * whatever baseline and optical size the emoji font decides.
 *
 * ### The rules that keep them a set
 * Every icon is a **24×24 viewport**, stroked at **2 units**, never filled, with round
 * caps and joins. Same grid, same weight, same terminals — so no icon reads heavier
 * than its neighbours in the same row. Geometry stays 1 unit inside the viewport,
 * which is where a 2-wide stroke's own half-width goes: a line drawn *on* the edge
 * would be clipped in half.
 *
 * The declared [Color.Black] is a stencil, not a colour choice — `Icon()` overwrites
 * it with a tint from the theme, which is how a selected tab's icon takes the accent.
 * (This is exactly what Material's own icons do.)
 *
 * Each icon is `by lazy`: an `ImageVector` is immutable and its path list is not free
 * to build, so it is built once per process and shared by every composition.
 */
object GymIcons {

    /** Workout log. A bar with two plates each side. */
    val Dumbbell: ImageVector by lazy {
        gymIcon("Dumbbell") {
            // Outer plates, then inner plates, then the bar between them. The plates
            // are drawn as bare strokes rather than boxes because at 24dp a filled
            // plate closes up into a blob.
            moveTo(3f, 9.5f)
            lineTo(3f, 14.5f)
            moveTo(6.5f, 6.5f)
            lineTo(6.5f, 17.5f)
            moveTo(17.5f, 6.5f)
            lineTo(17.5f, 17.5f)
            moveTo(21f, 9.5f)
            lineTo(21f, 14.5f)
            moveTo(6.5f, 12f)
            lineTo(17.5f, 12f)
        }
    }

    /** The activity catalogue. A bulleted list. */
    val Catalogue: ImageVector by lazy {
        gymIcon("Catalogue") {
            // The bullets are round caps on near-zero-length segments, so they are
            // the same 2-unit weight as the rules beside them — a drawn circle would
            // have to be either an outline (lighter) or a fill (heavier).
            moveTo(4.5f, 6f)
            lineTo(4.51f, 6f)
            moveTo(9f, 6f)
            lineTo(20f, 6f)
            moveTo(4.5f, 12f)
            lineTo(4.51f, 12f)
            moveTo(9f, 12f)
            lineTo(20f, 12f)
            moveTo(4.5f, 18f)
            lineTo(4.51f, 18f)
            moveTo(9f, 18f)
            lineTo(20f, 18f)
        }
    }

    /**
     * "This row opens something."
     *
     * Trailing a row that is itself the button, where a labelled control would be
     * making a second promise the row has already made.
     */
    val ChevronRight: ImageVector by lazy {
        gymIcon("ChevronRight") {
            moveTo(9.5f, 5f)
            lineTo(16.5f, 12f)
            lineTo(9.5f, 19f)
        }
    }

    /**
     * Back, in the top bar of a screen that was pushed onto another.
     *
     * A shaft as well as a head: [ChevronRight] on its own says "there is more this
     * way", which is what a row uses it for, while a bar needs the one control that
     * leaves the screen to be unmistakable at a glance. It is also the only way back
     * on iOS, where nothing in the system offers one.
     */
    val ArrowLeft: ImageVector by lazy {
        gymIcon("ArrowLeft") {
            moveTo(20f, 12f)
            lineTo(4f, 12f)
            moveTo(10f, 6f)
            lineTo(4f, 12f)
            lineTo(10f, 18f)
        }
    }

    /** Delete. Revealed behind a row swiped to the left. */
    val Trash: ImageVector by lazy {
        gymIcon("Trash") {
            // Lid, then the handle above it.
            moveTo(4f, 6.5f)
            lineTo(20f, 6.5f)
            moveTo(9.5f, 6.5f)
            lineTo(9.5f, 4f)
            lineTo(14.5f, 4f)
            lineTo(14.5f, 6.5f)
            // The can: open at the top, where the lid already draws the line.
            moveTo(6.5f, 6.5f)
            lineTo(6.5f, 19f)
            lineTo(17.5f, 19f)
            lineTo(17.5f, 6.5f)
            // Two ribs. Short of the rim at both ends so they read as inside it.
            moveTo(10.5f, 10f)
            lineTo(10.5f, 15.5f)
            moveTo(13.5f, 10f)
            lineTo(13.5f, 15.5f)
        }
    }

    /**
     * Settings. Two sliders, each with its knob at a different stop.
     *
     * Not a cogwheel: a cog at 24dp with a 2-unit stroke is a ring of tooth-sized
     * gaps that fill in at any smaller optical size, and the teeth would be the only
     * curved detail in a set built from straight runs. Sliders also say what this
     * section actually is — things you set, not machinery.
     */
    val Sliders: ImageVector by lazy {
        gymIcon("Sliders") {
            // Two rails, and a knob on each: the knobs sit at different stops so the
            // pair reads as adjustable rather than as a list.
            moveTo(4f, 8.5f)
            lineTo(20f, 8.5f)
            moveTo(4f, 15.5f)
            lineTo(20f, 15.5f)
            // Drawn as short vertical bars rather than circles, which at this weight
            // would either outline (too light) or fill (too heavy) against the rails.
            moveTo(9f, 6f)
            lineTo(9f, 11f)
            moveTo(15f, 13f)
            lineTo(15f, 18f)
        }
    }

    /** Body weight. A scale: a platform with a dial and a needle. */
    val Scale: ImageVector by lazy {
        gymIcon("Scale") {
            moveTo(4f, 4f)
            lineTo(20f, 4f)
            lineTo(20f, 20f)
            lineTo(4f, 20f)
            close()
            // The dial: one cubic, so its shoulders stay symmetrical about x = 12.
            moveTo(8.5f, 15.5f)
            curveTo(8.5f, 10f, 15.5f, 10f, 15.5f, 15.5f)
            // The needle, off-centre — a vertical one reads as a fold in the platform.
            moveTo(12f, 15.5f)
            lineTo(14f, 11.8f)
        }
    }
}

/**
 * The shared stencil: 24×24, stroked at 2, round everything.
 *
 * Every icon above goes through this, so the set cannot drift — changing the weight of
 * all of them is one number here.
 */
private fun gymIcon(
    name: String,
    pathBuilder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = pathBuilder,
    )
}.build()
