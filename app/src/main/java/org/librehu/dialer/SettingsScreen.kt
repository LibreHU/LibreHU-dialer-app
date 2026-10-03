package org.librehu.dialer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@Composable
internal fun SettingsScreen(context: Context) {
    var appTheme by remember { mutableStateOf(DialerPreferences.appTheme(context)) }
    var widgetTheme by remember { mutableStateOf(DialerPreferences.widgetTheme(context)) }
    var widgetStyle by remember { mutableStateOf(DialerPreferences.widgetStyle(context)) }
    var widgetDetails by remember { mutableStateOf(DialerPreferences.widgetShowRecents(context)) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(DialerColors.Card),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Heading("Settings", "Personalize LibreHU Dialer and its widgets") }

        item {
            SettingsSection("Appearance", "Theme used by the dialer") {
                ThemeMode.entries.forEach { mode ->
                    SettingChoice(mode.label, mode == appTheme) {
                        appTheme = mode
                        DialerPreferences.setAppTheme(context, mode)
                        (context as? MainActivity)?.refreshTheme()
                    }
                }
            }
        }

        item {
            SettingsSection("Widget personalization", "Theme and Android Auto presentation") {
                Text("Widget theme", color = DialerColors.Muted, fontSize = 12.sp)
                ThemeMode.entries.forEach { mode ->
                    SettingChoice(mode.label, mode == widgetTheme) {
                        widgetTheme = mode
                        DialerPreferences.setWidgetTheme(context, mode)
                        DialerWidgetTheme.refreshAll(context)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Widget design", color = DialerColors.Muted, fontSize = 12.sp)
                WidgetStyle.entries.forEach { style ->
                    SettingChoice(style.label, style == widgetStyle) {
                        widgetStyle = style
                        DialerPreferences.setWidgetStyle(context, style)
                        DialerWidgetTheme.refreshAll(context)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DialerColors.Raised)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Show call type and time", fontSize = 13.sp)
                        Text("Adds metadata to recent calls", fontSize = 11.sp, color = DialerColors.Muted)
                    }
                    Switch(
                        checked = widgetDetails,
                        onCheckedChange = {
                            widgetDetails = it
                            DialerPreferences.setWidgetShowRecents(context, it)
                            DialerWidgetTheme.refreshAll(context)
                        }
                    )
                }
                Text(
                    "Auto follows the LibreHU Launcher theme and falls back to Android night mode.",
                    color = DialerColors.Muted, fontSize = 11.sp
                )
                OutlinedButton(
                    onClick = { DialerWidgetTheme.refreshAll(context) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DialerColors.Accent)
                ) {
                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Refresh widgets now")
                }
            }
        }

        item {
            SettingsSection("Permissions", "Data used by contacts and recent calls") {
                PermissionLine("Contacts", ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
                PermissionLine("Call history", ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED)
            }
        }

        item {
            SettingsSection("About", "LibreHU Dialer") {
                Text("Native head-unit phone UI using the stock Jancar Bluetooth Binder service.", color = DialerColors.Muted, fontSize = 12.sp)
                Text("Version 0.1.0 · ivi integration branch", color = DialerColors.Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(DialerColors.Raised).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, fontSize = 11.sp, color = DialerColors.Muted)
        Spacer(Modifier.height(3.dp))
        content()
    }
}

@Composable
private fun SettingChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
            .background(if (selected) DialerColors.Card else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
            null, tint = if (selected) DialerColors.Accent else DialerColors.Muted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(label, color = if (selected) DialerColors.Text else DialerColors.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun PermissionLine(name: String, granted: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (granted) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            null, tint = if (granted) DialerColors.Accent else DialerColors.Muted,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(name, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(if (granted) "Granted" else "Required", color = DialerColors.Muted, fontSize = 11.sp)
    }
}
