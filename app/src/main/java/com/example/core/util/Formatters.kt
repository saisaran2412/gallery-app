package com.example.core.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object Formatters {

    private val fullDateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("EEE, MMM d, yyyy • h:mm a", Locale.getDefault())
    }
    private val sameYearFormat = ThreadLocal.withInitial {
        SimpleDateFormat("EEE, MMM d", Locale.getDefault())
    }
    private val otherYearFormat = ThreadLocal.withInitial {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    }

    fun formatDuration(millis: Long): String {
        if (millis <= 0L) return "00:00"
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> "${(bytes / 1024.0).roundToInt()} KB"
            bytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun formatFullDate(millis: Long): String {
        return fullDateFormat.get()?.format(Date(millis)).orEmpty()
    }

    fun formatDateSectionHeader(millis: Long): String {
        val cal = Calendar.getInstance()
        val todayYear = cal.get(Calendar.YEAR)
        val todayDay = cal.get(Calendar.DAY_OF_YEAR)

        cal.timeInMillis = millis
        val itemYear = cal.get(Calendar.YEAR)
        val itemDay = cal.get(Calendar.DAY_OF_YEAR)

        return when {
            itemYear == todayYear && itemDay == todayDay -> "Today"
            itemYear == todayYear && itemDay == todayDay - 1 -> "Yesterday"
            itemYear == todayYear -> sameYearFormat.get()?.format(Date(millis)).orEmpty()
            else -> otherYearFormat.get()?.format(Date(millis)).orEmpty()
        }
    }

    /**
     * Fast date section resolver for batch timeline grouping without per-item Calendar allocations.
     */
    class SectionHeaderResolver {
        private val cal = Calendar.getInstance()
        private val todayYear = cal.get(Calendar.YEAR)
        private val todayDay = cal.get(Calendar.DAY_OF_YEAR)
        private val dayKeyCache = HashMap<Long, String>(64)

        fun resolve(millis: Long): String {
            cal.timeInMillis = millis
            val y = cal.get(Calendar.YEAR)
            val d = cal.get(Calendar.DAY_OF_YEAR)
            val key = (y.toLong() shl 10) or d.toLong()
            return dayKeyCache.getOrPut(key) {
                when {
                    y == todayYear && d == todayDay -> "Today"
                    y == todayYear && d == todayDay - 1 -> "Yesterday"
                    y == todayYear -> sameYearFormat.get()?.format(Date(millis)).orEmpty()
                    else -> otherYearFormat.get()?.format(Date(millis)).orEmpty()
                }
            }
        }
    }
}
