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
public val info_icon: ImageVector
    get() {
        if (_info != null) {
            return _info!!
        }
        _info =
            ImageVector.Builder(
                name = "info",
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
                        moveTo(11f, 17f)
                        lineTo(13f, 17f)
                        lineTo(13f, 11f)
                        lineTo(11f, 11f)
                        lineTo(11f, 17f)
                        close()
                        moveTo(12f, 9f)
                        quadTo(12.425f, 9f, 12.713f, 8.713f)
                        reflectiveQuadTo(13f, 8f)
                        quadTo(13f, 7.575f, 12.713f, 7.288f)
                        reflectiveQuadTo(12f, 7f)
                        quadTo(11.575f, 7f, 11.288f, 7.288f)
                        reflectiveQuadTo(11f, 8f)
                        quadTo(11f, 8.425f, 11.288f, 8.713f)
                        reflectiveQuadTo(12f, 9f)
                        close()
                        moveTo(12f, 22f)
                        quadTo(9.925f, 22f, 8.1f, 21.213f)
                        reflectiveQuadTo(4.925f, 19.075f)
                        quadTo(3.575f, 17.725f, 2.788f, 15.9f)
                        reflectiveQuadTo(2f, 12f)
                        quadTo(2f, 9.925f, 2.788f, 8.1f)
                        reflectiveQuadTo(4.925f, 4.925f)
                        quadTo(6.275f, 3.575f, 8.1f, 2.788f)
                        reflectiveQuadTo(12f, 2f)
                        quadTo(14.075f, 2f, 15.9f, 2.788f)
                        reflectiveQuadTo(19.075f, 4.925f)
                        quadTo(20.425f, 6.275f, 21.213f, 8.1f)
                        reflectiveQuadTo(22f, 12f)
                        quadTo(22f, 14.075f, 21.213f, 15.9f)
                        reflectiveQuadTo(19.075f, 19.075f)
                        quadTo(17.725f, 20.425f, 15.9f, 21.213f)
                        reflectiveQuadTo(12f, 22f)
                        close()
                        moveTo(12f, 20f)
                        quadTo(15.35f, 20f, 17.675f, 17.675f)
                        reflectiveQuadTo(20f, 12f)
                        quadTo(20f, 8.65f, 17.675f, 6.325f)
                        reflectiveQuadTo(12f, 4f)
                        quadTo(8.65f, 4f, 6.325f, 6.325f)
                        reflectiveQuadTo(4f, 12f)
                        quadTo(4f, 15.35f, 6.325f, 17.675f)
                        reflectiveQuadTo(12f, 20f)
                        close()
                        moveTo(12f, 12f)
                        close()
                    }
                }
                .build()
        return _info!!
    }

private var _info: ImageVector? = null
