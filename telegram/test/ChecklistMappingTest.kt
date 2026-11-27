package com.mrboomdev.scrollix.data.api

import dev.g000sha256.tdl.dto.Checklist
import dev.g000sha256.tdl.dto.ChecklistTask
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.MessageChecklist
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.TextEntity
import dev.g000sha256.tdl.dto.TextEntityTypeCustomEmoji

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChecklistMappingTest {
    @Test
    fun `tasks map with their completion state`() {
        val checklist = mapChecklist(
            MessageChecklist(
                Checklist(
                    title = FormattedText(text = "Trip", entities = emptyArray()),
                    tasks = arrayOf(
                        task(id = 1, text = "Book a flight"),
                        task(
                            id = 2,
                            text = "Pack",
                            completedBy = MessageSenderUser(userId = 9),
                            completionDate = 1_700_000_000
                        )
                    ),
                    othersCanAddTasks = false,
                    canAddTasks = false,
                    othersCanMarkTasksAsDone = true,
                    canMarkTasksAsDone = false
                )
            )
        )

        val mapped = requireNotNull(checklist)

        assertEquals("Trip", mapped.title)
        assertEquals(listOf(1, 2), mapped.tasks.map { it.id })
        assertFalse(mapped.tasks.first().isDone)
        assertTrue(mapped.tasks.last().isDone)
        assertEquals(1, mapped.completedCount)
        assertFalse(mapped.canMarkTasksAsDone)
    }

    @Test
    fun `the completion date alone marks a task done`() {
        val checklist = requireNotNull(
            mapChecklist(
                MessageChecklist(
                    Checklist(
                        title = FormattedText(text = "List", entities = emptyArray()),
                        tasks = arrayOf(task(id = 3, text = "Done", completionDate = 1_700_000_000)),
                        othersCanAddTasks = false,
                        canAddTasks = true,
                        othersCanMarkTasksAsDone = false,
                        canMarkTasksAsDone = true
                    )
                )
            )
        )

        assertTrue(checklist.tasks.single().isDone)
        assertTrue(checklist.canMarkTasksAsDone)
    }

    @Test
    fun `custom emoji in a task keep their ranges`() {
        val checklist = requireNotNull(
            mapChecklist(
                MessageChecklist(
                    Checklist(
                        title = FormattedText(text = "Title", entities = emptyArray()),
                        tasks = arrayOf(
                            ChecklistTask(
                                id = 4,
                                text = FormattedText(
                                    text = "Ship it",
                                    entities = arrayOf(
                                        TextEntity(0, 4, TextEntityTypeCustomEmoji(customEmojiId = 42))
                                    )
                                ),
                                completedBy = null,
                                completionDate = 0
                            )
                        ),
                        othersCanAddTasks = false,
                        canAddTasks = false,
                        othersCanMarkTasksAsDone = false,
                        canMarkTasksAsDone = false
                    )
                )
            )
        )

        val span = checklist.tasks.single().customEmojis.single()
        assertEquals(0, span.start)
        assertEquals(4, span.end)
        assertEquals(42, span.emojiId)
    }

    @Test
    fun `other content is not a checklist`() {
        assertNull(
            mapChecklist(
                MessageText(
                    text = FormattedText(text = "hi", entities = emptyArray()),
                    linkPreview = null,
                    linkPreviewOptions = null
                )
            )
        )
    }

    private fun task(
        id: Int,
        text: String,
        completedBy: MessageSenderUser? = null,
        completionDate: Int = 0
    ): ChecklistTask {
        return ChecklistTask(
            id = id,
            text = FormattedText(text = text, entities = emptyArray()),
            completedBy = completedBy,
            completionDate = completionDate
        )
    }
}
