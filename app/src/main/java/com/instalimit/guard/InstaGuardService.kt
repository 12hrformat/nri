package com.instalimit.guard

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
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
    private val windowManager: WindowManager by lazy {
        getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private var lastBlockAt = 0L
    private var instagramOpenedAt = 0L
    private var overlayView: View? = null
    private var lastReelsRect: Rect? = null

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
        hideReelsShield()
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

            val root = rootInActiveWindow

            // Invisible shield over the Reels tab: taps land here and do nothing.
            if (Prefs.blockReels(this) && Settings.canDrawOverlays(this)) {
                updateReelsShield(root)
            } else {
                hideReelsShield()
            }

            if (System.currentTimeMillis() - lastBlockAt < COOLDOWN_MS) return

            if (Prefs.blockReels(this) && looksLikeReels(event, root)) {
                block(AppConfig.REASON_REELS)
                return
            }
            if (Prefs.blockStories(this) && looksLikeStories(event, root)) {
                block(AppConfig.REASON_STORIES)
                return
            }
        } else if (instagramOpenedAt != 0L) {
            hideReelsShield()
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

    private fun updateReelsShield(root: AccessibilityNodeInfo?) {
        val rect = findReelsTabRect(root)
        if (rect == null || rect.width() < 8 || rect.height() < 8) {
            hideReelsShield()
            return
        }
        val previous = lastReelsRect
        if (previous != null &&
            previous.left == rect.left && previous.top == rect.top &&
            previous.right == rect.right && previous.bottom == rect.bottom
        ) return
        lastReelsRect = rect

        val view = overlayView ?: createShieldView() ?: return
        val params = view.layoutParams as WindowManager.LayoutParams
        params.x = rect.left
        params.y = rect.top
        params.width = rect.width()
        params.height = rect.height()
        params.gravity = Gravity.TOP or Gravity.START
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.w(TAG, "Could not position Reels shield", e)
        }
    }

    private fun createShieldView(): View? {
        if (!Settings.canDrawOverlays(this)) return null
        val view = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            // Consume the tap so the Reels tab never receives it.
            setOnTouchListener { _, _ -> true }
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            1, 1, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        return try {
            windowManager.addView(view, params)
            overlayView = view
            view
        } catch (e: Exception) {
            Log.w(TAG, "Could not create Reels shield", e)
            null
        }
    }

    private fun hideReelsShield() {
        lastReelsRect = null
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                Log.w(TAG, "Could not remove Reels shield", e)
            }
        }
        overlayView = null
    }

    private fun findReelsTabRect(node: AccessibilityNodeInfo?): Rect? {
        if (node == null) return null
        val label = buildString {
            append(node.text?.toString().orEmpty())
            append(' ')
            append(node.contentDescription?.toString().orEmpty())
        }.lowercase(Locale.getDefault()).trim()

        if (label.contains("reels") && (node.isClickable || node.isFocusable)) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) return rect
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findReelsTabRect(child)
            if (found != null) return found
        }
        return null
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
        hideReelsShield()

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
