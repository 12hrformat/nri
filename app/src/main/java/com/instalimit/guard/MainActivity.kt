package com.instalimit.guard

import android.Manifest
import android.app.NotificationChannel
import android.content.ActivityNotFoundException
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.instalimit.guard.ui.HomeScreen
import com.instalimit.guard.ui.InstaLimitTheme
import com.instalimit.guard.ui.SetupScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private var showSetup by mutableStateOf(false)

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* state is re-read on the next tick */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel(this)
        showSetup = intent?.getBooleanExtra(EXTRA_FORCE_SETUP, false) == true ||
            Prefs.getLimitHours(this) == 0

        setContent {
            InstaLimitTheme {
                val context = LocalContext.current
                var tick by remember { mutableLongStateOf(0L) }
                LaunchedEffect(Unit) {
                    while (true) {
                        tick = System.currentTimeMillis()
                        delay(1_000L)
                    }
                }

                var reels by remember { mutableStateOf(Prefs.blockReels(context)) }
                var stories by remember { mutableStateOf(Prefs.blockStories(context)) }
                var quotes by remember { mutableStateOf(Prefs.showQuotes(context)) }

                val hours = remember(tick) { Prefs.getLimitHours(context) }
                val used = remember(tick) { Prefs.getUsedMs(context) }
                val remaining = (hours * AppConfig.HOUR_MS - used).coerceAtLeast(0L)
                val reached = remember(tick) { Prefs.isLimitReached(context) }
                val usageOk = remember(tick) { Prefs.hasUsageAccess(context) }
                val accessOk = remember(tick) { isAccessibilityEnabled(context) }
                val notifOk = remember(tick) { Prefs.hasNotificationPermission(context) }
                val overlayOk = remember(tick) { Prefs.hasOverlayPermission(context) }

                if (showSetup) {
                    SetupScreen(onLimitChosen = { chosen ->
                        Prefs.setLimitHours(context, chosen)
                        showSetup = false
                    })
                } else {
                    HomeScreen(
                        limitHours = hours,
                        usedMs = used,
                        remainingMs = remaining,
                        limitReached = reached,
                        blockReels = reels,
                        blockStories = stories,
                        showQuotes = quotes,
                        usageAccessGranted = usageOk,
                        accessibilityEnabled = accessOk,
                        notificationGranted = notifOk,
                        overlayGranted = overlayOk,
                        onOpenInstagram = { openInstagram() },
                        onChangeLimit = { showSetup = true },
                        onToggleReels = {
                            Prefs.setBlockReels(context, it)
                            reels = it
                        },
                        onToggleStories = {
                            Prefs.setBlockStories(context, it)
                            stories = it
                        },
                        onToggleQuotes = {
                            Prefs.setShowQuotes(context, it)
                            quotes = it
                        },
                        onUsageSettings = { openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS) },
                        onAccessibilitySettings = {
                            openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        },
                        onNotificationRequest = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onOverlaySettings = {
                            try {
                                startActivity(
                                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                        data = Uri.parse("package:$packageName")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            } catch (e: ActivityNotFoundException) {
                                openSettings(Settings.ACTION_SETTINGS)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_FORCE_SETUP, false)) {
            showSetup = true
        }
    }

    private fun openInstagram() {
        if (Prefs.isLimitReached(this)) {
            showBlockScreen(AppConfig.REASON_LIMIT)
            return
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(AppConfig.INSTAGRAM_PACKAGE)
        if (launchIntent == null) {
            Toast.makeText(this, "Instagram isn't installed on this device.", Toast.LENGTH_LONG).show()
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
    }

    private fun showBlockScreen(reason: String) {
        startActivity(Intent(this, BlockActivity::class.java).apply {
            putExtra(InstaGuardService.EXTRA_REASON, reason)
            putExtra(InstaGuardService.EXTRA_QUOTE, MotivationalQuotes.forReason(reason))
        })
    }

    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = "${context.packageName}/${InstaGuardService::class.java.name}"
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AppConfig.CHANNEL_ID,
                "Limit alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when Instagram is blocked or your limit is reached"
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        const val EXTRA_FORCE_SETUP = "force_setup"

        @Suppress("unused")
        fun openAppDetails(context: Context) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        @Suppress("unused")
        fun isInstalled(context: Context): Boolean = try {
            context.packageManager.getPackageInfo(AppConfig.INSTAGRAM_PACKAGE, 0) != null
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
