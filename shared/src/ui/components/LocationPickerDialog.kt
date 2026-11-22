package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mrboomdev.scrollix.fetchUrlBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

private const val MapZoom = 15
private const val MapTileUrl = "https://tile.openstreetmap.org/$MapZoom"

private data class LiveLocationChoice(
    val label: String,
    val seconds: Int
)

private val LiveLocationChoices = listOf(
    LiveLocationChoice("Static", 0),
    LiveLocationChoice("15 min", 15 * 60),
    LiveLocationChoice("1 hour", 60 * 60),
    LiveLocationChoice("8 hours", 8 * 60 * 60)
)

/**
 * A deliberately small OpenStreetMap picker: one raster tile with a draggable
 * pin. Whole-map panning or zooming would need tile caching and a proper grid,
 * which this client does not ship. Coordinates can be typed directly, which is
 * also how the map is moved to another city.
 */
@Composable
fun LocationPickerDialog(
    onDismiss: () -> Unit,
    onSend: (latitude: Double, longitude: Double, livePeriodSeconds: Int) -> Unit
) {
    var latitudeText by remember { mutableStateOf("") }
    var longitudeText by remember { mutableStateOf("") }
    var liveChoice by remember { mutableStateOf(LiveLocationChoices.first()) }
    var pinFraction by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var tileImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoadingTile by remember { mutableStateOf(false) }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }

    val latitude = latitudeText.toDoubleOrNull()
    val longitude = longitudeText.toDoubleOrNull()
    val hasCoordinates = latitude != null &&
        longitude != null &&
        latitude in -90.0..90.0 &&
        longitude in -180.0..180.0

    val tileCount = 1 shl MapZoom
    val tileX = longitude
        ?.let { lon -> floor(lonToTileX(lon, MapZoom)).toInt() }
        ?.coerceIn(0, tileCount - 1)
        ?: 0
    val tileY = latitude
        ?.let { lat -> floor(latToTileY(lat, MapZoom)).toInt() }
        ?.coerceIn(0, tileCount - 1)
        ?: 0

    LaunchedEffect(hasCoordinates, tileX, tileY) {
        if (!hasCoordinates) {
            tileImage = null
            isLoadingTile = false
            return@LaunchedEffect
        }

        isLoadingTile = true
        val bytes = fetchUrlBytes("$MapTileUrl/$tileX/$tileY.png")
        tileImage = bytes?.let { data ->
            withContext(Dispatchers.Default) { decodeImageBitmap(data) }
        }
        isLoadingTile = false
    }

    // Keep the pin in sync when coordinates are typed or clamped.
    LaunchedEffect(latitude, longitude, hasCoordinates) {
        if (latitude == null || longitude == null || !hasCoordinates) return@LaunchedEffect

        pinFraction = Offset(
            x = (lonToTileX(longitude, MapZoom) - tileX).toFloat().coerceIn(0f, 1f),
            y = (latToTileY(latitude, MapZoom) - tileY).toFloat().coerceIn(0f, 1f)
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Share a location",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .onSizeChanged { size -> mapSize = size }
                            .pointerInput(hasCoordinates, tileX, tileY) {
                                if (!hasCoordinates) return@pointerInput

                                detectDragGestures { change, _ ->
                                    change.consume()

                                    val fractionX = (
                                        change.position.x / size.width.coerceAtLeast(1)
                                    ).coerceIn(0f, 1f)
                                    val fractionY = (
                                        change.position.y / size.height.coerceAtLeast(1)
                                    ).coerceIn(0f, 1f)

                                    pinFraction = Offset(fractionX, fractionY)

                                    val globalX = (tileX + fractionX).toDouble() / tileCount
                                    val globalY = (tileY + fractionY).toDouble() / tileCount

                                    latitudeText = formatLocationCoordinate(
                                        tileYToLatitude(globalY)
                                    )
                                    longitudeText = formatLocationCoordinate(
                                        globalX * 360.0 - 180.0
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        tileImage?.let { image ->
                            Image(
                                bitmap = image,
                                contentDescription = "OpenStreetMap tile",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        if (!hasCoordinates) {
                            Text(
                                modifier = Modifier.padding(24.dp),
                                text = "Enter the latitude and longitude to load the map",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        if (isLoadingTile) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }

                        if (hasCoordinates) {
                            val pinHeight = with(LocalDensity.current) { 28.dp.roundToPx() }
                            val pinWidth = with(LocalDensity.current) { 24.dp.roundToPx() }

                            Text(
                                text = "📍",
                                fontSize = 28.sp,
                                modifier = Modifier.offset {
                                    IntOffset(
                                        x = (pinFraction.x * mapSize.width).roundToInt() -
                                            pinWidth / 2,
                                        y = (pinFraction.y * mapSize.height).roundToInt() -
                                            pinHeight
                                    )
                                }
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = latitudeText,
                            onValueChange = { latitudeText = it },
                            label = { Text("Latitude") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )

                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = longitudeText,
                            onValueChange = { longitudeText = it },
                            label = { Text("Longitude") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }

                    Text(
                        text = "How long should it be shared?",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(LiveLocationChoices) { choice ->
                            FilterChip(
                                selected = liveChoice == choice,
                                onClick = { liveChoice = choice },
                                label = { Text(choice.label) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }

                        Button(
                            enabled = hasCoordinates,
                            onClick = {
                                onSend(
                                    latitude ?: 0.0,
                                    longitude ?: 0.0,
                                    liveChoice.seconds
                                )
                            }
                        ) {
                            Text("Send")
                        }
                    }
                }
            }
        }
    }
}

private fun lonToTileX(lon: Double, zoom: Int): Double {
    return (lon + 180.0) / 360.0 * (1 shl zoom)
}

private fun latToTileY(lat: Double, zoom: Int): Double {
    val radians = lat.coerceIn(-85.05112878, 85.05112878) * PI / 180.0
    val mercator = ln(tan(radians) + 1.0 / cos(radians))
    return (1.0 - mercator / PI) / 2.0 * (1 shl zoom)
}

/** Inverse Web Mercator for a global tile fraction in the range 0..1. */
private fun tileYToLatitude(globalFraction: Double): Double {
    return atan(sinh(PI * (1.0 - 2.0 * globalFraction))) * 180.0 / PI
}
