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
public val gif_box_icon_filled: ImageVector
    get() {
        if (_gif_box != null) {
            return _gif_box!!
        }
        _gif_box =
            ImageVector.Builder(
                name = "gif_box",
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
                        pathFillType = PathFillType.Companion.NonZero,
                    ) {
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
                        moveToRelative(6f, -6f)
                        horizontalLineToRelative(1.5f)
                        verticalLineTo(9f)
                        horizontalLineTo(11f)
                        verticalLineToRelative(6f)
                        close()
                        moveTo(7f, 15f)
                        horizontalLineTo(9f)
                        quadToRelative(0.43f, 0f, 0.71f, -0.29f)
                        reflectiveQuadTo(10f, 14f)
                        verticalLineTo(12f)
                        horizontalLineTo(8.5f)
                        verticalLineToRelative(1.5f)
                        horizontalLineToRelative(-1f)
                        verticalLineToRelative(-3f)
                        horizontalLineTo(10f)
                        verticalLineTo(10f)
                        quadTo(10f, 9.57f, 9.71f, 9.29f)
                        reflectiveQuadTo(9f, 9f)
                        horizontalLineTo(7f)
                        quadTo(6.58f, 9f, 6.29f, 9.29f)
                        reflectiveQuadTo(6f, 10f)
                        verticalLineToRelative(4f)
                        quadToRelative(0f, 0.42f, 0.29f, 0.71f)
                        reflectiveQuadTo(7f, 15f)
                        close()
                        moveToRelative(7f, 0f)
                        horizontalLineToRelative(1.5f)
                        verticalLineTo(13f)
                        horizontalLineToRelative(2f)
                        verticalLineTo(11.5f)
                        horizontalLineToRelative(-2f)
                        verticalLineToRelative(-1f)
                        horizontalLineToRelative(3f)
                        verticalLineTo(9f)
                        horizontalLineTo(14f)
                        verticalLineToRelative(6f)
                        close()
                    }
                }
                .build()
        return _gif_box!!
    }

private var _gif_box: ImageVector? = null
