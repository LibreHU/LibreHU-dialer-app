package org.librehu.dialer.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.librehu.dialer.R
import org.librehu.dialer.data.Contact
import org.librehu.dialer.data.PhoneBook
import org.librehu.dialer.data.RecentCall
import org.librehu.dialer.data.RecentType
import org.librehu.dialer.data.T9
import org.librehu.dialer.data.initialsFor
import org.librehu.dialer.phone.CallStatus
import org.librehu.dialer.phone.PhoneBackend
import org.librehu.dialer.phone.PhoneCall
import org.librehu.dialer.phone.PhoneLink

/*
 * The phone in LibreHU Launcher's two car styles. Factory (Modularer Infotainment-Baukasten): the screen's title over
 * a line of the accent colour, dark lists, framed keys, soft keys Favourites / Recents / Contacts / Keypad / Setup (the
 * call's actions during a call). Ambient (LHU-Link): a menu of six tiles (Contacts, Call history, Dial a number,
 * Favourites, Smartphone, Settings), the contacts with their letter and framed picture, the call history with its
 * four filters, a dial pad of framed keys, the call screen with the large framed picture, and the bar Back / main
 * action / Options.
 */

/** What both styles need: the state of [DialerApp], already worked out. */
internal class DialerScreens(
    val phone: PhoneBackend,
    val state: DialerState,
    val calls: List<PhoneCall>,
    val link: PhoneLink,
    val contacts: List<Contact>,
    val recents: List<RecentCall>,
    val call: PhoneCall?,
    val showCall: Boolean,
    val minimize: (Boolean) -> Unit,
    val onDial: (String) -> Unit,
    val onGrant: () -> Unit,
    val settings: SettingsActions,
    val canContacts: Boolean,
    val canLog: Boolean,
)

/** A contact's picture, null while loading or without one. */
@Composable
private fun rememberPhoto(uri: Uri?): ImageBitmap? {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, uri) {
        value =
            uri?.let {
                withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(it)?.use { s -> BitmapFactory.decodeStream(s)?.asImageBitmap() } }.getOrNull()
                }
            }
    }
    return image
}

private fun recentIcon(type: RecentType): Pair<ImageVector, Color> =
    when (type) {
        RecentType.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to Color(0xFF2E8BEF)
        RecentType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to Color(0xFF55D62B)
        RecentType.MISSED -> Icons.AutoMirrored.Filled.CallMissed to Color(0xFFE5241B)
        RecentType.REJECTED -> Icons.Default.Block to Color(0xFFE5241B)
        RecentType.OTHER -> Icons.Default.Call to Color.White
    }

@Composable
private fun statusLine(call: PhoneCall): String =
    when (call.status) {
        CallStatus.DIALING -> stringResource(R.string.status_dialing)
        CallStatus.ALERTING -> stringResource(R.string.status_alerting)
        CallStatus.RINGING -> stringResource(R.string.status_ringing)
        CallStatus.WAITING -> stringResource(R.string.status_waiting)
        CallStatus.ACTIVE -> elapsed(call.activeSince).ifBlank { stringResource(R.string.status_active) }
        CallStatus.HELD -> stringResource(R.string.status_held)
        CallStatus.ENDED -> stringResource(R.string.status_ended)
    }

/** Contacts whose number holds the digits typed (3 or more), or whose name is spelt on the keys (T9). */
private fun keypadMatches(
    number: String,
    contacts: List<Contact>,
): List<Pair<Contact, String>> {
    val digits = PhoneBook.normalize(number)
    if (digits.isEmpty()) return emptyList()
    return contacts
        .mapNotNull { c ->
            val byNumber = if (digits.length >= 3) c.numbers.firstOrNull { PhoneBook.normalize(it.number).contains(digits) } else null
            val t9 = T9.score(c.name, digits)
            when {
                t9 > 0 -> Triple(c, c.primary.number, t9 + 10)
                byNumber != null -> Triple(c, byNumber.number, 5)
                else -> null
            }
        }.sortedByDescending { it.third }
        .take(4)
        .map { it.first to it.second }
}

