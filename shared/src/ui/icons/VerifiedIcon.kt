package com.mrboomdev.scrollix.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val verified_icon: ImageVector
    get() {
        if (_verified != null) {
            return _verified!!
        }
        _verified =
            ImageVector.Builder(
                name = "verified",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            )
                .apply {
                    addPath(
                        pathData = addPathNodes(
                            "M23,12l-2.44,-2.79l0.34,-3.69l-3.61,-0.82L15.4,1.5L12,2.96L8.6,1.5" +
                                "L6.71,4.69L3.1,5.5L3.44,9.2L1,12l2.44,2.79l-0.34,3.7l3.61,0.82" +
                                "L8.6,22.5l3.4,-1.47l3.4,1.46l1.89,-3.19l3.61,-0.82L20.56,14.8L23,12z" +
                                "M10.09,16.72l-3.8,-3.81l1.48,-1.48l2.32,2.33l5.85,-5.87l1.48,1.49" +
                                "L10.09,16.72z"
                        ),
                        fill = SolidColor(Color.Black),
                        fillAlpha = 1f,
                        stroke = null,
                        strokeAlpha = 1f,
                        strokeLineWidth = 1f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Bevel,
                        strokeLineMiter = 1f,
                        pathFillType = PathFillType.NonZero,
                    )
                }
                .build()
        return _verified!!
    }

private var _verified: ImageVector? = null
