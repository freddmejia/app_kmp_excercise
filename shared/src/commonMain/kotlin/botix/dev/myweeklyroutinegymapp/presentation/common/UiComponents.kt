package botix.dev.myweeklyroutinegymapp.presentation.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import botix.dev.myweeklyroutinegymapp.presentation.preview.PreviewSurface
import botix.dev.myweeklyroutinegymapp.presentation.theme.GymIcons
import botix.dev.myweeklyroutinegymapp.presentation.theme.LocalGymAccents
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
 * The controls that belong to a screen, pinned under the app bar.
 *
 * The title used to live here too, on the same line. It is in the app bar now, which
 * is what put the two platforms back in step: without one, iOS drew the screen's own
 * headline against the bare safe-area inset while Android had a window background
 * behind the status bar, so the same screen started at a different place and in a
 * different colour on each. A bar that paints the inset is the only thing both
 * platforms hand you for free.
 *
 * What stays here is the actions, and the reason they are not in the bar's `actions`
 * slot either: that slot is a plain `Row`, and these are text buttons whose labels are
 * translated. A `Row` measures its children in order and gives each one only the width
 * the previous ones left over, so a long label does not push the trailing button off
 * the edge — it measures it to zero, which removes it from the tree entirely: no
 * pixels, and nothing for accessibility or a tap to find. On the Log screen that
 * deleted the one control that creates a log, and the collapsed button then wrapped
 * its own label a letter per line.
 *
 * A width that holds in English is no evidence at all here: "New log" becomes "Nuevo
 * registro". So this is a [FlowRow]: the actions drop to a second line when they do
 * not fit, rather than competing for the first.
 *
 * Trailing-aligned, because that is the edge the reading eye ends at and where the
 * primary action of every one of these screens already sat.
 */
@Composable
fun ScreenActions(
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        actions()
    }
}

/**
 * The pinned footer a form's primary action sits in.
 *
 * ### Why the button leaves the form
 * It used to be the last child of the form's own scrolling column, which meant Save
 * scrolled away with everything else. On a short form that is invisible; on a long one —
 * the activity editor with a muscle catalogue in it — or at a large font scale with the
 * keyboard open, the one control that commits the form is somewhere below the fold, and
 * the user has to scroll past what they were typing to reach it.
 *
 * The old comment on those forms said the opposite: *scrollable rather than fixed, so a
 * large font scale cannot leave the button below the fold.* That reasoning was upside
 * down. Scrolling the button does not keep it reachable, it only guarantees there is a
 * scroll position where it is not — pinning it is what makes it always reachable, and
 * costs the form one bar's height instead.
 *
 * ### Why it is a bar and not a floating button
 * It is a sibling of the scrolling area, not an overlay on it: the scroll viewport ends
 * where this begins, so nothing is ever hidden underneath and the form needs no bottom
 * padding to compensate. [MaterialTheme.colorScheme.surfaceContainer] is the same role
 * the app bar and the navigation bar take, because this is the third bar framing the
 * screen and the three should sit at the same step above it.
 *
 * The keyboard is already handled a level up: the root `Scaffold` carries `imePadding()`,
 * so this rides above the keyboard rather than behind it.
 *
 * A slot rather than a Save button, for the same reason [ScreenActions] is one — the
 * label and the enablement rule belong to the screen, not to its chrome.
 */
@Composable
fun FormActionBar(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
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

/**
 * Wraps one list row so that dragging it to the left asks to delete it.
 *
 * ### Why this is hand-rolled and not `SwipeToDismissBox`
 * It was built on `SwipeToDismissBox` first, and that version deleted a workout log
 * that had never been confirmed: the box reached its dismissed anchor on its own — no
 * gesture involved — and fired the dismiss callback, and mid-drag it painted the red
 * backdrop as a frame *around* a row that had not moved. Both are its own state
 * machine misbehaving, and neither is reachable from the outside to fix. A control
 * that can delete a row nobody swiped is not one to keep for the convenience of it.
 *
 * What is left is the part this app actually needs: a drag, a threshold, and a spring
 * back. There is no dismissed state anywhere in it — nothing here can settle into one.
 *
 * ### The row always springs back
 * A drag past the threshold calls [onDelete] and then animates the row home
 * regardless. That is what lets one component serve both callers: a workout log opens
 * a confirmation the user may cancel, and an activity delete is *refused outright* by
 * the domain once sessions reference it. A row that animated itself away on release
 * would be erasing rows that are still in the database — and since the lists are fed
 * by the database, they would flicker straight back.
 *
 * The row leaves the screen when the data says it has gone, never because it was
 * swiped. Swiping is the request, not the outcome.
 *
 * ### Accessibility
 * A swipe is not reachable by a screen reader, so dropping a Delete button and putting
 * nothing in its place would take away the only route some users have to it. [label]
 * is registered as a custom accessibility action on the row — TalkBack and VoiceOver
 * list it in their actions menu, which is where a gesture-only action belongs.
 */
@Composable
fun SwipeToDelete(
    label: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val accents = LocalGymAccents.current
    val scope = rememberCoroutineScope()
    // The row's own displacement, in pixels, and never positive: this gesture has one
    // direction. An Animatable rather than plain state because the same value is both
    // dragged by hand and animated home, and it must not fight itself when a second
    // drag starts before the first has finished springing back.
    val offsetX = remember { Animatable(0f) }
    var widthPx by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width.toFloat() }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(label) {
                        onDelete()
                        true
                    },
                )
            },
    ) {
        // Painted only while the row is off its resting place. At rest the row covers
        // this exactly, so drawing it anyway would be invisible — until a rounded
        // corner or a half-pixel rounding error let a red edge show through.
        if (offsetX.value < 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    // Matched to the Card in front of it, which is `shapes.medium`.
                    .clip(MaterialTheme.shapes.medium)
                    .background(accents.destructive)
                    .padding(horizontal = 20.dp),
                // Under the edge the row is travelling towards, so the icon is the
                // first thing uncovered and the gesture shows what it will do.
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = GymIcons.Trash,
                    // The custom action above already names this; a description here
                    // would announce a decoration no reader can reach.
                    contentDescription = null,
                    tint = accents.onDestructive,
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        // Clamped at zero on one side so the row cannot be pulled
                        // right onto nothing, and at its own width on the other.
                        scope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceIn(-widthPx, 0f))
                        }
                    },
                    onDragStopped = {
                        val committed = widthPx > 0f &&
                            offsetX.value <= -widthPx * DELETE_SWIPE_FRACTION
                        // Home first, then the callback: a dialog opening on top of a
                        // row still sliding is the one frame where this looks broken.
                        offsetX.animateTo(0f)
                        if (committed) onDelete()
                    },
                ),
        ) {
            content()
        }
    }
}