private fun firstLetter(name: String): Char = name.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'

// --- Factory -------------------------------------------------------------------------------------------------------

@Composable
internal fun FactoryDialer(d: DialerScreens) {
    val tab = d.state.tab.value
    val call = d.call
    var picking by remember { mutableStateOf<Contact?>(null) }
    val dial: (Contact) -> Unit = { c -> if (c.numbers.size > 1) picking = c else d.onDial(c.primary.number) }
    val keys =
        if (d.showCall && call != null) {
            factoryCallKeys(d, call)
        } else {
            listOf(
                SoftKey(stringResource(R.string.tab_favorites), Icons.Default.Star, tab == Tab.FAVORITES) { d.state.tab.value = Tab.FAVORITES },
                SoftKey(stringResource(R.string.tab_recents), Icons.Default.History, tab == Tab.RECENTS) { d.state.tab.value = Tab.RECENTS },
                SoftKey(stringResource(R.string.tab_contacts), Icons.Default.Contacts, tab == Tab.CONTACTS) { d.state.tab.value = Tab.CONTACTS },
                SoftKey(stringResource(R.string.tab_keypad), Icons.Default.Dialpad, tab == Tab.KEYPAD) { d.state.tab.value = Tab.KEYPAD },
                null,
                if (call != null) SoftKey(stringResource(R.string.call_title), Icons.Default.Call) { d.minimize(false) } else null,
                SoftKey(stringResource(R.string.car_setup), Icons.Default.Settings, tab == Tab.SETTINGS) { d.state.tab.value = Tab.SETTINGS },
            )
        }
    FactoryScreen(
        title = if (d.showCall) stringResource(R.string.call_title) else stringResource(tab.label),
        back = false,
        keys = keys,
        actions = { FactoryLinkStatus(d.link) },
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
            when {
                d.showCall && call != null -> FactoryCall(d, call)
                tab == Tab.SETTINGS -> SettingsScreen(d.link, d.settings)
                tab == Tab.KEYPAD -> FactoryKeypad(d)
                tab == Tab.RECENTS ->
                    if (!d.canLog) PermissionCard(stringResource(R.string.permission_calllog_text), d.onGrant) else FactoryRecents(d.recents, d.onDial)
                tab == Tab.CONTACTS ->
                    if (!d.canContacts) PermissionCard(stringResource(R.string.permission_contacts_text), d.onGrant) else FactoryContacts(d.contacts, dial)
                else ->
                    if (!d.canContacts) PermissionCard(stringResource(R.string.permission_contacts_text), d.onGrant) else FactoryFavorites(d, dial)
            }
        }
    }
    picking?.let { c ->
        NumberPicker(c, onDismiss = { picking = null }) {
            picking = null
            d.onDial(it)
        }
    }
}

/** The phone connected (its name) or not, on the right of the title. */
@Composable
private fun FactoryLinkStatus(link: PhoneLink) {
    Icon(Icons.Default.Smartphone, null, tint = if (link.connected) Color.White else Color(0xFF8A8C90), modifier = Modifier.size(26.dp))
    Spacer(Modifier.width(8.dp))
    Text(
        when {
            link.connected -> link.deviceName.ifBlank { stringResource(R.string.phone_connected_short) }
            !link.available -> stringResource(R.string.phone_unavailable)
            else -> stringResource(R.string.phone_disconnected)
        },
        color = if (link.connected) Color.White else Color(0xFF8A8C90),
        fontSize = 22.sp,
        maxLines = 1,
    )
}

