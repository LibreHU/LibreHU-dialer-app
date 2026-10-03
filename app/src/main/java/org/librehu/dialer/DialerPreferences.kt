package org.librehu.dialer

import android.content.Context

internal enum class ThemeMode(val key: String, val label: String) {
    AUTO("auto", "Auto"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromKey(value: String?): ThemeMode = entries.firstOrNull { it.key == value } ?: AUTO
    }
}

internal enum class WidgetStyle(val key: String, val label: String) {
    AUTO("auto", "Android Auto"),
    COMPACT("compact", "Compact")
    
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

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun appTheme(context: Context): ThemeMode = ThemeMode.fromKey(prefs(context).getString(APP_THEME, ThemeMode.AUTO.key))
    fun widgetTheme(context: Context): ThemeMode = ThemeMode.fromKey(prefs(context).getString(WIDGET_THEME, ThemeMode.AUTO.key))
    fun widgetStyle(context: Context): WidgetStyle = WidgetStyle.fromKey(prefs(context).getString(WIDGET_STYLE, WidgetStyle.AUTO.key))
    fun widgetShowRecents(context: Context): Boolean = prefs(context).getBoolean(WIDGET_SHOW_RECENTS, true)

    fun setAppTheme(context: Context, value: ThemeMode) =
        prefs(context).edit().putString(APP_THEME, value.key).apply()

    fun setWidgetTheme(context: Context, value: ThemeMode) =
        prefs(context).edit().putString(WIDGET_THEME, value.key).apply()

    fun setWidgetStyle(context: Context, value: WidgetStyle) =
        prefs(context).edit().putString(WIDGET_STYLE, value.key).apply()

    fun setWidgetShowRecents(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(WIDGET_SHOW_RECENTS, value).apply()
}
