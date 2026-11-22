package com.mrboomdev.scrollix.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class MessageAlbumsTest {
    @Test
    fun `groups consecutive album items into one message`() {
        val messages = listOf(
            message(id = 1, albumId = 7, media = photo(10), text = "Caption"),
            message(id = 2, albumId = 7, media = photo(20)),
            message(id = 3, albumId = 7, media = photo(30))
        )

        val grouped = messages.groupMessageAlbums()

        assertEquals(1, grouped.size)
        assertEquals(listOf(1L, 2L, 3L), grouped.single().allMessageIds)
        assertEquals(listOf(10, 20, 30), grouped.single().allMedia.map { it.fileId })
        assertEquals(1L, grouped.single().messageId)
        assertEquals("Caption", grouped.single().text)
    }

    @Test
    fun `keeps separate albums and standalone messages apart`() {
        val messages = listOf(
            message(id = 1, albumId = 7, media = photo(10)),
            message(id = 2, albumId = 7, media = photo(20)),
            message(id = 3, media = photo(30)),
            message(id = 4, albumId = 8, media = photo(40)),
            message(id = 5, albumId = 8, media = photo(50))
        )

        val grouped = messages.groupMessageAlbums()

        assertEquals(3, grouped.size)
        assertEquals(listOf(1L, 2L), grouped[0].allMessageIds)
        assertEquals(listOf(3L), grouped[1].allMessageIds)
        assertEquals(listOf(4L, 5L), grouped[2].allMessageIds)
    }

    @Test
    fun `uses the newest item for time and the first caption`() {
        val messages = listOf(
            message(id = 1, albumId = 7, media = photo(10), timestamp = 100),
            message(id = 2, albumId = 7, media = photo(20), timestamp = 200, text = "Second")
        )

        val grouped = messages.groupMessageAlbums().single()

        assertEquals(200, grouped.timestamp)
        assertEquals("Second", grouped.text)
        assertEquals(2, grouped.allMedia.size)
    }

    @Test
    fun `merges album items split across a page boundary`() {
        val messages = listOf(
            message(id = 5, albumId = 7, media = photo(50)),
            message(id = 6, media = photo(60)),
            message(id = 7, albumId = 7, media = photo(70))
        )

        val grouped = messages.groupMessageAlbums()

        assertEquals(2, grouped.size)
        assertEquals(listOf(5L, 7L), grouped[0].allMessageIds)
        assertEquals(listOf(6L), grouped[1].allMessageIds)
    }

    @Test
    fun `row id follows the album even when the base changes`() {
        val firstLoaded = message(id = 10, albumId = 7, media = photo(10))
        val earlierLoaded = message(id = 8, albumId = 7, media = photo(20))

        assertEquals(firstLoaded.rowId, earlierLoaded.rowId)
        assertEquals("account:telegram:album:7", firstLoaded.rowId)
    }

    private fun message(
        id: Long,
        albumId: Long? = null,
        media: MessageMedia? = null,
        text: String = "",
        timestamp: Long = id * 100
    ) = ChatMessage(
        providerId = "telegram",
        accountId = "account",
        providerDialogId = "dialog",
        messageId = id,
        senderId = 1,
        text = text,
        timestamp = timestamp,
        isOutgoing = false,
        media = media,
        mediaAlbumId = albumId
    )

    private fun photo(fileId: Int) = MessageMedia(
        kind = MessageMediaKind.Photo,
        previewFileId = fileId,
        fileId = fileId,
        width = 100,
        height = 100
    )
}