/** During a call the soft keys are its actions: answer / reject, or mute, keypad, hold, phone, end. */
@Composable
private fun factoryCallKeys(
    d: DialerScreens,
    call: PhoneCall,
): List<SoftKey?> {
    val f = d.phone.features
    if (call.status.incoming) {
        return listOf(
            SoftKey(stringResource(R.string.call_answer), Icons.Default.Call) { d.phone.answer(call) },
            SoftKey(stringResource(R.string.call_reject), Icons.Default.CallEnd) { d.phone.reject(call) },
            null, null, null, null, null,
        )
    }
    val other = d.calls.firstOrNull { it.id != call.id && it.status.live }
    return listOf(
        if (f.mute) SoftKey(stringResource(R.string.call_mute), Icons.Default.MicOff, d.link.micMuted) { d.phone.setMuted(!d.link.micMuted) } else null,
        if (f.hold && other == null) {
            SoftKey(stringResource(R.string.call_hold), Icons.Default.Pause, call.status == CallStatus.HELD) { d.phone.hold(call, call.status != CallStatus.HELD) }
        } else if (f.swap && other != null) {
            SoftKey(stringResource(R.string.call_swap), Icons.Default.SwapCalls) { d.phone.swap() }
        } else {
            null
        },
        if (f.audioRoute) {
            val inCar = d.link.audioInCar != false
            SoftKey(stringResource(R.string.call_on_phone), Icons.Default.PhoneAndroid, !inCar) { d.phone.setAudioInCar(!inCar) }
        } else {
            null
        },
        SoftKey(stringResource(R.string.call_end), Icons.Default.CallEnd) { d.phone.hangup(call) },
        null,
        null,
        SoftKey(stringResource(R.string.call_minimize), Icons.Default.Dialpad) { d.minimize(true) },
    )
}

/** The caller in large letters, the number and the state under it; the DTMF keys on the right while talking. */
@Composable
private fun FactoryCall(
    d: DialerScreens,
    call: PhoneCall,
) {
    val name = callName(call)
    var typed by remember(call.id) { mutableStateOf("") }
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(initialsFor(name), 120)
            Spacer(Modifier.height(18.dp))
            Text(name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 38.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (name != call.number && call.number.isNotBlank()) Text(call.number, color = Color(0xFFD2D3D8), fontSize = 26.sp)
            Spacer(Modifier.height(10.dp))
            Text(statusLine(call), color = DialerColors.Accent, fontSize = 26.sp)
            d.calls.firstOrNull { it.id != call.id && it.status.live }?.let { o ->
                Spacer(Modifier.height(10.dp))
                Text(callName(o) + "  ·  " + statusLine(o), color = Color(0xFFD2D3D8), fontSize = 20.sp)
            }
        }
        if (!call.status.incoming && d.phone.features.dtmf) {
            Column(Modifier.weight(0.9f)) {
                Text(typed.ifBlank { " " }, color = Color.White, fontSize = 30.sp, maxLines = 1, overflow = TextOverflow.StartEllipsis, modifier = Modifier.padding(bottom = 8.dp))
                FactoryKeyGrid(58) { if (d.phone.dtmf(it)) typed += it }
            }
        }
    }
}

