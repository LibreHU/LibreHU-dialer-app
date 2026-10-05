package org.librehu.dialer.phone

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.librehu.dialer.R
import org.librehu.service.ILibreHuService
import org.librehu.service.bt.BtCallInfo
import org.librehu.service.bt.BtDeviceInfo
import org.librehu.service.bt.BtMediaInfo
import org.librehu.service.bt.BtStatus
import org.librehu.service.bt.ILibreHuBluetooth
import org.librehu.service.bt.ILibreHuBluetoothCallback

/**
 * LibreHU-service (https://github.com/LibreHU/LibreHU-service), Bluetooth API 3 (`ILibreHuBluetooth`): HFP client
 * calls, phone status, call audio in the car or on the phone. Bind permission: org.librehu.permission.HEADUNIT.
 */
class LibreHuBackend(
    context: Context,
) : PhoneBackend {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val _link = MutableStateFlow(PhoneLink(message = app.getString(R.string.librehu_connecting)))
    override val link: StateFlow<PhoneLink> = _link.asStateFlow()
    private val _calls = MutableStateFlow<List<PhoneCall>>(emptyList())
    override val calls: StateFlow<List<PhoneCall>> = _calls.asStateFlow()
    override val features = PhoneFeatures(hold = false, swap = true, audioRoute = true)

    @Volatile
    private var bt: ILibreHuBluetooth? = null
    private var bound = false

    private val callback =
        object : ILibreHuBluetoothCallback.Stub() {
            override fun onStatusChanged(status: BtStatus?) {
                if (status != null) main.post { publish(status) }
            }

            override fun onCallsChanged(calls: MutableList<BtCallInfo>?) {
                val list = calls.orEmpty().toList()
                main.post { publish(list) }
            }

            override fun onMediaChanged(media: BtMediaInfo?) = Unit

            override fun onDevicesChanged() = Unit

            override fun onDeviceFound(device: BtDeviceInfo?) = Unit

            override fun onPhonebookChanged() = Unit

            override fun onPairingRequest(
                address: String?,
                name: String?,
                variant: Int,
                passkey: Int,
            ) = Unit
        }

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                binder: IBinder?,
            ) {
                val service = binder?.let { ILibreHuService.Stub.asInterface(it) } ?: return
                try {
                    if (service.apiVersion < API_BLUETOOTH) {
                        _link.value = PhoneLink(message = app.getString(R.string.librehu_too_old))
                        return
                    }
                    val b = service.bluetooth ?: return
                    bt = b
                    b.registerCallback(callback)
                    publish(b.status)
                    publish(b.calls.orEmpty())
                } catch (e: RemoteException) {
                    Log.w(TAG, "LibreHU-service: ${e.message}")
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                bt = null
                _link.value = PhoneLink(message = app.getString(R.string.librehu_lost))
                _calls.value = emptyList()
            }
        }

    override fun start() {
        if (bound) return
        bound =
            try {
                app.bindService(Intent(ACTION_BIND).setPackage(PACKAGE), connection, Context.BIND_AUTO_CREATE)
            } catch (e: SecurityException) {
                Log.w(TAG, "LibreHU-service: ${e.message}")
                false
            }
        if (!bound) _link.value = PhoneLink(message = app.getString(R.string.librehu_missing))
    }

    override fun stop() {
        runCatching { bt?.unregisterCallback(callback) }
        if (bound) runCatching { app.unbindService(connection) }
        bound = false
        bt = null
    }

    private fun publish(s: BtStatus) {
        _link.value =
            PhoneLink(
                available = true,
                connected = s.hfpState == CONNECTED,
                deviceName = s.deviceName,
                message = if (s.enabled) "" else app.getString(R.string.librehu_bt_off),
                audioInCar = s.audioInCar,
                micMuted = s.micMuted,
                battery = s.battery,
                signal = s.signal,
            )
    }

    private fun publish(list: List<BtCallInfo>) {
        _calls.value =
            list.map { c ->
                PhoneCall(
                    id = "l${c.id}",
                    number = c.number,
                    name = c.name,
                    status =
                        when (c.state) {
                            0 -> CallStatus.ACTIVE
                            1, 6 -> CallStatus.HELD
                            2 -> CallStatus.DIALING
                            3 -> CallStatus.ALERTING
                            4 -> CallStatus.RINGING
                            5 -> CallStatus.WAITING
                            else -> CallStatus.ENDED
                        },
                    activeSince = c.activeSince,
                    conference = c.multiParty,
                )
            }
    }

    private fun run(action: (ILibreHuBluetooth) -> Unit): Boolean {
        val b = bt ?: return false
        return try {
            action(b)
            true
        } catch (e: RemoteException) {
            Log.w(TAG, "LibreHU-service: ${e.message}")
            false
        }
    }

    override fun dial(number: String) = run { it.dial(number) }

    override fun answer(call: PhoneCall) = run { it.answer() }

    override fun reject(call: PhoneCall) = run { it.reject() }

    override fun hangup(call: PhoneCall) = run { it.hangup() }

    override fun swap() = run { it.swapCalls() }

    override fun dtmf(key: Char) = run { it.sendDtmf(key) }

    override fun setMuted(muted: Boolean) = run { it.setMicMuted(muted) }

    override fun setAudioInCar(inCar: Boolean) = run { it.setAudioInCar(inCar) }

    private companion object {
        const val TAG = "LibreHU-Dialer"
        const val ACTION_BIND = "org.librehu.service.BIND"
        const val PACKAGE = "org.librehu.service"
        const val API_BLUETOOTH = 3
        const val CONNECTED = 2
    }
}
