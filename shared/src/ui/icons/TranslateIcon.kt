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
public val translate_icon: ImageVector
    get() {
        if (_translate != null) {
            return _translate!!
        }
        _translate =
            ImageVector.Builder(
                name = "translate",
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
                        moveTo(12.87f, 15.07f)
                        lineTo(10.33f, 12.56f)
                        lineTo(10.36f, 12.53f)
                        arcTo(17.52f, 17.52f, 0f, false, false, 14.07f, 6f)
                        lineTo(17f, 6f)
                        lineTo(17f, 4f)
                        lineTo(10f, 4f)
                        lineTo(10f, 2f)
                        lineTo(8f, 2f)
                        lineTo(8f, 4f)
                        lineTo(1f, 4f)
                        lineTo(1f, 6f)
                        lineTo(12.17f, 6f)
                        curveTo(11.5f, 7.92f, 10.44f, 9.75f, 9f, 11.35f)
                        curveTo(8.07f, 10.32f, 7.3f, 9.19f, 6.69f, 8f)
                        lineTo(4.69f, 8f)
                        curveTo(5.42f, 9.63f, 6.42f, 11.17f, 7.67f, 12.56f)
                        lineTo(2.58f, 17.58f)
                        lineTo(4f, 19f)
                        lineTo(9f, 14f)
                        lineTo(12.11f, 17.11f)
                        lineTo(12.87f, 15.07f)
                        close()
                        moveTo(18.5f, 10f)
                        lineTo(16.5f, 10f)
                        lineTo(12f, 22f)
                        lineTo(14f, 22f)
                        lineTo(15.12f, 19f)
                        lineTo(19.87f, 19f)
                        lineTo(21f, 22f)
                        lineTo(23f, 22f)
                        lineTo(18.5f, 10f)
                        close()
                        moveTo(15.88f, 17f)
                        lineTo(17.5f, 12.67f)
                        lineTo(19.12f, 17f)
                        lineTo(15.88f, 17f)
                        close()
                    }
                }
                .build()
        return _translate!!
    }

private var _translate: ImageVector? = null
