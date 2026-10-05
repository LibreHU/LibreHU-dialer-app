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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.librehu.dialer.R
import org.librehu.dialer.data.PhoneBook
import org.librehu.dialer.data.initialsFor
import org.librehu.dialer.phone.CallStatus
import org.librehu.dialer.phone.PhoneBackend
import org.librehu.dialer.phone.PhoneCall
import org.librehu.dialer.phone.PhoneLink

/** The call being handled: incoming first, then the active one. */
fun primaryCall(calls: List<PhoneCall>): PhoneCall? =
    calls.firstOrNull { it.status == CallStatus.RINGING }
        ?: calls.firstOrNull { it.status == CallStatus.ACTIVE }
        ?: calls.firstOrNull { it.status == CallStatus.WAITING }
        ?: calls.firstOrNull { it.status.live }

/** Contact name of a call: the backend's, else Android's contacts. */
@Composable
fun callName(call: PhoneCall): String {
    val context = LocalContext.current
    val looked by produceState(call.name, call.number, call.name) {
        if (call.name.isBlank()) value = withContext(Dispatchers.IO) { PhoneBook.lookupName(context, call.number) }
    }
    return looked.ifBlank { call.number.ifBlank { context.getString(R.string.call_unknown) } }
}

@Composable
fun elapsed(since: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (since > 0) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    if (since <= 0) return ""
    val s = ((now - since) / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun statusText(call: PhoneCall): String =
    when (call.status) {
        CallStatus.DIALING -> stringResource(R.string.status_dialing)
        CallStatus.ALERTING -> stringResource(R.string.status_alerting)
        CallStatus.RINGING -> stringResource(R.string.status_ringing)
        CallStatus.WAITING -> stringResource(R.string.status_waiting)
        CallStatus.ACTIVE -> elapsed(call.activeSince).ifBlank { stringResource(R.string.status_active) }
        CallStatus.HELD -> stringResource(R.string.status_held)
        CallStatus.ENDED -> stringResource(R.string.status_ended)
    }

@Composable
fun InCallScreen(
    phone: PhoneBackend,
    calls: List<PhoneCall>,
    link: PhoneLink,
    onMinimize: () -> Unit,
) {
    val call = primaryCall(calls) ?: return
    val other = calls.firstOrNull { it.id != call.id && it.status.live }
    var keypad by remember { mutableStateOf(false) }
    var typed by remember(call.id) { mutableStateOf("") }
    val name = callName(call)
    Row(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(DialerColors.Card)
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                if (!call.status.incoming) {
                    RoundButton(
                        Icons.Default.KeyboardArrowDown,
                        stringResource(R.string.call_minimize),
                        DialerColors.Raised,
                        DialerColors.Text,
                        48,
                        onMinimize,
                    )
                }
            }
            Avatar(initialsFor(name), 112)
            Spacer(Modifier.height(16.dp))
            Text(
                name,
                color = DialerColors.Text,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (name != call.number && call.number.isNotBlank()) Text(call.number, color = DialerColors.Muted, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text(statusText(call), color = DialerColors.Accent, fontSize = 20.sp)
            if (link.deviceName.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PhoneAndroid, null, tint = DialerColors.Muted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(link.deviceName, color = DialerColors.Muted, fontSize = 14.sp)
                }
            }
            if (other != null) {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(DialerColors.Raised).padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(callName(other) + " · " + statusText(other), color = DialerColors.Text, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            if (call.status.incoming) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CallAction(
                        Icons.Default.CallEnd,
                        stringResource(R.string.call_reject),
                        background = DialerColors.Red,
                        size = 88,
                    ) { phone.reject(call) }
                    CallAction(
                        Icons.Default.Call,
                        stringResource(if (call.status == CallStatus.WAITING) R.string.call_answer_hold else R.string.call_answer),
                        background = DialerColors.Green,
                        size = 88,
                    ) { phone.answer(call) }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (phone.features.mute) {
                        CallAction(
                            Icons.Default.MicOff,
                            stringResource(R.string.call_mute),
                            selected = link.micMuted,
                        ) {
                            phone.setMuted(!link.micMuted)
                        }
                    }
                    if (phone.features.dtmf) {
                        CallAction(Icons.Default.Dialpad, stringResource(R.string.call_keypad), selected = keypad) {
                            keypad =
                                !keypad
                        }
                    }
                    if (phone.features.hold && other == null) {
                        CallAction(Icons.Default.Pause, stringResource(R.string.call_hold), selected = call.status == CallStatus.HELD) {
                            phone.hold(call, call.status != CallStatus.HELD)
                        }
                    }
                    if (phone.features.swap &&
                        other != null
                    ) {
                        CallAction(Icons.Default.SwapCalls, stringResource(R.string.call_swap)) { phone.swap() }
                    }
                    if (phone.features.audioRoute) {
                        val inCar = link.audioInCar != false
                        CallAction(
                            Icons.Default.PhoneAndroid,
                            stringResource(R.string.call_on_phone),
                            selected = !inCar,
                        ) { phone.setAudioInCar(!inCar) }
                    }
                    CallAction(
                        Icons.Default.CallEnd,
                        stringResource(R.string.call_end),
                        background = DialerColors.Red,
                    ) { phone.hangup(call) }
                }
            }
        }
        if (keypad && !call.status.incoming) {
            Column(Modifier.weight(0.8f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Text(
                    typed.ifBlank { " " },
                    color = DialerColors.Text,
                    fontSize = 28.sp,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                    modifier = Modifier.padding(bottom = 12.dp, start = 8.dp),
                )
                KeyGrid(64) {
                    if (phone.dtmf(it)) typed += it
                }
            }
        }
    }
}

/** Thin bar over the tabs while a call goes on and its screen is minimised. */
@Composable
fun OngoingCallBar(
    call: PhoneCall,
    onOpen: () -> Unit,
    onEnd: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DialerColors.Green)
            .clickable(onClick = onOpen)
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Call, null, tint = Color.White)
        Spacer(Modifier.width(12.dp))
        Text(
            callName(call) + "  ·  " + statusText(call),
            color = Color.White,
            fontSize = 17.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(Modifier.padding(start = 8.dp)) {
            RoundButton(Icons.Default.CallEnd, stringResource(R.string.call_end), DialerColors.Red, Color.White, 44, onEnd)
        }
    }
}
