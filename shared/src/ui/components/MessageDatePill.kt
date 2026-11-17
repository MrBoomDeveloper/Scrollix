package com.mrboomdev.scrollix.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.data.storage.currentTimeMillis
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private val monthNames = listOf(
    "January",
    "February",
    "March",
    "April",
    "May",
    "June",
    "July",
    "August",
    "September",
    "October",
    "November",
    "December"
)

fun messageDate(timestampMillis: Long): LocalDate {
    return Instant.fromEpochMilliseconds(timestampMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
}

@Composable
fun MessageDatePill(date: LocalDate) {
    val today = remember(date) { messageDate(currentTimeMillis()) }

    val label = remember(date, today) {
        when {
            date == today -> "Today"
            date == today.minus(1, DateTimeUnit.DAY) -> "Yesterday"
            date.year == today.year -> "${monthNames[date.month.ordinal]} ${date.day}"
            else -> "${monthNames[date.month.ordinal]} ${date.day}, ${date.year}"
        }
    }
    
    Surface(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            text = label
        )
    }
}
