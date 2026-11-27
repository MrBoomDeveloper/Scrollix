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
public val scrollix_icon: ImageVector
    get() {
        if (_cards_stack != null) {
            return _cards_stack!!
        }
        _cards_stack =
            ImageVector.Builder(
                name = "cards_stack",
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
                        moveTo(3.25f, 19.27f)
                        lineTo(2.03f, 10.35f)
                        quadTo(1.9f, 9.55f, 2.41f, 8.9f)
                        reflectiveQuadTo(3.73f, 8.13f)
                        lineTo(5.25f, 19f)
                        lineToRelative(7.08f, -1f)
                        horizontalLineTo(19f)
                        quadToRelative(-0.2f, 0.52f, -0.61f, 0.89f)
                        quadToRelative(-0.41f, 0.36f, -1.01f, 0.44f)
                        lineTo(5.45f, 20.98f)
                        quadTo(4.63f, 21.1f, 4f, 20.6f)
                        reflectiveQuadTo(3.25f, 19.27f)
                        close()
                        moveTo(8f, 16.1f)
                        quadToRelative(-0.82f, 0f, -1.41f, -0.59f)
                        quadTo(6f, 14.93f, 6f, 14.1f)
                        verticalLineTo(5f)
                        quadTo(6f, 4.17f, 6.59f, 3.59f)
                        reflectiveQuadTo(8f, 3f)
                        horizontalLineTo(20f)
                        quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                        reflectiveQuadTo(22f, 5f)
                        verticalLineToRelative(9.1f)
                        quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                        reflectiveQuadTo(20f, 16.1f)
                        horizontalLineTo(8f)
                        close()
                        moveToRelative(0f, -2f)
                        horizontalLineTo(20f)
                        verticalLineTo(5f)
                        horizontalLineTo(8f)
                        verticalLineToRelative(9.1f)
                        close()
                        moveToRelative(0f, 0f)
                        verticalLineTo(5f)
                        verticalLineToRelative(9.1f)
                        close()
                        moveTo(5.25f, 19f)
                        close()
                        moveTo(10f, 9f)
                        horizontalLineToRelative(8f)
                        verticalLineTo(7f)
                        horizontalLineTo(10f)
                        verticalLineTo(9f)
                        close()
                        moveToRelative(0f, 3f)
                        horizontalLineToRelative(5f)
                        verticalLineTo(10f)
                        horizontalLineTo(10f)
                        verticalLineToRelative(2f)
                        close()
                    }
                }
                .build()
        return _cards_stack!!
    }

private var _cards_stack: ImageVector? = null
