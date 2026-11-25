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
public val folder_icon: ImageVector
    get() {
        if (_folder != null) {
            return _folder!!
        }
        _folder =
            ImageVector.Builder(
                name = "folder",
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
                        moveTo(4f, 20f)
                        quadTo(3.175f, 20f, 2.588f, 19.413f)
                        reflectiveQuadTo(2f, 18f)
                        lineTo(2f, 6f)
                        quadTo(2f, 5.175f, 2.588f, 4.588f)
                        reflectiveQuadTo(4f, 4f)
                        lineTo(10f, 4f)
                        lineTo(12f, 6f)
                        lineTo(20f, 6f)
                        quadTo(20.825f, 6f, 21.413f, 6.588f)
                        reflectiveQuadTo(22f, 8f)
                        lineTo(22f, 18f)
                        quadTo(22f, 18.825f, 21.413f, 19.413f)
                        reflectiveQuadTo(20f, 20f)
                        lineTo(4f, 20f)
                        close()
                        moveTo(4f, 18f)
                        lineTo(20f, 18f)
                        lineTo(20f, 8f)
                        lineTo(11.175f, 8f)
                        lineTo(9.175f, 6f)
                        lineTo(4f, 6f)
                        lineTo(4f, 18f)
                        close()
                        moveTo(4f, 18f)
                        lineTo(4f, 6f)
                        lineTo(4f, 18f)
                        close()
                    }
                }
                .build()
        return _folder!!
    }

private var _folder: ImageVector? = null
