package org.librehu.dialer.backend.jancar

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Minimal Binder client for the stock UJC201 Jancar Bluetooth service.
 *
 * Transaction numbers and callback descriptors were read from the original Jancar APKs.
 * This intentionally avoids pretending that Android's public Bluetooth APIs control the
 * phone paired to the head unit. Unknown status integers are exposed raw until mapped.
 */
internal class JancarBluetoothClient(context: Context) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(JancarState())
    val state: StateFlow<JancarState> = _state.asStateFlow()

    private var bound = false
    private var service: IBinder? = null
    private var listenerRegistered = false

    private val callback = object : Binder() {
        init { attachInterface(this, CALLBACK_DESCRIPTOR) }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(CALLBACK_DESCRIPTOR)
                return true
            }
            data.enforceInterface(CALLBACK_DESCRIPTOR)
            when (code) {
                1 -> {
                    val status = data.readInt()
                    val first = data.readString().orEmpty()
                    val second = data.readString().orEmpty()
                    _state.value = _state.value.copy(
                        connectionEvent = "Jancar connection event $status",
                        connectionDetails = listOf(first, second).filter { it.isNotBlank() }.joinToString(" · ")
                    )
                }
                2 -> {
                    val status = data.readInt()
                    val first = data.readString().orEmpty()
                    val second = data.readString().orEmpty()
                    _state.value = _state.value.copy(
                        callEvent = "Jancar call event $status",
                        callDetails = listOf(first, second).filter { it.isNotBlank() }.joinToString(" · ")
                    )
                }
                3 -> data.readInt() // Voice routing event; mapping not confirmed yet.
                4 -> {
                    val status = data.readInt()
                    val connected = data.readInt() != 0
                    _state.value = _state.value.copy(a2dpEvent = "A2DP event $status · connected=$connected")
                }
                5 -> {
                    val artist = data.readString().orEmpty()
                    val title = data.readString().orEmpty()
                    val album = data.readString().orEmpty()
                    data.readLong()
                    _state.value = _state.value.copy(musicInfo = listOf(title, artist, album).filter { it.isNotBlank() }.joinToString(" — "))
                }
                6 -> data.readInt() // Battery event
                7 -> data.readInt() // Signal event
                8 -> {
                    val powered = data.readInt() != 0
                    _state.value = _state.value.copy(bluetoothPowered = powered)
                }
                else -> return super.onTransact(code, data, reply, flags)
            }
            if (reply != null) reply.writeNoException()
            return true
        }
    }

    private val currentNameCallback = object : Binder() {
        init { attachInterface(this, EXEC_CALLBACK_DESCRIPTOR) }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(EXEC_CALLBACK_DESCRIPTOR)
                return true
            }
            data.enforceInterface(EXEC_CALLBACK_DESCRIPTOR)
            when (code) {
                1 -> {
                    val name = data.readString().orEmpty()
                    _state.value = _state.value.copy(currentPhoneName = name.ifBlank { null })
                }
                2 -> {
                    val errorCode = data.readInt()
                    _state.value = _state.value.copy(lastError = "Could not read current phone name ($errorCode)")
                }
                else -> return super.onTransact(code, data, reply, flags)
            }
            if (reply != null) reply.writeNoException()
            return true
        }
    }

    private val execCallback = object : Binder() {
        init { attachInterface(this, EXEC_CALLBACK_DESCRIPTOR) }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(EXEC_CALLBACK_DESCRIPTOR)
                return true
            }
            data.enforceInterface(EXEC_CALLBACK_DESCRIPTOR)
            when (code) {
                1 -> _state.value = _state.value.copy(lastCommandResult = data.readString().orEmpty(), lastError = null)
                2 -> {
                    val errorCode = data.readInt()
                    _state.value = _state.value.copy(lastError = "Jancar command failed ($errorCode)", lastCommandResult = null)
                }
                else -> return super.onTransact(code, data, reply, flags)
            }
            if (reply != null) reply.writeNoException()
            return true
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = binder
            _state.value = _state.value.copy(
                serviceAvailable = true,
                serviceMessage = "Connected to Jancar Bluetooth service",
                lastError = null
            )
            runCatching {
                _state.value = _state.value.copy(bluetoothPowered = callBoolean(TRANSACTION_IS_POWER_ON))
                transactVoid(TRANSACTION_REQUEST_LISTENER) { writeStrongBinder(callback) }
                listenerRegistered = true
                transactVoid(TRANSACTION_GET_CURRENT_DEVICE_NAME) { writeStrongBinder(currentNameCallback) }
            }.onFailure {
                _state.value = _state.value.copy(lastError = "Binder initialization failed: ${it.message}")
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            listenerRegistered = false
            _state.value = _state.value.copy(
                serviceAvailable = false,
                serviceMessage = "Jancar Bluetooth service disconnected",
                bluetoothPowered = null
            )
        }

        override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
        override fun onNullBinding(name: ComponentName) {
            _state.value = _state.value.copy(
                serviceAvailable = false,
                serviceMessage = "Jancar service returned a null binding",
                lastError = "The exported Jancar service did not provide a Binder"
            )
        }
    }

    fun bind() {
        if (bound) return
        val intent = Intent(SERVICE_ACTION).setClassName(SERVICE_PACKAGE, SERVICE_CLASS)
        bound = runCatching { appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        if (!bound) {
            _state.value = _state.value.copy(
                serviceAvailable = false,
                serviceMessage = "Jancar Bluetooth service unavailable",
                lastError = "Could not bind $SERVICE_CLASS. Check that the stock Jancar service is installed."
            )
        } else {
            _state.value = _state.value.copy(serviceMessage = "Binding to Jancar Bluetooth service…", lastError = null)
        }
    }

    fun unbind() {
        if (!bound) return
        if (listenerRegistered) runCatching { transactVoid(TRANSACTION_UNREQUEST_LISTENER) { writeStrongBinder(callback) } }
        listenerRegistered = false
        runCatching { appContext.unbindService(connection) }
        bound = false
        service = null
        _state.value = _state.value.copy(serviceAvailable = false, serviceMessage = "Jancar service disconnected")
    }

    fun callPhone(number: String): Boolean {
        _state.value = _state.value.copy(callEvent = null, callDetails = null, lastError = null)
        return runCommand("Call request sent") {
            writeString(number)
            writeStrongBinder(execCallback)
        }.let { it && _state.value.lastError == null }
    }

    fun hangPhone(): Boolean = runCommand("Hang-up request sent", TRANSACTION_HANG_PHONE)
    fun answerPhone(): Boolean = runCommand("Answer request sent", TRANSACTION_LISTEN_PHONE)
    fun rejectPhone(): Boolean = runCommand("Reject request sent", TRANSACTION_REJECT_PHONE)
    fun muteMic(muted: Boolean): Boolean = runCommand("Microphone mute request sent", TRANSACTION_MUTE_MIC) {
        writeInt(if (muted) 1 else 0)
        writeStrongBinder(execCallback)
    }

    fun sendDtmf(digit: Int): Boolean = runCommand("DTMF request sent", TRANSACTION_REQUEST_DTMF) {
        writeInt(digit)
        writeStrongBinder(execCallback)
    }

    private fun runCommand(message: String, code: Int, args: Parcel.() -> Unit = { writeStrongBinder(execCallback) }): Boolean {
        val binder = service
        if (binder == null || !binder.isBinderAlive) {
            _state.value = _state.value.copy(lastError = "Jancar service is not connected")
            return false
        }
        return runCatching {
            transactVoid(code, args)
            _state.value = _state.value.copy(lastCommandResult = message, lastError = null)
            true
        }.getOrElse {
            _state.value = _state.value.copy(lastError = "Binder command failed: ${it.message}")
            false
        }
    }

    private fun runCommand(message: String, args: Parcel.() -> Unit): Boolean =
        runCommand(message, TRANSACTION_CALL_PHONE, args)

    private fun callBoolean(code: Int): Boolean {
        val binder = service ?: throw RemoteException("Service not bound")
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(SERVICE_DESCRIPTOR)
            if (!binder.transact(code, data, reply, 0)) throw RemoteException("Binder transaction $code was not handled")
            reply.readException()
            return reply.readInt() != 0
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun transactVoid(code: Int, args: Parcel.() -> Unit = {}) {
        val binder = service ?: throw RemoteException("Service not bound")
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(SERVICE_DESCRIPTOR)
            data.args()
            if (!binder.transact(code, data, reply, 0)) throw RemoteException("Binder transaction $code was not handled")
            reply.readException()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    companion object {
        private const val SERVICE_PACKAGE = "com.jancar.btservice"
        private const val SERVICE_CLASS = "com.jancar.btservice.bluetooth.BluetoothService"
        private const val SERVICE_ACTION = "com.jancar.btservice.action.bluetooth"
        private const val SERVICE_DESCRIPTOR = "com.jancar.btservice.bluetooth.IBluetooth"
        private const val CALLBACK_DESCRIPTOR = "com.jancar.btservice.bluetooth.IBluetoothCallback"
        private const val EXEC_CALLBACK_DESCRIPTOR = "com.jancar.btservice.bluetooth.IBluetoothExecCallback"

        // Confirmed from the TRANSACTION_* constants in the stock IBluetooth.Stub.
        private const val TRANSACTION_GET_CURRENT_DEVICE_NAME = 52
        private const val TRANSACTION_CALL_PHONE = 23
        private const val TRANSACTION_HANG_PHONE = 25
        private const val TRANSACTION_REJECT_PHONE = 26
        private const val TRANSACTION_LISTEN_PHONE = 27
        private const val TRANSACTION_REQUEST_DTMF = 31
        private const val TRANSACTION_MUTE_MIC = 32
        private const val TRANSACTION_REQUEST_LISTENER = 38
        private const val TRANSACTION_UNREQUEST_LISTENER = 39
        private const val TRANSACTION_IS_POWER_ON = 70
    }
}

internal data class JancarState(
    val serviceAvailable: Boolean = false,
    val serviceMessage: String = "Jancar Bluetooth service not connected",
    val bluetoothPowered: Boolean? = null,
    val currentPhoneName: String? = null,
    val connectionEvent: String? = null,
    val connectionDetails: String? = null,
    val callEvent: String? = null,
    val callDetails: String? = null,
    val a2dpEvent: String? = null,
    val musicInfo: String? = null,
    val lastCommandResult: String? = null,
    val lastError: String? = null
)
