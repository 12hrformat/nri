package com.instalimit.guard

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * The engine of the app.
 *
 * It does two jobs:
 *  1. Polls Android's usage stats and closes Instagram when today's limit is spent.
 *  2. Watches Instagram's screens and bounces you out of Reels / stories,
 *     showing a motivational screen instead.
 *
 * It never modifies or repackages Instagram.
 */
class InstaGuardService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val usageStats: UsageStatsManager by lazy {
        getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    }

    private var lastBlockAt = 0L
    private var instagramOpenedAt = 0L

    private val pollRunnable = object : Runnable {
        override fun run() {
            try {
                checkUsage()
            } catch (t: Throwable) {
                Log.w(TAG, "Usage check failed", t)
            }
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        handler.post(pollRunnable)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString().orEmpty()

        if (pkg == AppConfig.INSTAGRAM_PACKAGE) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                instagramOpenedAt == 0L
            ) {
                instagramOpenedAt = System.currentTimeMillis()
            }

            if (Prefs.isLimitReached(this)) {
                block(AppConfig.REASON_LIMIT)
                return
            }

            if (System.currentTimeMillis() - lastBlockAt < COOLDOWN_MS) return

            val root = rootInActiveWindow
            if (Prefs.blockReels(this) && looksLikeReels(event, root)) {
                block(AppConfig.REASON_REELS)
                return
            }
            if (Prefs.blockStories(this) && looksLikeStories(event, root)) {
                block(AppConfig.REASON_STORIES)
                return
            }
        } else if (instagramOpenedAt != 0L) {
            // Fallback timing when usage-access permission was not granted.
            if (!Prefs.hasUsageAccess(this)) {
                Prefs.addFallbackMs(this, System.currentTimeMillis() - instagramOpenedAt)
                try {
                    checkUsage()
                } catch (t: Throwable) {
                    Log.w(TAG, "Fallback usage check failed", t)
                }
            }
            instagramOpenedAt = 0L
        }
    }

    private fun checkUsage() {
        val hours = Prefs.getLimitHours(this)
        if (hours <= 0) return
        if (Prefs.isLimitReached(this)) return

        val start = Prefs.getStartTime(this)
        if (start <= 0L) return

        val usedMs = if (Prefs.hasUsageAccess(this)) {
            val stats = usageStats.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                start,
                System.currentTimeMillis()
            )
            stats?.asSequence()
                ?.filter { it.packageName == AppConfig.INSTAGRAM_PACKAGE }
                ?.sumOf { it.totalTimeInForeground }
                ?: 0L
        } else {
            Prefs.getFallbackMs(this)
        }

        Prefs.setUsedMs(this, usedMs)

        if (usedMs >= hours * AppConfig.HOUR_MS) {
            Prefs.markLimitReached(this)
            block(AppConfig.REASON_LIMIT)
        }
    }

    private fun looksLikeReels(event: AccessibilityEvent, root: AccessibilityNodeInfo?): Boolean {
        val className = event.className?.toString().orEmpty().lowercase(Locale.getDefault())
        if (className.contains("reel")) return true
        return containsText(root, listOf("reels"))
    }

    private fun looksLikeStories(event: AccessibilityEvent, root: AccessibilityNodeInfo?): Boolean {
        val className = event.className?.toString().orEmpty().lowercase(Locale.getDefault())
        if (className.contains("story")) return true
        return containsText(root, listOf("stories", "story viewer", "view story"))
    }

    /**
     * Looks for words in non-interactive text only. This avoids matching the
     * "Reels" tab in Instagram's bottom navigation, which would block everything.
     */
    private fun containsText(
        node: AccessibilityNodeInfo?,
        needles: List<String>,
        insideClickable: Boolean = false
    ): Boolean {
        if (node == null) return false
        val clickable = insideClickable || node.isClickable

        val text = buildString {
            append(node.text?.toString().orEmpty())
            append(' ')
            append(node.contentDescription?.toString().orEmpty())
        }.lowercase(Locale.getDefault())

        if (!clickable && needles.any { text.contains(it) }) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (containsText(child, needles, clickable)) return true
        }
        return false
    }

    private fun block(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastBlockAt < COOLDOWN_MS) return
        lastBlockAt = now

        val quote = if (Prefs.showQuotes(this)) {
            MotivationalQuotes.forReason(reason)
        } else {
            "Your time is up. Step away."
        }

        // Leave Instagram first, then put our message on top.
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        val intent = Intent(this, BlockActivity::class.java).apply {
            putExtra(EXTRA_REASON, reason)
            putExtra(EXTRA_QUOTE, quote)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        handler.postDelayed({ startActivity(intent) }, 150L)

        showNotification(reason, quote)
    }

    private fun showNotification(reason: String, quote: String) {
        if (!Prefs.hasNotificationPermission(this)) return
        val title = when (reason) {
            AppConfig.REASON_REELS -> "Reels blocked"
            AppConfig.REASON_STORIES -> "Stories blocked"
            else -> "Limit reached"
        }
        val notification = NotificationCompat.Builder(this, AppConfig.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(quote)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    fun isRunning(): Boolean = true

    companion object {
        private const val TAG = "InstaGuard"
        private const val POLL_MS = 15_000L
        private const val COOLDOWN_MS = 3_000L
        private const val NOTIFICATION_ID = 42

        const val EXTRA_REASON = "reason"
        const val EXTRA_QUOTE = "quote"

        @Suppress("unused")
        fun isEnabled(context: Context): Boolean {
            val expected = context.packageName + "/" + InstaGuardService::class.java.name
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }

        @Suppress("unused")
        fun hasNotificationPermissionCompat(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
            return ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
