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
public val select_check_box_icon: ImageVector
    get() {
        if (_select_check_box != null) {
            return _select_check_box!!
        }
        _select_check_box =
            ImageVector.Builder(
                name = "select_check_box",
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
                        moveTo(5f, 21f)
                        quadTo(4.18f, 21f, 3.59f, 20.41f)
                        reflectiveQuadTo(3f, 19f)
                        verticalLineTo(5f)
                        quadTo(3f, 4.17f, 3.59f, 3.59f)
                        reflectiveQuadTo(5f, 3f)
                        horizontalLineTo(19f)
                        quadToRelative(0.2f, 0f, 0.38f, 0.04f)
                        reflectiveQuadToRelative(0.35f, 0.11f)
                        lineTo(17.88f, 5f)
                        horizontalLineTo(5f)
                        verticalLineTo(19f)
                        horizontalLineTo(19f)
                        verticalLineTo(12.35f)
                        lineToRelative(2f, -2f)
                        verticalLineTo(19f)
                        quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                        reflectiveQuadTo(19f, 21f)
                        horizontalLineTo(5f)
                        close()
                        moveToRelative(6.53f, -4f)
                        lineTo(5.88f, 11.35f)
                        lineToRelative(1.4f, -1.4f)
                        lineToRelative(4.25f, 4.25f)
                        lineTo(20.7f, 5.02f)
                        lineTo(22.13f, 6.4f)
                        lineTo(11.53f, 17f)
                        close()
                    }
                }
                .build()
        return _select_check_box!!
    }

private var _select_check_box: ImageVector? = null
