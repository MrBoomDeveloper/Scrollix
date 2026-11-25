package com.mrboomdev.scrollix.ui.components

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

class ZoomOffsetTest {
    @Test
    fun `a photo that is not zoomed never pans`() {
        assertEquals(
            Offset.Zero,
            clampZoomOffset(Offset(50f, 40f), scale = 1f, containerWidth = 1000, containerHeight = 800)
        )
    }

    @Test
    fun `a pan inside the bounds is kept`() {
        // At 2x the content extends 500px to each side on a 1000px container.
        assertEquals(
            Offset(120f, -300f),
            clampZoomOffset(Offset(120f, -300f), scale = 2f, containerWidth = 1000, containerHeight = 800)
        )
    }

    @Test
    fun `a pan past the edge is clamped on both axes`() {
        assertEquals(
            Offset(500f, -400f),
            clampZoomOffset(Offset(9_000f, -9_000f), scale = 2f, containerWidth = 1000, containerHeight = 800)
        )
    }

    @Test
    fun `a container that is not measured yet cannot pan`() {
        assertEquals(
            Offset.Zero,
            clampZoomOffset(Offset(10f, 10f), scale = 3f, containerWidth = 0, containerHeight = 0)
        )
    }
}
