package com.instalimit.guard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.instalimit.guard.ui.BlockScreen
import com.instalimit.guard.ui.InstaLimitTheme

class BlockActivity : ComponentActivity() {

    private var reason by mutableStateOf(AppConfig.REASON_LIMIT)
    private var quote by mutableStateOf("")
    private var usedMs by mutableLongStateOf(0L)
    private var limitHours by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyIntent(intent)

        setContent {
            InstaLimitTheme {
                BlockScreen(
                    reason = reason,
                    quote = quote,
                    usedMs = usedMs,
                    limitHours = limitHours,
                    onDone = { finish() },
                    onChangeLimit = {
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            putExtra(MainActivity.EXTRA_FORCE_SETUP, true)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        })
                        finish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyIntent(intent)
    }

    private fun applyIntent(intent: Intent) {
        reason = intent.getStringExtra(InstaGuardService.EXTRA_REASON)
            ?: AppConfig.REASON_LIMIT
        quote = intent.getStringExtra(InstaGuardService.EXTRA_QUOTE)
            ?: MotivationalQuotes.forReason(reason)
        usedMs = Prefs.getUsedMs(this)
        limitHours = Prefs.getLimitHours(this)
    }
}
