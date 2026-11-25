package com.mrboomdev.scrollix.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val push_pin_icon: ImageVector
    get() {
        if (_push_pin != null) {
            return _push_pin!!
        }
        _push_pin =
            ImageVector.Builder(
                name = "push_pin",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            )
                .apply {
                    path(
                        fill = SolidColor(Color.Black),
                        fillAlpha = 1f,
                        stroke = null,
                        strokeAlpha = 1f,
                        strokeLineWidth = 1f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Bevel,
                        strokeLineMiter = 1f,
                        pathFillType = PathFillType.NonZero,
                    ) {
                        moveTo(16f, 12f)
                        lineTo(18f, 14f)
                        lineTo(18f, 16f)
                        lineTo(13f, 16f)
                        lineTo(13f, 22f)
                        lineTo(12f, 23f)
                        lineTo(11f, 22f)
                        lineTo(11f, 16f)
                        lineTo(6f, 16f)
                        lineTo(6f, 14f)
                        lineTo(8f, 12f)
                        lineTo(8f, 5f)
                        lineTo(7f, 5f)
                        lineTo(7f, 3f)
                        lineTo(17f, 3f)
                        lineTo(17f, 5f)
                        lineTo(16f, 5f)
                        close()
                        moveTo(8.85f, 7f)
                        lineTo(15.15f, 7f)
                        lineTo(14f, 12.85f)
                        lineTo(14f, 5f)
                        lineTo(10f, 5f)
                        lineTo(10f, 12.85f)
                        close()
                        moveTo(12f, 14f)
                        close()
                    }
                }
                .build()
        return _push_pin!!
    }

private var _push_pin: ImageVector? = null
