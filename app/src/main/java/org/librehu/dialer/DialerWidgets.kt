package org.librehu.dialer

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.provider.CallLog
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

internal data class WidgetTheme(
    val dark: Boolean,
    val accent: Int,
    val backgroundRes: Int,
    val cardRes: Int,
    val chipRes: Int,
    val autoStyle: Boolean,
)

internal object DialerWidgetTheme {
    private const val LAUNCHER_THEME_URI = "content://org.librehu.launcher.theme/theme"

    fun resolve(context: Context): WidgetTheme {
        val launcher =
            runCatching {
                context.contentResolver.query(Uri.parse(LAUNCHER_THEME_URI), null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) (cursor.getInt(0) != 0) to cursor.getInt(1) else null
                }
            }.getOrNull()

        val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_NO
        val sourceDark = launcher?.first ?: systemDark
        val dark =
            when (DialerPreferences.widgetTheme(context)) {
                ThemeMode.AUTO -> sourceDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
        val accent = launcher?.second?.takeIf { it != 0 } ?: if (dark) 0xFF8AB4F8.toInt() else 0xFF1A73E8.toInt()

        return WidgetTheme(
            dark = dark,
            accent = accent,
            backgroundRes = if (dark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light,
            cardRes = if (dark) R.drawable.widget_card_dark else R.drawable.widget_card_light,
            chipRes = if (dark) R.drawable.widget_chip_dark else R.drawable.widget_chip_light,
            autoStyle = DialerPreferences.widgetStyle(context) == WidgetStyle.AUTO,
        )
    }

    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val shortcuts = manager.getAppWidgetIds(ComponentName(context, DialerShortcutsWidget::class.java))
        val recent = manager.getAppWidgetIds(ComponentName(context, DialerRecentCallsWidget::class.java))
        DialerShortcutsWidget().onUpdate(context, manager, shortcuts)
        DialerRecentCallsWidget().let { provider -> recent.forEach { provider.updateOnePublic(context, manager, it) } }
    }
}

private object DialerWidgetIntents {
    fun open(
        context: Context,
        tab: String,
        requestCode: Int,
    ): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_TAB, tab)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class DialerShortcutsWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        ids.forEach { id ->
            val theme = DialerWidgetTheme.resolve(context)
            val primary = if (theme.dark) 0xFFE8EAED.toInt() else 0xFF202124.toInt()
            val muted = if (theme.dark) 0xFF9AA0A6.toInt() else 0xFF5F6368.toInt()
            val views =
                RemoteViews(context.packageName, R.layout.widget_dialer_shortcuts).apply {
                    setInt(R.id.widget_shortcuts_root, "setBackgroundResource", theme.backgroundRes)
                    setInt(R.id.widget_shortcuts_card, "setBackgroundResource", theme.cardRes)
                    listOf(R.id.widget_open_dialer, R.id.widget_contacts, R.id.widget_recents, R.id.widget_keypad).forEach {
                        setInt(it, "setBackgroundResource", theme.chipRes)
                        setTextColor(it, primary)
                    }
                    setInt(R.id.widget_accent_bar, "setBackgroundColor", theme.accent)
                    setTextColor(R.id.widget_title, primary)
                    setTextColor(R.id.widget_subtitle, muted)
                    setTextViewTextSize(R.id.widget_title, android.util.TypedValue.COMPLEX_UNIT_SP, if (theme.autoStyle) 17f else 15f)
                    setTextViewTextSize(R.id.widget_subtitle, android.util.TypedValue.COMPLEX_UNIT_SP, if (theme.autoStyle) 11f else 10f)
                    setOnClickPendingIntent(R.id.widget_open_dialer, DialerWidgetIntents.open(context, "FAVORITES", id * 10 + 1))
                    setOnClickPendingIntent(R.id.widget_contacts, DialerWidgetIntents.open(context, "CONTACTS", id * 10 + 2))
                    setOnClickPendingIntent(R.id.widget_recents, DialerWidgetIntents.open(context, "RECENTS", id * 10 + 3))
                    setOnClickPendingIntent(R.id.widget_keypad, DialerWidgetIntents.open(context, "KEYPAD", id * 10 + 4))
                }
            manager.updateAppWidget(id, views)
        }
    }
}

class DialerRecentCallsWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        ids.forEach { updateOnePublic(context, manager, it) }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DialerRecentCallsWidget::class.java))
            val provider = DialerRecentCallsWidget()
            ids.forEach { provider.updateOnePublic(context, manager, it) }
        }
    }

    internal fun updateOnePublic(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
    ) {
        val theme = DialerWidgetTheme.resolve(context)
        val primary = if (theme.dark) 0xFFE8EAED.toInt() else 0xFF202124.toInt()
        val muted = if (theme.dark) 0xFF9AA0A6.toInt() else 0xFF5F6368.toInt()
        val views =
            RemoteViews(context.packageName, R.layout.widget_recent_calls).apply {
                setInt(R.id.widget_recent_root, "setBackgroundResource", theme.backgroundRes)
                setInt(R.id.widget_recent_card, "setBackgroundResource", theme.cardRes)
                setInt(R.id.widget_recent_open, "setBackgroundResource", theme.chipRes)
                setInt(R.id.widget_recent_accent_bar, "setBackgroundColor", theme.accent)
                setTextColor(R.id.widget_recent_title, primary)
                setTextColor(R.id.widget_recent_summary, muted)
                setTextColor(R.id.widget_recent_open, primary)
                setTextViewTextSize(R.id.widget_recent_title, android.util.TypedValue.COMPLEX_UNIT_SP, if (theme.autoStyle) 17f else 15f)
                setTextViewTextSize(R.id.widget_recent_summary, android.util.TypedValue.COMPLEX_UNIT_SP, if (theme.autoStyle) 11f else 10f)
                setOnClickPendingIntent(R.id.widget_recent_root, DialerWidgetIntents.open(context, "RECENTS", id * 10 + 5))
                setOnClickPendingIntent(R.id.widget_recent_open, DialerWidgetIntents.open(context, "RECENTS", id * 10 + 6))
                listOf(R.id.widget_recent_line1, R.id.widget_recent_line2, R.id.widget_recent_line3).forEach {
                    setInt(it, "setBackgroundResource", theme.chipRes)
                    setTextColor(it, primary)
                    setTextViewTextSize(it, android.util.TypedValue.COMPLEX_UNIT_SP, if (theme.autoStyle) 12f else 11f)
                }
            }
        val rows = readRecentCalls(context)
        val lineIds = intArrayOf(R.id.widget_recent_line1, R.id.widget_recent_line2, R.id.widget_recent_line3)
        if (rows == null) {
            views.setTextViewText(R.id.widget_recent_summary, context.getString(R.string.widget_need_permission))
            lineIds.forEach {
                views.setTextViewText(it, "")
                views.setViewVisibility(it, android.view.View.GONE)
            }
        } else {
            views.setTextViewText(
                R.id.widget_recent_summary,
                if (rows.isEmpty()) context.getString(R.string.recents_empty) else context.getString(R.string.widget_latest),
            )
            lineIds.forEachIndexed { index, viewId ->
                val line = rows.getOrNull(index)
                views.setTextViewText(viewId, line ?: "")
                views.setViewVisibility(viewId, if (line == null) android.view.View.GONE else android.view.View.VISIBLE)
            }
        }
        manager.updateAppWidget(id, views)
    }

    private fun readRecentCalls(context: Context): List<String>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return null
        val result = mutableListOf<String>()
        val projection = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE)
        return runCatching {
            context.contentResolver
                .query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    null,
                    null,
                    CallLog.Calls.DATE + " DESC",
                )?.use { cursor ->
                    val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                    val nameIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                    val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                    val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                    val formatter = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
                    while (cursor.moveToNext() && result.size < 3) {
                        val number = cursor.getString(numberIndex).orEmpty()
                        val name =
                            cursor
                                .getString(
                                    nameIndex,
                                )?.trim()
                                .orEmpty()
                                .ifBlank { number.ifBlank { context.getString(R.string.call_unknown) } }
                        val type =
                            when (cursor.getInt(typeIndex)) {
                                CallLog.Calls.INCOMING_TYPE -> context.getString(R.string.call_incoming)
                                CallLog.Calls.OUTGOING_TYPE -> context.getString(R.string.call_outgoing)
                                CallLog.Calls.MISSED_TYPE -> context.getString(R.string.call_missed)
                                CallLog.Calls.REJECTED_TYPE -> context.getString(R.string.call_rejected)
                                else -> context.getString(R.string.call)
                            }
                        val date = formatter.format(java.util.Date(cursor.getLong(dateIndex)))
                        val details = if (DialerPreferences.widgetShowRecents(context)) " · " + type + " · " + date else ""
                        result.add(name + details)
                    }
                }
            result
        }.getOrNull()
    }
}
