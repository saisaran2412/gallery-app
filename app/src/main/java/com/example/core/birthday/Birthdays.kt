package com.example.core.birthday

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.local.AlbumDatabase
import com.example.data.local.BirthdayEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Birthday reminder settings and per-day state. SharedPreferences (not DataStore) so alarm receivers
 * can read them instantly, even right after boot.
 */
object BirthdayPrefs {
    private const val FILE = "birthday_prefs"

    data class Snapshot(
        val notify: Boolean = true,
        /** 0 = once on the day, otherwise repeat every N minutes inside the time window (1, 15, 30, 60). */
        val repeatMinutes: Int = 60,
        val startMinutes: Int = 8 * 60,
        val endMinutes: Int = 22 * 60,
        val dayBefore: Boolean = false
    )

    fun read(context: Context): Snapshot {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return Snapshot(
            notify = p.getBoolean("notify", true),
            repeatMinutes = p.getInt("repeat", 60),
            startMinutes = p.getInt("start", 8 * 60),
            endMinutes = p.getInt("end", 22 * 60),
            dayBefore = p.getBoolean("day_before", false)
        )
    }

    fun write(context: Context, s: Snapshot) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("notify", s.notify)
            .putInt("repeat", s.repeatMinutes)
            .putInt("start", s.startMinutes)
            .putInt("end", s.endMinutes.coerceAtLeast(s.startMinutes + 1))
            .putBoolean("day_before", s.dayBefore)
            .apply()
    }

    // ---- per-day state: which day the user tapped "Done", what already fired
    fun doneDay(context: Context): String = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("done_day", "") ?: ""
    fun setDoneDay(context: Context, key: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("done_day", key).apply()
    }
    fun firedDay(context: Context): String = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("fired_day", "") ?: ""
    fun firedCount(context: Context): Int = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("fired_count", 0)
    fun recordFired(context: Context, dayKey: String) {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val count = if (p.getString("fired_day", "") == dayKey) p.getInt("fired_count", 0) + 1 else 1
        p.edit().putString("fired_day", dayKey).putInt("fired_count", count).apply()
    }
    fun beforeFiredFor(context: Context): String = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("before_fired", "") ?: ""
    fun setBeforeFired(context: Context, key: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("before_fired", key).apply()
    }
}

object BirthdayScheduler {
    const val ACTION_FIRE = "com.example.birthday.FIRE"
    const val ACTION_DONE = "com.example.birthday.DONE"
    const val EXTRA_BEFORE = "before"
    private const val REQ_FIRE = 7001
    /** Hard cap on notifications per day, so "every minute" can never run away. */
    private const val MAX_PER_DAY = 60

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class Next(val atMillis: Long, val isDayBefore: Boolean)

    fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0

    /** True when [b] has its birthday on the calendar day [c]. Feb 29 is celebrated on Feb 28 in non-leap years. */
    fun occursOn(b: BirthdayEntity, c: Calendar): Boolean {
        val m = c.get(Calendar.MONTH) + 1
        val d = c.get(Calendar.DAY_OF_MONTH)
        if (b.month == m && b.day == d) return true
        return b.month == 2 && b.day == 29 && m == 2 && d == 28 && !isLeap(c.get(Calendar.YEAR))
    }

    fun dayKey(c: Calendar): String =
        "%04d%02d%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))

    private fun startOfDay(millis: Long, plusDays: Int): Calendar = Calendar.getInstance().apply {
        timeInMillis = millis
        add(Calendar.DAY_OF_YEAR, plusDays)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    private fun atMinutes(day: Calendar, minutes: Int): Long = (day.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, minutes / 60); set(Calendar.MINUTE, minutes % 60)
    }.timeInMillis