/** The twelve keys as framed boxes; a long touch on 0 types "+". */
@Composable
private fun FactoryKeyGrid(
    keyHeight: Int,
    onKey: (Char) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KEYS.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (digit, letters) ->
                    FramedKey(
                        Modifier.weight(1f).height(keyHeight.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF26272B), Color(0xFF0C0C0E)))),
                        onClick = { onKey(digit[0]) },
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(digit, color = Color.White, fontSize = 30.sp)
                            if (letters.isNotEmpty()) Text(" $letters", color = Color(0xFF9A9CA1), fontSize = 14.sp, modifier = Modifier.padding(bottom = 5.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FactoryKeypad(d: DialerScreens) {
    val number = d.state.number.value
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).height(72.dp).border(BorderStroke(1.5.dp, Color.White)).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        number.ifBlank { stringResource(R.string.keypad_hint) },
                        color = if (number.isBlank()) Color(0xFF8A8C90) else Color.White,
                        fontSize = if (number.length > 14) 26.sp else 34.sp,
                        maxLines = 1,
                        overflow = TextOverflow.StartEllipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                FramedKey(onClick = { d.state.number.value = number.dropLast(1) }) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.keypad_delete), tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(8.dp))
                FramedKey(onClick = { if (number.isNotBlank()) d.onDial(number) }) {
                    Icon(Icons.Default.Call, stringResource(R.string.call), tint = if (number.isBlank()) Color(0xFF8A8C90) else Color(0xFF55D62B), modifier = Modifier.size(34.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            keypadMatches(number, d.contacts).forEach { (c, n) ->
                FactoryListRow(c.name, value = n, icon = Icons.Default.Call) { d.onDial(n) }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            FactoryKeyGrid(66) { d.state.number.value = number + it }
        }
    }
}

@Composable
private fun FactoryRecents(
    recents: List<RecentCall>,
    onDial: (String) -> Unit,
) {
    var missed by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            FramedTextKey(stringResource(R.string.recents_all) + if (!missed) " ✓" else "") { missed = false }
            FramedTextKey(stringResource(R.string.recents_missed) + if (missed) " ✓" else "") { missed = true }
        }
        val list = if (missed) recents.filter { it.type == RecentType.MISSED } else recents
        if (list.isEmpty()) Text(stringResource(R.string.recents_empty), color = Color(0xFFD2D3D8), fontSize = 22.sp, modifier = Modifier.padding(16.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(list) { r ->
                val (icon, color) = recentIcon(r.type)
                FactoryListRow(
                    r.title + if (r.count > 1) " (${r.count})" else "",
                    leading = { Icon(icon, null, tint = color, modifier = Modifier.size(30.dp)) },
                    value = DateUtils.getRelativeTimeSpanString(r.date, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                ) { onDial(r.number) }
            }
        }
    }
}

@Composable
private fun FactoryContacts(
    contacts: List<Contact>,
    onPick: (Contact) -> Unit,
) {
    if (contacts.isEmpty()) return Text(stringResource(R.string.contacts_empty), color = Color(0xFFD2D3D8), fontSize = 22.sp, modifier = Modifier.padding(16.dp))
    LazyColumn(Modifier.fillMaxSize()) {
        var letter = ' '
        contacts.forEach { c ->
            val first = firstLetter(c.name)
            if (first != letter) {
                letter = first
                item(key = "h$first${c.id}") { FactoryListTitle(first.toString()) }
            }
            item(key = c.id) {
                FactoryListRow(
                    c.name,
                    leading = { Avatar(c.initials, 44, c.photo) },
                    value = c.primary.number + if (c.numbers.size > 1) "  +${c.numbers.size - 1}" else "",
                ) { onPick(c) }
            }
        }
    }
}

/** Favourites (starred contacts, else the most called) as tiles, the last calls under them. */
@Composable
private fun FactoryFavorites(
    d: DialerScreens,
    onPick: (Contact) -> Unit,
) {
    val starred = d.contacts.filter { it.starred }
    val frequent = if (starred.isEmpty()) PhoneBook.frequent(d.recents, 6) else emptyList()
    Column(Modifier.fillMaxSize()) {
        if (starred.isEmpty() && frequent.isEmpty()) {
            Text(stringResource(R.string.favorites_empty), color = Color(0xFFD2D3D8), fontSize = 22.sp, modifier = Modifier.padding(16.dp))
        } else {
            LazyRow(Modifier.fillMaxWidth().height(210.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(starred, key = { it.id }) { c ->
                    FactoryTile(c.name, modifier = Modifier.width(190.dp), aspect = 1.45f, onClick = { onPick(c) }) {
                        Box(Modifier.align(Alignment.Center)) { Avatar(c.initials, 72, c.photo) }
                    }
                }
                items(frequent, key = { it.number }) { r ->
                    FactoryTile(r.title, modifier = Modifier.width(190.dp), aspect = 1.45f, onClick = { d.onDial(r.number) }) {
                        Box(Modifier.align(Alignment.Center)) { Avatar(initialsFor(r.title), 72) }
                    }
                }
            }
        }
        FactoryListTitle(stringResource(R.string.tab_recents))
        LazyColumn(Modifier.fillMaxSize()) {
            items(d.recents.take(20)) { r ->
                val (icon, color) = recentIcon(r.type)
                FactoryListRow(
                    r.title,
                    leading = { Icon(icon, null, tint = color, modifier = Modifier.size(30.dp)) },
                    value = DateUtils.getRelativeTimeSpanString(r.date, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                ) { d.onDial(r.number) }
            }
        }
    }
}

// --- Ambient -------------------------------------------------------------------------------------------------------

/** Pages of the ambient phone: the menu, then the tabs and a contact's page. */
private enum class AmbientPage { MENU, TAB, CONTACT }

@Composable
internal fun AmbientDialer(d: DialerScreens) {
    val context = LocalContext.current
    val tab = d.state.tab.value
    val call = d.call
    // The menu first, unless another app asked for a tab (launcher, widget, tel: link).
    var page by rememberSaveable { mutableStateOf(if (d.state.tabRequest.value > 0) AmbientPage.TAB else AmbientPage.MENU) }
    LaunchedEffect(d.state.tabRequest.value) { if (d.state.tabRequest.value > 0) page = AmbientPage.TAB }
    var contact by remember { mutableStateOf<Contact?>(null) }
    var search by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = d.showCall && call != null && !call.status.incoming) { d.minimize(true) }
    BackHandler(enabled = !d.showCall && page == AmbientPage.CONTACT) { page = AmbientPage.TAB }
    BackHandler(enabled = !d.showCall && page == AmbientPage.TAB && search != null) { search = null }
    BackHandler(enabled = !d.showCall && page == AmbientPage.TAB && search == null) { page = AmbientPage.MENU }
    val open: (Tab) -> Unit = {
        d.state.tab.value = it
        page = AmbientPage.TAB
    }
    val title =
        when {
            d.showCall && call != null -> statusTitle(call)
            page == AmbientPage.MENU -> stringResource(R.string.dial_phone)
            page == AmbientPage.CONTACT -> contact?.name.orEmpty()
            else -> stringResource(ambientTabTitle(tab))
        }
    val keys =
        when {
            d.showCall && call != null && call.status.incoming ->
                listOf(
                    AmbientKey(stringResource(R.string.call_reject)) { d.phone.reject(call) },
                    AmbientKey(stringResource(R.string.call_answer)) { d.phone.answer(call) },
                )
            d.showCall && call != null -> listOf(AmbientKey(stringResource(R.string.call_end)) { d.phone.hangup(call) })
            page == AmbientPage.TAB && tab == Tab.CONTACTS -> listOf(AmbientKey(stringResource(R.string.dial_search)) { search = if (search == null) "" else null })
            page == AmbientPage.TAB && tab == Tab.KEYPAD ->
                listOf(AmbientKey(stringResource(R.string.call), enabled = d.state.number.value.isNotBlank()) { d.onDial(d.state.number.value) })
            page == AmbientPage.CONTACT -> contact?.let { c -> listOf(AmbientKey(stringResource(R.string.call)) { d.onDial(c.primary.number) }) } ?: emptyList()
            else -> emptyList()
        }
    val options =
        buildList {
            if (call != null && !d.showCall) add(AmbientOption(stringResource(R.string.call_title)) { d.minimize(false) })
            if (d.showCall && call != null && !call.status.incoming && d.phone.features.mute) {
                add(AmbientOption(stringResource(R.string.call_mute)) { d.phone.setMuted(!d.link.micMuted) })
            }
            add(AmbientOption(stringResource(R.string.tab_settings)) { open(Tab.SETTINGS) })
        }
    AmbientScreen(title, keys, options) {
        when {
            d.showCall && call != null -> AmbientCall(d, call)
            page == AmbientPage.MENU ->
                AmbientPhoneMenu(open) {
                    runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }
                }
            page == AmbientPage.CONTACT && contact != null -> AmbientContact(contact!!, d.onDial)
            tab == Tab.SETTINGS -> SettingsScreen(d.link, d.settings)
            tab == Tab.KEYPAD -> AmbientKeypad(d)
            tab == Tab.RECENTS ->
                if (!d.canLog) PermissionCard(stringResource(R.string.permission_calllog_text), d.onGrant) else AmbientRecents(d.recents, d.onDial)
            tab == Tab.CONTACTS ->
                if (!d.canContacts) {
                    PermissionCard(stringResource(R.string.permission_contacts_text), d.onGrant)
                } else {
                    AmbientContacts(d.contacts, search, { search = it }) {
                        contact = it
                        page = AmbientPage.CONTACT
                    }
                }
            else ->
                if (!d.canContacts) {
                    PermissionCard(stringResource(R.string.permission_contacts_text), d.onGrant)
                } else {
                    AmbientContacts(d.contacts.filter { it.starred }, null, {}) {
                        contact = it
                        page = AmbientPage.CONTACT
                    }
                }
        }
    }
}

private fun ambientTabTitle(tab: Tab): Int =
    when (tab) {
        Tab.CONTACTS -> R.string.dial_directory
        Tab.RECENTS -> R.string.dial_history
        Tab.KEYPAD -> R.string.dial_number
        else -> tab.label
    }

@Composable
private fun statusTitle(call: PhoneCall): String =
    when (call.status) {
        CallStatus.DIALING, CallStatus.ALERTING -> stringResource(R.string.dial_outgoing)
        CallStatus.RINGING, CallStatus.WAITING -> stringResource(R.string.status_ringing)
        else -> stringResource(R.string.call_title)
    }

/** Six tiles like the original's phone menu. */
@Composable
private fun AmbientPhoneMenu(
    open: (Tab) -> Unit,
    onSmartphone: () -> Unit,
) {
    val tiles =
        listOf(
            Triple(R.string.dial_directory, Icons.Default.Contacts) { open(Tab.CONTACTS) },
            Triple(R.string.dial_history, Icons.Default.History) { open(Tab.RECENTS) },
            Triple(R.string.dial_number, Icons.Default.Dialpad) { open(Tab.KEYPAD) },
            Triple(R.string.tab_favorites, Icons.Default.Star) { open(Tab.FAVORITES) },
            Triple(R.string.dial_smartphone, Icons.Default.Smartphone, onSmartphone),
            Triple(R.string.tab_settings, Icons.Default.Settings) { open(Tab.SETTINGS) },
        )
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        tiles.chunked(3).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { (label, icon, onClick) -> AmbientMenuTile(stringResource(label), icon, Modifier.weight(1f), onClick) }
            }
        }
    }
}

