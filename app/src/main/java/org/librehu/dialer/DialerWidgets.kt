package org.librehu.dialer

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CallLog
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

private object DialerWidgetIntents {
    fun open(context: Context, tab: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_TAB, tab)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

class DialerShortcutsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_dialer_shortcuts).apply {
                setOnClickPendingIntent(R.id.widget_open_dialer, DialerWidgetIntents.open(context, "Favorites", id * 10 + 1))
                setOnClickPendingIntent(R.id.widget_contacts, DialerWidgetIntents.open(context, "Contacts", id * 10 + 2))
                setOnClickPendingIntent(R.id.widget_recents, DialerWidgetIntents.open(context, "Recents", id * 10 + 3))
                setOnClickPendingIntent(R.id.widget_keypad, DialerWidgetIntents.open(context, "Keypad", id * 10 + 4))
            }
            manager.updateAppWidget(id, views)
        }
    }
}

class DialerRecentCallsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateOne(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: return
            ids.forEach { updateOne(context, manager, it) }
        }
    }

    private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_recent_calls)
        views.setOnClickPendingIntent(R.id.widget_recent_root, DialerWidgetIntents.open(context, "Recents", id * 10 + 5))
        views.setOnClickPendingIntent(R.id.widget_recent_open, DialerWidgetIntents.open(context, "Recents", id * 10 + 6))
        val rows = readRecentCalls(context)
        val lineIds = intArrayOf(R.id.widget_recent_line1, R.id.widget_recent_line2, R.id.widget_recent_line3)
        if (rows == null) {
            views.setTextViewText(R.id.widget_recent_summary, "Open dialer and allow call-log access")
            lineIds.forEach { viewId ->
                views.setTextViewText(viewId, "")
                views.setViewVisibility(viewId, android.view.View.GONE)
            }
        } else {
            views.setTextViewText(R.id.widget_recent_summary, if (rows.isEmpty()) "No recent calls" else "Latest calls")
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
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI, projection, null, null, CallLog.Calls.DATE + " DESC"
            )?.use { cursor ->
                val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val formatter = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
                while (cursor.moveToNext() && result.size < 3) {
                    val number = cursor.getString(numberIndex).orEmpty()
                    val name = cursor.getString(nameIndex)?.trim().orEmpty().ifBlank { number.ifBlank { "Unknown number" } }
                    val type = when (cursor.getInt(typeIndex)) {
                        CallLog.Calls.INCOMING_TYPE -> "Incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                        CallLog.Calls.MISSED_TYPE -> "Missed"
                        CallLog.Calls.REJECTED_TYPE -> "Rejected"
                        else -> "Call"
                    }
                    result.add(name + " · " + type + " · " + formatter.format(java.util.Date(cursor.getLong(dateIndex))))
                }
            }
            result
        }.getOrNull()
    }
}
