package com.mrboomdev.scrollix.features.settings

import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.data.storage.AppSettingsStore
import com.mrboomdev.scrollix.data.storage.DataSavingPreset
import com.mrboomdev.scrollix.data.storage.DataSavingSettings
import com.mrboomdev.scrollix.data.storage.DataUsageStore
import com.mrboomdev.scrollix.data.storage.LocalDataUsage
import com.mrboomdev.scrollix.ui.icons.data_usage_icon
import com.mrboomdev.scrollix.ui.icons.mood_icon
import com.mrboomdev.scrollix.ui.icons.send_icon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp

/**
 * Traffic counted on this device and the local data-saver policy. Neither the
 * statistics nor the auto-play switches are read from or written to the
 * provider: an install decides on its own what it loads without a tap.
 */
@Composable
fun SettingsDataUsageContent() {
    val accountState by ApiManager.accountState.collectAsState()
    val accounts = accountState.accounts
    val usage by DataUsageStore.state.collectAsState()
    val settings by AppSettingsStore.state.collectAsState()
    var pickedAccountId by remember { mutableStateOf<String?>(null) }

    val selectedAccountId = pickedAccountId
        ?.takeIf { id -> accounts.any { it.id == id } }
        ?: accountState.activeAccountId?.takeIf { id -> accounts.any { it.id == id } }
        ?: accounts.firstOrNull()?.id

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsSectionTitle(icon = data_usage_icon, title = "Traffic")

        Text(
            text = "Counted on this device: media Scrollix downloads and text it " +
                "sends. Traffic of other apps and files fetched elsewhere is not " +
                "included.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )

        if (accounts.isEmpty()) {
            Text(
                text = "Add an account to see its traffic statistics.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        } else {
            if (accounts.size > 1) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    accounts.forEach { account ->
                        if (account.id == selectedAccountId) {
                            Button(onClick = {}) {
                                Text(account.displayName)
                            }
                        } else {
                            TextButton(onClick = { pickedAccountId = account.id }) {
                                Text(account.displayName)
                            }
                        }
                    }
                }
            }

            val summary = selectedAccountId?.let { id -> usage[id] } ?: LocalDataUsage()

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    UsageRow("Sent", formatDataSize(summary.sentBytes))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                    UsageRow("Received", formatDataSize(summary.receivedBytes))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                    UsageRow("Total", formatDataSize(summary.totalBytes))

                    if (summary.sinceMillis > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                        UsageRow("Since", formatDataDate(summary.sinceMillis))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    enabled = summary.totalBytes > 0,
                    onClick = { selectedAccountId?.let { id -> DataUsageStore.reset(id) } }
                ) {
                    Text("Reset statistics")
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

        SettingsSectionTitle(icon = data_usage_icon, title = "Data saver")

        Text(
            text = "Animated stickers, GIFs and round video notes are downloaded " +
                "and played as soon as they appear. Turn them off to save data; " +
                "they can still be opened by tapping them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )

        val dataSaving = settings.dataSaving
        val selectedPreset = dataSaving.matchingPreset

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DataSavingPreset.entries.forEach { preset ->
                if (selectedPreset == preset) {
                    Button(onClick = {}) {
                        Text("${preset.title} ✓")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            AppSettingsStore.update { current ->
                                current.copy(dataSaving = DataSavingSettings.of(preset))
                            }
                        }
                    ) {
                        Text(preset.title)
                    }
                }
            }

            if (selectedPreset == null) {
                Button(onClick = {}) {
                    Text("Custom ✓")
                }
            } else {
                OutlinedButton(onClick = {}) {
                    Text("Custom")
                }
            }
        }

        Text(
            text = if (selectedPreset == null) {
                "This install uses its own mix of the switches below."
            } else {
                "The ${selectedPreset.title.lowercase()} profile is active on this " +
                    "device. Changing a switch below turns it into a custom profile."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )

        SettingsSwitch(
            icon = mood_icon,
            title = "Auto-play GIFs and animated stickers",
            description = "Load and play them without a tap.",
            checked = dataSaving.animations,
            onCheckedChange = { enabled ->
                AppSettingsStore.update { current ->
                    current.copy(
                        dataSaving = current.dataSaving.copy(animations = enabled)
                    )
                }
            }
        )

        SettingsSwitch(
            icon = send_icon,
            title = "Auto-play round video notes",
            description = "Load and play round video messages without a tap.",
            checked = dataSaving.videoNotes,
            onCheckedChange = { enabled ->
                AppSettingsStore.update { current ->
                    current.copy(
                        dataSaving = current.dataSaving.copy(videoNotes = enabled)
                    )
                }
            }
        )
    }
}

@Composable
private fun UsageRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
