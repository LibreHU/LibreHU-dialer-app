package org.librehu.dialer.phone

import android.telecom.Call
import android.telecom.InCallService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Telecom calls handed to [DialerInCallService], as [PhoneCall]s. Main thread only. */
object TelecomCalls {
    private val list = mutableListOf<Call>()
    private val ids = HashMap<Call, String>()
    private var next = 1
    private val _calls = MutableStateFlow<List<PhoneCall>>(emptyList())
    val calls: StateFlow<List<PhoneCall>> = _calls.asStateFlow()

    var service: InCallService? = null
    var muted = false
        set(value) {
            field = value
            onChange?.invoke()
        }

    /** Link refresh of the backend (mute state). */
    var onChange: (() -> Unit)? = null

    private val callback =
        object : Call.Callback() {
            override fun onStateChanged(
                call: Call,
                state: Int,
            ) = publish()

            override fun onDetailsChanged(
                call: Call,
                details: Call.Details,
            ) = publish()
        }

    fun add(call: Call) {
        if (call in list) return
        list += call
        ids[call] = "t${next++}"
        call.registerCallback(callback)
        publish()
    }

    fun remove(call: Call) {
        call.unregisterCallback(callback)
        list -= call
        ids.remove(call)
        publish()
    }

    fun clear() {
        list.toList().forEach(::remove)
    }

    fun raw(): List<Call> = list.toList()

    fun find(id: String): Call? = ids.entries.firstOrNull { it.value == id }?.key

    private fun publish() {
        val anyActive = list.any { it.state == Call.STATE_ACTIVE || it.state == Call.STATE_HOLDING }
        _calls.value =
            list.map { c ->
                val d = c.details
                val status =
                    when (c.state) {
                        Call.STATE_RINGING -> if (anyActive) CallStatus.WAITING else CallStatus.RINGING
                        Call.STATE_ACTIVE -> CallStatus.ACTIVE
                        Call.STATE_HOLDING -> CallStatus.HELD
                        Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> CallStatus.ENDED
                        Call.STATE_DIALING -> CallStatus.ALERTING
                        else -> CallStatus.DIALING
                    }
                PhoneCall(
                    id = ids[c] ?: "",
                    number = d?.handle?.schemeSpecificPart.orEmpty(),
                    name = d?.callerDisplayName.orEmpty(),
                    status = status,
                    activeSince = d?.connectTimeMillis?.takeIf { it > 0 } ?: 0,
                    conference = d?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true,
                )
            }
    }
}
