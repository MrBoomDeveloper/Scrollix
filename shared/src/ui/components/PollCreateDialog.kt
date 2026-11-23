package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.model.CustomEmojiSpan
import com.mrboomdev.scrollix.data.provider.AccountSession
import com.mrboomdev.scrollix.data.storage.readFileBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One option of a poll being composed. */
data class PollOptionDraft(
    val text: String,
    val customEmojis: List<CustomEmojiSpan> = emptyList(),
    val mediaPath: String? = null
)

/** Everything the create-poll dialog collected. */
data class PollDraft(
    val question: String,
    val questionCustomEmojis: List<CustomEmojiSpan> = emptyList(),
    val options: List<PollOptionDraft> = emptyList(),
    val isAnonymous: Boolean = true,
    val allowsMultipleAnswers: Boolean = false,
    val allowsRevoting: Boolean = true,
    val isQuiz: Boolean = false,
    /** Index into [options] of the single correct answer. */
    val correctOptionIndex: Int? = null,
    val explanation: String = ""
)

private data class PollOptionInput(
    val text: String = "",
    val customEmojis: List<CustomEmojiSpan> = emptyList(),
    val media: PickedFile? = null
)

private sealed interface PollEmojiTarget {
    data object Question : PollEmojiTarget
    data class Option(val index: Int) : PollEmojiTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollCreateDialog(
    session: AccountSession?,
    isMobile: Boolean,
    onDismiss: () -> Unit,
    onCreate: (PollDraft) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var questionCustomEmojis by remember { mutableStateOf<List<CustomEmojiSpan>>(emptyList()) }
    var options by remember {
        mutableStateOf(listOf(PollOptionInput(), PollOptionInput()))
    }
    var isAnonymous by remember { mutableStateOf(true) }
    var allowsMultipleAnswers by remember { mutableStateOf(false) }
    var allowsRevoting by remember { mutableStateOf(true) }
    var isQuiz by remember { mutableStateOf(false) }
    var correctOptionIndex by remember { mutableStateOf<Int?>(null) }
    var explanation by remember { mutableStateOf("") }
    var emojiTarget by remember { mutableStateOf<PollEmojiTarget?>(null) }
    var attachmentTarget by remember { mutableStateOf<Int?>(null) }
    var emojiData by remember { mutableStateOf<Map<Long, ByteArray>>(emptyMap()) }
    var emojiAdaptive by remember { mutableStateOf<Set<Long>>(emptySet()) }

    // Custom emoji picked into the question or an option are drawn as stickers,
    // so their assets have to be loaded before the field can render them.
    LaunchedEffect(session, questionCustomEmojis, options) {
        val currentSession = session ?: return@LaunchedEffect
        val ids = (
            questionCustomEmojis.map { span -> span.emojiId } +
                options.flatMap { option -> option.customEmojis.map { it.emojiId } }
            )
            .distinct()
            .filter { id -> id !in emojiData }

        if (ids.isEmpty()) return@LaunchedEffect

        val loaded = ids.mapNotNull { id ->
            currentSession.loadCustomEmoji(id)?.let { asset -> id to asset }
        }.toMap()

        if (loaded.isNotEmpty()) {
            emojiData = emojiData + loaded.mapValues { (_, asset) -> asset.data }
            emojiAdaptive = emojiAdaptive + loaded
                .filterValues { asset -> asset.needsRepainting }
                .keys
        }
    }

    val filePicker = rememberFilePicker(
        onImagesPicked = { picked ->
            val index = attachmentTarget
            attachmentTarget = null
            val file = picked.firstOrNull()

            if (index != null && file != null && index in options.indices) {
                options = options.toMutableList().apply {
                    this[index] = this[index].copy(media = file)
                }
            }
        },
        onFilesPicked = { attachmentTarget = null }
    )

    fun updateQuestion(value: String) {
        questionCustomEmojis = adjustCustomEmojiSpans(
            spans = questionCustomEmojis,
            oldText = question,
            newText = value
        )
        question = value
    }

    fun updateOption(index: Int, value: String) {
        val current = options[index]
        val updated = current.copy(
            text = value,
            customEmojis = adjustCustomEmojiSpans(
                spans = current.customEmojis,
                oldText = current.text,
                newText = value
            )
        )

        options = options.toMutableList().apply { this[index] = updated }
    }

    fun insertEmoji(target: PollEmojiTarget, fallback: String, emojiId: Long?) {
        when (target) {
            PollEmojiTarget.Question -> {
                val start = question.length
                question += fallback

                if (emojiId != null) {
                    questionCustomEmojis = questionCustomEmojis + CustomEmojiSpan(
                        start = start,
                        end = start + fallback.length,
                        emojiId = emojiId
                    )
                }
            }

            is PollEmojiTarget.Option -> {
                if (target.index !in options.indices) return

                val current = options[target.index]
                val start = current.text.length
                val span = emojiId?.let { id ->
                    CustomEmojiSpan(
                        start = start,
                        end = start + fallback.length,
                        emojiId = id
                    )
                }

                val updated = current.copy(
                    text = current.text + fallback,
                    customEmojis = current.customEmojis + listOfNotNull(span)
                )

                options = options.toMutableList().apply { this[target.index] = updated }
            }
        }
    }

    fun create() {
        val filled = options.mapIndexedNotNull { index, option ->
            if (option.text.isNotBlank()) index to option else null
        }

        // The dialog may filter out blank options, so the visible answer index
        // has to be translated into the sent option list.
        val quizAnswer = correctOptionIndex?.let { selected ->
            filled.indexOfFirst { (index, _) -> index == selected }.takeIf { it >= 0 }
        }

        onCreate(
            PollDraft(
                question = question,
                questionCustomEmojis = questionCustomEmojis,
                options = filled.map { (_, option) ->
                    PollOptionDraft(
                        text = option.text,
                        customEmojis = option.customEmojis,
                        mediaPath = option.media?.path
                    )
                },
                isAnonymous = isAnonymous,
                allowsMultipleAnswers = allowsMultipleAnswers && !isQuiz,
                allowsRevoting = if (isQuiz) false else allowsRevoting,
                isQuiz = isQuiz,
                correctOptionIndex = quizAnswer,
                explanation = explanation
            )
        )
    }

    val canCreate = question.isNotBlank() &&
        options.count { option -> option.text.isNotBlank() } >= 2 &&
        (!isQuiz || correctOptionIndex?.let { index ->
            options.getOrNull(index)?.text?.isNotBlank() == true
        } == true)

    val form: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PollEmojiTextField(
                    modifier = Modifier.weight(1f),
                    value = question,
                    onValueChange = ::updateQuestion,
                    label = "Question",
                    customEmojis = questionCustomEmojis,
                    emojiData = emojiData,
                    emojiAdaptive = emojiAdaptive
                )

                TextButton(
                    enabled = session != null,
                    onClick = { emojiTarget = PollEmojiTarget.Question }
                ) {
                    Text("😀")
                }
            }

