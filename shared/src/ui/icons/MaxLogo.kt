package com.mrboomdev.scrollix.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * MAX brand mark, extracted from the adaptive launcher icon of Max.apkm
 * (res/drawable/ic_launcher_foreground.xml and ic_launcher_background.xml):
 *
 * - background: linear gradient #0026FF -> #A500C6 from (41.92, 87.99) to
 *   (466.19, 420.96), with a #48C0FC -> transparent glow from (89.13, 499.42)
 *   to (338, 138.54);
 * - foreground: the white 373x371 path below, filled with the even-odd rule.
 */
private const val MaxLogoPath =
    "M188.175 271.609c-17.194 0-25.18-2.504-39.033-12.525-8.814 11.27-36.553 " +
        "20.036-37.771 5.008 0-11.256-2.541-20.743-5.34-31.19-3.43-12.791-7.247" +
        "-27.024-7.247-47.707 0-49.317 40.689-86.408 88.935-86.408s86.074 38.903 " +
        "86.074 86.89-39.033 85.93-85.618 85.93zm0.701-130.183c-23.476-1.226-41.8 " +
        "14.968-45.837 40.316-3.346 20.987 2.583 46.565 7.656 47.852 2.395 0.608 " +
        "8.522-4.32 12.296-8.078 6.216 4.006 13.345 7.105 21.27 7.521 24.102 1.254 " +
        "45.465-17.495 46.726-41.458 1.261-23.959-18.008-44.898-42.11-46.153z"

/** MAX brand mark used next to MAX accounts and profiles. */
@Composable
fun MaxLogoIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp
) {
    val logo = remember {
        PathParser().parsePathString(MaxLogoPath).toPath().apply {
            fillType = PathFillType.EvenOdd
        }
    }

    Canvas(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
    ) {
        val width = this@Canvas.size.width
        val height = this@Canvas.size.height

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF0026FF), Color(0xFFA500C6)),
                start = Offset(width * 0.082f, height * 0.172f),
                end = Offset(width * 0.910f, height * 0.822f)
            )
        )

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF48C0FC), Color(0x0048C0FC)),
                start = Offset(width * 0.174f, height * 0.975f),
                end = Offset(width * 0.660f, height * 0.271f)
            )
        )

        val scale = this@Canvas.size.minDimension / 373f

        withTransform({
            translate(
                left = (width - 373f * scale) / 2f,
                top = (height - 371f * scale) / 2f
            )
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {
            drawPath(path = logo, color = Color.White)
        }
    }
}

/** Brand mark of the provider behind an account, if it has one. */
@Composable
fun ProviderLogoIcon(
    providerId: String?,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp
) {
    when (providerId) {
        "telegram" -> TelegramLogoIcon(modifier = modifier, size = size)
        "max" -> MaxLogoIcon(modifier = modifier, size = size)
    }
}
