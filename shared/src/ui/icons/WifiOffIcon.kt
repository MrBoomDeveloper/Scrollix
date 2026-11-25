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
public val wifi_off_icon: ImageVector
    get() {
        if (_wifi_off != null) {
            return _wifi_off!!
        }
        _wifi_off =
            ImageVector.Builder(
                name = "wifi_off",
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
                        moveTo(19.75f, 22.6f)
                        lineTo(10.35f, 13.15f)
                        quadTo(9.175f, 13.425f, 8.162f, 13.975f)
                        reflectiveQuadTo(6.35f, 15.35f)
                        lineTo(4.25f, 13.2f)
                        quadTo(5.05f, 12.4f, 5.975f, 11.8f)
                        reflectiveQuadTo(7.95f, 10.75f)
                        lineTo(5.7f, 8.5f)
                        quadTo(4.675f, 9.025f, 3.788f, 9.663f)
                        reflectiveQuadTo(2.1f, 11.1f)
                        lineTo(0f, 8.95f)
                        quadTo(0.8f, 8.15f, 1.663f, 7.513f)
                        reflectiveQuadTo(3.5f, 6.3f)
                        lineTo(1.4f, 4.2f)
                        lineTo(2.8f, 2.8f)
                        lineTo(21.2f, 21.2f)
                        lineTo(19.75f, 22.6f)
                        close()
                        moveTo(12f, 21f)
                        quadTo(10.95f, 21f, 10.225f, 20.263f)
                        reflectiveQuadTo(9.5f, 18.5f)
                        quadTo(9.5f, 17.45f, 10.225f, 16.725f)
                        reflectiveQuadTo(12f, 16f)
                        quadTo(13.05f, 16f, 13.775f, 16.725f)
                        reflectiveQuadTo(14.5f, 18.5f)
                        quadTo(14.5f, 19.525f, 13.775f, 20.263f)
                        reflectiveQuadTo(12f, 21f)
                        close()
                        moveTo(17.9f, 15.05f)
                        lineTo(17.175f, 14.325f)
                        lineTo(16.45f, 13.6f)
                        lineTo(12.85f, 10f)
                        quadTo(14.875f, 10.2f, 16.637f, 11.025f)
                        reflectiveQuadTo(19.75f, 13.2f)
                        lineTo(17.9f, 15.05f)
                        close()
                        moveTo(21.9f, 11.1f)
                        quadTo(19.975f, 9.175f, 17.438f, 8.088f)
                        reflectiveQuadTo(12f, 7f)
                        quadTo(11.475f, 7f, 10.988f, 7.038f)
                        reflectiveQuadTo(10f, 7.15f)
                        lineTo(7.45f, 4.6f)
                        quadTo(8.55f, 4.3f, 9.688f, 4.15f)
                        reflectiveQuadTo(12f, 4f)
                        quadTo(15.55f, 4f, 18.625f, 5.325f)
                        reflectiveQuadTo(24f, 8.95f)
                        lineTo(21.9f, 11.1f)
                        close()
                    }
                }
                .build()
        return _wifi_off!!
    }

private var _wifi_off: ImageVector? = null
