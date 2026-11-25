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
public val bug_report_icon: ImageVector
    get() {
        if (_bug_report != null) {
            return _bug_report!!
        }
        _bug_report =
            ImageVector.Builder(
                name = "bug_report",
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
                        moveTo(12f, 19f)
                        quadTo(13.65f, 19f, 14.825f, 17.825f)
                        reflectiveQuadTo(16f, 15f)
                        lineTo(16f, 11f)
                        quadTo(16f, 9.35f, 14.825f, 8.175f)
                        reflectiveQuadTo(12f, 7f)
                        quadTo(10.35f, 7f, 9.175f, 8.175f)
                        reflectiveQuadTo(8f, 11f)
                        lineTo(8f, 15f)
                        quadTo(8f, 16.65f, 9.175f, 17.825f)
                        reflectiveQuadTo(12f, 19f)
                        close()
                        moveTo(10f, 16f)
                        lineTo(14f, 16f)
                        lineTo(14f, 14f)
                        lineTo(10f, 14f)
                        lineTo(10f, 16f)
                        close()
                        moveTo(10f, 12f)
                        lineTo(14f, 12f)
                        lineTo(14f, 10f)
                        lineTo(10f, 10f)
                        lineTo(10f, 12f)
                        close()
                        moveTo(12f, 13f)
                        close()
                        moveTo(12f, 21f)
                        quadTo(10.375f, 21f, 8.988f, 20.2f)
                        reflectiveQuadTo(6.8f, 18f)
                        lineTo(4f, 18f)
                        lineTo(4f, 16f)
                        lineTo(6.1f, 16f)
                        quadTo(6.025f, 15.5f, 6.013f, 15f)
                        reflectiveQuadTo(6f, 14f)
                        lineTo(4f, 14f)
                        lineTo(4f, 12f)
                        lineTo(6f, 12f)
                        quadTo(6f, 11.5f, 6.013f, 11f)
                        reflectiveQuadTo(6.1f, 10f)
                        lineTo(4f, 10f)
                        lineTo(4f, 8f)
                        lineTo(6.8f, 8f)
                        quadTo(7.15f, 7.425f, 7.588f, 6.925f)
                        reflectiveQuadTo(8.6f, 6.05f)
                        lineTo(7f, 4.4f)
                        lineTo(8.4f, 3f)
                        lineTo(10.55f, 5.15f)
                        quadTo(11.25f, 4.925f, 11.975f, 4.925f)
                        reflectiveQuadTo(13.4f, 5.15f)
                        lineTo(15.6f, 3f)
                        lineTo(17f, 4.4f)
                        lineTo(15.35f, 6.05f)
                        quadTo(15.925f, 6.425f, 16.387f, 6.913f)
                        reflectiveQuadTo(17.2f, 8f)
                        lineTo(20f, 8f)
                        lineTo(20f, 10f)
                        lineTo(17.9f, 10f)
                        quadTo(17.975f, 10.5f, 17.988f, 11f)
                        reflectiveQuadTo(18f, 12f)
                        lineTo(20f, 12f)
                        lineTo(20f, 14f)
                        lineTo(18f, 14f)
                        quadTo(18f, 14.5f, 17.988f, 15f)
                        reflectiveQuadTo(17.9f, 16f)
                        lineTo(20f, 16f)
                        lineTo(20f, 18f)
                        lineTo(17.2f, 18f)
                        quadTo(16.4f, 19.4f, 15.013f, 20.2f)
                        reflectiveQuadTo(12f, 21f)
                        close()
                    }
                }
                .build()
        return _bug_report!!
    }

private var _bug_report: ImageVector? = null
