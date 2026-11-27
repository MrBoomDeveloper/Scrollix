package com.mrboomdev.scrollix.ui.components

import com.mrboomdev.scrollix.data.AppInfo
import com.mrboomdev.scrollix.data.diagnostics.AppLog
import com.mrboomdev.scrollix.data.diagnostics.DiagnosticsFacts
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.data.storage.currentTimeMillis
import com.mrboomdev.scrollix.getPlatform
import com.mrboomdev.scrollix.ui.icons.info_icon

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The one empty state every screen uses: a title, a sentence about what would
 * be here, and optionally the action that creates the first item. Keeping it
 * shared means an empty folder, an empty draft list and an empty log look and
 * read the same.
 */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (icon != null) {
            Icon(
                modifier = Modifier.size(40.dp),
                imageVector = icon,
                tint = MaterialTheme.colorScheme.secondary,
                contentDescription = null
            )
        }

        Text(
            modifier = Modifier.semantics { heading() },
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )

        Text(
            text = message,
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

/**
 * A failed load with a retry, plus the two diagnostics entry points from the
 * error itself: the report can be copied without hunting for the diagnostics
 * screen, and the log can be opened in one tap.
 */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    title: String = "Something went wrong",
    onRetry: (() -> Unit)? = null,
    onOpenLogs: (() -> Unit)? = null
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            modifier = Modifier.size(40.dp),
            imageVector = info_icon,
            tint = MaterialTheme.colorScheme.error,
            contentDescription = null
        )

        Text(
            modifier = Modifier.semantics { heading() },
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )

        Text(
            text = message,
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        if (onRetry != null) {
            Button(onClick = onRetry) {
                Text("Try again")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(buildDebugInfoText()))
                    copied = true
                }
            ) {
                Text(if (copied) "Debug info copied" else "Copy debug info")
            }

            if (onOpenLogs != null) {
                TextButton(onClick = onOpenLogs) {
                    Text("Open logs")
                }
            }
        }
    }
}

/**
 * Rows shown while the first page of a list loads. The pulse is decorative, so
 * reduce motion leaves a static placeholder instead of animating it.
 */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    rows: Int = 6
) {
    val settings by AppSettingsStore.state.collectAsState()
    val reduceMotion = settings.reduceMotion

    val alpha = if (reduceMotion) {
        0.35f
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val animated by transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse
            ),
            label = "skeleton-alpha"
        )

        animated
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        repeat(rows.coerceAtLeast(1)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha),
                            shape = CircleShape
                        )
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SkeletonBar(alpha = alpha, widthFraction = 0.55f)
                    SkeletonBar(alpha = alpha, widthFraction = 0.85f)
                }
            }
        }
    }
}

@Composable
private fun SkeletonBar(alpha: Float, widthFraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(12.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha),
                shape = RoundedCornerShape(6.dp)
            )
    )
}

/**
 * The report copied from an error entry point. Deliberately the same text the
 * diagnostics screen produces, so support does not need two formats.
 */
fun buildDebugInfoText(): String {
    return runCatching {
        AppLog.report(
            DiagnosticsFacts(
                platform = getPlatform().name,
                edition = AppInfo.version,
                generatedAtMillis = currentTimeMillis()
            )
        )
    }.getOrElse { "Scrollix ${AppInfo.version} on ${getPlatform().name}\nNo diagnostics available." }
}

/** Colour used by the skeleton and inline placeholders. */
internal val PlaceholderColor: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
