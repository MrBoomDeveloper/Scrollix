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
public val storage_icon: ImageVector
    get() {
        if (_storage != null) {
            return _storage!!
        }
        _storage =
            ImageVector.Builder(
                name = "storage",
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
                        moveTo(3f, 20f)
                        lineTo(3f, 16f)
                        lineTo(21f, 16f)
                        lineTo(21f, 20f)
                        lineTo(3f, 20f)
                        close()
                        moveTo(5f, 19f)
                        lineTo(7f, 19f)
                        lineTo(7f, 17f)
                        lineTo(5f, 17f)
                        lineTo(5f, 19f)
                        close()
                        moveTo(3f, 8f)
                        lineTo(3f, 4f)
                        lineTo(21f, 4f)
                        lineTo(21f, 8f)
                        lineTo(3f, 8f)
                        close()
                        moveTo(5f, 7f)
                        lineTo(7f, 7f)
                        lineTo(7f, 5f)
                        lineTo(5f, 5f)
                        lineTo(5f, 7f)
                        close()
                        moveTo(3f, 14f)
                        lineTo(3f, 10f)
                        lineTo(21f, 10f)
                        lineTo(21f, 14f)
                        lineTo(3f, 14f)
                        close()
                        moveTo(5f, 13f)
                        lineTo(7f, 13f)
                        lineTo(7f, 11f)
                        lineTo(5f, 11f)
                        lineTo(5f, 13f)
                        close()
                    }
                }
                .build()
        return _storage!!
    }

private var _storage: ImageVector? = null
