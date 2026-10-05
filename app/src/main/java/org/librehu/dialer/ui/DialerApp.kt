package org.librehu.dialer.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.librehu.dialer.R
import org.librehu.dialer.data.Contact
import org.librehu.dialer.data.PhoneBook
import org.librehu.dialer.data.RecentCall
import org.librehu.dialer.phone.PhoneBackend
import org.librehu.dialer.phone.PhoneLink
import java.util.Date

enum class Tab(
    val icon: ImageVector,
    val label: Int,
) {
    FAVORITES(Icons.Default.Star, R.string.tab_favorites),
    RECENTS(Icons.Default.History, R.string.tab_recents),
    CONTACTS(Icons.Default.Contacts, R.string.tab_contacts),
    KEYPAD(Icons.Default.Dialpad, R.string.tab_keypad),
    SETTINGS(Icons.Default.Settings, R.string.tab_settings),
    ;

    companion object {
        /** Widgets and other apps name tabs loosely ("Recents", "keypad"…). */
        fun parse(name: String?): Tab? = entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) }
    }
}

class DialerState(
    val tab: MutableState<Tab>,
    val number: MutableState<String>,
    /** Bumped to bring the call screen back (call added, notification…). */
    val showCall: MutableState<Int>,
    /** Bumped when permissions change, to reload contacts and log. */
    val dataVersion: MutableState<Int>,
)

@Composable
fun DialerApp(
    phone: PhoneBackend,
    state: DialerState,
    onDial: (String) -> Unit,
    onGrant: () -> Unit,
    settings: SettingsActions,
) {
    val context = LocalContext.current
    val calls by phone.calls.collectAsStateWithLifecycle()
    val link by phone.link.collectAsStateWithLifecycle()
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var recents by remember { mutableStateOf<List<RecentCall>>(emptyList()) }
    val noCall = calls.none { it.status.live }
    LaunchedEffect(state.dataVersion.value, noCall) {
        contacts = withContext(Dispatchers.IO) { PhoneBook.contacts(context) }
        recents = withContext(Dispatchers.IO) { PhoneBook.recents(context) }
    }
    val call = primaryCall(calls)
    var minimized by remember { mutableStateOf(false) }
    LaunchedEffect(state.showCall.value, call?.id) { minimized = false }
    val showCall = call != null && (!minimized || call.status.incoming)
    val tab = state.tab.value

    Row(Modifier.fillMaxSize().background(DialerColors.Bg).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        NavRail(if (showCall) null else tab) {
            state.tab.value = it
            minimized = call != null
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Header(stringResource(if (showCall) R.string.call_title else tab.label), link)
            Spacer(Modifier.height(12.dp))
            if (showCall && call != null) {
                InCallScreen(phone, calls, link) { minimized = true }
            } else {
                if (call != null) {
                    OngoingCallBar(call, onOpen = { minimized = false }, onEnd = { phone.hangup(call) })
                    Spacer(Modifier.height(10.dp))
                }
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        Tab.FAVORITES -> FavoritesScreen(contacts, recents, PhoneBook.canReadContacts(context), onGrant, onDial)
                        Tab.RECENTS -> RecentsScreen(recents, PhoneBook.canReadCallLog(context), onGrant, onDial)
                        Tab.CONTACTS -> ContactsScreen(contacts, PhoneBook.canReadContacts(context), onGrant, onDial)
                        Tab.KEYPAD -> KeypadScreen(state.number.value, { state.number.value = it }, contacts, onDial)
                        Tab.SETTINGS -> SettingsScreen(link, settings)
                    }
                }
            }
        }
    }
}

@Composable
private fun NavRail(
    selected: Tab?,
    onTab: (Tab) -> Unit,
) {
    Column(
        Modifier
            .width(104.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(26.dp))
            .background(DialerColors.Card)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Tab.entries.forEach { item ->
            val active = item == selected
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (active) DialerColors.Raised else Color.Transparent)
                    .clickable { onTab(item) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(item.icon, null, tint = if (active) DialerColors.Accent else DialerColors.Muted, modifier = Modifier.size(30.dp))
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(item.label),
                    color = if (active) DialerColors.Text else DialerColors.Muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Header(
    title: String,
    link: PhoneLink,
) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L * (60 - (now / 1000) % 60))
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DialerColors.Card)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = DialerColors.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Row(
            Modifier.clip(CircleShape).background(DialerColors.Raised).padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(if (link.connected) DialerColors.Green else DialerColors.Muted))
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    link.connected -> link.deviceName.ifBlank { stringResource(R.string.phone_connected_short) }
                    !link.available -> stringResource(R.string.phone_unavailable)
                    else -> stringResource(R.string.phone_disconnected)
                },
                color = if (link.connected) DialerColors.Text else DialerColors.Muted,
                fontSize = 15.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(20.dp))
        Text(
            android.text.format.DateFormat
                .getTimeFormat(context)
                .format(Date(now)),
            color = DialerColors.Text,
            fontSize = 24.sp,
        )
    }
}
