package com.mrboomdev.scrollix.features.dialog

import com.mrboomdev.scrollix.data.model.MessageLocationInfo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.round

/**
 * Renders a shared static or live location inside a message bubble.
 * The card opens the point in OpenStreetMap; it never loads message media.
 */
@Composable
internal fun LocationMessageCard(
    location: MessageLocationInfo,
    isOutgoing: Boolean,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val cardColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val titleColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.secondary
    }

    val coordinates = "${formatLocationCoordinate(location.latitude)}, " +
        formatLocationCoordinate(location.longitude)
    val url = "https://www.openstreetmap.org/?mlat=${location.latitude}" +
        "&mlon=${location.longitude}#map=16/${location.latitude}/${location.longitude}"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardColor)
            .clickable {
                runCatching { uriHandler.openUri(url) }
            }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = if (location.isLive) "Live location" else "Location",
            style = MaterialTheme.typography.titleSmall,
            color = titleColor
        )

        Text(
            text = coordinates,
            style = MaterialTheme.typography.bodySmall,
            color = secondaryColor
        )

        if (location.isLive && location.expiresInSeconds > 0) {
            val minutes = (location.expiresInSeconds + 59) / 60

            Text(
                text = "Expires in about $minutes min",
                style = MaterialTheme.typography.labelSmall,
                color = secondaryColor
            )
        }
    }
}

/** Formats a coordinate with six decimals without relying on JVM formatting. */
internal fun formatLocationCoordinate(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "0"

    val negative = value < 0
    val scaled = round(abs(value) * 1_000_000).toLong()
    val whole = scaled / 1_000_000
    val fraction = (scaled % 1_000_000).toString().padStart(6, '0').trimEnd('0')

    val number = if (fraction.isEmpty()) whole.toString() else "$whole.$fraction"
    return if (negative) "-$number" else number
}
