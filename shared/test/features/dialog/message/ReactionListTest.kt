package com.mrboomdev.scrollix.features.dialog.message

import com.mrboomdev.scrollix.data.model.MessageReactionInfo
import com.mrboomdev.scrollix.data.model.MessageReactionReactor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReactionListTest {
    @Test
    fun `the unlisted tail is reported as extra reactors`() {
        val reaction = reaction(count = 7, reactors = listOf(reactor(1), reactor(2), reactor(3)))

        assertEquals(4, reactionExtraCount(reaction))
    }

    @Test
    fun `a fully listed reaction has no extra reactors`() {
        val listed = reaction(count = 2, reactors = listOf(reactor(1), reactor(2)))

        assertEquals(0, reactionExtraCount(listed))
        assertEquals(0, reactionExtraCount(reaction(count = 0, reactors = emptyList())))
    }

    @Test
    fun `a stale count never produces a negative tail`() {
        // An update can lower the count before the reactor list refreshes.
        assertEquals(0, reactionExtraCount(reaction(count = 1, reactors = listOf(reactor(1), reactor(2)))))
    }

    @Test
    fun `only the reactors the count accounts for are shown`() {
        val visible = reactionVisibleReactors(
            reaction(count = 2, reactors = listOf(reactor(1), reactor(2), reactor(3)))
        )

        assertEquals(listOf(1L, 2L), visible.map { it.id })
    }

    @Test
    fun `an unknown reactor keeps its id and an empty name`() {
        val unknown = reaction(count = 1, reactors = listOf(MessageReactionReactor(id = 99)))

        assertTrue(unknown.reactors.single().name.isEmpty())
        assertEquals(1, reactionVisibleReactors(unknown).size)
    }

    private fun reaction(count: Int, reactors: List<MessageReactionReactor>): MessageReactionInfo {
        return MessageReactionInfo(
            emoji = "👍",
            count = count,
            reactors = reactors
        )
    }

    private fun reactor(id: Long): MessageReactionReactor {
        return MessageReactionReactor(id = id, name = "User $id")
    }
}
