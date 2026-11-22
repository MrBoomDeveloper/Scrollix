package com.mrboomdev.scrollix.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
val videocam_icon: ImageVector
    get() {
        if (_videocam != null) return _videocam!!

        _videocam = ImageVector.Builder(
            name = "videocam",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(17f, 10.5f)
                verticalLineTo(7f)
                quadToRelative(0f, -0.825f, -0.588f, -1.412f)
                reflectiveQuadTo(15f, 5f)
                horizontalLineTo(4f)
                quadToRelative(-0.825f, 0f, -1.412f, 0.588f)
                reflectiveQuadTo(2f, 7f)
                verticalLineToRelative(10f)
                quadToRelative(0f, 0.825f, 0.588f, 1.412f)
                reflectiveQuadTo(4f, 19f)
                horizontalLineToRelative(11f)
                quadToRelative(0.825f, 0f, 1.412f, -0.588f)
                reflectiveQuadTo(17f, 17f)
                verticalLineToRelative(-3.5f)
                lineToRelative(4f, 4f)
                verticalLineToRelative(-11f)
                close()
                moveToRelative(-2f, 6.5f)
                horizontalLineTo(4f)
                verticalLineTo(7f)
                horizontalLineToRelative(11f)
                close()
            }
        }.build()

        return _videocam!!
    }

private var _videocam: ImageVector? = null
