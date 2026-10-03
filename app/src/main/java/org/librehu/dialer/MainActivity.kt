package org.librehu.dialer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class DialerPalette(
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val accent: Color,
    val onAccent: Color,
    val text: Color,
    val textDim: Color,
) {
    companion object {
        fun fromLauncher(dark: Boolean, accentArgb: Int): DialerPalette {
            val accent = if (accentArgb != 0) Color(accentArgb) else Color(if (dark) 0xFF8AB4F8 else 0xFF1A73E8)
            return if (dark) {
                DialerPalette(true, Color(0xFF000000), Color(0xFF1E1F22), Color(0xFF2B2D31),
                    accent, Color(0xFF202124), Color(0xFFE8EAED), Color(0xFF9AA0A6))
            } else {
                DialerPalette(false, Color(0xFFF1F3F4), Color(0xFFFFFFFF), Color(0xFFE8EAED),
                    accent, Color.White, Color(0xFF202124), Color(0xFF5F6368))
            }
        }
    }
}

/** Same palette contract as LibreHU Launcher: effective night mode + launcher-selected accent. */
private object DialerColors {
    var palette by mutableStateOf(DialerPalette.fromLauncher(true, 0))
    val Bg get() = palette.background
    val Card get() = palette.surface
    val Raised get() = palette.surfaceHigh
    val Accent get() = palette.accent
    val OnAccent get() = palette.onAccent
    val Text get() = palette.text
    val Muted get() = palette.textDim
}

private enum class Tab(val title: String) { Favorites("Favorites"), Recents("Recents"), Contacts("Contacts"), Keypad("Keypad"), Settings("Settings") }
private enum class CallState { None, Calling, Connected }
internal data class Person(val name: String, val number: String, val initials: String, val detail: String)

class MainActivity : ComponentActivity() {
    private lateinit var themeFollower: ThemeFollower

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeFollower = ThemeFollower(this) { dark, accent ->
            DialerColors.palette = DialerPalette.fromLauncher(dark, accent)
            val bg = DialerColors.Bg
            window.statusBarColor = android.graphics.Color.rgb(
                (bg.red * 255).toInt(), (bg.green * 255).toInt(), (bg.blue * 255).toInt())
            window.navigationBarColor = window.statusBarColor
            window.decorView.systemUiVisibility = if (dark) 0 else
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        setContent { DialerApp(intent.getStringExtra(EXTRA_OPEN_TAB)) }
    }

    override fun onStart() {
        super.onStart()
        themeFollower.start()
    }

    override fun onStop() {
        themeFollower.stop()
        super.onStop()
    }
}

