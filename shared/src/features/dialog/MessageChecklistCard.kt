package com.mrboomdev.scrollix.features.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

import com.mrboomdev.scrollix.data.model.MessageChecklistInfo
import com.mrboomdev.scrollix.data.model.MessageChecklistTaskInfo
import com.mrboomdev.scrollix.ui.icons.check_icon

/**
 * A checklist message: the title, how much of the list is done and one tappable
 * row per task. Tapping is only offered when the provider says this account may
 * mark tasks done.
 */
@Composable
internal fun MessageChecklistCard(
    checklist: MessageChecklistInfo,
    isOutgoing: Boolean,
    customEmojiData: Map<Long, ByteArray>,
    customEmojiAdaptive: Set<Long> = emptySet(),
    onToggleTask: (MessageChecklistTaskInfo) -> Unit
) {
    val contentColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.secondary
    }
    val accent = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .width(260.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isOutgoing) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            LocalContentColor provides contentColor
        ) {
            CustomEmojiMessageText(
                text = checklist.title.ifBlank { "Checklist" },
                emojis = checklist.customEmojis,
                data = customEmojiData,
                adaptiveEmojis = customEmojiAdaptive
            )
        }

        checklist.tasks.forEach { task ->
            ChecklistTaskRow(
                task = task,
                enabled = checklist.canMarkTasksAsDone,
                contentColor = contentColor,
                secondaryColor = secondaryColor,
                accent = accent,
                customEmojiData = customEmojiData,
                customEmojiAdaptive = customEmojiAdaptive,
                onClick = { onToggleTask(task) }
            )
        }

        Text(
            text = "${checklist.completedCount} of ${checklist.tasks.size} done",
            style = MaterialTheme.typography.labelSmall,
            color = secondaryColor
        )
    }
}

@Composable
private fun ChecklistTaskRow(
    task: MessageChecklistTaskInfo,
    enabled: Boolean,
    contentColor: Color,
    secondaryColor: Color,
    accent: Color,
    customEmojiData: Map<Long, ByteArray>,
    customEmojiAdaptive: Set<Long>,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .let { row ->
                if (enabled) row.clickable(onClick = onClick) else row
            }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .border(1.5.dp, if (task.isDone) accent else secondaryColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (task.isDone) {
                Icon(
                    modifier = Modifier.size(11.dp),
                    imageVector = check_icon,
                    contentDescription = "Done",
                    tint = accent
                )
            }
        }

        CustomEmojiMessageText(
            modifier = Modifier.weight(1f, fill = false),
            text = task.text,
            emojis = task.customEmojis,
            data = customEmojiData,
            adaptiveEmojis = customEmojiAdaptive,
            style = MaterialTheme.typography.bodyMedium.copy(
                textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                color = if (task.isDone) secondaryColor else contentColor
            )
        )
    }
}