/** Contacts: the letter in a grey square before the first of each letter, the framed picture, the name. */
@Composable
private fun AmbientContacts(
    contacts: List<Contact>,
    search: String?,
    onSearch: (String) -> Unit,
    onOpen: (Contact) -> Unit,
) {
    val shown =
        if (search.isNullOrBlank()) {
            contacts
        } else {
            val digits = PhoneBook.normalize(search)
            contacts.filter { c ->
                c.name.contains(search, ignoreCase = true) || (digits.length >= 2 && c.numbers.any { PhoneBook.normalize(it.number).contains(digits) })
            }
        }
    Column(Modifier.fillMaxSize().border(BorderStroke(1.dp, DialerColors.Accent))) {
        if (search != null) {
            Box(Modifier.fillMaxWidth().padding(10.dp).height(56.dp).background(Color(0xFF26272B)).border(BorderStroke(1.dp, Color.White)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    search,
                    onSearch,
                    singleLine = true,
                    textStyle = TextStyle(color = KitLight, fontSize = 22.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(KitLight),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (shown.isEmpty()) Text(stringResource(R.string.contacts_empty), color = KitDim, fontSize = 21.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(24.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown.size, key = { shown[it].id }) { i ->
                val c = shown[i]
                val first = firstLetter(c.name)
                val head = i == 0 || firstLetter(shown[i - 1].name) != first
                AmbientListRow(
                    c.name,
                    modifier = Modifier.height(86.dp),
                    leading = {
                        Box(Modifier.size(52.dp).background(if (head) Color(0xFF9A9DA3) else Color.Transparent), contentAlignment = Alignment.Center) {
                            if (head) Text(first.toString(), color = Color(0xFF1B1C1F), fontSize = 30.sp, fontWeight = FontWeight.Light)
                        }
                        Spacer(Modifier.width(10.dp))
                        FramedPicture(66.dp, image = rememberPhoto(c.photo))
                    },
                ) { onOpen(c) }
            }
        }
    }
}

/** A contact: the large framed picture and the name, then a line by number (touch: call). */
@Composable
private fun AmbientContact(
    c: Contact,
    onDial: (String) -> Unit,
) {
    Row(Modifier.fillMaxSize().border(BorderStroke(1.dp, DialerColors.Accent)).padding(20.dp)) {
        Column(Modifier.weight(0.8f), horizontalAlignment = Alignment.CenterHorizontally) {
            FramedPicture(150.dp, image = rememberPhoto(c.photo))
            Spacer(Modifier.height(18.dp))
            Text(c.name, color = KitLight, fontSize = 26.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center)
        }
        LazyColumn(Modifier.weight(1f)) {
            items(c.numbers) { n ->
                AmbientListRow(
                    n.number,
                    value = n.label,
                    leading = { MetalIcon(Icons.Default.PhoneAndroid, 32.dp) },
                ) { onDial(n.number) }
            }
        }
    }
}

/** Call history: four icon tabs (all, missed, received, made), then the calls with their picture and date. */
@Composable
private fun AmbientRecents(
    recents: List<RecentCall>,
    onDial: (String) -> Unit,
) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val types = listOf(null, RecentType.MISSED, RecentType.INCOMING, RecentType.OUTGOING)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp)) {
            types.forEachIndexed { i, t ->
                val open = i == filter
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(BorderStroke(1.dp, if (open) DialerColors.Accent else DialerColors.Accent.copy(alpha = 0.6f)))
                        .background(if (open) Color.Transparent else Color.Black.copy(alpha = 0.4f))
                        .clickable { filter = i },
                    contentAlignment = Alignment.Center,
                ) {
                    if (t == null) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(30.dp))
                            Text(stringResource(R.string.recents_all), color = Color.White, fontSize = 12.sp)
                        }
                    } else {
                        val (icon, color) = recentIcon(t)
                        Icon(icon, null, tint = color, modifier = Modifier.size(30.dp))
                    }
                }
            }
        }
        val list = types[filter]?.let { t -> recents.filter { it.type == t } } ?: recents
        Box(Modifier.fillMaxSize().border(BorderStroke(1.dp, DialerColors.Accent))) {
            if (list.isEmpty()) Text(stringResource(R.string.recents_empty), color = KitDim, fontSize = 21.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(24.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(list) { r ->
                    val (icon, color) = recentIcon(r.type)
                    AmbientListRow(
                        r.number.ifBlank { r.title },
                        modifier = Modifier.height(96.dp),
                        value = DateUtils.formatDateTime(LocalContext.current, r.date, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NUMERIC_DATE),
                        leading = {
                            Icon(icon, null, tint = color, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.width(14.dp))
                            FramedPicture(66.dp)
                            if (r.name.isNotBlank()) {
                                Spacer(Modifier.width(16.dp))
                                Text(r.name, color = KitDim, fontSize = 20.sp, fontWeight = FontWeight.Light, maxLines = 1, modifier = Modifier.width(220.dp))
                            }
                        },
                    ) { onDial(r.number) }
                }
            }
        }
    }
}

