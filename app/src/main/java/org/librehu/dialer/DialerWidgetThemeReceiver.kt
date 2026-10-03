package org.librehu.dialer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

internal class DialerWidgetThemeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "org.librehu.action.THEME_CHANGED") {
            DialerWidgetTheme.refreshAll(context)
        }
    }
}
