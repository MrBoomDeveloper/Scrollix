package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.ui.icons.check_icon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Picks the language the chat translator targets. [currentCode] is the stored
 * explicit choice (null follows the device), while [deviceLanguage] is already
 * resolved so the first row can name it.
 */
@Composable
internal fun TranslationLanguageDialog(
    currentCode: String?,
    deviceLanguage: String,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Translate chat") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                item(key = "device") {
                    TranslationLanguageRow(
                        title = "Device language",
                        subtitle = translationLanguageLabel(deviceLanguage),
                        selected = currentCode == null,
                        onClick = { onSelect(null) }
                    )
                }

                items(TranslationLanguages, key = { language -> language.code }) { language ->
                    TranslationLanguageRow(
                        title = language.title,
                        subtitle = translationLanguageLabel(language.code)
                            .takeIf { label -> label != language.title }
                            ?: language.code.uppercase(),
                        selected = currentCode == language.code,
                        onClick = { onSelect(language.code) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun TranslationLanguageRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        if (selected) {
            Icon(
                modifier = Modifier.size(18.dp),
                imageVector = check_icon,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