@Composable
private fun DialerApp(initialTab: String? = null) {
    var tab by remember { mutableStateOf(Tab.Favorites) }
    var query by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Person?>(null) }
    var call by remember { mutableStateOf(CallState.None) }
    val context = LocalContext.current
    var dataRefresh by remember { mutableIntStateOf(0) }
    var deviceContacts by remember { mutableStateOf<List<Person>>(emptyList()) }
    var deviceRecents by remember { mutableStateOf<List<Person>>(emptyList()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { dataRefresh++ }
    LaunchedEffect(initialTab) {
        if (initialTab != null) {
            val needed = when (tab) {
                Tab.Contacts -> arrayOf(Manifest.permission.READ_CONTACTS)
                Tab.Recents, Tab.Favorites -> arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG)
                Tab.Keypad, Tab.Settings -> emptyArray()
            }
            val missing = needed.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
        }
    }
    LaunchedEffect(tab, dataRefresh) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            deviceContacts = withContext(Dispatchers.IO) { readDeviceContacts(context) }
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            deviceRecents = withContext(Dispatchers.IO) { readDeviceCallLog(context) }
        }
    }
    val p = DialerColors.palette
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    val scheme = if (p.dark) {
        darkColorScheme(background = p.background, surface = p.surface, surfaceVariant = p.surfaceHigh,
            primary = p.accent, onPrimary = p.onAccent, onBackground = p.text, onSurface = p.text,
            onSurfaceVariant = p.textDim, secondary = p.accent)
    } else {
        lightColorScheme(background = p.background, surface = p.surface, surfaceVariant = p.surfaceHigh,
            primary = p.accent, onPrimary = p.onAccent, onBackground = p.text, onSurface = p.text,
            onSurfaceVariant = p.textDim, secondary = p.accent)
    }

    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = DialerColors.Bg, contentColor = DialerColors.Text) {
            Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.width(86.dp).fillMaxHeight().clip(RoundedCornerShape(26.dp)).background(DialerColors.Card).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(DialerColors.Accent), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Call, null, tint = DialerColors.OnAccent)
                    }
                    Spacer(Modifier.height(12.dp))
                    Tab.values().forEach { item ->
                        val active = item == tab
                        Column(Modifier.fillMaxWidth().padding(horizontal = 7.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (active) DialerColors.Raised else Color.Transparent)
                            .clickable { tab = item }.padding(vertical = 13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(when (item) {
                                Tab.Favorites -> Icons.Default.Star
                                Tab.Recents -> Icons.Default.History
                                Tab.Contacts -> Icons.Default.Contacts
                                Tab.Keypad -> Icons.Default.Dialpad
                                Tab.Settings -> Icons.Default.Settings
                            }, item.title, tint = if (active) DialerColors.Accent else DialerColors.Muted, modifier = Modifier.size(23.dp))
                            Spacer(Modifier.height(5.dp))
                            Text(item.title, color = if (active) DialerColors.Text else DialerColors.Muted, fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.BluetoothDisabled, "Bluetooth disconnected", tint = DialerColors.Muted)
                    Text("PHONE", color = DialerColors.Muted, fontSize = 9.sp, letterSpacing = 1.2.sp)
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(DialerColors.Card).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Phone", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                            Text("Keep your focus on the road", color = DialerColors.Muted, fontSize = 12.sp)
                        }
                        Row(Modifier.clip(CircleShape).background(DialerColors.Raised).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(DialerColors.Muted))
                            Spacer(Modifier.width(8.dp)); Text("Phone not connected", color = DialerColors.Muted, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(20.dp)); Text(time, fontSize = 22.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    if (call != CallState.None) {
                        Column(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(DialerColors.Card).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(6.dp)); Avatar(selected ?: Person("New number", number, "?", ""), 78)
                            Spacer(Modifier.height(12.dp)); Text(selected?.name ?: "New number", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                            Text(selected?.number ?: number, color = DialerColors.Muted)
                            Spacer(Modifier.height(8.dp)); Text(if (call == CallState.Calling) "Calling… (preview)" else "Connected (preview)", color = DialerColors.Accent)
                            Spacer(Modifier.weight(1f))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                CallAction(Icons.Default.MicOff, "Mute", {})
                                CallAction(Icons.Default.Dialpad, "Keypad", { tab = Tab.Keypad; call = CallState.None })
                                CallAction(Icons.Default.VolumeUp, "Audio", {})
                                CallAction(Icons.Default.CallEnd, "End", { call = CallState.None }, isDestructive = true)
                            }
                        }
                    } else when (tab) {
                        Tab.Favorites -> {
                            Heading("Favorites", "Your people, one tap away")
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                deviceContacts.take(4).forEach { person ->
                                    Column(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(DialerColors.Card)
                                        .clickable { selected = person; number = person.number }.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Avatar(person, 58); Spacer(Modifier.height(10.dp))
                                        Text(person.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("Mobile", fontSize = 11.sp, color = DialerColors.Muted)
                                        Spacer(Modifier.height(8.dp))
                                        FilledTonalButton(onClick = { selected = person; number = person.number; call = CallState.Calling },
                                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = DialerColors.Raised, contentColor = DialerColors.Accent)) {
                                            Icon(Icons.Default.Call, null); Spacer(Modifier.width(4.dp)); Text("Call")
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp)); Heading("Recent calls", "Your latest conversations")
                            LazyColumn(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(DialerColors.Card), contentPadding = PaddingValues(8.dp)) {
                                items(deviceRecents.take(5)) { person ->
                                    PersonRow(person, { selected = person; number = person.number }, { selected = person; number = person.number; call = CallState.Calling })
                                }
                            }
                        }
                        Tab.Recents -> ContactList("Recent calls", "Incoming, outgoing and missed", deviceRecents,
                            { selected = it; number = it.number }, { selected = it; number = it.number; call = CallState.Calling })
                        Tab.Contacts -> {
                            Heading("Contacts", "Find someone to call")
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DialerColors.Card).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Search, null, tint = DialerColors.Muted); Spacer(Modifier.width(10.dp))
                                BasicTextField(value = query, onValueChange = { query = it }, singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(color = DialerColors.Text, fontSize = 16.sp),
                                    modifier = Modifier.fillMaxWidth(), decorationBox = { inner ->
                                        if (query.isEmpty()) Text("Search name or number", color = DialerColors.Muted)
                                        inner()
                                    })
                            }
                            Spacer(Modifier.height(10.dp))
                            ContactList("All contacts", "Contacts from this device", deviceContacts.filter { it.name.contains(query, true) || it.number.contains(query) },
                                { selected = it; number = it.number }, { selected = it; number = it.number; call = CallState.Calling })
                        }
                        Tab.Keypad -> {
                            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(0.9f)) {
                                    Heading("Keypad", "Enter a phone number")
                                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DialerColors.Card).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            number.ifBlank { "Enter number" },
                                            color = if (number.isBlank()) DialerColors.Muted else DialerColors.Text,
                                            fontSize = 19.sp,
                                            modifier = Modifier.weight(1f).pointerInput(Unit) {
                                                detectTapGestures(onLongPress = { number = "" })
                                            },
                                            maxLines = 1
                                        )
                                        IconButton(onClick = { if (number.isNotEmpty()) number = number.dropLast(1) }) { Icon(Icons.AutoMirrored.Filled.Backspace, "Delete", tint = DialerColors.Muted) }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Button(onClick = { selected = Person("New number", number, number.take(2).ifBlank { "?" }, ""); call = CallState.Calling },
                                        enabled = number.isNotBlank(), modifier = Modifier.fillMaxWidth().height(54.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = DialerColors.Accent, contentColor = DialerColors.OnAccent)) {
                                        Icon(Icons.Default.Call, null); Spacer(Modifier.width(8.dp)); Text("Call", fontSize = 17.sp)
                                    }
                                    TextButton(onClick = { number = "" }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Clear", color = DialerColors.Muted) }
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(listOf("1" to "", "2" to "ABC", "3" to "DEF"), listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
                                        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"), listOf("*" to "", "0" to "+", "#" to "")).forEach { row ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            row.forEach { (digit, letters) ->
                                                Column(Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(16.dp)).background(DialerColors.Raised)
                                                    .clickable { number += digit }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                                    Text(digit, fontSize = 22.sp)
                                                    if (letters.isNotEmpty()) Text(letters, fontSize = 9.sp, color = DialerColors.Muted)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Tab.Settings -> SettingsScreen(context)
                    }
                    if (call == CallState.None) {
                        Spacer(Modifier.height(5.dp)); Text("PREVIEW MODE · DEVICE CONTACTS · NO REAL CALLS", color = DialerColors.Muted, fontSize = 10.sp, letterSpacing = 1.1.sp)
                    }
                }
            }
        }
    }
}

