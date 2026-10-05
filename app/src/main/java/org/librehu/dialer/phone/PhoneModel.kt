package org.librehu.dialer.phone

import kotlinx.coroutines.flow.StateFlow

/** Call states shown by the UI, whatever the backend (Telecom, Jancar btservice, LibreHU-service). */
enum class CallStatus {
    /** Placing the call. */
    DIALING,

    /** The other phone rings. */
    ALERTING,

    /** Incoming call ringing. */
    RINGING,

    /** Second incoming call while talking. */
    WAITING,
    ACTIVE,
    HELD,
    ENDED,
    ;

    val incoming get() = this == RINGING || this == WAITING
    val live get() = this != ENDED
}

data class PhoneCall(
    /** Backend specific, stable while the call lasts. */
    val id: String,
    val number: String,
    /** Name given by the phone / backend, empty when unknown (the UI then looks the number up). */
    val name: String = "",
    val status: CallStatus,
    /** System.currentTimeMillis() when answered, 0 before. */
    val activeSince: Long = 0,
    val conference: Boolean = false,
)

/** Link to the phone (Bluetooth hands-free) and what the backend can do. */
data class PhoneLink(
    /** The backend itself works (service bound, Telecom available…). */
    val available: Boolean = false,
    val connected: Boolean = false,
    val deviceName: String = "",
    /** Why it is not available / extra information, empty when none. */
    val message: String = "",
    /** Call audio in the car (true), on the phone (false), unknown (null). */
    val audioInCar: Boolean? = null,
    val micMuted: Boolean = false,
    /** 0..5, -1 unknown. */
    val battery: Int = -1,
    val signal: Int = -1,
)

/** What the in-call screen may offer. */
data class PhoneFeatures(
    val hold: Boolean = false,
    val swap: Boolean = false,
    val audioRoute: Boolean = false,
    val mute: Boolean = true,
    val dtmf: Boolean = true,
)

/**
 * Phone backend. `main`: Android Telecom (the dialer is the default phone app, calls of the Bluetooth phone go
 * through Android's HFP client connection service); `ivi`: Jancar btservice; `librehu-service`: LibreHU-service.
 * Commands return false when they could not be sent; the outcome shows in [calls].
 */
interface PhoneBackend {
    val link: StateFlow<PhoneLink>
    val calls: StateFlow<List<PhoneCall>>
    val features: PhoneFeatures

    fun start()

    fun stop()

    fun dial(number: String): Boolean

    fun answer(call: PhoneCall): Boolean

    fun reject(call: PhoneCall): Boolean

    fun hangup(call: PhoneCall): Boolean

    fun hold(
        call: PhoneCall,
        on: Boolean,
    ): Boolean = false

    fun swap(): Boolean = false

    fun dtmf(key: Char): Boolean

    fun setMuted(muted: Boolean): Boolean

    /** Call audio in the car (true) or on the phone (false). */
    fun setAudioInCar(inCar: Boolean): Boolean = false
}
