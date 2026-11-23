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
public val music_note_icon: ImageVector
    get() {
        if (_music_note != null) {
            return _music_note!!
        }
        _music_note =
            ImageVector.Builder(
                name = "music_note",
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
                        moveTo(7.18f, 19.83f)
                        quadTo(6f, 18.65f, 6f, 17f)
                        reflectiveQuadTo(7.18f, 14.18f)
                        reflectiveQuadTo(10f, 13f)
                        quadToRelative(0.58f, 0f, 1.06f, 0.14f)
                        reflectiveQuadTo(12f, 13.55f)
                        verticalLineTo(3f)
                        horizontalLineToRelative(6f)
                        verticalLineTo(7f)
                        horizontalLineTo(14f)
                        verticalLineTo(17f)
                        quadToRelative(0f, 1.65f, -1.17f, 2.82f)
                        reflectiveQuadTo(10f, 21f)
                        reflectiveQuadTo(7.18f, 19.83f)
                        close()
                    }
                }
                .build()
        return _music_note!!
    }

private var _music_note: ImageVector? = null
