package com.example.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.birthday.BirthdayPrefs
import com.example.core.birthday.BirthdayScheduler
import com.example.data.local.AlbumDatabase
import com.example.data.local.BirthdayEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.DateFormatSymbols

private val REPEAT_OPTIONS = listOf(0 to "Once", 60 to "Hourly", 30 to "30 min", 15 to "15 min", 1 to "Every minute")

private fun fmtTime(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    val ap = if (h < 12) "AM" else "PM"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, m, ap)
}

/** Birthdays list + reminder preferences. Drop inside SettingsCard. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BirthdaySection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AlbumDatabase.getInstance(context).galleryDao() }
    val list by dao.observeBirthdays().collectAsState(initial = emptyList())
    var prefs by remember { mutableStateOf(BirthdayPrefs.read(context)) }
    var editing by remember { mutableStateOf<BirthdayEntity?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var pendingEveryMinute by remember { mutableStateOf(false) }
    var exactOk by remember { mutableStateOf(canExact(context)) }

    fun save(p: BirthdayPrefs.Snapshot) {
        prefs = p
        BirthdayPrefs.write(context, p)
        BirthdayScheduler.scheduleNextAsync(context)
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Cake, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Birthdays", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Marked on the calendar and reminded on the day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = prefs.notify, onCheckedChange = {
                if (it && Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                save(prefs.copy(notify = it))
            })
        }

        if (list.isEmpty()) {
            Text("No birthdays yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        list.forEach { b ->
            Row(
                Modifier.fillMaxWidth().clickable { editing = b; showDialog = true }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(b.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "${b.day} ${DateFormatSymbols().months[b.month - 1]}" + (b.year?.let { " $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = b.enabled, onCheckedChange = { on ->
                    scope.launch(Dispatchers.IO) { dao.upsertBirthday(b.copy(enabled = on)); BirthdayScheduler.scheduleNext(context) }
                })
                IconButton(onClick = {
                    scope.launch(Dispatchers.IO) { dao.deleteBirthday(b.id); BirthdayScheduler.scheduleNext(context) }
                }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete ${b.name}") }
            }
        }
        OutlinedButton(onClick = { editing = null; showDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Add birthday")
        }

        if (prefs.notify) {
            Text("Remind me", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                REPEAT_OPTIONS.forEach { (min, label) ->
                    FilterChip(
                        selected = prefs.repeatMinutes == min,
                        onClick = { if (min == 1) pendingEveryMinute = true else save(prefs.copy(repeatMinutes = min)) },
                        label = { Text(label) }
                    )
                }
            }
            if (prefs.repeatMinutes != 0) {
                Text("From ${fmtTime(prefs.startMinutes)}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = prefs.startMinutes / 60f, valueRange = 0f..23f, steps = 22,
                    onValueChange = { save(prefs.copy(startMinutes = (it.toInt() * 60), endMinutes = maxOf(prefs.endMinutes, it.toInt() * 60 + 60))) }
                )
                Text("Until ${fmtTime(prefs.endMinutes)}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = prefs.endMinutes / 60f, valueRange = 1f..24f, steps = 22,
                    onValueChange = { save(prefs.copy(endMinutes = maxOf(it.toInt() * 60, prefs.startMinutes + 60).coerceAtMost(24 * 60 - 1))) }
                )
            } else {
                Text("At ${fmtTime(prefs.startMinutes)}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = prefs.startMinutes / 60f, valueRange = 0f..23f, steps = 22,
                    onValueChange = { save(prefs.copy(startMinutes = it.toInt() * 60, endMinutes = maxOf(prefs.endMinutes, it.toInt() * 60 + 60))) }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Also remind the day before", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = prefs.dayBefore, onCheckedChange = { save(prefs.copy(dayBefore = it)) })
            }
            if (!exactOk && prefs.repeatMinutes in 1..30) {
                Text(
                    "Android needs the \"Alarms & reminders\" permission for minute-exact reminders. Without it they may be delayed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 31) {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                    exactOk = canExact(context)
                }) { Text("Allow exact alarms") }
            }
        }
    }

    if (pendingEveryMinute) {
        AlertDialog(
            onDismissRequest = { pendingEveryMinute = false },
            title = { Text("Every minute?") },
            text = { Text("You'll get a notification each minute during the time window on a birthday (max 60 per day). Tap Done on the notification to stop for the day. This uses more battery.") },
            confirmButton = { TextButton(onClick = { pendingEveryMinute = false; save(prefs.copy(repeatMinutes = 1)); exactOk = canExact(context) }) { Text("Use it") } },
            dismissButton = { TextButton(onClick = { pendingEveryMinute = false }) { Text("Cancel") } }
        )
    }

    if (showDialog) {
        BirthdayDialog(
            initial = editing,
            onDismiss = { showDialog = false },
            onSave = { b ->
                showDialog = false
                scope.launch(Dispatchers.IO) { dao.upsertBirthday(b); BirthdayScheduler.scheduleNext(context) }
            }
        )
    }
}

private fun canExact(context: Context): Boolean =
    Build.VERSION.SDK_INT < 31 || (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BirthdayDialog(initial: BirthdayEntity?, onDismiss: () -> Unit, onSave: (BirthdayEntity) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var day by remember { mutableStateOf(initial?.day?.toString() ?: "") }
    var month by remember { mutableStateOf(initial?.month ?: 1) }
    var year by remember { mutableStateOf(initial?.year?.toString() ?: "") }
    val months = remember { DateFormatSymbols().shortMonths.take(12) }
    val d = day.toIntOrNull()
    val maxDay = when (month) { 2 -> 29; 4, 6, 9, 11 -> 30; else -> 31 }
    val yearVal = year.toIntOrNull()
    val valid = name.isNotBlank() && d != null && d in 1..maxDay && (year.isBlank() || (yearVal != null && yearVal in 1900..2100))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add birthday" else "Edit birthday") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    months.forEachIndexed { i, m -> FilterChip(selected = month == i + 1, onClick = { month = i + 1 }, label = { Text(m) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(day, { day = it.filter(Char::isDigit).take(2) }, label = { Text("Day") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Year (optional)") }, singleLine = true, modifier = Modifier.weight(1.4f))
                }
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = {
                onSave((initial ?: BirthdayEntity(name = "", day = 1, month = 1)).copy(
                    name = name.trim(), day = d!!, month = month, year = yearVal
                ))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