            options.forEachIndexed { index, option ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        PollEmojiTextField(
                            modifier = Modifier.weight(1f),
                            value = option.text,
                            onValueChange = { value -> updateOption(index, value) },
                            label = "Option ${index + 1}",
                            singleLine = true,
                            customEmojis = option.customEmojis,
                            emojiData = emojiData,
                            emojiAdaptive = emojiAdaptive
                        )

                        TextButton(
                            enabled = session != null,
                            onClick = { emojiTarget = PollEmojiTarget.Option(index) }
                        ) {
                            Text("😀")
                        }

                        TextButton(
                            onClick = {
                                attachmentTarget = index
                                filePicker.pickImages()
                            }
                        ) {
                            Text("🖼")
                        }

                        if (isQuiz) {
                            TextButton(
                                enabled = option.text.isNotBlank(),
                                onClick = { correctOptionIndex = index }
                            ) {
                                Text(
                                    text = if (correctOptionIndex == index) {
                                        "✓ Answer"
                                    } else {
                                        "Answer"
                                    },
                                    color = if (correctOptionIndex == index) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.secondary
                                    }
                                )
                            }
                        }

                        if (options.size > 2) {
                            TextButton(
                                onClick = {
                                    val correct = correctOptionIndex
                                    options = options.filterIndexed { itemIndex, _ ->
                                        itemIndex != index
                                    }
                                    correctOptionIndex = when {
                                        correct == null -> null
                                        correct == index -> null
                                        correct > index -> correct - 1
                                        else -> correct
                                    }
                                }
                            ) {
                                Text("✕")
                            }
                        }
                    }

                    option.media?.let { media ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PollImagePreview(
                                path = media.path,
                                name = media.name
                            )

                            Text(
                                modifier = Modifier.weight(1f),
                                text = media.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )

                            TextButton(
                                onClick = {
                                    options = options.toMutableList().apply {
                                        this[index] = this[index].copy(media = null)
                                    }
                                }
                            ) {
                                Text("Remove image")
                            }
                        }
                    }
                }
            }

            TextButton(
                enabled = options.size < 10,
                onClick = { options = options + PollOptionInput() }
            ) {
                Text("Add option")
            }

            PollSettingRow(
                title = "Anonymous",
                checked = isAnonymous,
                onCheckedChange = { isAnonymous = it }
            )

            PollSettingRow(
                title = "Multiple answers",
                checked = allowsMultipleAnswers && !isQuiz,
                enabled = !isQuiz,
                onCheckedChange = { allowsMultipleAnswers = it }
            )

            if (!isQuiz) {
                PollSettingRow(
                    title = "Allow revoting",
                    subtitle = "Voters can retract or change their vote",
                    checked = allowsRevoting,
                    onCheckedChange = { allowsRevoting = it }
                )
            }

            PollSettingRow(
                title = "Quiz mode",
                subtitle = "Mark one answer as correct and add an explanation",
                checked = isQuiz,
                onCheckedChange = { enabled ->
                    isQuiz = enabled
                    if (enabled) allowsMultipleAnswers = false
                }
            )

            if (isQuiz) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = explanation,
                    onValueChange = { explanation = it },
                    label = { Text("Explanation (optional)") },
                    minLines = 1,
                    maxLines = 3
                )
            }
        }
    }

    if (isMobile) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Create poll",
                    style = MaterialTheme.typography.titleLarge
                )

                form()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Button(enabled = canCreate, onClick = ::create) {
                        Text("Create")
                    }
                }
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Create poll") },
            text = form,
            confirmButton = {
                Button(enabled = canCreate, onClick = ::create) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    emojiTarget?.let { target ->
        if (session != null) {
            EmojiPickerDialog(
                isMobile = isMobile,
                session = session,
                onDismiss = { emojiTarget = null },
                onEmojiSelected = { emoji ->
                    insertEmoji(target, emoji, null)
                    emojiTarget = null
                },
                onCustomEmojiSelected = { emojiId, fallback ->
                    insertEmoji(target, fallback, emojiId)
                    emojiTarget = null
                },
                // The field only accepts emoji, so stickers and GIFs are not
                // offered here.
                emojiOnly = true
            )
        } else {
            AlertDialog(
                onDismissRequest = { emojiTarget = null },
                text = { Text("Sign in to use custom emoji") },
                confirmButton = {
                    TextButton(onClick = { emojiTarget = null }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
private fun PollSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)

            subtitle?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * An outlined field whose custom emoji are drawn as stickers. The real field
 * keeps the caret and selection while its text is transparent, and the emoji
 * text is painted on top of it.
 */
@Composable
private fun PollEmojiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    customEmojis: List<CustomEmojiSpan>,
    emojiData: Map<Long, ByteArray>,
    emojiAdaptive: Set<Long>,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false
) {
    val hasCustomEmojis = customEmojis.isNotEmpty()
    val baseStyle: TextStyle = MaterialTheme.typography.bodyLarge

    Box(modifier) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else 3,
            textStyle = baseStyle.copy(
                color = if (hasCustomEmojis) Color.Transparent else MaterialTheme.colorScheme.onSurface
            )
        )

        if (hasCustomEmojis) {
            CustomEmojiMessageText(
                modifier = Modifier
                    .matchParentSize()
                    .padding(start = 16.dp, end = 16.dp, top = 18.dp),
                text = value,
                style = baseStyle,
                color = MaterialTheme.colorScheme.onSurface,
                emojis = customEmojis,
                data = emojiData,
                adaptiveEmojis = emojiAdaptive
            )
        }
    }
}

/** Thumbnail of the image attached to a poll option. */
@Composable
private fun PollImagePreview(path: String, name: String) {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(path) {
        val bytes = withContext(Dispatchers.IO) { readFileBytes(path) }

        bitmap = bytes?.let { data ->
            withContext(Dispatchers.Default) { decodeImageBitmap(data) }
        }
    }

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let { image ->
            Image(
                bitmap = image,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/**
 * Keeps custom-emoji spans aligned while the poll text is edited. Spans that
 * the edit fully replaces are dropped; their fallback text stays in the field.
 */
internal fun adjustCustomEmojiSpans(
    spans: List<CustomEmojiSpan>,
    oldText: String,
    newText: String
): List<CustomEmojiSpan> {
    if (spans.isEmpty() || oldText == newText) return spans

    val prefix = oldText.commonPrefixWith(newText).length
    val suffix = oldText.commonSuffixWith(newText).length
        .coerceAtMost(minOf(oldText.length, newText.length) - prefix)
    val removedEnd = oldText.length - suffix
    val delta = newText.length - oldText.length

    return spans.mapNotNull { span ->
        when {
            span.end <= prefix -> span
            span.start >= removedEnd -> span.copy(
                start = span.start + delta,
                end = span.end + delta
            )

            else -> null
        }
    }
}
