package org.librehu.dialer.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.librehu.dialer.DialerPreferences
import org.librehu.dialer.DialerWidgetTheme
import org.librehu.dialer.R
import org.librehu.dialer.ThemeMode
import org.librehu.dialer.WidgetStyle
import org.librehu.dialer.phone.CheckedBackend
import org.librehu.dialer.phone.PhoneBackends
import org.librehu.dialer.phone.PhoneLink

class SettingsActions(
    val requestPermissions: () -> Unit,
    val makeDefaultDialer: () -> Unit,
    val isDefaultDialer: () -> Boolean,
    val openOverlaySettings: () -> Unit,
    val refreshTheme: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    link: PhoneLink,
    actions: SettingsActions,
) {
    val context = LocalContext.current
    var appTheme by remember { mutableStateOf(DialerPreferences.appTheme(context)) }
    var widgetTheme by remember { mutableStateOf(DialerPreferences.widgetTheme(context)) }
    var widgetStyle by remember { mutableStateOf(DialerPreferences.widgetStyle(context)) }
    var widgetDetails by remember { mutableStateOf(DialerPreferences.widgetShowRecents(context)) }
    var bubble by remember { mutableStateOf(DialerPreferences.callBubble(context)) }
    var openOnCall by remember { mutableStateOf(DialerPreferences.openOnCall(context)) }
    // Re-read permissions and default app when coming back from Android's screens.
    var resumed by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumed++
        onPauseOrDispose { }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(DialerColors.Card),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Heading(stringResource(R.string.tab_settings), stringResource(R.string.settings_subtitle)) }

        item {
            // Read so that the section is redrawn on resume.
            resumed.hashCode()
            Section(stringResource(R.string.settings_phone)) {
                StatusLine(
                    if (link.connected) {
                        stringResource(
                            R.string.phone_connected,
                            link.deviceName,
                        )
                    } else {
                        stringResource(R.string.phone_disconnected)
                    },
                    link.connected,
                )
                if (link.message.isNotBlank()) {
                    Text(
                        if (link.message ==
                            CheckedBackend.MISMATCH
                        ) {
                            stringResource(R.string.phone_mismatch, PhoneBackends.NAME)
                        } else {
                            link.message
                        },
                        color = DialerColors.Muted,
                        fontSize = 14.sp,
                    )
                }
                if (PhoneBackends.NEEDS_DEFAULT_DIALER) {
                    val isDefault = actions.isDefaultDialer()
                    StatusLine(stringResource(if (isDefault) R.string.default_dialer_yes else R.string.default_dialer_no), isDefault)
                    if (!isDefault) Pill(stringResource(R.string.default_dialer_set), selected = true, onClick = actions.makeDefaultDialer)
                    Text(stringResource(R.string.default_dialer_hint), color = DialerColors.Muted, fontSize = 13.sp)
                } else {
                    SwitchLine(stringResource(R.string.open_on_call), stringResource(R.string.open_on_call_hint), openOnCall) {
                        openOnCall = it
                        DialerPreferences.setOpenOnCall(context, it)
                    }
                }
                SwitchLine(stringResource(R.string.call_bubble), stringResource(R.string.call_bubble_hint), bubble) {
                    bubble = it
                    DialerPreferences.setCallBubble(context, it)
                    if (it && !Settings.canDrawOverlays(context)) actions.openOverlaySettings()
                }
                if (bubble && !Settings.canDrawOverlays(context)) {
                    Pill(stringResource(R.string.overlay_allow), onClick = actions.openOverlaySettings)
                }
            }
        }

        item {
            Section(stringResource(R.string.settings_permissions)) {
                PermissionLine(stringResource(R.string.permission_contacts), granted(context, Manifest.permission.READ_CONTACTS))
                PermissionLine(stringResource(R.string.permission_calllog), granted(context, Manifest.permission.READ_CALL_LOG))
                PermissionLine(stringResource(R.string.permission_call), granted(context, Manifest.permission.CALL_PHONE))
                Pill(stringResource(R.string.grant), onClick = actions.requestPermissions)
            }
        }

        item {
            Section(stringResource(R.string.settings_appearance)) {
                Text(stringResource(R.string.settings_theme_hint), color = DialerColors.Muted, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        Pill(stringResource(mode.label), selected = mode == appTheme) {
                            appTheme = mode
                            DialerPreferences.setAppTheme(context, mode)
                            actions.refreshTheme()
                        }
                    }
                }
            }
        }

        item {
            Section(stringResource(R.string.settings_widgets)) {
                Text(stringResource(R.string.widget_theme), color = DialerColors.Muted, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        Pill(stringResource(mode.label), selected = mode == widgetTheme) {
                            widgetTheme = mode
                            DialerPreferences.setWidgetTheme(context, mode)
                            DialerWidgetTheme.refreshAll(context)
                        }
                    }
                }
                Text(stringResource(R.string.widget_style), color = DialerColors.Muted, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WidgetStyle.entries.forEach { style ->
                        Pill(stringResource(style.label), selected = style == widgetStyle) {
                            widgetStyle = style
                            DialerPreferences.setWidgetStyle(context, style)
                            DialerWidgetTheme.refreshAll(context)
                        }
                    }
                }
                SwitchLine(stringResource(R.string.widget_details), stringResource(R.string.widget_details_hint), widgetDetails) {
                    widgetDetails = it
                    DialerPreferences.setWidgetShowRecents(context, it)
                    DialerWidgetTheme.refreshAll(context)
                }
            }
        }

        item {
            Section(stringResource(R.string.settings_about)) {
                val version =
                    remember {
                        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
                    }
                Text(stringResource(R.string.about_text, version, PhoneBackends.NAME), color = DialerColors.Muted, fontSize = 13.sp)
            }
        }
    }
}

private fun granted(
    context: Context,
    permission: String,
) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

@Composable
private fun Section(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DialerColors.Raised)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = DialerColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun SwitchLine(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = DialerColors.Text, fontSize = 17.sp)
            Text(subtitle, color = DialerColors.Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = DialerColors.Accent, checkedThumbColor = DialerColors.OnAccent),
        )
    }
}

@Composable
private fun StatusLine(
    text: String,
    ok: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            null,
            tint = if (ok) DialerColors.Accent else DialerColors.Muted,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, color = DialerColors.Text, fontSize = 16.sp)
    }
}

@Composable
private fun PermissionLine(
    name: String,
    granted: Boolean,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { StatusLine(name, granted) }
        Text(
            stringResource(if (granted) R.string.permission_granted else R.string.permission_required),
            color = DialerColors.Muted,
            fontSize = 14.sp,
        )
    }
    Spacer(Modifier.height(2.dp))
}
