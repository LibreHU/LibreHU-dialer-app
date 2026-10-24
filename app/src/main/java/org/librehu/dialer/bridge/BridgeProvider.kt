package org.librehu.dialer.bridge

import android.content.Context
import org.librehu.dialer.DialerPreferences
import org.librehu.dialer.DialerWidgetTheme
import org.librehu.dialer.R
import org.librehu.dialer.ThemeMode
import org.librehu.dialer.WidgetStyle

/**
 * The phone app's settings for LibreHU's phone app (through LibreHU-service's bridge, [BridgeKitProvider]): calls
 * (open on a call, incoming call overlay, call bubble), appearance and widgets. Same labels and same preferences as
 * the app's screen.
 */
class BridgeProvider : BridgeKitProvider() {
    override fun pages(context: Context): List<KitPage> =
        listOf(
            page(context, "settings", context.getString(R.string.tab_settings), "phone") {
                section(s(R.string.settings_phone))
                switch("open_on_call", s(R.string.open_on_call), DialerPreferences.openOnCall(context), s(R.string.open_on_call_hint)) { v ->
                    DialerPreferences.setOpenOnCall(context, v)
                }
                switch("incoming_overlay", s(R.string.incoming_overlay), DialerPreferences.incomingOverlay(context), s(R.string.incoming_overlay_hint)) { v ->
                    DialerPreferences.setIncomingOverlay(context, v)
                }
                switch("call_bubble", s(R.string.call_bubble), DialerPreferences.callBubble(context), s(R.string.call_bubble_hint)) { v ->
                    DialerPreferences.setCallBubble(context, v)
                }
                section(s(R.string.settings_appearance))
                pick("app_theme", s(R.string.settings_appearance), DialerPreferences.appTheme(context), s(R.string.settings_theme_hint), label = { s(it.label) }) { v ->
                    DialerPreferences.setAppTheme(context, v)
                }
                section(s(R.string.settings_widgets))
                pick("widget_theme", s(R.string.widget_theme), DialerPreferences.widgetTheme(context), label = { s(it.label) }) { v ->
                    DialerPreferences.setWidgetTheme(context, v)
                    DialerWidgetTheme.refreshAll(context)
                }
                pick("widget_style", s(R.string.widget_style), DialerPreferences.widgetStyle(context), label = { s(it.label) }) { v ->
                    DialerPreferences.setWidgetStyle(context, v)
                    DialerWidgetTheme.refreshAll(context)
                }
                switch("widget_details", s(R.string.widget_details), DialerPreferences.widgetShowRecents(context), s(R.string.widget_details_hint)) { v ->
                    DialerPreferences.setWidgetShowRecents(context, v)
                    DialerWidgetTheme.refreshAll(context)
                }
            },
        )
}
