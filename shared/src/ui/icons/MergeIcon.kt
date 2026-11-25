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
public val merge_icon: ImageVector
    get() {
        if (_merge != null) {
            return _merge!!
        }
        _merge =
            ImageVector.Builder(
                name = "call_merge",
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
                        moveTo(7.4f, 20f)
                        lineTo(6f, 18.6f)
                        lineTo(11f, 13.6f)
                        lineTo(11f, 6.875f)
                        lineTo(8.425f, 9.45f)
                        lineTo(7f, 8.025f)
                        lineTo(12f, 3.025f)
                        lineTo(17.025f, 8.05f)
                        lineTo(15.6f, 9.475f)
                        lineTo(13f, 6.875f)
                        lineTo(13f, 14.4f)
                        lineTo(7.4f, 20f)
                        close()
                        moveTo(16.6f, 20.025f)
                        lineTo(13.4f, 16.85f)
                        lineTo(14.825f, 15.425f)
                        lineTo(18f, 18.625f)
                        lineTo(16.6f, 20.025f)
                        close()
                    }
                }
                .build()
        return _merge!!
    }

private var _merge: ImageVector? = null
