package com.example.share

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class QuickShareTarget(
    val id: String,
    val label: String,
    val packageName: String? = null,
    val activityClassName: String? = null,
    val iconBitmap: ImageBitmap? = null,
    val isAirShare: Boolean = false,
    val isCopyClipboard: Boolean = false,
    val isSystemShare: Boolean = false,
    val isPinned: Boolean = false
)

object ShareTargetsHelper {

    private const val PREFS_NAME = "folio_share_mru"
    private const val PINS_PREFS = "folio_share_pins"

    fun isPinned(context: Context, packageName: String?): Boolean =
        !packageName.isNullOrBlank() &&
            context.getSharedPreferences(PINS_PREFS, Context.MODE_PRIVATE).getBoolean(packageName, false)

    /** Returns the new pinned state. */
    fun togglePin(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val prefs = context.getSharedPreferences(PINS_PREFS, Context.MODE_PRIVATE)
        val now = !prefs.getBoolean(packageName, false)
        prefs.edit().putBoolean(packageName, now).apply()
        return now
    }

    private val PRIORITY_PACKAGES = listOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.instagram.android",
        "com.facebook.katana",
        "com.facebook.orca",
        "org.telegram.messenger",
        "com.google.android.apps.messaging",
        "com.snapchat.android",
        "com.google.android.gm"
    )

    fun recordShareUsage(context: Context, packageName: String?) {
        if (packageName.isNullOrBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(packageName, 0) + 1
        prefs.edit().putInt(packageName, count).apply()
    }

    fun resolveQuickShareTargets(
        context: Context,
        mimeType: String = "image/*",
        maxAppTargets: Int = 4
    ): List<QuickShareTarget> {
        val result = mutableListOf<QuickShareTarget>()
        result.add(
            QuickShareTarget(
                id = "airshare",
                label = "AirShare",
                isAirShare = true
            )
        )

        val pm = context.packageManager
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val probeIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType.ifBlank { "image/*" }
        }

        val resolved: List<ResolveInfo> = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    probeIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(probeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            }
        }.getOrDefault(emptyList())

        val ownPackage = context.packageName
        val distinctByPkg = resolved
            .filter { it.activityInfo?.packageName != null && it.activityInfo.packageName != ownPackage }
            .distinctBy { it.activityInfo.packageName }
            .sortedWith(
                compareByDescending<ResolveInfo> { info ->
                    if (isPinned(context, info.activityInfo.packageName)) 1 else 0
                }.thenByDescending { info ->
                    prefs.getInt(info.activityInfo.packageName, 0)
                }.thenBy { info ->
                    val idx = PRIORITY_PACKAGES.indexOf(info.activityInfo.packageName)
                    if (idx >= 0) idx else PRIORITY_PACKAGES.size + 1
                }
            )
            .take(maxAppTargets)

        distinctByPkg.forEach { info ->
            val pkg = info.activityInfo.packageName
            val cls = info.activityInfo.name
            val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
            val bitmap = runCatching {
                info.loadIcon(pm).toBitmap(width = 88, height = 88).asImageBitmap()
            }.getOrNull()

            result.add(
                QuickShareTarget(
                    id = "app_$pkg",
                    label = label,
                    packageName = pkg,
                    activityClassName = cls,
                    iconBitmap = bitmap,
                    isPinned = isPinned(context, pkg)
                )
            )
        }

        result.add(
            QuickShareTarget(
                id = "copy",
                label = "Copy",
                isCopyClipboard = true
            )
        )

        result.add(
            QuickShareTarget(
                id = "system_share",
                label = "More",
                isSystemShare = true
            )
        )

        return result
    }
}
