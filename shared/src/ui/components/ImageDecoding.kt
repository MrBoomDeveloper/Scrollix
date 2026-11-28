package com.mrboomdev.scrollix.ui.components

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decodes encoded image bytes for callers that need a real bitmap, such as
 * animation frame lists, the photo editor and video posters. Ordinary UI
 * images go through Coil instead.
 */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?
