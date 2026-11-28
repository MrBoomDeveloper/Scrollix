package com.mrboomdev.scrollix.features.explore

import com.mrboomdev.scrollix.ui.components.EmptyState
import com.mrboomdev.scrollix.ui.icons.explore_icon

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ExploreScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        EmptyState(
            icon = explore_icon,
            title = "Explore is coming soon",
            message = "Public channels, posts and chat discovery will appear here."
        )
    }
}
