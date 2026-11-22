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
public val keep_off_icon: ImageVector
    get() {
        if (_keep_off != null) {
            return _keep_off!!
        }
        _keep_off =
            ImageVector.Builder(
                name = "keep_off",
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
                        moveTo(17f, 3f)
                        verticalLineTo(5f)
                        horizontalLineTo(16f)
                        verticalLineToRelative(8.17f)
                        lineToRelative(-2f, -2f)
                        verticalLineTo(5f)
                        horizontalLineTo(10f)
                        verticalLineTo(7.18f)
                        lineTo(7.83f, 5f)
                        lineTo(7f, 4.17f)
                        verticalLineTo(3f)
                        horizontalLineTo(17f)
                        close()
                        moveTo(12f, 23f)
                        lineTo(11f, 22f)
                        verticalLineTo(16f)
                        horizontalLineTo(6f)
                        verticalLineTo(14f)
                        lineTo(8f, 12f)
                        verticalLineTo(10.85f)
                        lineTo(1.4f, 4.2f)
                        lineTo(2.8f, 2.8f)
                        lineTo(21.2f, 21.2f)
                        lineToRelative(-1.45f, 1.4f)
                        lineTo(13.15f, 16f)
                        horizontalLineTo(13f)
                        verticalLineToRelative(6f)
                        lineToRelative(-1f, 1f)
                        close()
                        moveTo(8.85f, 14f)
                        horizontalLineToRelative(2.3f)
                        lineToRelative(-1.1f, -1.1f)
                        lineTo(10f, 12.85f)
                        lineTo(8.85f, 14f)
                        close()
                        moveTo(12f, 9.17f)
                        close()
                        moveTo(10.05f, 12.9f)
                        close()
                    }
                }
                .build()
        return _keep_off!!
    }

private var _keep_off: ImageVector? = null
