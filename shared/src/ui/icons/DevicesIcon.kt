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
public val devices_icon: ImageVector
    get() {
        if (_devices != null) {
            return _devices!!
        }
        _devices =
            ImageVector.Builder(
                name = "devices",
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
                        moveTo(2f, 20f)
                        lineTo(2f, 17f)
                        lineTo(4f, 17f)
                        lineTo(4f, 6f)
                        quadTo(4f, 5.175f, 4.588f, 4.588f)
                        reflectiveQuadTo(6f, 4f)
                        lineTo(21f, 4f)
                        lineTo(21f, 6f)
                        lineTo(6f, 6f)
                        lineTo(6f, 17f)
                        lineTo(12f, 17f)
                        lineTo(12f, 20f)
                        lineTo(2f, 20f)
                        close()
                        moveTo(15f, 20f)
                        quadTo(14.575f, 20f, 14.288f, 19.713f)
                        reflectiveQuadTo(14f, 19f)
                        lineTo(14f, 9f)
                        quadTo(14f, 8.575f, 14.288f, 8.287f)
                        reflectiveQuadTo(15f, 8f)
                        lineTo(21f, 8f)
                        quadTo(21.425f, 8f, 21.713f, 8.287f)
                        reflectiveQuadTo(22f, 9f)
                        lineTo(22f, 19f)
                        quadTo(22f, 19.425f, 21.713f, 19.713f)
                        reflectiveQuadTo(21f, 20f)
                        lineTo(15f, 20f)
                        close()
                        moveTo(16f, 17f)
                        lineTo(20f, 17f)
                        lineTo(20f, 10f)
                        lineTo(16f, 10f)
                        lineTo(16f, 17f)
                        close()
                        moveTo(16f, 17f)
                        lineTo(20f, 17f)
                        lineTo(16f, 17f)
                        close()
                    }
                }
                .build()
        return _devices!!
    }

private var _devices: ImageVector? = null
