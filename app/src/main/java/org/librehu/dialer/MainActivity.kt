package org.librehu.dialer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.ContactsContract
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.librehu.dialer.backend.jancar.JancarBluetoothClient

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

private enum class Tab(val title: String) { Favorites("Favorites"), Recents("Recents"), Contacts("Contacts"), Keypad("Keypad") }
private enum class CallState { None, Calling, Connected }
private data class Person(val name: String, val number: String, val initials: String, val detail: String)

class MainActivity : ComponentActivity() {
    private lateinit var themeFollower: ThemeFollower
    private lateinit var btClient: JancarBluetoothClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        btClient = JancarBluetoothClient(this)
        themeFollower = ThemeFollower(this) { dark, accent ->
            DialerColors.palette = DialerPalette.fromLauncher(dark, accent)
            val bg = DialerColors.Bg
            window.statusBarColor = android.graphics.Color.rgb(
                (bg.red * 255).toInt(), (bg.green * 255).toInt(), (bg.blue * 255).toInt())
            window.navigationBarColor = window.statusBarColor
            window.decorView.systemUiVisibility = if (dark) 0 else
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        setContent { DialerApp(btClient) }
    }

    override fun onStart() {
        super.onStart()
        themeFollower.start()
        btClient.bind()
    }

    override fun onStop() {
        btClient.unbind()
        themeFollower.stop()
        super.onStop()
    }
}

