package com.mrboomdev.scrollix.ui.golden

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.model.ChatMessage
import com.mrboomdev.scrollix.data.model.DialogKind
import com.mrboomdev.scrollix.ui.components.ComposerInputRow
import com.mrboomdev.scrollix.ui.components.JustifiedMediaGrid
import com.mrboomdev.scrollix.ui.components.JustifiedMediaItem
import com.mrboomdev.scrollix.ui.components.MessageBubble
import com.mrboomdev.scrollix.ui.components.MessageBubbleConnection
import com.mrboomdev.scrollix.ui.theme.appTypography
import java.util.TimeZone
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Renders real UI components offscreen and compares them with committed PNGs.
 *
 * Keep fixtures deterministic: fixed width, fixed message timestamp and no
 * animated or network-backed content. Regenerate goldens only for an intended
 * visual change, with `SCROLLIX_UPDATE_GOLDENS=1`.
 */
@OptIn(ExperimentalTestApi::class)
class GoldenImageTest {
    @BeforeTest
    fun useDeterministicTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @Test
    fun outgoingTextBubbleMatchesGolden() = runComposeUiTest {
        setContent {
            GoldenTheme {
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .background(Color(0xFF0E1621))
                        .padding(8.dp)
                        .testTag(BubbleTag)
                ) {
                    MessageBubble(
                        modifier = Modifier.fillMaxWidth(),
                        dialogKind = DialogKind.Direct,
                        connection = MessageBubbleConnection.NONE,
                        message = ChatMessage(
                            providerId = "telegram",
                            accountId = "golden",
                            providerDialogId = "1",
                            messageId = 1,
                            senderName = "Alice",
                            senderId = 2,
                            text = "Golden bubble with a wrapped second line.",
                            timestamp = 1_700_000_000_000L,
                            isOutgoing = true,
                            isRead = true
                        )
                    )
                }
            }
        }

        waitForIdle()
        GoldenImageSupport.assertMatches(
            name = "message-bubble-outgoing",
            image = onNodeWithTag(BubbleTag).captureToImage()
        )
    }

    @Test
    fun justifiedMediaGridMatchesGolden() = runComposeUiTest {
        val colors = listOf(
            Color(0xFF2E7D32),
            Color(0xFF1565C0),
            Color(0xFFC62828),
            Color(0xFF6A1B9A),
            Color(0xFFEF6C00)
        )

        setContent {
            GoldenTheme {
                Box(
                    modifier = Modifier
                        .size(width = 360.dp, height = 260.dp)
                        .background(Color(0xFF0E1621))
                        .testTag(GridTag)
                ) {
                    JustifiedMediaGrid(
                        items = listOf(
                            JustifiedMediaItem(key = "wide", aspectRatio = 1.6f),
                            JustifiedMediaItem(key = "portrait", aspectRatio = 0.75f),
                            JustifiedMediaItem(key = "square", aspectRatio = 1f),
                            JustifiedMediaItem(key = "wide-2", aspectRatio = 1.4f),
                            JustifiedMediaItem(key = "tall", aspectRatio = 0.6f)
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) { item, tileModifier ->
                        Box(
                            modifier = tileModifier.background(
                                colors[item.key.toString().hashCode().mod(colors.size)]
                            )
                        )
                    }
                }
            }
        }

        waitForIdle()
        GoldenImageSupport.assertMatches(
            name = "justified-media-grid",
            image = onNodeWithTag(GridTag).captureToImage()
        )
    }

    @Composable
    private fun GoldenTheme(content: @Composable () -> Unit) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                background = Color.Black,
                surfaceContainer = Color(0xFF101010),
                secondaryContainer = Color(0xFF3A1E5A)
            ),
            typography = appTypography(),
            content = content
        )
    }

    @Test
    fun composerWithTextMatchesGolden() = runComposeUiTest {
        setContent {
            GoldenTheme {
                var input by remember { mutableStateOf("Golden composer text") }
                val interaction = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .width(420.dp)
                        .background(Color(0xFF0E1621))
                        .testTag(ComposerTag)
                ) {
                    ComposerInputRow(
                        input = input,
                        inputSelection = TextRange(input.length),
                        inputCustomEmojis = emptyList(),
                        inputEmojiData = emptyMap(),
                        inputEmojiAdaptive = emptySet(),
                        isSending = false,
                        canAttach = true,
                        hasAttachments = false,
                        showAttachmentMenu = false,
                        showEmojiPicker = false,
                        videoMessageMode = false,
                        isRecording = false,
                        emojiButtonInteraction = interaction,
                        onAttachmentMenuChange = {},
                        onPickImages = {},
                        onPickFiles = {},
                        onAddPoll = {},
                        onAddLocation = {},
                        onEmojiAnchorChanged = {},
                        onToggleEmojiPicker = {},
                        onInputChange = { value, _ -> input = value },
                        onPasteImages = { false },
                        onToggleVideoMessageMode = {},
                        onBeginRecording = {},
                        onSend = {}
                    )
                }
            }
        }

        waitForIdle()
        GoldenImageSupport.assertMatches(
            name = "composer-with-text",
            image = onNodeWithTag(ComposerTag).captureToImage()
        )
    }

    private companion object {
        const val BubbleTag = "golden-bubble"
        const val GridTag = "golden-grid"
        const val ComposerTag = "golden-composer"
    }
}
