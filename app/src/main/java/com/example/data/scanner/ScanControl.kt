package com.example.data.scanner

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/** Why a scan is waiting on its own (not because the user paused it). */
enum class HoldReason { NONE, USER, LOW_BATTERY, HOT }

/** Smart power/thermal reading, cached for a few seconds so workers don't poll the system. */
class PowerGate(context: Context, private val smart: () -> Boolean = { true }) {
    private val app = context.applicationContext
    private var stamp = 0L
    private var level = 100
    private var charging = true
    private var saver = false
    private var thermal = 0

    private fun refresh() {
        val now = System.currentTimeMillis()
        if (now - stamp < 3000) return
        stamp = now
        val i: Intent? = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (i != null) {
            val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
            val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            level = l * 100 / s
            val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        }
        val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        saver = pm?.isPowerSaveMode == true
        thermal = if (Build.VERSION.SDK_INT >= 29) pm?.currentThermalStatus ?: 0 else 0
    }

    /** Non-NONE means the scan should wait. */
    fun holdReason(): HoldReason {
        if (!smart()) return HoldReason.NONE
        refresh()
        if (Build.VERSION.SDK_INT >= 29 && thermal >= PowerManager.THERMAL_STATUS_SEVERE) return HoldReason.HOT
        if (!charging && level <= 15) return HoldReason.LOW_BATTERY
        return HoldReason.NONE
    }

    /** Extra delay (ms) between items when the device is stressed. */
    fun throttleMs(): Long {
        if (!smart()) return 0
        refresh()
        var ms = 0L
        if (saver) ms += 25
        if (!charging && level <= 30) ms += 15
        if (Build.VERSION.SDK_INT >= 29 && thermal >= PowerManager.THERMAL_STATUS_MODERATE) ms += 40
        return ms
    }

    fun workers(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        if (!smart()) return (cores / 2).coerceIn(1, 4)
        refresh()
        val hot = Build.VERSION.SDK_INT >= 29 && thermal >= PowerManager.THERMAL_STATUS_MODERATE
        return when {
            hot || saver || (!charging && level <= 30) -> 1
            charging -> (cores / 2).coerceIn(2, 4)
            else -> 2
        }
    }
}

/** Pause / resume / stop handle shared by every long-running scan. */
class ScanControl(private val gate: PowerGate) {
    private val userPaused = MutableStateFlow(false)
    @Volatile private var stopped = false
    private val _hold = MutableStateFlow(HoldReason.NONE)
    val hold: StateFlow<HoldReason> = _hold.asStateFlow()
    val isPaused: StateFlow<Boolean> = userPaused.asStateFlow()

    fun pause() { userPaused.value = true; _hold.value = HoldReason.USER }
    fun resume() { userPaused.value = false; _hold.value = HoldReason.NONE }
    fun stop() { stopped = true; userPaused.value = false }
    val isStopped: Boolean get() = stopped

    fun workers() = gate.workers()

    /** Call between items. Returns false when the scan should end (stopped). Suspends while paused/held. */
    suspend fun checkpoint(): Boolean {
        if (stopped) return false
        while (!stopped) {
            if (userPaused.value) {
                _hold.value = HoldReason.USER
                userPaused.first { !it || stopped }
                continue
            }
            val r = gate.holdReason()
            if (r == HoldReason.NONE) break
            _hold.value = r
            delay(4000)
        }
        if (stopped) return false
        _hold.value = HoldReason.NONE
        val t = gate.throttleMs()
        if (t > 0) delay(t)
        return !stopped
    }
}

/**
 * Runs [block] over [items] on [workers] parallel workers, honouring [control].
 * Progress is throttled. Returns number of items completed.
 */
suspend fun <T> runParallel(
    items: List<T>,
    control: ScanControl,
    workers: Int = control.workers(),
    onProgress: (Int, Int) -> Unit,
    block: suspend (T) -> Unit
): Int = coroutineScope {
    val queue = ConcurrentLinkedQueue(items)
    val done = AtomicInteger(0)
    val total = items.size
    val jobs = List(workers.coerceAtLeast(1)) {
        launch(Dispatchers.IO) {
            while (true) {
                if (!control.checkpoint()) break
                val item = queue.poll() ?: break
                try { block(item) } catch (e: CancellationException) { throw e } catch (_: Throwable) { }
                val d = done.incrementAndGet()
                if (d % 4 == 0 || d == total) onProgress(d, total)
            }
        }
    }
    jobs.forEach { it.join() }
    done.get()
}
