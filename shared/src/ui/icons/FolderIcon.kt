package com.mrboomdev.scrollix.ui.icons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrboomdev.scrollix.data.model.DialogFolder

@Composable
fun FolderIcon(
    folder: DialogFolder,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val color = folderColor(folder.colorId)
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
    } else {
        color.copy(alpha = 0.24f)
    }

    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = folderIconSymbol(folder.iconName),
            fontSize = 12.sp,
            lineHeight = 12.sp
        )
    }
}

private val folderColors = listOf(
    Color(0xFF6C9CFF),
    Color(0xFFFFB454),
    Color(0xFFB388FF),
    Color(0xFF66BB6A),
    Color(0xFFFF8A80),
    Color(0xFF4DD0E1),
    Color(0xFFF06292)
)

private fun folderColor(colorId: Int): Color {
    return folderColors.getOrNull(colorId) ?: Color(0xFF9E9E9E)
}

private fun folderIconSymbol(name: String?): String {
    return when (name) {
        "All" -> "🗂"
        "Unread" -> "🔵"
        "Unmuted" -> "🔔"
        "Bots" -> "🤖"
        "Channels" -> "📢"
        "Groups" -> "👥"
        "Private" -> "👤"
        "Custom" -> "📁"
        "Setup" -> "⚙"
        "Cat" -> "🐱"
        "Crown" -> "👑"
        "Favorite" -> "⭐"
        "Flower" -> "🌸"
        "Game" -> "🎮"
        "Home" -> "🏠"
        "Love" -> "❤"
        "Mask" -> "🎭"
        "Party" -> "🎉"
        "Sport" -> "⚽"
        "Study" -> "📚"
        "Trade" -> "📈"
        "Travel" -> "🧳"
        "Work" -> "💼"
        "Airplane" -> "✈"
        "Book" -> "📖"
        "Light" -> "💡"
        "Like" -> "👍"
        "Money" -> "💰"
        "Note" -> "📝"
        "Palette" -> "🎨"
        else -> "📁"
    }
}
