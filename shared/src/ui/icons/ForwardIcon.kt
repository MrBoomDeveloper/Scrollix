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
public val forward_icon: ImageVector
    get() {
        if (_forward != null) {
            return _forward!!
        }
        _forward =
            ImageVector.Builder(
                name = "forward",
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
                        moveTo(16f, 17f)
                        lineTo(14.58f, 15.6f)
                        lineTo(19.18f, 11f)
                        lineTo(14.58f, 6.4f)
                        lineTo(16f, 5f)
                        lineToRelative(6f, 6f)
                        lineToRelative(-6f, 6f)
                        close()
                        moveTo(2f, 19f)
                        verticalLineTo(15f)
                        quadTo(2f, 12.93f, 3.46f, 11.46f)
                        reflectiveQuadTo(7f, 10f)
                        horizontalLineToRelative(6.18f)
                        lineTo(9.58f, 6.4f)
                        lineTo(11f, 5f)
                        lineToRelative(6f, 6f)
                        lineToRelative(-6f, 6f)
                        lineTo(9.58f, 15.6f)
                        lineTo(13.18f, 12f)
                        horizontalLineTo(7f)
                        quadTo(5.75f, 12f, 4.88f, 12.88f)
                        reflectiveQuadTo(4f, 15f)
                        verticalLineToRelative(4f)
                        horizontalLineTo(2f)
                        close()
                    }
                }
                .build()
        return _forward!!
    }

private var _forward: ImageVector? = null
