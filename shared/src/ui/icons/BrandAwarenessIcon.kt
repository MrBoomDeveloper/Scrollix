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
public val brand_awareness_icon: ImageVector
    get() {
        if (_brand_awareness != null) {
            return _brand_awareness!!
        }
        _brand_awareness =
            ImageVector.Builder(
                name = "brand_awareness",
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
                        moveTo(16f, 13f)
                        verticalLineTo(11f)
                        horizontalLineToRelative(4f)
                        verticalLineToRelative(2f)
                        horizontalLineTo(16f)
                        close()
                        moveToRelative(1.2f, 7f)
                        lineTo(14f, 17.6f)
                        lineTo(15.2f, 16f)
                        lineToRelative(3.2f, 2.4f)
                        lineTo(17.2f, 20f)
                        close()
                        moveTo(15.2f, 8f)
                        lineTo(14f, 6.4f)
                        lineTo(17.2f, 4f)
                        lineToRelative(1.2f, 1.6f)
                        lineTo(15.2f, 8f)
                        close()
                        moveTo(3f, 15f)
                        verticalLineTo(9f)
                        horizontalLineTo(7f)
                        lineTo(12f, 4f)
                        verticalLineTo(20f)
                        lineTo(7f, 15f)
                        horizontalLineTo(3f)
                        close()
                        moveTo(10f, 8.85f)
                        lineTo(7.85f, 11f)
                        horizontalLineTo(5f)
                        verticalLineToRelative(2f)
                        horizontalLineTo(7.85f)
                        lineTo(10f, 15.15f)
                        verticalLineTo(8.85f)
                        close()
                        moveTo(7.5f, 12f)
                        close()
                    }
                }
                .build()
        return _brand_awareness!!
    }

private var _brand_awareness: ImageVector? = null
