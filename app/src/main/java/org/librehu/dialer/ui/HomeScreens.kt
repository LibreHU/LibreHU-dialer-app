package org.librehu.dialer.ui

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.librehu.dialer.R
import org.librehu.dialer.data.Contact
import org.librehu.dialer.data.PhoneBook
import org.librehu.dialer.data.RecentCall
import org.librehu.dialer.data.RecentType
import org.librehu.dialer.data.initialsFor

/** Message and button shown instead of a list when a permission is missing. */
@Composable
fun PermissionCard(
    text: String,
    onGrant: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(DialerColors.Card)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, color = DialerColors.Muted, fontSize = 17.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Pill(stringResource(R.string.grant), selected = true, onClick = onGrant)
    }
}

@Composable
fun Pill(
    label: String,
    selected: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) DialerColors.Accent else DialerColors.Raised)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (selected) DialerColors.OnAccent else DialerColors.Text
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

// --- Favourites --------------------------------------------------------------------------------------------------

@Composable
fun FavoritesScreen(
    contacts: List<Contact>,
    recents: List<RecentCall>,
    canRead: Boolean,
    onGrant: () -> Unit,
    onDial: (String) -> Unit,
) {
    if (!canRead) return PermissionCard(stringResource(R.string.permission_contacts_text), onGrant)
    var picking by remember { mutableStateOf<Contact?>(null) }
    val starred = contacts.filter { it.starred }
    Column(Modifier.fillMaxSize()) {
        if (starred.isNotEmpty()) {
            Heading(stringResource(R.string.tab_favorites), stringResource(R.string.favorites_subtitle))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(starred, key = { it.id }) { c ->
                    FavoriteTile(c.name, c.initials, c.photo) { if (c.numbers.size > 1) picking = c else onDial(c.primary.number) }
                }
            }
        } else {
            val frequent = PhoneBook.frequent(recents, 6)
            Heading(stringResource(R.string.frequent), stringResource(R.string.frequent_subtitle))
            if (frequent.isEmpty()) {
                Text(
                    stringResource(R.string.favorites_empty),
                    color = DialerColors.Muted,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(4.dp),
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(frequent, key = { it.number }) { r -> FavoriteTile(r.title, initialsFor(r.title), null) { onDial(r.number) } }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Heading(stringResource(R.string.tab_recents))
        RecentList(recents.take(20), onDial)
    }
    picking?.let { c ->
        NumberPicker(c, onDismiss = { picking = null }) {
            picking = null
            onDial(it)
        }
    }
}

@Composable
private fun FavoriteTile(
    name: String,
    initials: String,
    photo: android.net.Uri?,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(DialerColors.Card)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(initials, 72, photo)
        Spacer(Modifier.height(10.dp))
        Text(name, color = DialerColors.Text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Icon(Icons.Default.Call, null, tint = DialerColors.Accent, modifier = Modifier.size(22.dp))
    }
}

// --- Recents -----------------------------------------------------------------------------------------------------

@Composable
fun RecentsScreen(
    recents: List<RecentCall>,
    canRead: Boolean,
    onGrant: () -> Unit,
    onDial: (String) -> Unit,
) {
    if (!canRead) return PermissionCard(stringResource(R.string.permission_calllog_text), onGrant)
    var filter by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Heading(stringResource(R.string.tab_recents), stringResource(R.string.recents_subtitle)) }
            Pill(stringResource(R.string.recents_all), selected = !filter) { filter = false }
            Spacer(Modifier.width(8.dp))
            Pill(stringResource(R.string.recents_missed), selected = filter) { filter = true }
        }
        RecentList(if (filter) recents.filter { it.type == RecentType.MISSED } else recents, onDial)
    }
}

