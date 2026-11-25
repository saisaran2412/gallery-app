package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.scanner.HoldReason

/** Progress bar + Pause/Resume + Stop, shared by every long-running scan. */
@Composable
fun ScanControlBar(
    current: Int,
    total: Int,
    label: String,
    hold: HoldReason,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val waiting = hold != HoldReason.NONE
    val status = when (hold) {
        HoldReason.USER -> "Paused - $current / $total"
        HoldReason.LOW_BATTERY -> "Waiting: battery low. Plug in to continue"
        HoldReason.HOT -> "Waiting: phone is hot. Cooling down"
        HoldReason.NONE -> "$label $current / $total"
    }
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            MagicProgressBar(
                progress = current.toFloat() / total.coerceAtLeast(1),
                active = hold == HoldReason.NONE,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { if (hold == HoldReason.USER) onResume() else onPause() },
                modifier = Modifier.testTag("scan_pause_resume")
            ) {
                Icon(
                    if (hold == HoldReason.USER) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (hold == HoldReason.USER) "Resume" else "Pause",
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onStop, modifier = Modifier.testTag("scan_stop")) {
                Icon(Icons.Filled.Stop, contentDescription = "Stop", modifier = Modifier.size(22.dp))
            }
        }
        Text(
            status,
            style = MaterialTheme.typography.labelSmall,
            color = if (waiting && hold != HoldReason.USER) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
