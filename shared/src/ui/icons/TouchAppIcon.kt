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
public val touch_app_icon: ImageVector
    get() {
        if (_touch_app != null) {
            return _touch_app!!
        }
        _touch_app =
            ImageVector.Builder(
                name = "touch_app",
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
                        moveTo(10.475f, 22f)
                        quadTo(9.775f, 22f, 9.162f, 21.7f)
                        reflectiveQuadTo(8.125f, 20.85f)
                        lineTo(2.675f, 13.925f)
                        lineTo(3.15f, 13.425f)
                        quadTo(3.65f, 12.9f, 4.35f, 12.8f)
                        reflectiveQuadTo(5.65f, 13.075f)
                        lineTo(7.5f, 14.2f)
                        lineTo(7.5f, 6f)
                        quadTo(7.5f, 5.575f, 7.788f, 5.288f)
                        reflectiveQuadTo(8.5f, 5f)
                        quadTo(8.925f, 5f, 9.225f, 5.288f)
                        reflectiveQuadTo(9.525f, 6f)
                        lineTo(9.525f, 17.8f)
                        lineTo(7.1f, 16.3f)
                        lineTo(9.7f, 19.625f)
                        quadTo(9.85f, 19.8f, 10.05f, 19.9f)
                        reflectiveQuadTo(10.475f, 20f)
                        lineTo(16f, 20f)
                        quadTo(16.825f, 20f, 17.413f, 19.413f)
                        reflectiveQuadTo(18f, 18f)
                        lineTo(18f, 14f)
                        quadTo(18f, 13.575f, 17.713f, 13.288f)
                        reflectiveQuadTo(17f, 13f)
                        lineTo(11.525f, 13f)
                        lineTo(11.525f, 11f)
                        lineTo(17f, 11f)
                        quadTo(18.25f, 11f, 19.125f, 11.875f)
                        reflectiveQuadTo(20f, 14f)
                        lineTo(20f, 18f)
                        quadTo(20f, 19.65f, 18.825f, 20.825f)
                        reflectiveQuadTo(16f, 22f)
                        lineTo(10.475f, 22f)
                        close()
                        moveTo(4.175f, 8.5f)
                        quadTo(3.85f, 7.95f, 3.675f, 7.312f)
                        reflectiveQuadTo(3.5f, 6f)
                        quadTo(3.5f, 3.925f, 4.963f, 2.463f)
                        reflectiveQuadTo(8.5f, 1f)
                        quadTo(10.575f, 1f, 12.038f, 2.463f)
                        reflectiveQuadTo(13.5f, 6f)
                        quadTo(13.5f, 6.675f, 13.325f, 7.312f)
                        reflectiveQuadTo(12.825f, 8.5f)
                        lineTo(11.1f, 7.5f)
                        quadTo(11.3f, 7.15f, 11.4f, 6.788f)
                        reflectiveQuadTo(11.5f, 6f)
                        quadTo(11.5f, 4.75f, 10.625f, 3.875f)
                        reflectiveQuadTo(8.5f, 3f)
                        quadTo(7.25f, 3f, 6.375f, 3.875f)
                        reflectiveQuadTo(5.5f, 6f)
                        quadTo(5.5f, 6.425f, 5.6f, 6.788f)
                        reflectiveQuadTo(5.9f, 7.5f)
                        lineTo(4.175f, 8.5f)
                        close()
                        moveTo(12.55f, 15.5f)
                        close()
                    }
                }
                .build()
        return _touch_app!!
    }

private var _touch_app: ImageVector? = null
