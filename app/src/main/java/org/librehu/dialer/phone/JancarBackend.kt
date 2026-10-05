package org.librehu.dialer.phone

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Jancar `ivi-btservice` (stock UJC201 ROM), raw Binder calls on `com.jancar.btservice.bluetooth.IBluetooth`.
 * Transaction numbers, callback order and status values are those of the decompiled stock APK
 * (docs/jancar-binder-contract.md):
 *
 * - `IBluetoothCallback`: 1 onConnectStatus(int, addr, name), 2 onCallStatus(int, number, name), 3 onVoiceChange(int),
 *   4 onA2DPConnectStatus(int, boolean), 5 onBtMusicId3Info, 6 onBtBatteryValue(int), 7 onBtSignalValue(int),
 *   8 onPowerStatus(boolean);
 * - connection: 0 disconnected, 1 connected, 2 connecting, 4 pairing;
 * - call (`IVIBluetooth.CallStatus`): 0 normal, 1 incoming, 2 outgoing, 3 hang-up, 4 talking, 5 second incoming,
 *   6 held, 7 second outgoing, 8 two calls, 9 second call ended;
 * - voice: 1 call audio in the car, 0 on the phone.
 *
 * Jancar reports one number at a time: two calls are rebuilt from the successive events.
 */
class JancarBackend(
    context: Context,
) : PhoneBackend {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val _link = MutableStateFlow(PhoneLink(message = app.getString(org.librehu.dialer.R.string.jancar_connecting)))
    override val link: StateFlow<PhoneLink> = _link.asStateFlow()
    private val _calls = MutableStateFlow<List<PhoneCall>>(emptyList())
    override val calls: StateFlow<List<PhoneCall>> = _calls.asStateFlow()
    override val features = PhoneFeatures(hold = false, swap = true, audioRoute = true)

    @Volatile
    private var service: IBinder? = null
    private var bound = false
    private var nextId = 1

    private val listener =
        object : Binder() {
            init {
                attachInterface(null, CALLBACK)
            }

            override fun onTransact(
                code: Int,
                data: Parcel,
                reply: Parcel?,
                flags: Int,
            ): Boolean {
                if (code == INTERFACE_TRANSACTION) {
                    reply?.writeString(CALLBACK)
                    return true
                }
                data.enforceInterface(CALLBACK)
                when (code) {
                    1 -> {
                        val status = data.readInt()
                        data.readString()
                        val name = data.readString().orEmpty()
                        main.post { onConnect(status, name) }
                    }

                    2 -> {
                        val status = data.readInt()
                        val number = data.readString().orEmpty()
                        val name = data.readString().orEmpty()
                        main.post { onCall(status, number, name) }
                    }

                    3 -> {
                        val inCar = data.readInt() == 1
                        main.post { _link.value = _link.value.copy(audioInCar = inCar) }
                    }

                    6 -> {
                        val v = data.readInt()
                        main.post { _link.value = _link.value.copy(battery = v) }
                    }

                    7 -> {
                        val v = data.readInt()
                        main.post { _link.value = _link.value.copy(signal = v) }
                    }

                    8 -> {
                        val on = data.readInt() != 0
                        main.post { if (!on) _link.value = _link.value.copy(connected = false, deviceName = "") }
                    }

                    // 4 A2DP, 5 music: not for the phone app.
                    4, 5 -> {
                        Unit
                    }

                    else -> {
                        return super.onTransact(code, data, reply, flags)
                    }
                }
                reply?.writeNoException()
                return true
            }
        }

    /** Result of a command: failures are logged (Jancar error codes: 2 no device, 10 not in a call…). */
    private val exec =
        object : Binder() {
            init {
                attachInterface(null, EXEC_CALLBACK)
            }

            override fun onTransact(
                code: Int,
                data: Parcel,
                reply: Parcel?,
                flags: Int,
            ): Boolean {
                if (code == INTERFACE_TRANSACTION) {
                    reply?.writeString(EXEC_CALLBACK)
                    return true
                }
                data.enforceInterface(EXEC_CALLBACK)
                when (code) {
                    1 -> data.readString()
                    2 -> Log.w(TAG, "btservice command failed: ${data.readInt()}")
                    else -> return super.onTransact(code, data, reply, flags)
                }
                reply?.writeNoException()
                return true
            }
        }

    private val nameCallback =
        object : Binder() {
            init {
                attachInterface(null, EXEC_CALLBACK)
            }

            override fun onTransact(
                code: Int,
                data: Parcel,
                reply: Parcel?,
                flags: Int,
            ): Boolean {
                if (code == INTERFACE_TRANSACTION) {
                    reply?.writeString(EXEC_CALLBACK)
                    return true
                }
                data.enforceInterface(EXEC_CALLBACK)
                when (code) {
                    1 -> {
                        val name = data.readString().orEmpty()
                        main.post { if (name.isNotBlank()) _link.value = _link.value.copy(connected = true, deviceName = name) }
                    }

                    2 -> {
                        data.readInt()
                    }

                    else -> {
                        return super.onTransact(code, data, reply, flags)
                    }
                }
                reply?.writeNoException()
                return true
            }
        }

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName,
                binder: IBinder,
            ) {
                service = binder
                _link.value = _link.value.copy(available = true, message = "")
                transact(TX_REQUEST_LISTENER) { writeStrongBinder(listener) }
                transact(TX_GET_CURRENT_DEVICE_NAME) { writeStrongBinder(nameCallback) }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                service = null
                _link.value = PhoneLink(message = app.getString(org.librehu.dialer.R.string.jancar_lost))
                _calls.value = emptyList()
            }
        }

    override fun start() {
        if (bound) return
        bound =
            runCatching {
                app.bindService(Intent(ACTION).setClassName(PACKAGE, CLASS), connection, Context.BIND_AUTO_CREATE)
            }.getOrDefault(false)
        if (!bound) _link.value = PhoneLink(message = app.getString(org.librehu.dialer.R.string.jancar_missing))
    }

    override fun stop() {
        if (!bound) return
        transact(TX_UNREQUEST_LISTENER) { writeStrongBinder(listener) }
        runCatching { app.unbindService(connection) }
        bound = false
        service = null
    }

    private fun onConnect(
        status: Int,
        name: String,
    ) {
        val connected = status == 1
        _link.value = _link.value.copy(connected = connected, deviceName = if (connected) name else "")
        if (!connected) _calls.value = emptyList()
    }

    private fun onCall(
        status: Int,
        number: String,
        name: String,
    ) {
        val old = _calls.value
        val now = System.currentTimeMillis()

        fun call(
            st: CallStatus,
            n: String = number,
            nm: String = name,
        ): PhoneCall {
            val same = old.firstOrNull { it.number == n }
            val since = if (st == CallStatus.ACTIVE) same?.activeSince?.takeIf { it > 0 } ?: now else same?.activeSince ?: 0
            return PhoneCall(same?.id ?: "j${nextId++}", n, nm.ifBlank { same?.name.orEmpty() }, st, since)
        }
        val first = old.firstOrNull { it.number != number }
        _calls.value =
            when (status) {
                1 -> {
                    listOf(call(CallStatus.RINGING))
                }

                2 -> {
                    listOf(call(CallStatus.DIALING))
                }

                4 -> {
                    listOf(call(CallStatus.ACTIVE))
                }

                5 -> {
                    listOfNotNull(first?.copy(status = CallStatus.ACTIVE), call(CallStatus.WAITING))
                }

                6 -> {
                    listOf(call(CallStatus.HELD))
                }

                7 -> {
                    listOfNotNull(first?.copy(status = CallStatus.HELD), call(CallStatus.DIALING))
                }

                8 -> {
                    listOfNotNull(first?.copy(status = CallStatus.HELD), call(CallStatus.ACTIVE))
                }

                // Second call ended: the remaining one goes on.
                9 -> {
                    old
                        .filter { it.number != number }
                        .map {
                            it.copy(
                                status = CallStatus.ACTIVE,
                            )
                        }.ifEmpty { listOf(call(CallStatus.ACTIVE)) }
                }

                else -> {
                    emptyList()
                }
            }
    }

    override fun dial(number: String) =
        transact(TX_CALL_PHONE) {
            writeString(number)
            writeStrongBinder(exec)
        }

    override fun answer(call: PhoneCall) =
        if (call.status == CallStatus.WAITING) {
            threeParty(THREE_ANSWER_HOLD)
        } else {
            transact(TX_LISTEN_PHONE) { writeStrongBinder(exec) }
        }

    override fun reject(call: PhoneCall) = transact(TX_REJECT_PHONE) { writeStrongBinder(exec) }

    override fun hangup(call: PhoneCall) = transact(TX_HANG_PHONE) { writeStrongBinder(exec) }

    override fun swap() = threeParty(THREE_SWAP)

    override fun dtmf(key: Char) =
        transact(TX_REQUEST_DTMF) {
            writeInt(key.code)
            writeStrongBinder(exec)
        }

    override fun setMuted(muted: Boolean): Boolean {
        val ok =
            transact(TX_MUTE_MIC) {
                writeInt(if (muted) 1 else 0)
                writeStrongBinder(exec)
            }
        // btservice does not report the microphone state back.
        if (ok) _link.value = _link.value.copy(micMuted = muted)
        return ok
    }

    /** btservice toggles: only send when the audio is not already where asked. */
    override fun setAudioInCar(inCar: Boolean): Boolean {
        if (_link.value.audioInCar == inCar) return true
        return transact(TX_TRANSFER_CALL) { writeStrongBinder(exec) }
    }

    private fun threeParty(action: Int) =
        transact(TX_THREE_PARTY) {
            writeInt(action)
            writeStrongBinder(exec)
        }

    private fun transact(
        code: Int,
        args: Parcel.() -> Unit,
    ): Boolean {
        val b = service ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.args()
            b.transact(code, data, reply, 0) &&
                run {
                    reply.readException()
                    true
                }
        } catch (e: Exception) {
            Log.w(TAG, "IBluetooth $code: ${e.message}")
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private companion object {
        const val TAG = "LibreHU-Dialer"
        const val PACKAGE = "com.jancar.btservice"
        const val CLASS = "com.jancar.btservice.bluetooth.BluetoothService"
        const val ACTION = "com.jancar.btservice.action.bluetooth"
        const val DESCRIPTOR = "com.jancar.btservice.bluetooth.IBluetooth"
        const val CALLBACK = "com.jancar.btservice.bluetooth.IBluetoothCallback"
        const val EXEC_CALLBACK = "com.jancar.btservice.bluetooth.IBluetoothExecCallback"

        const val TX_CALL_PHONE = 23
        const val TX_HANG_PHONE = 25
        const val TX_REJECT_PHONE = 26
        const val TX_LISTEN_PHONE = 27
        const val TX_TRANSFER_CALL = 29
        const val TX_REQUEST_DTMF = 31
        const val TX_MUTE_MIC = 32
        const val TX_REQUEST_LISTENER = 38
        const val TX_UNREQUEST_LISTENER = 39
        const val TX_GET_CURRENT_DEVICE_NAME = 52
        const val TX_THREE_PARTY = 59

        /** IVIBluetooth.ThreePartyCallCtrl: 2 answer the second call (holds the first), 4 swap. */
        const val THREE_ANSWER_HOLD = 2
        const val THREE_SWAP = 4
    }
}
