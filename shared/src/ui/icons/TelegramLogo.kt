package com.mrboomdev.scrollix.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Rounded Telegram brand mark used next to Telegram accounts and profiles. */
@Composable
fun TelegramLogoIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp
) {
    Canvas(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF229ED9))
    ) {
        val plane = Path().apply {
            moveTo(this@Canvas.size.width * 0.20f, this@Canvas.size.height * 0.52f)
            lineTo(this@Canvas.size.width * 0.82f, this@Canvas.size.height * 0.22f)
            lineTo(this@Canvas.size.width * 0.68f, this@Canvas.size.height * 0.78f)
            lineTo(this@Canvas.size.width * 0.52f, this@Canvas.size.height * 0.60f)
            lineTo(this@Canvas.size.width * 0.38f, this@Canvas.size.height * 0.72f)
            lineTo(this@Canvas.size.width * 0.40f, this@Canvas.size.height * 0.55f)
            close()
        }

        drawPath(path = plane, color = Color.White)
        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = this@Canvas.size.minDimension * 0.015f,
            center = Offset(
                this@Canvas.size.width * 0.52f,
                this@Canvas.size.height * 0.60f
            )
        )
    }
}
