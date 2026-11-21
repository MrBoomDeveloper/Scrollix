package com.mrboomdev.scrollix.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private var _qrCodeScanner: ImageVector? = null

@Suppress("CheckReturnValue")
public val qr_code_scanner_icon: ImageVector
    get() {
        if (_qrCodeScanner != null) {
            return _qrCodeScanner!!
        }

        _qrCodeScanner = ImageVector.Builder(
            name = "qr_code_scanner",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
            .addPath(
                pathData = addPathNodes(
                    "M9.5,6.5v3h-3v-3H9.5 M11,5H5v6h6V5L11,5z " +
                        "M9.5,14.5v3h-3v-3H9.5 M11,13H5v6h6V13L11,13z " +
                        "M17.5,6.5v3h-3v-3H17.5 M19,5h-6v6h6V5L19,5z " +
                        "M13,13h1.5v1.5H13V13z M14.5,14.5H16V16h-1.5V14.5z " +
                        "M16,13h1.5v1.5H16V13z M13,16h1.5v1.5H13V16z " +
                        "M14.5,17.5H16V19h-1.5V17.5z M16,16h1.5v1.5H16V16z " +
                        "M17.5,14.5H19V16h-1.5V14.5z M17.5,17.5H19V19h-1.5V17.5z " +
                        "M22,7h-2V4h-3V2h5V7z M22,22v-5h-2v3h-3v2H22z " +
                        "M2,22h5v-2H4v-3H2V22z M2,2v5h2V4h3V2H2z"
                ),
                fill = SolidColor(Color.Black)
            )
            .build()

        return _qrCodeScanner!!
    }
