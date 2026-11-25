package com.mrboomdev.scrollix.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.dp

/** Title of the row a settings search result asked to reveal. */
internal val LocalSettingsHighlightTitle = compositionLocalOf<String?> { null }

/**
 * Whether a row title answers the search result [query]. Index titles are not
 * always the visible heading ("Traffic statistics" points at the "Traffic"
 * section), so a longer query may match a shorter anchor and the other way
 * around. Very short fragments are ignored to keep a stray word from tinting
 * half the section.
 */
internal fun String.matchesSettingsQuery(query: String): Boolean {
    if (length < 4 || query.length < 4) return equals(query, ignoreCase = true)

    return equals(query, ignoreCase = true) ||
        contains(query, ignoreCase = true) ||
        query.contains(this, ignoreCase = true)
}

/** Y offset of every anchored settings row inside its section column. */
internal val LocalSettingsRowOffsets = staticCompositionLocalOf<MutableMap<String, Int>> {
    mutableMapOf()
}

/**
 * Marks a settings row as searchable under [title]: its offset is registered
 * for scrolling, and the row is tinted while a search result points at it.
 *
 * Adding a setting stays local. Render the row with this modifier and add one
 * [SettingsEntry] to the index; the section layout, search results and the
 * mobile/desktop navigation need no further change.
 */
@Composable
internal fun Modifier.settingsAnchor(title: String): Modifier {
    val highlighted = LocalSettingsHighlightTitle.current
        ?.let { query -> title.matchesSettingsQuery(query) } == true

    val offsets = LocalSettingsRowOffsets.current

    return this
        .onGloballyPositioned { coordinates ->
            offsets[title] = coordinates.positionInParent().y.toInt()
        }
        .background(
            color = if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.Transparent
            },
            shape = RoundedCornerShape(12.dp)
        )
        .padding(
            horizontal = if (highlighted) 8.dp else 0.dp,
            vertical = if (highlighted) 6.dp else 0.dp
        )
}

/** Groups a non-switch settings row under a searchable [title]. */
@Composable
internal fun SettingsRowGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.settingsAnchor(title)) {
        content()
    }
}
