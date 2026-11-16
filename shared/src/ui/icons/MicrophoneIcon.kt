package com.example.test

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val microphone_icon: ImageVector
    get() {
        if (_mic != null) {
            return _mic!!
        }
        _mic =
            ImageVector.Builder(
                name = "mic",
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
                        moveTo(9.88f, 13.13f)
                        quadTo(9f, 12.25f, 9f, 11f)
                        verticalLineTo(5f)
                        quadTo(9f, 3.75f, 9.88f, 2.88f)
                        reflectiveQuadTo(12f, 2f)
                        reflectiveQuadToRelative(2.13f, 0.88f)
                        reflectiveQuadTo(15f, 5f)
                        verticalLineToRelative(6f)
                        quadToRelative(0f, 1.25f, -0.88f, 2.13f)
                        reflectiveQuadTo(12f, 14f)
                        reflectiveQuadTo(9.88f, 13.13f)
                        close()
                        moveTo(12f, 8f)
                        close()
                        moveTo(11f, 21f)
                        verticalLineTo(17.93f)
                        quadTo(8.4f, 17.58f, 6.7f, 15.6f)
                        reflectiveQuadTo(5f, 11f)
                        horizontalLineTo(7f)
                        quadToRelative(0f, 2.07f, 1.46f, 3.54f)
                        reflectiveQuadTo(12f, 16f)
                        reflectiveQuadToRelative(3.54f, -1.46f)
                        reflectiveQuadTo(17f, 11f)
                        horizontalLineToRelative(2f)
                        quadToRelative(0f, 2.63f, -1.7f, 4.6f)
                        reflectiveQuadTo(13f, 17.93f)
                        verticalLineTo(21f)
                        horizontalLineTo(11f)
                        close()
                        moveToRelative(1.71f, -9.29f)
                        quadTo(13f, 11.43f, 13f, 11f)
                        verticalLineTo(5f)
                        quadTo(13f, 4.57f, 12.71f, 4.29f)
                        reflectiveQuadTo(12f, 4f)
                        reflectiveQuadTo(11.29f, 4.29f)
                        reflectiveQuadTo(11f, 5f)
                        verticalLineToRelative(6f)
                        quadToRelative(0f, 0.42f, 0.29f, 0.71f)
                        reflectiveQuadTo(12f, 12f)
                        reflectiveQuadToRelative(0.71f, -0.29f)
                        close()
                    }
                }
                .build()
        return _mic!!
    }

private var _mic: ImageVector? = null
