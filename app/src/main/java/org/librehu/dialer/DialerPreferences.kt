package org.librehu.dialer

import android.content.Context

internal enum class ThemeMode(
    val key: String,
    val label: Int,
) {
    AUTO("auto", R.string.theme_auto),
    LIGHT("light", R.string.theme_light),
    DARK("dark", R.string.theme_dark),
    ;

    companion object {
        fun fromKey(value: String?): ThemeMode = entries.firstOrNull { it.key == value } ?: AUTO
    }
}

internal enum class WidgetStyle(
    val key: String,
    val label: Int,
) {
    AUTO("auto", R.string.widget_style_large),
    COMPACT("compact", R.string.widget_style_compact),
    ;

    companion object {
        fun fromKey(value: String?): WidgetStyle = entries.firstOrNull { it.key == value } ?: AUTO
    }
}

internal object DialerPreferences {
    private const val PREFS = "librehu_dialer_preferences"
    private const val APP_THEME = "app_theme"
    private const val WIDGET_THEME = "widget_theme"
    private const val WIDGET_STYLE = "widget_style"
    private const val WIDGET_SHOW_RECENTS = "widget_show_recents"
    private const val CALL_BUBBLE = "call_bubble"
    private const val OPEN_ON_CALL = "open_on_call"
    private const val INCOMING_OVERLAY = "incoming_overlay"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun appTheme(context: Context): ThemeMode = ThemeMode.fromKey(prefs(context).getString(APP_THEME, ThemeMode.AUTO.key))

    fun widgetTheme(context: Context): ThemeMode = ThemeMode.fromKey(prefs(context).getString(WIDGET_THEME, ThemeMode.AUTO.key))

    fun widgetStyle(context: Context): WidgetStyle = WidgetStyle.fromKey(prefs(context).getString(WIDGET_STYLE, WidgetStyle.AUTO.key))

    fun widgetShowRecents(context: Context): Boolean = prefs(context).getBoolean(WIDGET_SHOW_RECENTS, true)

    /** Floating call controls over other apps while a call goes on (needs the overlay permission). */
    fun callBubble(context: Context): Boolean = prefs(context).getBoolean(CALL_BUBBLE, true)

    /** Bring the call screen up on incoming / outgoing calls (backends that are not Telecom). */
    fun openOnCall(context: Context): Boolean = prefs(context).getBoolean(OPEN_ON_CALL, true)

    /** Incoming call as a card over the current app (Android Auto style) instead of the full call screen. */
    fun incomingOverlay(context: Context): Boolean = prefs(context).getBoolean(INCOMING_OVERLAY, true)

    fun setIncomingOverlay(
        context: Context,
        value: Boolean,
    ) = prefs(context).edit().putBoolean(INCOMING_OVERLAY, value).apply()

    fun setAppTheme(
        context: Context,
        value: ThemeMode,
    ) = prefs(context).edit().putString(APP_THEME, value.key).apply()

    fun setWidgetTheme(
        context: Context,
        value: ThemeMode,
    ) = prefs(context).edit().putString(WIDGET_THEME, value.key).apply()

    fun setWidgetStyle(
        context: Context,
        value: WidgetStyle,
    ) = prefs(context).edit().putString(WIDGET_STYLE, value.key).apply()

    fun setWidgetShowRecents(
        context: Context,
        value: Boolean,
    ) = prefs(context).edit().putBoolean(WIDGET_SHOW_RECENTS, value).apply()

    fun setCallBubble(
        context: Context,
        value: Boolean,
    ) = prefs(context).edit().putBoolean(CALL_BUBBLE, value).apply()

    fun setOpenOnCall(
        context: Context,
        value: Boolean,
    ) = prefs(context).edit().putBoolean(OPEN_ON_CALL, value).apply()
}
