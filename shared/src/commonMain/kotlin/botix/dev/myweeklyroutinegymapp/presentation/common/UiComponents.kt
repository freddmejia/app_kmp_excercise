package botix.dev.myweeklyroutinegymapp.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The message a screen shows when it has nothing to list.
 *
 * A distinct component rather than a bare [Text] so "empty" never gets confused with
 * "still loading" — the two look identical if both are just blank space.
 */
@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        hint?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A screen's pinned title and the actions that belong to it.
 *
 * [FlowRow] rather than a plain `Row`, because a `Row` measures its children in order and
 * gives each one only the width the previous ones left over. A title long enough to
 * fill the line therefore does not push the trailing buttons off the edge — it
 * measures them to zero, which removes them from the tree entirely: no pixels, and
 * nothing for accessibility or a tap to find. On the Log screen that deleted the one
 * control that creates a log, and the collapsed button then wrapped its own label a
 * letter per line, making the row tall enough to strand the title in mid-screen.
 *
 * A width that holds in English is no evidence at all here: "Log workout" becomes
 * "Registrar entrenamiento", and "New log" becomes "Nuevo registro". So the actions
 * drop to a second line when they do not fit rather than competing for the first,
 * and stay beside the title when they do.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        // One line: title left, actions right, exactly as before. Two: each line
        // holds a single item, which SpaceBetween leaves at the start.
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        // Also a FlowRow: the actions alone can outgrow the line once translated,
        // and inside a Row the last of them would vanish the same way.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }
}

/**
 * The app's one snackbar queue, reachable from any screen.
 *
 * Ambient rather than a parameter threaded through five screens: the host lives in
 * the [androidx.compose.material3.Scaffold] at the root, above the navigation bar, and
 * every screen inside it wants the same one. Passing it down would put a plumbing
 * argument in signatures that are otherwise about the screen's own data.
 */
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided — wrap the content in CompositionLocalProvider.")
}

/**
 * Shows [text] as a snackbar and then tells the view model it has been delivered.
 *
 * Replaces the message cards each screen used to inline. A card is a layout node: it
 * appeared *between* the header and the list, pushed everything below it down by its
 * own height, and needed an OK press to go away. A confirmation that reflows the
 * screen it is confirming — and that has to be dismissed before the screen looks
 * right again — costs more than it tells you.
 *
 * [text] is resolved by the caller because the message-to-string mapping needs
 * `stringResource` and plurals, which are `@Composable` and cannot be called from
 * inside the effect.
 *
 * Keyed on [text], so the effect restarts only when the message actually changes;
 * a second identical message still fires, because consuming the first sets the state
 * to null in between and that is a key change either way. [onConsume] runs *after*
 * the snackbar goes away rather than before: consuming first would null the key and
 * cancel the very snackbar being shown.
 */
@Composable
fun MessageSnackbar(text: String?, onConsume: () -> Unit) {
    val host = LocalSnackbarHostState.current
    LaunchedEffect(text) {
        if (text != null) {
            host.showSnackbar(
                message = text,
                withDismissAction = true,
                duration = SnackbarDuration.Short,
            )
            onConsume()
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

/**
 * One decimal place without `String.format`, which is JVM-only — this code is
 * compiled for iOS too.
 */
fun Double.format1(): String {
    val rounded = kotlin.math.round(this * 10) / 10
    val negative = rounded < 0
    val absolute = kotlin.math.abs(rounded)
    val whole = absolute.toLong()
    val decimal = kotlin.math.round((absolute - whole) * 10).toLong()
    // Rounding the fraction can carry (9.97 -> 10.0), so fold the carry back in
    // rather than rendering "9.10".
    return if (decimal >= 10) {
        "${if (negative) "-" else ""}${whole + 1}.0"
    } else {
        "${if (negative) "-" else ""}$whole.$decimal"
    }
}

fun Double.signed1(): String = if (this >= 0) "+${format1()}" else format1()