@Composable private fun Heading(title: String, subtitle: String) {
    Column(Modifier.padding(start = 4.dp, bottom = 10.dp, top = 2.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, fontSize = 12.sp, color = DialerColors.Muted)
    }
}

@Composable private fun Avatar(person: Person, size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(DialerColors.Raised), contentAlignment = Alignment.Center) {
        Text(person.initials, color = DialerColors.Accent, fontSize = (size / 3.4).sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable private fun PersonRow(person: Person, onSelect: () -> Unit, onCall: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onSelect() }.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Avatar(person, 44)
        Column(Modifier.weight(1f)) {
            Text(person.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(person.detail, color = DialerColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onCall) { Icon(Icons.Default.Call, "Call", tint = DialerColors.Accent) }
    }
}

@Composable private fun ContactList(title: String, subtitle: String, people: List<Person>, onSelect: (Person) -> Unit, onCall: (Person) -> Unit) {
    Heading(title, subtitle)
    LazyColumn(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(DialerColors.Card), contentPadding = PaddingValues(8.dp)) {
        items(people) { person -> PersonRow(person, { onSelect(person) }, { onCall(person) }) }
    }
}

@Composable private fun CallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, isDestructive: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        val container = if (isDestructive) Color(0xFFB3261E) else DialerColors.Raised
        FilledIconButton(onClick = onClick, modifier = Modifier.size(58.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = container, contentColor = if (isDestructive) Color.White else DialerColors.Text)) {
            Icon(icon, label, modifier = Modifier.size(24.dp))
        }
        Text(label, color = DialerColors.Muted, fontSize = 11.sp)
    }
}
