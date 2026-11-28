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
public val poll_icon: ImageVector
    get() {
        if (_insert_chart != null) {
            return _insert_chart!!
        }
        _insert_chart =
            ImageVector.Builder(
                name = "insert_chart",
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
                        moveTo(7f, 17f)
                        horizontalLineTo(9f)
                        verticalLineTo(10f)
                        horizontalLineTo(7f)
                        verticalLineToRelative(7f)
                        close()
                        moveToRelative(4f, 0f)
                        horizontalLineToRelative(2f)
                        verticalLineTo(7f)
                        horizontalLineTo(11f)
                        verticalLineTo(17f)
                        close()
                        moveToRelative(4f, 0f)
                        horizontalLineToRelative(2f)
                        verticalLineTo(13f)
                        horizontalLineTo(15f)
                        verticalLineToRelative(4f)
                        close()
                        moveTo(5f, 21f)
                        quadTo(4.18f, 21f, 3.59f, 20.41f)
                        reflectiveQuadTo(3f, 19f)
                        verticalLineTo(5f)
                        quadTo(3f, 4.17f, 3.59f, 3.59f)
                        reflectiveQuadTo(5f, 3f)
                        horizontalLineTo(19f)
                        quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                        reflectiveQuadTo(21f, 5f)
                        verticalLineTo(19f)
                        quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                        reflectiveQuadTo(19f, 21f)
                        horizontalLineTo(5f)
                        close()
                        moveTo(5f, 19f)
                        horizontalLineTo(19f)
                        verticalLineTo(5f)
                        horizontalLineTo(5f)
                        verticalLineTo(19f)
                        close()
                        moveTo(5f, 5f)
                        verticalLineTo(19f)
                        verticalLineTo(5f)
                        close()
                    }
                }
                .build()
        return _insert_chart!!
    }

private var _insert_chart: ImageVector? = null
