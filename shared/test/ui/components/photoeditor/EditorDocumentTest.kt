package com.mrboomdev.scrollix.ui.components.photoeditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EditorDocumentTest {

    @Test
    fun `rotating clockwise turns the crop with the image`() {
        val rotated = EditorDocument(crop = CropRect(0.2f, 0.1f, 0.6f, 0.5f))
            .rotatedClockwise()

        assertRect(CropRect(0.5f, 0.2f, 0.9f, 0.6f), rotated.crop)
        assertEquals(1, rotated.rotation)
    }

    @Test
    fun `flipping mirrors the crop`() {
        val flipped = EditorDocument(crop = CropRect(0.2f, 0.1f, 0.6f, 0.5f))
            .flippedHorizontally()

        assertRect(CropRect(0.4f, 0.1f, 0.8f, 0.5f), flipped.crop)
        assertTrue(flipped.flipH)
    }

    @Test
    fun `a centred crop keeps a centred layer in place`() {
        val document = EditorDocument(
            layers = listOf(TextLayer(text = "hi", x = 0.5f, y = 0.5f, size = 0.1f))
        )

        val cropped = document.withCrop(CropRect(0.25f, 0.25f, 0.75f, 0.75f))
        val layer = cropped.layers.single() as TextLayer

        assertEquals(0.5f, layer.x, 0.0001f)
        assertEquals(0.5f, layer.y, 0.0001f)
    }

    @Test
    fun `cropping off centre moves the layer with the picture`() {
        val document = EditorDocument(
            layers = listOf(StickerLayer(glyph = "★", x = 0.5f, y = 0.5f, size = 0.2f))
        )

        val cropped = document.withCrop(CropRect(0f, 0f, 0.5f, 0.5f))
        val layer = cropped.layers.single() as StickerLayer

        assertEquals(1f, layer.x, 0.0001f)
        assertEquals(1f, layer.y, 0.0001f)
    }

    @Test
    fun `a full crop is not an edit but a tighter one is`() {
        assertFalse(EditorDocument().hasEdits)
        assertTrue(EditorDocument(crop = CropRect(0f, 0f, 0.9f, 1f)).hasEdits)
        assertTrue(EditorDocument(filter = FilterPreset.Warm).hasEdits)
    }

    @Test
    fun `undo and redo walk the history`() {
        val session = EditorSession()

        session.apply(EditorDocument(rotation = 1))
        session.apply(EditorDocument(rotation = 2))

        assertTrue(session.canUndo)

        session.undo()
        assertEquals(1, session.document.rotation)
        assertTrue(session.canRedo)

        session.redo()
        assertEquals(2, session.document.rotation)
    }

    @Test
    fun `reset returns to an untouched document`() {
        val session = EditorSession()
        session.apply(EditorDocument(rotation = 3, filter = FilterPreset.Mono))
        session.reset()

        assertFalse(session.document.hasEdits)
        assertTrue(session.canUndo)
    }

    @Test
    fun `a gesture is a single undo step`() {
        val session = EditorSession()
        session.beginGesture()
        session.updateGesture(EditorDocument(rotation = 1))
        session.updateGesture(EditorDocument(rotation = 2))
        session.endGesture()

        session.undo()
        assertEquals(0, session.document.rotation)
    }

    @Test
    fun `mono preset turns red into grey`() {
        val mono = assertNotNull(FilterPreset.Mono.matrix)
        val result = PixelImage(1, 1, intArrayOf(0xFFFF0000.toInt())).withColorMatrix(mono)
        val pixel = result.pixels.single()

        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF

        assertEquals(red, green)
        assertEquals(green, blue)
        assertEquals(255, (pixel ushr 24) and 0xFF)
    }

    @Test
    fun `rotation swaps the dimensions and moves the pixels`() {
        val source = PixelImage(
            width = 2,
            height = 1,
            pixels = intArrayOf(0xFF0000FF.toInt(), 0xFFFF0000.toInt())
        )

        val rotated = source.rotatedQuarterTurns(1)

        assertEquals(1, rotated.width)
        assertEquals(2, rotated.height)
        assertEquals(0xFF0000FF.toInt(), rotated.pixels[0])
        assertEquals(0xFFFF0000.toInt(), rotated.pixels[1])
    }

    @Test
    fun `flipping the pixels mirrors every row`() {
        val source = PixelImage(
            width = 2,
            height = 1,
            pixels = intArrayOf(1, 2)
        )

        val flipped = source.rotatedAndFlipped(turns = 0, flipH = true, flipV = false)

        assertEquals(2, flipped.pixels[0])
        assertEquals(1, flipped.pixels[1])
    }

    @Test
    fun `crop copies the requested window`() {
        val pixels = IntArray(4 * 4) { index -> index }
        val source = PixelImage(4, 4, pixels)

        val cropped = source.cropped(CropRect(0.5f, 0.5f, 1f, 1f))

        assertEquals(2, cropped.width)
        assertEquals(2, cropped.height)
        assertEquals(pixels[2 * 4 + 2], cropped.pixels[0])
        assertEquals(pixels[3 * 4 + 3], cropped.pixels[3])
    }

    @Test
    fun `a stroke paints its colour into the pixels`() {
        val source = PixelImage(8, 8, IntArray(64) { 0xFF000000.toInt() })
        val stroke = StrokeLayer(
            points = listOf(NormPoint(0.5f, 0.5f)),
            color = 0xFFFFFFFF,
            width = 0.5f
        )

        val painted = source.drawStrokes(listOf(stroke))

        assertEquals(0xFFFFFFFF.toInt(), painted.pixels[4 * 8 + 4])
        assertEquals(0xFF000000.toInt(), painted.pixels[0])
    }

    @Test
    fun `aspect preset produces a proportional window`() {
        val crop = centredCropFor(EditorAspect.Square, imageAspect = 1f)

        assertEquals(1f, crop.width, 0.0001f)
        assertEquals(1f, crop.height, 0.0001f)
    }

    @Test
    fun `resizing a crop corner keeps the opposite corner`() {
        val resized = resizeCrop(
            crop = CropRect.Full,
            corner = 0,
            x = 0.25f,
            y = 0.25f,
            targetRatio = null,
            imageAspect = 1f
        )

        assertEquals(0.25f, resized.left, 0.0001f)
        assertEquals(0.25f, resized.top, 0.0001f)
        assertEquals(1f, resized.right, 0.0001f)
        assertEquals(1f, resized.bottom, 0.0001f)
    }

    @Test
    fun `edited file name keeps the original base`() {
        assertEquals("IMG_1234-edited.jpg", editedFileName("/tmp/a/IMG_1234.png", "jpg"))
    }

    private fun assertRect(expected: CropRect, actual: CropRect) {
        assertEquals(expected.left, actual.left, 0.0001f)
        assertEquals(expected.top, actual.top, 0.0001f)
        assertEquals(expected.right, actual.right, 0.0001f)
        assertEquals(expected.bottom, actual.bottom, 0.0001f)
    }
}
