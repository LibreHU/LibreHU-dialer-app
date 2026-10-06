package org.librehu.dialer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Dial pad on the home screen: the number is kept per widget; "Call" hands it to LibreHU Dialer. */
class DialerKeypadWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onDeleted(
        context: Context,
        ids: IntArray,
    ) {
        val edit = prefs(context).edit()
        ids.forEach { edit.remove(key(it)) }
        edit.apply()
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || intent.action !in ACTIONS) {
            super.onReceive(context, intent)
            return
        }
        val number = prefs(context).getString(key(id), "").orEmpty()
        when (intent.action) {
            ACTION_KEY -> {
                setNumber(context, id, (number + intent.getStringExtra(EXTRA_KEY).orEmpty()).take(MAX_LENGTH))
            }

            ACTION_DELETE -> {
                setNumber(context, id, number.dropLast(1))
            }

            ACTION_CALL -> {
                if (number.isNotBlank()) {
                    context.startActivity(
                        Intent(context, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_CALL_NUMBER, number)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    )
                    setNumber(context, id, "")
                } else {
                    context.startActivity(
                        Intent(context, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_OPEN_TAB, "KEYPAD")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    )
                }
            }
        }
    }

    private fun setNumber(
        context: Context,
        id: Int,
        number: String,
    ) {
        prefs(context).edit().putString(key(id), number).apply()
        update(context, AppWidgetManager.getInstance(context), id)
    }

    companion object {
        private const val ACTION_KEY = "org.librehu.dialer.widget.KEY"
        private const val ACTION_DELETE = "org.librehu.dialer.widget.DELETE"
        private const val ACTION_CALL = "org.librehu.dialer.widget.CALL"
        private val ACTIONS = setOf(ACTION_KEY, ACTION_DELETE, ACTION_CALL)
        private const val EXTRA_KEY = "key"
        private const val MAX_LENGTH = 32

        private val KEYS =
            listOf(
                "1" to R.id.widget_keypad_k1,
                "2" to R.id.widget_keypad_k2,
                "3" to R.id.widget_keypad_k3,
                "4" to R.id.widget_keypad_k4,
                "5" to R.id.widget_keypad_k5,
                "6" to R.id.widget_keypad_k6,
                "7" to R.id.widget_keypad_k7,
                "8" to R.id.widget_keypad_k8,
                "9" to R.id.widget_keypad_k9,
                "*" to R.id.widget_keypad_kstar,
                "0" to R.id.widget_keypad_k0,
                "#" to R.id.widget_keypad_khash,
            )

        private fun prefs(context: Context) = context.getSharedPreferences("keypad_widget", Context.MODE_PRIVATE)

        private fun key(id: Int) = "number_$id"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, DialerKeypadWidget::class.java)).forEach { update(context, manager, it) }
        }

        private fun pending(
            context: Context,
            id: Int,
            action: String,
            index: Int,
            key: String? = null,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                id * 100 + index,
                Intent(context, DialerKeypadWidget::class.java)
                    .setAction(action)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .apply { if (key != null) putExtra(EXTRA_KEY, key) },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        fun update(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
        ) {
            val theme = DialerWidgetTheme.resolve(context)
            val primary = if (theme.dark) 0xFFE8EAED.toInt() else 0xFF202124.toInt()
            val number = prefs(context).getString(key(id), "").orEmpty()
            val views =
                RemoteViews(context.packageName, R.layout.widget_keypad).apply {
                    setInt(R.id.widget_keypad_root, "setBackgroundResource", theme.backgroundRes)
                    setInt(R.id.widget_keypad_card, "setBackgroundResource", theme.cardRes)
                    setInt(R.id.widget_keypad_accent_bar, "setBackgroundColor", theme.accent)
                    setTextViewText(R.id.widget_keypad_number, number)
                    setTextColor(R.id.widget_keypad_number, primary)
                    KEYS.forEachIndexed { i, (k, view) ->
                        setInt(view, "setBackgroundResource", theme.chipRes)
                        setTextColor(view, primary)
                        setOnClickPendingIntent(view, pending(context, id, ACTION_KEY, i, k))
                    }
                    setInt(R.id.widget_keypad_delete, "setBackgroundResource", theme.chipRes)
                    setTextColor(R.id.widget_keypad_delete, primary)
                    setOnClickPendingIntent(R.id.widget_keypad_delete, pending(context, id, ACTION_DELETE, 20))
                    setInt(R.id.widget_keypad_call, "setBackgroundResource", theme.chipRes)
                    setTextColor(R.id.widget_keypad_call, theme.accent)
                    setOnClickPendingIntent(R.id.widget_keypad_call, pending(context, id, ACTION_CALL, 21))
                }
            manager.updateAppWidget(id, views)
        }
    }
}
