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
public val history_icon: ImageVector
    get() {
        if (_history != null) {
            return _history!!
        }
        _history =
            ImageVector.Builder(
                name = "history",
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
                        moveTo(12f, 21f)
                        quadTo(8.55f, 21f, 5.988f, 18.713f)
                        reflectiveQuadTo(3.05f, 13f)
                        lineTo(5.1f, 13f)
                        quadTo(5.45f, 15.6f, 7.413f, 17.3f)
                        reflectiveQuadTo(12f, 19f)
                        quadTo(14.925f, 19f, 16.963f, 16.963f)
                        reflectiveQuadTo(19f, 12f)
                        quadTo(19f, 9.075f, 16.963f, 7.038f)
                        reflectiveQuadTo(12f, 5f)
                        quadTo(10.275f, 5f, 8.775f, 5.8f)
                        reflectiveQuadTo(6.25f, 8f)
                        lineTo(9f, 8f)
                        lineTo(9f, 10f)
                        lineTo(3f, 10f)
                        lineTo(3f, 4f)
                        lineTo(5f, 4f)
                        lineTo(5f, 6.35f)
                        quadTo(6.275f, 4.75f, 8.113f, 3.875f)
                        reflectiveQuadTo(12f, 3f)
                        quadTo(13.875f, 3f, 15.513f, 3.713f)
                        reflectiveQuadTo(18.363f, 5.638f)
                        quadTo(19.575f, 6.85f, 20.288f, 8.488f)
                        reflectiveQuadTo(21f, 12f)
                        quadTo(21f, 13.875f, 20.288f, 15.513f)
                        reflectiveQuadTo(18.363f, 18.363f)
                        quadTo(17.15f, 19.575f, 15.513f, 20.288f)
                        reflectiveQuadTo(12f, 21f)
                        close()
                        moveTo(14.8f, 16.2f)
                        lineTo(11f, 12.4f)
                        lineTo(11f, 7f)
                        lineTo(13f, 7f)
                        lineTo(13f, 11.6f)
                        lineTo(16.2f, 14.8f)
                        lineTo(14.8f, 16.2f)
                        close()
                    }
                }
                .build()
        return _history!!
    }

private var _history: ImageVector? = null
