package botix.dev.myweeklyroutinegymapp.presentation.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import botix.dev.myweeklyroutinegymapp.presentation.preview.PreviewSurface
import botix.dev.myweeklyroutinegymapp.presentation.theme.GymIcons
import myweeklyroutinegymapp.shared.generated.resources.Res
import myweeklyroutinegymapp.shared.generated.resources.categories_title
import myweeklyroutinegymapp.shared.generated.resources.muscles_title
import myweeklyroutinegymapp.shared.generated.resources.preferences_categories_hint
import myweeklyroutinegymapp.shared.generated.resources.preferences_muscles_hint
import org.jetbrains.compose.resources.stringResource

/**
 * Where the catalogues live: muscles, and the categories that say what kinds of
 * training exist.
 *
 * Muscles and categories were reached from buttons in the corner of the two screens
 * that *use* them — the muscle catalogue from the log, categories from the activity
 * list. That put configuration inside the thing being configured: the log screen's
 * header carried a control that had nothing to do with logging, and the two
 * catalogues, which are the same kind of thing, were kept in different places and
 * found by different routes.
 *
 * They are neither logging nor browsing. They are the shape of the data every other
 * screen reads, edited rarely and deliberately, which is what a settings section is
 * for. A tab costs a permanent slot in the navigation bar; the alternative is what
 * this replaces — the same screens hidden behind buttons that had to be discovered
 * one by one.
 *
 * No ViewModel: this screen holds nothing and reads nothing. It is a signpost, and
 * giving it state to be wrong about would be the only way it could break.
 */
@Composable
fun PreferencesScreen(
    onOpenMuscles: () -> Unit,
    onOpenCategories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PreferenceRow(
            title = stringResource(Res.string.muscles_title),
            // Each row says what the catalogue is *for* rather than repeating its
            // name in a longer form. "Muscles" alone does not tell you that this is
            // the list the log screen offers you, or why it would ever be empty.
            hint = stringResource(Res.string.preferences_muscles_hint),
            onClick = onOpenMuscles,
        )
        PreferenceRow(
            title = stringResource(Res.string.categories_title),
            hint = stringResource(Res.string.preferences_categories_hint),
            onClick = onOpenCategories,
        )
    }
}

/** The same card-is-the-button row every list in this app uses, with no data in it. */
@Composable
private fun PreferenceRow(
    title: String,
    hint: String,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    hint,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = GymIcons.ChevronRight,
                // Decorative: the card carries the click, so the row is announced
                // once rather than once for itself and once for an arrow.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --- Previews ---------------------------------------------------------------

/**
 * The whole screen, not a `…Content` half of one: holding no state is what lets this
 * screen be previewed exactly as the app composes it.
 */
@PreviewLightDark
@Composable
private fun PreferencesScreenPreview() {
    PreviewSurface {
        PreferencesScreen(onOpenMuscles = {}, onOpenCategories = {})
    }
}

@Preview
@Composable
private fun PreferenceRowPreview() {
    PreviewSurface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PreferenceRow(
                title = "Muscles",
                hint = "The list the log screen offers you.",
                onClick = {},
            )
            // A hint long enough to wrap: the chevron must stay on its own edge rather
            // than being pushed off by the text beside it.
            PreferenceRow(
                title = "Categorías",
                hint = "Cómo se agrupan tus actividades en la pestaña Actividades.",
                onClick = {},
            )
        }
    }
}
