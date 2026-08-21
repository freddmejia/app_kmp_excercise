package botix.dev.myweeklyroutinegymapp.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
