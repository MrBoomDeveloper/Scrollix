package com.mrboomdev.scrollix.features.dialog.message

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.mrboomdev.scrollix.data.model.MessageGiftInfo
import com.mrboomdev.scrollix.data.model.MessageGiftKind

/**
 * A gift or giveaway: the provider has no text formatting for these, so the
 * card states what was sent and how many stars it is worth. Sending gifts and
 * taking part in giveaways happen where they were launched — the provider
 * exposes no join call here.
 */
@Composable
internal fun MessageGiftCard(
    gift: MessageGiftInfo,
    isOutgoing: Boolean,
    modifier: Modifier = Modifier
) {
    val contentColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.secondary
    }

    Column(
        modifier = modifier
            .width(240.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isOutgoing) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = when (gift.kind) {
                    MessageGiftKind.Gift -> "🎁"
                    MessageGiftKind.Giveaway,
                    MessageGiftKind.GiveawayWinners -> "🎉"
                    MessageGiftKind.GiveawayCompleted -> "🏁"
                },
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = gift.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor
            )
        }

        gift.subtitle.takeIf { it.isNotBlank() }?.let { subtitle ->
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor
            )
        }

        if (gift.starCount > 0) {
            Text(
                text = "★ ${gift.starCount}",
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        }

        when {
            gift.isRefunded -> Text(
                text = "Refunded",
                style = MaterialTheme.typography.labelSmall,
                color = secondaryColor
            )

            gift.isUpgraded -> Text(
                text = "Upgraded",
                style = MaterialTheme.typography.labelSmall,
                color = secondaryColor
            )
        }
    }
}
