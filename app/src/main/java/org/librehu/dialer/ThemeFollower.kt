package org.librehu.dialer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Follows the effective appearance published by LibreHU Launcher. The provider supplies the current
 * dark flag and accent ARGB; broadcasts and content observation keep an already-running dialer in sync.
 * Android's night mode is the fallback when the launcher is not installed.
 */
internal class ThemeFollower(
    private val context: Context,
    private val onChange: (dark: Boolean, accentArgb: Int) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var started = false

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action == ACTION_THEME_CHANGED) {
                    onChange(intent.getBooleanExtra("dark", true), intent.getIntExtra("accent", 0))
                } else {
                    refresh()
                }
            }
        }

    private val observer =
        object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean) = refresh()
        }

    fun start() {
        if (started) return
        started = true
        val filter =
            IntentFilter().apply {
                addAction(ACTION_THEME_CHANGED)
                addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        runCatching { context.contentResolver.registerContentObserver(THEME_URI, false, observer) }
        refresh()
    }

    fun refreshNow() = refresh()

    fun stop() {
        if (!started) return
        started = false
        runCatching { context.unregisterReceiver(receiver) }
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
    }

    private fun refresh() {
        val launcherTheme =
            runCatching {
                context.contentResolver.query(THEME_URI, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) (cursor.getInt(0) != 0) to cursor.getInt(1) else null
                }
            }.getOrNull()
        val launcherDark =
            launcherTheme?.first ?: run {
                val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                night != Configuration.UI_MODE_NIGHT_NO
            }
        val launcherAccent = launcherTheme?.second ?: 0
        val effectiveDark =
            when (DialerPreferences.appTheme(context)) {
                ThemeMode.AUTO -> launcherDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
        onChange(effectiveDark, launcherAccent)
    }

    private companion object {
        const val ACTION_THEME_CHANGED = "org.librehu.action.THEME_CHANGED"
        val THEME_URI: Uri = Uri.parse("content://org.librehu.launcher.theme/theme")
    }
}
