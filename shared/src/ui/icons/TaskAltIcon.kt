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
public val task_alt_icon: ImageVector
    get() {
        if (_task_alt != null) {
            return _task_alt!!
        }
        _task_alt =
            ImageVector.Builder(
                name = "task_alt",
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
                        moveTo(12f, 22f)
                        quadTo(9.93f, 22f, 8.1f, 21.21f)
                        quadTo(6.28f, 20.43f, 4.93f, 19.08f)
                        quadTo(3.58f, 17.73f, 2.79f, 15.9f)
                        reflectiveQuadTo(2f, 12f)
                        quadTo(2f, 9.92f, 2.79f, 8.1f)
                        quadTo(3.58f, 6.27f, 4.93f, 4.93f)
                        quadTo(6.28f, 3.57f, 8.1f, 2.79f)
                        quadTo(9.93f, 2f, 12f, 2f)
                        quadToRelative(1.63f, 0f, 3.08f, 0.47f)
                        reflectiveQuadTo(17.75f, 3.8f)
                        lineTo(16.3f, 5.27f)
                        quadTo(15.35f, 4.67f, 14.28f, 4.34f)
                        reflectiveQuadTo(12f, 4f)
                        quadTo(8.68f, 4f, 6.34f, 6.34f)
                        reflectiveQuadTo(4f, 12f)
                        reflectiveQuadToRelative(2.34f, 5.66f)
                        reflectiveQuadTo(12f, 20f)
                        reflectiveQuadToRelative(5.66f, -2.34f)
                        reflectiveQuadTo(20f, 12f)
                        quadToRelative(0f, -0.45f, -0.05f, -0.9f)
                        reflectiveQuadTo(19.8f, 10.23f)
                        lineTo(21.43f, 8.6f)
                        quadToRelative(0.28f, 0.8f, 0.43f, 1.65f)
                        reflectiveQuadTo(22f, 12f)
                        quadToRelative(0f, 2.07f, -0.79f, 3.9f)
                        reflectiveQuadToRelative(-2.14f, 3.17f)
                        quadToRelative(-1.35f, 1.35f, -3.17f, 2.14f)
                        reflectiveQuadTo(12f, 22f)
                        close()
                        moveTo(10.6f, 16.6f)
                        lineTo(6.35f, 12.35f)
                        lineToRelative(1.4f, -1.4f)
                        lineTo(10.6f, 13.8f)
                        lineTo(20.6f, 3.77f)
                        lineTo(22f, 5.18f)
                        lineTo(10.6f, 16.6f)
                        close()
                    }
                }
                .build()
        return _task_alt!!
    }

private var _task_alt: ImageVector? = null
