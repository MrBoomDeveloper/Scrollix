package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.mrboomdev.scrollix.data.model.Avatar

@Composable
fun AvatarImage(
    avatar: Avatar?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val image = remember(avatar) {
        avatar?.image?.let(::decodeImageBitmap)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarColor(avatar?.colorSeed ?: 0)),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = avatar?.initials ?: "?",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1
            )
        }
    }
}

expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?

private fun avatarColor(seed: Int): Color {
    val palette = listOf(
        Color(0xFF5C6BC0),
        Color(0xFF26A69A),
        Color(0xFFEF5350),
        Color(0xFFAB47BC),
        Color(0xFF42A5F5),
        Color(0xFFFF7043)
    )

    val index = ((seed % palette.size) + palette.size) % palette.size
    return palette[index]
}
