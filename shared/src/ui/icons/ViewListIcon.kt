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
public val view_list_icon: ImageVector
    get() {
        if (_view_list != null) {
            return _view_list!!
        }
        _view_list =
            ImageVector.Builder(
                name = "view_list",
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
                        moveTo(9f, 18f)
                        lineTo(20f, 18f)
                        lineTo(20f, 15.325f)
                        lineTo(9f, 15.325f)
                        lineTo(9f, 18f)
                        close()
                        moveTo(4f, 8.675f)
                        lineTo(7f, 8.675f)
                        lineTo(7f, 6f)
                        lineTo(4f, 6f)
                        lineTo(4f, 8.675f)
                        close()
                        moveTo(4f, 13.35f)
                        lineTo(7f, 13.35f)
                        lineTo(7f, 10.675f)
                        lineTo(4f, 10.675f)
                        lineTo(4f, 13.35f)
                        close()
                        moveTo(4f, 18f)
                        lineTo(7f, 18f)
                        lineTo(7f, 15.325f)
                        lineTo(4f, 15.325f)
                        lineTo(4f, 18f)
                        close()
                        moveTo(9f, 13.35f)
                        lineTo(20f, 13.35f)
                        lineTo(20f, 10.675f)
                        lineTo(9f, 10.675f)
                        lineTo(9f, 13.35f)
                        close()
                        moveTo(9f, 8.675f)
                        lineTo(20f, 8.675f)
                        lineTo(20f, 6f)
                        lineTo(9f, 6f)
                        lineTo(9f, 8.675f)
                        close()
                        moveTo(4f, 20f)
                        quadTo(3.175f, 20f, 2.588f, 19.413f)
                        reflectiveQuadTo(2f, 18f)
                        lineTo(2f, 6f)
                        quadTo(2f, 5.175f, 2.588f, 4.588f)
                        reflectiveQuadTo(4f, 4f)
                        lineTo(20f, 4f)
                        quadTo(20.825f, 4f, 21.413f, 4.588f)
                        reflectiveQuadTo(22f, 6f)
                        lineTo(22f, 18f)
                        quadTo(22f, 18.825f, 21.413f, 19.413f)
                        reflectiveQuadTo(20f, 20f)
                        lineTo(4f, 20f)
                        close()
                    }
                }
                .build()
        return _view_list!!
    }

private var _view_list: ImageVector? = null
