package com.mrboomdev.scrollix.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * A floating pill toolbar used instead of the stock rectangular context menu.
 *
 * It is applied desktop-wide through [androidx.compose.foundation.LocalContextMenuRepresentation],
 * which also styles the menu that appears when selected text is right clicked.
 */
val ScrollixContextMenuRepresentation: ContextMenuRepresentation = object : ContextMenuRepresentation {
    @Composable
    override fun Representation(state: ContextMenuState, items: () -> List<ContextMenuItem>) {
        val status = state.status
        if (status !is ContextMenuState.Status.Open) return

        val entries = items()

        if (entries.isEmpty()) {
            LaunchedEffect(state) {
                state.status = ContextMenuState.Status.Closed
            }

            return
        }

        val offset = with(LocalDensity.current) {
            IntOffset(
                x = status.rect.left.toInt(),
                y = status.rect.bottom.toInt() + 8.dp.roundToPx()
            )
        }

        var visible by remember(state) { mutableStateOf(false) }

        LaunchedEffect(state) {
            visible = true
        }

        val scale by animateFloatAsState(
            targetValue = if (visible) 1f else 0.85f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "contextMenuScale"
        )

        val alpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "contextMenuAlpha"
        )

        Popup(
            alignment = Alignment.TopStart,
            offset = offset,
            onDismissRequest = { state.status = ContextMenuState.Status.Closed },
            properties = PopupProperties(focusable = true)
        ) {
            Row(
                modifier = Modifier
                    .scale(scale)
                    .alpha(alpha)
                    .shadow(elevation = 18.dp, shape = RoundedCornerShape(20.dp), clip = false)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                entries.forEach { item ->
                    ContextMenuEntry(item) {
                        state.status = ContextMenuState.Status.Closed
                        item.onClick()
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextMenuEntry(
    item: ContextMenuItem,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isHovered && item.enabled) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                } else {
                    Color.Transparent
                }
            )
            .clickable(
                enabled = item.enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                !item.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                isHovered -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
