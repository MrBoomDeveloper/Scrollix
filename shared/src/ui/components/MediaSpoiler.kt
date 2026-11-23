package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Covers media that the sender marked as a spoiler. The content underneath is
 * blurred and darkened until the reader taps the cover; the cover itself is
 * the only tap target, so a hidden image cannot be opened by accident.
 */
@Composable
fun SpoilerOverlay(
    modifier: Modifier = Modifier,
    onReveal: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onReveal),
        contentAlignment = Alignment.Center
    ) {
        Text(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            text = "SPOILER",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

/** Heavier blur for larger previews, so the content stays unreadable. */
fun Modifier.mediaSpoilerBlur(hidden: Boolean, radius: Dp = 22.dp): Modifier =
    then(if (hidden) blur(radius) else Modifier)
