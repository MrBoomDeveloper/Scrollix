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
public val filter_list_icon: ImageVector
    get() {
        if (_filterList != null) {
            return _filterList!!
        }
        _filterList =
            ImageVector.Builder(
                name = "filter_list",
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
                        moveTo(10f, 18f)
                        horizontalLineToRelative(4f)
                        verticalLineToRelative(-2f)
                        horizontalLineToRelative(-4f)
                        verticalLineToRelative(2f)
                        close()
                        moveTo(3f, 6f)
                        verticalLineToRelative(2f)
                        horizontalLineToRelative(18f)
                        verticalLineTo(6f)
                        horizontalLineTo(3f)
                        close()
                        moveTo(6f, 13f)
                        horizontalLineToRelative(12f)
                        verticalLineToRelative(-2f)
                        horizontalLineTo(6f)
                        verticalLineToRelative(2f)
                        close()
                    }
                }
                .build()
        return _filterList!!
    }

private var _filterList: ImageVector? = null
