package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.LocationFix
import com.mrboomdev.scrollix.currentLocation
import com.mrboomdev.scrollix.fetchUrlBytes
import com.mrboomdev.scrollix.isLocationEnabled
import com.mrboomdev.scrollix.openLocationSettings
import com.mrboomdev.scrollix.ui.components.decodeImageBitmap

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.ln
import kotlin.math.roundToInt

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
 * which this client does not ship.
 *
 * There is no coordinate field: the position always comes from the device. The
 * picker opens on the current position, and everything it cannot obtain is a
 * status message with a retry (or the system switch) instead of numbers typed
 * by hand. The composer hides the entry point entirely when the platform
 * cannot produce a location at all.
 */
@Composable
fun LocationPickerDialog(
    onDismiss: () -> Unit,
    onSend: (latitude: Double, longitude: Double, livePeriodSeconds: Int) -> Unit
) {
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var liveChoice by remember { mutableStateOf(LiveLocationChoices.first()) }
    var pinFraction by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var tileImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoadingTile by remember { mutableStateOf(false) }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    var isLocating by remember { mutableStateOf(false) }
    var locateRequest by remember { mutableStateOf(0) }
    var locationIssue by remember { mutableStateOf<LocationFix?>(null) }

    // The picker opens on the current position; there is no other source.
    LaunchedEffect(locateRequest) {
        isLocating = true
        locationIssue = null

        when (val fix = currentLocation()) {
            is LocationFix.Position -> {
                latitude = fix.latitude
                longitude = fix.longitude
            }

            // Nothing can be shown without access, so the sheet closes.
            LocationFix.PermissionDenied -> onDismiss()
            else -> locationIssue = fix
        }

        isLocating = false
    }

    // Location off: the user is sent to the system switch and the status is
    // re-checked here, so coming back to the app picks the position up.
    LaunchedEffect(locationIssue) {
        if (locationIssue != LocationFix.Disabled) return@LaunchedEffect

        while (isActive) {
            delay(1000L)

            if (isLocationEnabled()) {
                locateRequest++
                break
            }
        }
    }

    val lat = latitude
    val lon = longitude
    val hasPosition = lat != null && lon != null

    val tileCount = 1 shl OsmMapZoom
    val (tileX, tileY) = if (lat != null && lon != null) {
        osmTileCoordinates(lat, lon)
    } else {
        0 to 0
    }

    LaunchedEffect(hasPosition, tileX, tileY) {
        if (!hasPosition) {
            tileImage = null
            isLoadingTile = false
            return@LaunchedEffect
        }

        isLoadingTile = true
        val bytes = fetchUrlBytes(osmTileUrl(tileX, tileY))
        tileImage = bytes?.let { data ->
            withContext(Dispatchers.Default) { decodeImageBitmap(data) }
        }
        isLoadingTile = false
    }

    // Keep the pin in sync when a new fix arrives or the pin is dragged, since
    // dragging writes the new position straight back into the state above.
    LaunchedEffect(lat, lon) {
        if (lat == null || lon == null) return@LaunchedEffect

        pinFraction = Offset(
            x = (osmTileX(lon) - tileX).toFloat().coerceIn(0f, 1f),
            y = (osmTileY(lat) - tileY).toFloat().coerceIn(0f, 1f)
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
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        // The picker has more content than a short desktop
                        // window; keep every control reachable.
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Share a location",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = when {
                                isLocating -> "Getting your location…"
                                hasPosition -> "Using your current location"
                                locationIssue == LocationFix.Disabled ->
                                    "Location services are off"

                                locationIssue == LocationFix.Unavailable ->
                                    "Could not get your location"

                                else -> "No location yet"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        TextButton(
                            enabled = !isLocating,
                            onClick = { locateRequest++ }
                        ) {
                            Text(if (isLocating) "Locating…" else "Retry")
                        }
                    }

                    if (locationIssue == LocationFix.Disabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                modifier = Modifier.weight(1f),
                                text = "Turn on location to share where you are.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )

                            TextButton(onClick = { openLocationSettings() }) {
                                Text("Turn on")
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .onSizeChanged { size -> mapSize = size }
                            .pointerInput(hasPosition, tileX, tileY) {
                                if (!hasPosition) return@pointerInput

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

                                    latitude = osmTileYToLatitude(globalY)
                                    longitude = globalX * 360.0 - 180.0
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

                        if (!hasPosition && !isLocating) {
                            Text(
                                modifier = Modifier.padding(24.dp),
                                text = "The map appears once your position is known.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        if (isLoadingTile || isLocating) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }

                        if (hasPosition) {
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

                    Text(
                        text = "Drag the pin to adjust the point. How long should it " +
                            "be shared?",
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

                        TextButton(
                            enabled = hasPosition,
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