/**
 * How far left a row must go before releasing it counts as asking to delete.
 *
 * A third of the width. Much less and a horizontal nudge while scrolling the list
 * raises a delete prompt; much more and the gesture needs a deliberate reach across
 * the whole screen.
 */
private const val DELETE_SWIPE_FRACTION = 0.3f

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

/**
 * One decimal, but only when there is one worth showing.
 *
 * For a measurement the user typed: they entered "45" minutes, so reading it back as
 * "45.0 min" makes the app look like it recorded a precision it was never given.
 * A genuine 20.5 keeps its half.
 */
fun Double.formatMeasure(): String = format1().removeSuffix(".0")

// --- Previews ---------------------------------------------------------------

@PreviewLightDark
@Composable
private fun EmptyStatePreview() {
    PreviewSurface {
        Column(modifier = Modifier.padding(16.dp)) {
            EmptyState(text = "No activities yet.")
            // The hinted variant is the one worth checking: the two lines must read as
            // one message, not as a message with an unrelated caption under it.
            EmptyState(
                text = "No muscles in this group.",
                hint = "Add them from the Preferences tab's Muscles screen.",
            )
        }
    }
}

/**
 * The wrap this component exists for.
 *
 * The English labels fit on one line; the Spanish ones do not, and a `Row` would
 * measure the trailing button to zero rather than wrap it — which is how the New-log
 * button once disappeared. Both are previewed side by side so that the wrap is visible
 * as a normal state and not as damage.
 */
@Preview(name = "Fits on one line", widthDp = 360)
@Preview(name = "Wraps", widthDp = 240)
@Composable
private fun ScreenActionsPreview() {
    PreviewSurface {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ScreenActions {
                TextButton(onClick = {}) { Text("Muscles") }
                TextButton(onClick = {}) { Text("New log") }
            }
            ScreenActions {
                TextButton(onClick = {}) { Text("Músculos") }
                TextButton(onClick = {}) { Text("Nuevo registro") }
            }
        }
    }
}

/**
 * The bar against a scrolling body, which is the only way to judge it: on its own it is
 * a button in a box, and what matters is that it reads as pinned *above* content that
 * runs out underneath it.
 *
 * The disabled state is here too, because it is the one a form opens in.
 */
@PreviewLightDark
@Composable
private fun FormActionBarPreview() {
    PreviewSurface {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.weight(1f).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(6) { index -> Text("Form field ${index + 1}") }
            }
            FormActionBar {
                Text(
                    text = "Save is pinned; the fields above scroll.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Interactive mode only.
 *
 * The snackbar is raised from a `LaunchedEffect`, and the static renderer composes one
 * frame without running effects — so this shows an empty scaffold until the preview is
 * run. That is the component working as designed, not a broken preview.
 */
@Preview(heightDp = 200)
@Composable
private fun MessageSnackbarPreview() {
    PreviewSurface {
        Scaffold(
            snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                MessageSnackbar(text = "Log deleted", onConsume = {})
            }
        }
    }
}

/**
 * At rest, which is the only state a static preview can show: the red backdrop is
 * painted while the row is displaced, and nothing displaces it without a drag. Run the
 * preview to swipe it.
 */
@PreviewLightDark
@Composable
private fun SwipeToDeletePreview() {
    PreviewSurface {
        Box(modifier = Modifier.padding(16.dp)) {
            SwipeToDelete(label = "Delete", onDelete = {}) {
                Card {
                    Text(text = "Push day", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Preview(heightDp = 160)
@Composable
private fun LoadingStatePreview() {
    PreviewSurface { LoadingState() }
}

@PreviewLightDark
@Composable
private fun SectionHeaderPreview() {
    PreviewSurface {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(text = "Chest")
            Text(text = "Upper chest", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * The number formatters, rendered rather than asserted.
 *
 * They are not composables, so this is the only place the preview panel can show what
 * they do — and the rounding carry (9.97 renders as "10.0", never "9.10") and the
 * trailing-zero trim are exactly the cases that read wrong at a glance in a test.
 */
@Preview
@Composable
private fun FormattersPreview() {
    val samples = listOf(81.44, 9.97, -2.05, 45.0, 20.5, 0.0)
    PreviewSurface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionHeader(text = "format1 / signed1 / formatMeasure")
            samples.forEach { value ->
                Text(
                    text = "$value  ->  ${value.format1()}   ${value.signed1()}   ${value.formatMeasure()}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