@Composable
private fun RecentList(
    recents: List<RecentCall>,
    onDial: (String) -> Unit,
) {
    if (recents.isEmpty()) {
        Text(stringResource(R.string.recents_empty), color = DialerColors.Muted, fontSize = 15.sp, modifier = Modifier.padding(8.dp))
        return
    }
    val callLabel = stringResource(R.string.call)
    LazyColumn(
        Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(DialerColors.Card),
        contentPadding = PaddingValues(8.dp),
    ) {
        items(recents) { r ->
            val (icon, label) =
                when (r.type) {
                    RecentType.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to R.string.call_incoming
                    RecentType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to R.string.call_outgoing
                    RecentType.MISSED -> Icons.AutoMirrored.Filled.CallMissed to R.string.call_missed
                    RecentType.REJECTED -> Icons.Default.Block to R.string.call_rejected
                    RecentType.OTHER -> Icons.Default.Call to R.string.call
                }
            val missed = r.type == RecentType.MISSED
            val count = if (r.count > 1) " (${r.count})" else ""
            val subtitle =
                stringResource(label) + count + " · " +
                    DateUtils.getRelativeTimeSpanString(r.date, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
            PersonRow(
                title = r.title,
                subtitle = subtitle,
                initials = initialsFor(r.title),
                leading = {
                    Icon(icon, null, tint = if (missed) DialerColors.Red else DialerColors.Muted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                },
                titleColor = if (missed) DialerColors.Red else DialerColors.Text,
                onClick = { onDial(r.number) },
                onCall = { onDial(r.number) },
                callLabel = callLabel,
            )
        }
    }
}

// --- Contacts ----------------------------------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    contacts: List<Contact>,
    canRead: Boolean,
    onGrant: () -> Unit,
    onDial: (String) -> Unit,
) {
    if (!canRead) return PermissionCard(stringResource(R.string.permission_contacts_text), onGrant)
    var query by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf<Contact?>(null) }
    val digits = PhoneBook.normalize(query)
    val shown =
        if (query.isBlank()) {
            contacts
        } else {
            contacts.filter { c ->
                c.name.contains(query, ignoreCase = true) ||
                    (digits.length >= 2 && c.numbers.any { PhoneBook.normalize(it.number).contains(digits) })
            }
        }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(DialerColors.Card)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, null, tint = DialerColors.Muted)
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = DialerColors.Text, fontSize = 18.sp),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text(stringResource(R.string.contacts_search), color = DialerColors.Muted, fontSize = 18.sp)
                    inner()
                },
            )
            Text(stringResource(R.string.contacts_count, shown.size), color = DialerColors.Muted, fontSize = 14.sp)
        }
        Spacer(Modifier.height(10.dp))
        if (contacts.isEmpty()) {
            Text(stringResource(R.string.contacts_empty), color = DialerColors.Muted, fontSize = 15.sp, modifier = Modifier.padding(8.dp))
        }
        val callLabel = stringResource(R.string.call)
        LazyColumn(
            Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(DialerColors.Card),
            contentPadding = PaddingValues(8.dp),
        ) {
            var letter = ' '
            shown.forEach { c ->
                val first =
                    c.name
                        .firstOrNull()
                        ?.uppercaseChar()
                        ?.takeIf { it.isLetter() } ?: '#'
                if (query.isBlank() && first != letter) {
                    letter = first
                    stickyHeader(key = "h$first${c.id}") {
                        Text(
                            first.toString(),
                            color = DialerColors.Accent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth().background(DialerColors.Card).padding(horizontal = 14.dp, vertical = 4.dp),
                        )
                    }
                }
                item(key = c.id) {
                    val more = if (c.numbers.size > 1) " · +${c.numbers.size - 1}" else ""
                    PersonRow(
                        title = c.name,
                        subtitle = "${c.primary.label} · ${c.primary.number}$more",
                        initials = c.initials,
                        photo = c.photo,
                        onClick = { if (c.numbers.size > 1) picking = c else onDial(c.primary.number) },
                        onCall = { if (c.numbers.size > 1) picking = c else onDial(c.primary.number) },
                        callLabel = callLabel,
                    )
                }
            }
        }
    }
    picking?.let { c ->
        NumberPicker(c, onDismiss = { picking = null }) {
            picking = null
            onDial(it)
        }
    }
}

@Composable
fun NumberPicker(
    contact: Contact,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(contact.name) },
        text = {
            Column {
                contact.numbers.forEach { n ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onPick(n.number) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Call, null, tint = DialerColors.Accent)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(n.number, fontSize = 18.sp)
                            Text(n.label, color = DialerColors.Muted, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

// --- Keypad ------------------------------------------------------------------------------------------------------

val KEYS =
    listOf(
        listOf("1" to "", "2" to "ABC", "3" to "DEF"),
        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
        listOf("*" to "", "0" to "+", "#" to ""),
    )

/** 12 keys; a long press on 0 types "+". */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeyGrid(
    keyHeight: Int,
    onKey: (Char) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KEYS.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (digit, letters) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .height(keyHeight.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(DialerColors.Raised)
                            .combinedClickable(
                                onClick = { onKey(digit[0]) },
                                onLongClick = if (digit == "0") ({ onKey('+') }) else null,
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(digit, fontSize = 28.sp, color = DialerColors.Text)
                        if (letters.isNotEmpty()) Text(letters, fontSize = 11.sp, color = DialerColors.Muted)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeypadScreen(
    number: String,
    onNumber: (String) -> Unit,
    contacts: List<Contact>,
    onDial: (String) -> Unit,
) {
    val digits = PhoneBook.normalize(number)
    val matches =
        if (digits.length < 3) {
            emptyList()
        } else {
            contacts.mapNotNull { c -> c.numbers.firstOrNull { PhoneBook.normalize(it.number).contains(digits) }?.let { c to it } }.take(3)
        }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(Modifier.weight(0.9f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(18.dp),
                    ).background(DialerColors.Card)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    number.ifBlank { stringResource(R.string.keypad_hint) },
                    color = if (number.isBlank()) DialerColors.Muted else DialerColors.Text,
                    fontSize = if (number.length > 14) 24.sp else 32.sp,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(50))
                        .combinedClickable(onClick = { onNumber(number.dropLast(1)) }, onLongClick = { onNumber("") }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.keypad_delete), tint = DialerColors.Muted)
                }
            }
            matches.forEach { (c, n) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(16.dp),
                        ).background(DialerColors.Card)
                        .clickable { onDial(n.number) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(c.initials, 40, c.photo)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(c.name, color = DialerColors.Text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(n.number, color = DialerColors.Muted, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(if (number.isBlank()) DialerColors.Raised else DialerColors.Green)
                    .clickable(enabled = number.isNotBlank()) { onDial(number) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Call, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.call),
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            KeyGrid(76) { onNumber(number + it) }
        }
    }
}
