package com.mrboomdev.scrollix.features.dialog.media

import com.mrboomdev.scrollix.fetchUrlBytes
import com.mrboomdev.scrollix.ui.components.decodeImageBitmap

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Zoom of the static map: a whole city block, which is what a shared point is
 * about. One tile is enough at this zoom, so no tile grid or caching is needed.
 */
internal const val OsmMapZoom = 15

/** Fractional tile column of [longitude] at [zoom]. */
internal fun osmTileX(longitude: Double, zoom: Int = OsmMapZoom): Double {
    return (longitude + 180.0) / 360.0 * (1 shl zoom)
}

/** Fractional tile row of [latitude] at [zoom] (Web Mercator). */
internal fun osmTileY(latitude: Double, zoom: Int = OsmMapZoom): Double {
    val radians = latitude.coerceIn(-85.05112878, 85.05112878) * PI / 180.0
    val mercator = ln(tan(radians) + 1.0 / cos(radians))
    return (1.0 - mercator / PI) / 2.0 * (1 shl zoom)
}

/** Inverse Web Mercator for a global tile fraction in the range 0..1. */
internal fun osmTileYToLatitude(globalFraction: Double): Double {
    return atan(sinh(PI * (1.0 - 2.0 * globalFraction))) * 180.0 / PI
}

/** Tile that contains the point, clamped to the valid range of the zoom. */
internal fun osmTileCoordinates(latitude: Double, longitude: Double): Pair<Int, Int> {
    val count = 1 shl OsmMapZoom

    val x = floor(osmTileX(longitude)).toInt().coerceIn(0, count - 1)
    val y = floor(osmTileY(latitude)).toInt().coerceIn(0, count - 1)

    return x to y
}

internal fun osmTileUrl(x: Int, y: Int): String {
    return "https://tile.openstreetmap.org/$OsmMapZoom/$x/$y.png"
}

/**
 * One OpenStreetMap tile with a pin on the point. It is the map of the location
 * picker and the preview of a location message: raster only, loaded through
 * `fetchUrlBytes`, no map SDK and no tile prefetching.
 */
@Composable
internal fun OsmMapTile(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    showPin: Boolean = true
) {
    val (tileX, tileY) = remember(latitude, longitude) {
        osmTileCoordinates(latitude, longitude)
    }
    var image by remember(tileX, tileY) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember(tileX, tileY) { mutableStateOf(true) }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(tileX, tileY) {
        isLoading = true

        val bytes = fetchUrlBytes(osmTileUrl(tileX, tileY))
        image = bytes?.let { data ->
            withContext(Dispatchers.Default) { decodeImageBitmap(data) }
        }

        isLoading = false
    }

    // The pin sits where the point falls inside its own tile; the tile is
    // cropped to the box, so the fraction is what keeps the pin correct.
    val pinFraction = remember(latitude, longitude) {
        Offset(
            x = (osmTileX(longitude) - tileX).toFloat().coerceIn(0f, 1f),
            y = (osmTileY(latitude) - tileY).toFloat().coerceIn(0f, 1f)
        )
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .onSizeChanged { size -> mapSize = size },
        contentAlignment = Alignment.Center
    ) {
        image?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = "OpenStreetMap",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }

        // Required by the OpenStreetMap tile policy; shown on every state so
        // the credit never disappears while a tile is loading.
        Text(
            text = "© OpenStreetMap",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
        )

        if (showPin && image != null) {
            val density = LocalDensity.current
            val pinHeight = with(density) { 24.dp.roundToPx() }
            val pinWidth = with(density) { 20.dp.roundToPx() }

            // The square tile is center-cropped into the box, so the drawn
            // image is a box-wide square that may overflow vertically. The pin
            // has to follow that square, not the box, or it drifts off the
            // point on wide cards.
            val side = max(mapSize.width, mapSize.height).toFloat()
            val imageOffsetX = (mapSize.width - side) / 2f
            val imageOffsetY = (mapSize.height - side) / 2f

            Text(
                text = "📍",
                fontSize = 24.sp,
                modifier = Modifier.offset {
                    IntOffset(
                        x = (imageOffsetX + pinFraction.x * side).roundToInt() - pinWidth / 2,
                        y = (imageOffsetY + pinFraction.y * side).roundToInt() - pinHeight
                    )
                }
            )
        }
    }
}
