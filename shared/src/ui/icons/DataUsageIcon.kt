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
public val data_usage_icon: ImageVector
    get() {
        if (_data_usage != null) {
            return _data_usage!!
        }
        _data_usage =
            ImageVector.Builder(
                name = "data_usage",
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
                        quadTo(9.925f, 22f, 8.1f, 21.213f)
                        reflectiveQuadTo(4.925f, 19.062f)
                        quadTo(3.575f, 17.7f, 2.788f, 15.875f)
                        reflectiveQuadTo(2f, 12f)
                        quadTo(2f, 8.075f, 4.6f, 5.25f)
                        reflectiveQuadTo(11f, 2.05f)
                        lineTo(11f, 5.05f)
                        quadTo(8.425f, 5.4f, 6.713f, 7.363f)
                        reflectiveQuadTo(5f, 12f)
                        quadTo(5f, 14.9f, 7.05f, 16.95f)
                        reflectiveQuadTo(12f, 19f)
                        quadTo(13.65f, 19f, 15.088f, 18.3f)
                        reflectiveQuadTo(17.5f, 16.4f)
                        lineTo(20.1f, 17.9f)
                        quadTo(18.75f, 19.775f, 16.625f, 20.888f)
                        reflectiveQuadTo(12f, 22f)
                        close()
                        moveTo(21.15f, 16.05f)
                        lineTo(18.55f, 14.55f)
                        quadTo(18.775f, 13.95f, 18.887f, 13.312f)
                        reflectiveQuadTo(19f, 12f)
                        quadTo(19f, 9.325f, 17.288f, 7.363f)
                        reflectiveQuadTo(13f, 5.05f)
                        lineTo(13f, 2.05f)
                        quadTo(16.8f, 2.425f, 19.4f, 5.25f)
                        reflectiveQuadTo(22f, 12f)
                        quadTo(22f, 13.1f, 21.8f, 14.125f)
                        reflectiveQuadTo(21.15f, 16.05f)
                        close()
                    }
                }
                .build()
        return _data_usage!!
    }

private var _data_usage: ImageVector? = null
