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
public val reply_icon: ImageVector
    get() {
        if (_reply != null) {
            return _reply!!
        }
        _reply =
            ImageVector.Builder(
                name = "reply",
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
                        moveTo(19f, 19f)
                        verticalLineTo(15f)
                        quadToRelative(0f, -1.25f, -0.88f, -2.13f)
                        reflectiveQuadTo(16f, 12f)
                        horizontalLineTo(6.83f)
                        lineToRelative(3.6f, 3.6f)
                        lineTo(9f, 17f)
                        lineTo(3f, 11f)
                        lineTo(9f, 5f)
                        lineToRelative(1.43f, 1.4f)
                        lineTo(6.83f, 10f)
                        horizontalLineTo(16f)
                        quadToRelative(2.07f, 0f, 3.54f, 1.46f)
                        quadTo(21f, 12.93f, 21f, 15f)
                        verticalLineToRelative(4f)
                        horizontalLineTo(19f)
                        close()
                    }
                }
                .build()
        return _reply!!
    }

private var _reply: ImageVector? = null
