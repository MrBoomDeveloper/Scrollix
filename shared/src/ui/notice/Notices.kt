package com.mrboomdev.scrollix.ui.notice

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One transient message shown above the navigation bar. A notice outlives a
 * recomposition of the screen that created it, which is why failures use it
 * instead of a local SnackbarHostState.
 *
 * [action] runs when the user taps the action label, [onExpired] when the
 * notice disappears without one. A destructive action posts with
 * [SnackbarDuration.Long] and commits in [onExpired], so the action label is a
 * real undo window rather than a confirmation that cannot be taken back.
 */
data class AppNotice(
    val message: String,
    val id: Long = nextNoticeId(),
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
    val onExpired: (() -> Unit)? = null,
    val isError: Boolean = false,
    val duration: SnackbarDuration = SnackbarDuration.Short
)

private var noticeIdCounter = 0L

private fun nextNoticeId(): Long = ++noticeIdCounter

/**
 * App-wide queue of [AppNotice]s. Screens only post; the host in `App` renders
 * them, so an error raised while a screen is being replaced still reaches the
 * user.
 */
object NoticeCenter {
    private val _queue = MutableStateFlow<List<AppNotice>>(emptyList())
    val queue: StateFlow<List<AppNotice>> = _queue.asStateFlow()

    fun post(notice: AppNotice) {
        _queue.update { current -> current + notice }
    }

    /** Shows a failure with an optional retry. */
    fun error(message: String, retry: (() -> Unit)? = null) {
        post(
            AppNotice(
                message = message,
                isError = true,
                actionLabel = if (retry != null) "Retry" else null,
                action = retry
            )
        )
    }

    /**
     * Shows [message] and commits [commit] only after the notice expires.
     * Tapping [label] runs [undo] and drops the pending commit.
     */
    fun undo(
        message: String,
        label: String = "Undo",
        undo: () -> Unit,
        commit: () -> Unit
    ) {
        post(
            AppNotice(
                message = message,
                actionLabel = label,
                action = undo,
                onExpired = commit,
                duration = SnackbarDuration.Long
            )
        )
    }

    fun dismiss(id: Long) {
        _queue.update { current -> current.filterNot { notice -> notice.id == id } }
    }
}

/**
 * Renders the notice queue in order. Each notice is shown to completion so a
 * burst of failures cannot swallow an undo window.
 */
@Composable
fun AppNoticeHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val notices by NoticeCenter.queue.collectAsState()
    val current = notices.firstOrNull()

    LaunchedEffect(current?.id) {
        val notice = current ?: return@LaunchedEffect

        val result = hostState.showSnackbar(
            message = notice.message,
            actionLabel = notice.actionLabel,
            withDismissAction = notice.isError,
            duration = notice.duration
        )

        if (result == SnackbarResult.ActionPerformed) {
            notice.action?.invoke()
        } else {
            notice.onExpired?.invoke()
        }

        NoticeCenter.dismiss(notice.id)
    }

    SnackbarHost(hostState = hostState, modifier = modifier)
}
