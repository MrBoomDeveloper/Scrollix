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
public val done_all_icon: ImageVector
    get() {
        if (_doneAll != null) {
            return _doneAll!!
        }
        _doneAll =
            ImageVector.Builder(
                name = "done_all",
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
                        moveTo(18f, 7f)
                        lineTo(16.59f, 5.59f)
                        lineTo(10.25f, 11.93f)
                        lineTo(11.66f, 13.34f)
                        close()

                        moveTo(22.24f, 5.59f)
                        lineTo(11.66f, 16.17f)
                        lineTo(7.48f, 12f)
                        lineTo(6.07f, 13.41f)
                        lineTo(11.66f, 19f)
                        lineTo(23.66f, 7f)
                        close()

                        moveTo(0.41f, 13.41f)
                        lineTo(6f, 19f)
                        lineTo(7.41f, 17.59f)
                        lineTo(1.83f, 12f)
                        close()
                    }
                }
                .build()
        return _doneAll!!
    }

private var _doneAll: ImageVector? = null
