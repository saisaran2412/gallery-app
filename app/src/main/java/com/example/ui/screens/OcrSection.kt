package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.local.AlbumDatabase
import com.example.data.scanner.HoldReason
import com.example.data.scanner.OcrScanner

/** Search by text inside photos. Runs fully on this device. */
@Composable
fun OcrSection() {
    val context = LocalContext.current
    val dao = remember { AlbumDatabase.getInstance(context).galleryDao() }
    val count by dao.observeOcrCount().collectAsState(initial = 0)
    val progress by OcrScanner.progress.collectAsState()
    val hold by OcrScanner.hold.collectAsState()
    val paused = OcrScanner.paused?.collectAsState()?.value == true

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Search text in photos", style = MaterialTheme.typography.titleSmall)
        Text(
            "Reads text in your photos (receipts, notes, screenshots) on this device. Then type a word in Search to find them. Latin script only. $count photo(s) read.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val p = progress
        if (p != null) {
            LinearProgressIndicator(progress = { p.first / p.second.toFloat() }, modifier = Modifier.fillMaxWidth())
            Text(
                when {
                    paused -> "Paused  ${p.first}/${p.second}"
                    hold == HoldReason.LOW_BATTERY -> "Waiting: low battery  ${p.first}/${p.second}"
                    hold == HoldReason.HOT -> "Waiting: phone is hot  ${p.first}/${p.second}"
                    else -> "Reading ${p.first}/${p.second}"
                },
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { if (paused) OcrScanner.resume() else OcrScanner.pause() }) { Text(if (paused) "Resume" else "Pause") }
                OutlinedButton(onClick = { OcrScanner.stop() }) { Text("Stop") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { OcrScanner.start(context) }) { Text(if (count == 0) "Scan photos" else "Scan new photos") }
                if (count > 0) OutlinedButton(onClick = { OcrScanner.clear(context) }) { Text("Clear") }
            }
        }
    }
}