@Composable
private fun DialerApp(btClient: JancarBluetoothClient) {
    var tab by remember { mutableStateOf(Tab.Favorites) }
    var query by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Person?>(null) }
    var call by remember { mutableStateOf(CallState.None) }
    var muted by remember { mutableStateOf(false) }
    var overlayVisible by remember { mutableStateOf(false) }
    var showDtmf by remember { mutableStateOf(false) }
    val btState by btClient.state.collectAsState()
    val context = LocalContext.current
    var dataRefresh by remember { mutableIntStateOf(0) }
    var deviceContacts by remember { mutableStateOf<List<Person>>(emptyList()) }
    var deviceRecents by remember { mutableStateOf<List<Person>>(emptyList()) }
    var dataMessage by remember { mutableStateOf("Allow Android permissions to read device data") }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        dataMessage = if (results.values.all { it }) "Device data access enabled" else "Permission denied. Enable access in Android app settings."
        dataRefresh++
    }
    val openTab: (Tab) -> Unit = { item ->
        tab = item
        val needed = when (item) {
            Tab.Contacts -> arrayOf(Manifest.permission.READ_CONTACTS)
            Tab.Recents, Tab.Favorites -> arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG)
            else -> emptyArray()
        }
        if (needed.isNotEmpty()) {
            val missing = needed.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray()) else dataRefresh++
        }
    }
    LaunchedEffect(tab, dataRefresh) {
        when (tab) {
            Tab.Contacts, Tab.Favorites -> {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                    deviceContacts = withContext(Dispatchers.IO) { readDeviceContacts(context) }
                    dataMessage = if (deviceContacts.isEmpty()) "No contacts found on this device" else deviceContacts.size.toString() + " phone entries"
                } else {
                    deviceContacts = emptyList()
                    dataMessage = "Contacts permission required"
                }
                if (tab == Tab.Favorites && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
                    deviceRecents = withContext(Dispatchers.IO) { readDeviceCallLog(context) }
                }
            }
            Tab.Recents -> {
                val contactsAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                val logsAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
                if (contactsAllowed && logsAllowed) {
                    deviceRecents = withContext(Dispatchers.IO) { readDeviceCallLog(context) }
                    dataMessage = if (deviceRecents.isEmpty()) "No calls found in the device call log" else deviceRecents.size.toString() + " recent calls"
                } else {
                    deviceRecents = emptyList()
                    dataMessage = "Contacts and call-log permissions required"
                }
            }
            else -> Unit
        }
    }
    val requestCall: (Person) -> Unit = { person ->
        selected = person
        number = person.number
        if (btClient.callPhone(person.number)) {
            call = CallState.Calling
            overlayVisible = true
            showDtmf = false
            muted = false
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
            Box(Modifier.fillMaxSize()) {
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
                            .clickable { openTab(item) }.padding(vertical = 13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(when (item) {
                                Tab.Favorites -> Icons.Default.Star
                                Tab.Recents -> Icons.Default.History
                                Tab.Contacts -> Icons.Default.Contacts
                                Tab.Keypad -> Icons.Default.Dialpad
                            }, item.title, tint = if (active) DialerColors.Accent else DialerColors.Muted, modifier = Modifier.size(23.dp))
                            Spacer(Modifier.height(5.dp))
                            Text(item.title, color = if (active) DialerColors.Text else DialerColors.Muted, fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(if (btState.serviceAvailable && btState.bluetoothPowered == true) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled, "Jancar Bluetooth state", tint = DialerColors.Muted)
                    Text("PHONE", color = DialerColors.Muted, fontSize = 9.sp, letterSpacing = 1.2.sp)
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(DialerColors.Card).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Phone", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                            Text(btState.connectionEvent ?: btState.serviceMessage, color = DialerColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row(Modifier.clip(CircleShape).background(DialerColors.Raised).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(if (btState.serviceAvailable) DialerColors.Accent else DialerColors.Muted))
                            Spacer(Modifier.width(8.dp)); Text(
                                when {
                                    !btState.serviceAvailable -> "Jancar unavailable"
                                    btState.bluetoothPowered == false -> "Bluetooth off"
                                    !btState.currentPhoneName.isNullOrBlank() -> btState.currentPhoneName!!
                                    btState.bluetoothPowered == true -> "Bluetooth powered"
                                    else -> "Connecting…"
                                }, color = DialerColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(20.dp)); Text(time, fontSize = 22.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    when (tab) {
                        Tab.Favorites -> {
                            Heading("Quick contacts", if (deviceContacts.isEmpty()) dataMessage else "Contacts from this device")
                            if (deviceContacts.isEmpty()) {
                                Text("Open Contacts and grant permission to see people here.", color = DialerColors.Muted,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DialerColors.Card).padding(18.dp))
                            } else {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    deviceContacts.take(4).forEach { person ->
                                        Column(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(DialerColors.Card)
                                            .clickable { selected = person; number = person.number }.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Avatar(person, 58); Spacer(Modifier.height(10.dp))
                                            Text(person.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(person.detail, fontSize = 11.sp, color = DialerColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Spacer(Modifier.height(8.dp))
                                            FilledTonalButton(onClick = { requestCall(person) },
                                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = DialerColors.Raised, contentColor = DialerColors.Accent)) {
                                                Icon(Icons.Default.Call, null); Spacer(Modifier.width(4.dp)); Text("Call")
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp)); Heading("Recent calls", if (deviceRecents.isEmpty()) "No call history available" else "Latest calls from this device")
                            LazyColumn(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(DialerColors.Card), contentPadding = PaddingValues(8.dp)) {
                                if (deviceRecents.isEmpty()) {
                                    item { Text("Call history appears here after granting permission.", color = DialerColors.Muted, modifier = Modifier.padding(18.dp)) }
                                }
                                items(deviceRecents.take(5)) { person ->
                                    PersonRow(person, { selected = person; number = person.number }, { requestCall(person) })
                                }
                            }
                        }
                        Tab.Recents -> ContactList("Recent calls", dataMessage, deviceRecents,
                            { selected = it; number = it.number }, { requestCall(it) })
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
                            ContactList("All contacts", dataMessage, deviceContacts.filter { it.name.contains(query, true) || it.number.contains(query) },
                                { selected = it; number = it.number }, { requestCall(it) })
                        }
                        Tab.Keypad -> {
                            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(0.9f)) {
                                    Heading("Keypad", "Enter a phone number")
                                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DialerColors.Card).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(number.ifBlank { "Enter number" }, color = if (number.isBlank()) DialerColors.Muted else DialerColors.Text, fontSize = 19.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                        IconButton(onClick = { if (number.isNotEmpty()) number = number.dropLast(1) }) { Icon(Icons.Default.Backspace, "Delete", tint = DialerColors.Muted) }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Button(onClick = { requestCall(Person("New number", number, number.take(2).ifBlank { "?" }, "")) },
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
                    }
                    if (!btState.lastError.isNullOrBlank()) {
                        Text(btState.lastError!!, color = Color(0xFFB3261E), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (!btState.lastCommandResult.isNullOrBlank() && call == CallState.None) {
                        Text(btState.lastCommandResult!!, color = DialerColors.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (call == CallState.None) {
                        Spacer(Modifier.height(5.dp)); Text("JANCAR BT SERVICE · DEMO CONTACTS", color = DialerColors.Muted, fontSize = 10.sp, letterSpacing = 1.1.sp)
                    }
                }
            }
            if (call != CallState.None && !overlayVisible) {
                FilledTonalButton(
                    onClick = { overlayVisible = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(22.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = DialerColors.Accent, contentColor = DialerColors.OnAccent)
                ) {
                    Icon(Icons.Default.Call, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Return to call")
                }
            }
            if (call != CallState.None && overlayVisible) {
                Dialog(
                    onDismissRequest = {},
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false,
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false
                    )
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(0.72f).wrapContentHeight(),
                        shape = RoundedCornerShape(28.dp),
                        color = DialerColors.Card,
                        contentColor = DialerColors.Text,
                        tonalElevation = 12.dp
                    ) {
                        Column(Modifier.padding(24.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(selected ?: Person("New number", number, "?", ""), 60)
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(selected?.name ?: "New number", fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                                    Text(selected?.number ?: number, color = DialerColors.Muted, fontSize = 14.sp)
                                    Text(
                                        btState.callEvent ?: "Outgoing call requested",
                                        color = DialerColors.Accent,
                                        fontSize = 13.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!btState.callDetails.isNullOrBlank()) {
                                        Text(btState.callDetails!!, color = DialerColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                IconButton(onClick = { overlayVisible = false }, modifier = Modifier.size(42.dp)) {
                                    Icon(Icons.Default.KeyboardArrowDown, "Minimize call overlay")
                                }
                            }
                            Spacer(Modifier.height(22.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                CallAction(
                                    if (muted) Icons.Default.Mic else Icons.Default.MicOff,
                                    if (muted) "Unmute" else "Mute",
                                    { val next = !muted; if (btClient.muteMic(next)) muted = next }
                                )
                                CallAction(Icons.Default.Dialpad, if (showDtmf) "Hide keypad" else "Keypad", { showDtmf = !showDtmf })
                                CallAction(Icons.Default.CallEnd, "End", {
                                    if (btClient.hangPhone()) {
                                        call = CallState.None
                                        overlayVisible = false
                                        showDtmf = false
                                        muted = false
                                    }
                                }, isDestructive = true)
                            }
                            if (showDtmf) {
                                Spacer(Modifier.height(14.dp))
                                listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("0")).forEach { digitRow ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        digitRow.forEach { digit ->
                                            FilledTonalButton(
                                                onClick = { digit.toIntOrNull()?.let { btClient.sendDtmf(it) } },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = DialerColors.Raised, contentColor = DialerColors.Text)
                                            ) { Text(digit, fontSize = 18.sp) }
                                        }
                                    }
                                    Spacer(Modifier.height(5.dp))
                                }
                            }
                            if (!btState.lastError.isNullOrBlank()) {
                                Spacer(Modifier.height(12.dp))
                                Text(btState.lastError!!, color = Color(0xFFB3261E), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            }
        }
    }
}


private fun initialsFor(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").ifBlank { "?" }

private fun readDeviceContacts(context: Context): List<Person> {
    val result = mutableListOf<Person>()
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.TYPE
    )
    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        projection, null, null,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
    )?.use { cursor ->
        val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val typeIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
        while (cursor.moveToNext()) {
            val name = cursor.getString(nameIndex)?.trim().orEmpty().ifBlank { "Unknown contact" }
            val number = cursor.getString(numberIndex)?.trim().orEmpty()
            if (number.isNotBlank()) {
                val type = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                    context.resources, cursor.getInt(typeIndex), null
                ).toString()
                result += Person(name, number, initialsFor(name), type)
            }
        }
    }
    return result
}

private fun readDeviceCallLog(context: Context): List<Person> {
    val contactNames = mutableMapOf<String, String>()
    runCatching {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex)?.trim().orEmpty()
                val number = cursor.getString(numberIndex)?.filter { it.isDigit() || it == '+' }.orEmpty()
                if (name.isNotBlank() && number.isNotBlank()) contactNames[number] = name
            }
        }
    }
    val result = mutableListOf<Person>()
    val projection = arrayOf(
        CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE,
        CallLog.Calls.DATE, CallLog.Calls.DURATION
    )
    context.contentResolver.query(
        CallLog.Calls.CONTENT_URI, projection, null, null, CallLog.Calls.DATE + " DESC"
    )?.use { cursor ->
        val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
        val nameIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
        val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
        val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
        val durationIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
        val dateFormat = SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault())
        while (cursor.moveToNext()) {
            val number = cursor.getString(numberIndex)?.trim().orEmpty().ifBlank { "Unknown number" }
            val cachedName = cursor.getString(nameIndex)?.trim().orEmpty()
            val normalized = number.filter { it.isDigit() || it == '+' }
            val name = cachedName.ifBlank { contactNames[normalized] ?: number }
            val type = when (cursor.getInt(typeIndex)) {
                CallLog.Calls.INCOMING_TYPE -> "Incoming"
                CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                CallLog.Calls.MISSED_TYPE -> "Missed"
                CallLog.Calls.REJECTED_TYPE -> "Rejected"
                CallLog.Calls.BLOCKED_TYPE -> "Blocked"
                CallLog.Calls.VOICEMAIL_TYPE -> "Voicemail"
                else -> "Call"
            }
            val date = dateFormat.format(Date(cursor.getLong(dateIndex)))
            val seconds = cursor.getLong(durationIndex)
            val duration = if (seconds >= 3600) {
                String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
            } else {
                String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
            }
            result += Person(name, number, initialsFor(name), "$type · $date · $duration")
        }
    }
    return result
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
        if (people.isEmpty()) {
            item {
                Text("No entries to display", color = DialerColors.Muted, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().padding(24.dp))
            }
        }
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
