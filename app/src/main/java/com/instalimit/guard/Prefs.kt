package com.instalimit.guard

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * All state is stored locally on the device. Nothing is uploaded anywhere.
 */
object Prefs {
    private const val NAME = "instalimit_prefs"

    private const val KEY_LIMIT_HOURS = "limit_hours"
    private const val KEY_START_TIME = "start_time"
    private const val KEY_USED_MS = "used_ms"
    private const val KEY_FALLBACK_MS = "fallback_ms"
    private const val KEY_LIMIT_REACHED = "limit_reached"
    private const val KEY_BLOCK_REELS = "block_reels"
    private const val KEY_BLOCK_STORIES = "block_stories"
    private const val KEY_SHOW_QUOTES = "show_quotes"
    private const val KEY_LAST_DAY = "last_day"
    private const val USAGE_STATS_OP = "android:get_usage_stats"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    /** A new local day resets the timer but remembers the chosen limit. */
    fun ensureFreshDay(context: Context) {
        val p = prefs(context)
        if (p.getString(KEY_LAST_DAY, null) != today()) {
            p.edit()
                .putString(KEY_LAST_DAY, today())
                .putLong(KEY_START_TIME, 0L)
                .putLong(KEY_USED_MS, 0L)
                .putLong(KEY_FALLBACK_MS, 0L)
                .putBoolean(KEY_LIMIT_REACHED, false)
                .apply()
        }
    }

    fun getLimitHours(context: Context): Int {
        ensureFreshDay(context)
        return prefs(context).getInt(KEY_LIMIT_HOURS, 0)
    }

    /** Sets (or changes) today's limit and restarts the timer from now. */
    fun setLimitHours(context: Context, hours: Int) {
        ensureFreshDay(context)
        prefs(context).edit()
            .putInt(KEY_LIMIT_HOURS, hours.coerceIn(1, 6))
            .putLong(KEY_START_TIME, System.currentTimeMillis())
            .putLong(KEY_USED_MS, 0L)
            .putLong(KEY_FALLBACK_MS, 0L)
            .putBoolean(KEY_LIMIT_REACHED, false)
            .putString(KEY_LAST_DAY, today())
            .apply()
    }

    fun getStartTime(context: Context): Long {
        ensureFreshDay(context)
        return prefs(context).getLong(KEY_START_TIME, 0L)
    }

    fun getUsedMs(context: Context): Long {
        ensureFreshDay(context)
        return prefs(context).getLong(KEY_USED_MS, 0L)
    }

    fun setUsedMs(context: Context, ms: Long) {
        prefs(context).edit().putLong(KEY_USED_MS, ms.coerceAtLeast(0L)).apply()
    }

    fun getFallbackMs(context: Context): Long {
        ensureFreshDay(context)
        return prefs(context).getLong(KEY_FALLBACK_MS, 0L)
    }

    fun addFallbackMs(context: Context, delta: Long) {
        if (delta <= 0L) return
        val current = prefs(context).getLong(KEY_FALLBACK_MS, 0L)
        prefs(context).edit().putLong(KEY_FALLBACK_MS, current + delta).apply()
    }

    fun isLimitReached(context: Context): Boolean {
        ensureFreshDay(context)
        return prefs(context).getBoolean(KEY_LIMIT_REACHED, false)
    }

    fun markLimitReached(context: Context) {
        prefs(context).edit().putBoolean(KEY_LIMIT_REACHED, true).apply()
    }

    fun blockReels(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BLOCK_REELS, true)

    fun setBlockReels(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_BLOCK_REELS, value).apply()

    fun blockStories(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BLOCK_STORIES, true)

    fun setBlockStories(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_BLOCK_STORIES, value).apply()

    fun showQuotes(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_QUOTES, true)

    fun setShowQuotes(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_SHOW_QUOTES, value).apply()

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                USAGE_STATS_OP,
                Process.myUid(),
                context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                USAGE_STATS_OP,
                Process.myUid(),
                context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