    /** Pure scheduling logic: when should the next reminder fire? */
    fun computeNext(
        context: Context,
        now: Long,
        list: List<BirthdayEntity>,
        s: BirthdayPrefs.Snapshot
    ): Next? {
        if (!s.notify || list.isEmpty()) return null
        val doneDay = BirthdayPrefs.doneDay(context)
        val firedDay = BirthdayPrefs.firedDay(context)
        val firedCount = BirthdayPrefs.firedCount(context)
        val beforeFired = BirthdayPrefs.beforeFiredFor(context)
        for (offset in 0..400) {
            val day = startOfDay(now, offset)
            val key = dayKey(day)
            val startAt = atMinutes(day, s.startMinutes)
            val endAt = atMinutes(day, s.endMinutes)
            val hasToday = list.any { occursOn(it, day) }
            if (hasToday && !(offset == 0 && doneDay == key)) {
                val firedToday = if (offset == 0 && firedDay == key) firedCount else 0
                if (s.repeatMinutes <= 0) {
                    if (startAt > now && firedToday == 0) return Next(startAt, false)
                    // phone was off at the start time: still remind once, inside the window
                    if (offset == 0 && now < endAt && firedToday == 0) return Next(now + 5_000L, false)
                } else if (firedToday < MAX_PER_DAY) {
                    val step = s.repeatMinutes * 60_000L
                    val t = if (now < startAt) startAt else startAt + ((now - startAt) / step + 1) * step
                    if (t <= endAt) return Next(t, false)
                }
            }
            if (s.dayBefore) {
                val tomorrow = startOfDay(now, offset + 1)
                if (list.any { occursOn(it, tomorrow) } && startAt > now && beforeFired != key) return Next(startAt, true)
            }
        }
        return null
    }

    fun scheduleNextAsync(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { scheduleNext(app) } }
    }

    suspend fun scheduleNext(context: Context) {
        val dao = AlbumDatabase.getInstance(context).galleryDao()
        val list = dao.getEnabledBirthdays()
        val next = computeNext(context, System.currentTimeMillis(), list, BirthdayPrefs.read(context))
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = firePendingIntent(context, next?.isDayBefore ?: false)
        am.cancel(pi)
        if (next == null) return
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atMillis, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atMillis, pi)
    }

    private fun firePendingIntent(context: Context, before: Boolean): PendingIntent {
        val i = Intent(context, BirthdayReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_BEFORE, before)
        return PendingIntent.getBroadcast(context, REQ_FIRE, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** An alarm went off: show the notification, remember it, plan the next one. */
    suspend fun handleFire(context: Context, before: Boolean) {
        val s = BirthdayPrefs.read(context)
        if (!s.notify) return
        val dao = AlbumDatabase.getInstance(context).galleryDao()
        val list = dao.getEnabledBirthdays()
        val today = startOfDay(System.currentTimeMillis(), 0)
        val target = if (before) startOfDay(System.currentTimeMillis(), 1) else today
        val people = list.filter { occursOn(it, target) }
        if (people.isNotEmpty() && (before || BirthdayPrefs.doneDay(context) != dayKey(today))) {
            BirthdayNotifier.show(context, people, before, target.get(Calendar.YEAR))
        }
        if (before) BirthdayPrefs.setBeforeFired(context, dayKey(today)) else BirthdayPrefs.recordFired(context, dayKey(today))
        scheduleNext(context)
    }
}

object BirthdayNotifier {
    const val CHANNEL_ID = "birthdays"
    private const val NOTIF_ID = 7002

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Birthdays", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Birthday reminders from your list"
                }
            )
        }
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun show(context: Context, people: List<BirthdayEntity>, before: Boolean = false, year: Int = Calendar.getInstance().get(Calendar.YEAR)) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val names = people.joinToString(", ") { p ->
            val age = p.year?.let { year - it }?.takeIf { it in 1..130 }
            if (age != null) "${p.name} ($age)" else p.name
        }
        val title = if (before) "Birthday tomorrow" else "Birthday today"
        val text = if (before) "Tomorrow: $names" else "Wish a happy birthday to $names"

        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = open?.let {
            PendingIntent.getActivity(context, 1, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val done = Intent(context, BirthdayReceiver::class.java).setAction(BirthdayScheduler.ACTION_DONE)
        val donePi = PendingIntent.getBroadcast(context, 2, done, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_birthday)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .apply {
                if (openPi != null) setContentIntent(openPi)
                addAction(0, "Done", donePi)
                if (openPi != null) addAction(0, "Open photos", openPi)
            }
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID, n) }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }
}

/** Handles the reminder alarm, the "Done" button, and re-planning after boot / time change / app update. */
class BirthdayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    BirthdayScheduler.ACTION_FIRE ->
                        BirthdayScheduler.handleFire(app, intent.getBooleanExtra(BirthdayScheduler.EXTRA_BEFORE, false))
                    BirthdayScheduler.ACTION_DONE -> {
                        BirthdayPrefs.setDoneDay(app, BirthdayScheduler.dayKey(Calendar.getInstance()))
                        BirthdayNotifier.cancel(app)
                        BirthdayScheduler.scheduleNext(app)
                    }
                    else -> BirthdayScheduler.scheduleNext(app)   // boot, time or zone change, app update
                }
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }
    }
}