/** Dial a number: the field (and backspace) on top, the matching contacts, the twelve framed keys at the bottom. */
@Composable
private fun AmbientKeypad(d: DialerScreens) {
    val number = d.state.number.value
    Column(Modifier.fillMaxSize().border(BorderStroke(1.dp, DialerColors.Accent)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).fillMaxHeight().background(Color(0xFF26272B)).border(BorderStroke(1.dp, Color.White)).padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) { Text(number, color = KitLight, fontSize = 28.sp, fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.StartEllipsis) }
            AmbientGridKey(Modifier.width(110.dp).fillMaxHeight(), onLongClick = { d.state.number.value = "" }, onClick = { d.state.number.value = number.dropLast(1) }) {
                Icon(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.keypad_delete), tint = KitLight, modifier = Modifier.size(28.dp))
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth()) {
            keypadMatches(number, d.contacts).take(2).forEach { (c, n) -> AmbientListRow(c.name, value = n) { d.onDial(n) } }
        }
        KEYS.forEach { row ->
            Row(Modifier.fillMaxWidth().height(50.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { (digit, _) ->
                    AmbientGridKey(
                        Modifier.weight(1f).fillMaxHeight(),
                        onLongClick = if (digit == "0") ({ d.state.number.value = number + "+" }) else null,
                        onClick = { d.state.number.value = number + digit },
                    ) { Text(digit, color = KitLight, fontSize = 24.sp, fontWeight = FontWeight.Light) }
                }
            }
        }
    }
}

/** The call: the large framed picture, name, number and state; the call's keys in a row under them. */
@Composable
private fun AmbientCall(
    d: DialerScreens,
    call: PhoneCall,
) {
    val name = callName(call)
    var keypad by remember(call.id) { mutableStateOf(false) }
    var typed by remember(call.id) { mutableStateOf("") }
    AmbientFrame(Modifier.fillMaxSize(), glow = true) {
        Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (keypad) {
                Text(typed.ifBlank { " " }, color = KitLight, fontSize = 30.sp, fontWeight = FontWeight.Light, maxLines = 1)
                Column(Modifier.weight(1f).fillMaxWidth(0.7f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    KEYS.forEach { row ->
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { (digit, _) ->
                                AmbientGridKey(Modifier.weight(1f).fillMaxHeight(), onClick = { if (d.phone.dtmf(digit[0])) typed += digit }) {
                                    Text(digit, color = KitLight, fontSize = 24.sp, fontWeight = FontWeight.Light)
                                }
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    FramedPicture(150.dp)
                    Spacer(Modifier.height(18.dp))
                    Text(name, color = KitLight, fontSize = 26.sp, fontWeight = FontWeight.Light, maxLines = 1)
                    if (name != call.number && call.number.isNotBlank()) Text(call.number, color = KitLight, fontSize = 24.sp, fontWeight = FontWeight.Light)
                    Spacer(Modifier.height(8.dp))
                    Text(statusLine(call), color = KitLight, fontSize = 24.sp, fontWeight = FontWeight.Light)
                }
            }
            if (!call.status.incoming) {
                val f = d.phone.features
                Row(Modifier.fillMaxWidth(0.75f).height(58.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (f.hold) {
                        AmbientGridKey(Modifier.weight(1f).fillMaxHeight(), selected = call.status == CallStatus.HELD, onClick = { d.phone.hold(call, call.status != CallStatus.HELD) }) {
                            Icon(Icons.Default.Pause, stringResource(R.string.call_hold), tint = KitLight)
                        }
                    }
                    if (f.audioRoute) {
                        val inCar = d.link.audioInCar != false
                        AmbientGridKey(Modifier.weight(1f).fillMaxHeight(), selected = !inCar, onClick = { d.phone.setAudioInCar(!inCar) }) {
                            Icon(Icons.Default.PhoneAndroid, stringResource(R.string.call_on_phone), tint = KitLight)
                        }
                    }
                    if (f.mute) {
                        AmbientGridKey(Modifier.weight(1f).fillMaxHeight(), selected = d.link.micMuted, onClick = { d.phone.setMuted(!d.link.micMuted) }) {
                            Icon(Icons.Default.MicOff, stringResource(R.string.call_mute), tint = KitLight)
                        }
                    }
                    if (f.dtmf) {
                        AmbientGridKey(Modifier.weight(1f).fillMaxHeight(), selected = keypad, onClick = { keypad = !keypad }) {
                            Icon(Icons.Default.Dialpad, stringResource(R.string.call_keypad), tint = KitLight)
                        }
                    }
                }
            }
        }
    }
}
