package org.librehu.dialer.phone

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.librehu.dialer.R

/**
 * Android Telecom. On a head unit, the calls of the phone connected in Bluetooth are Telecom calls created by
 * Android's HFP client connection service (com.android.bluetooth); the default phone app receives them through its
 * [DialerInCallService]. Outgoing calls go through [TelecomManager.placeCall] (CALL_PHONE).
 *
 * The phone connection itself is read from the HFP client profile (BluetoothProfile 16, HEADSET_CLIENT).
 */
class TelecomBackend(
    private val context: Context,
) : PhoneBackend {
    private val telecom = context.getSystemService(TelecomManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val _link = MutableStateFlow(PhoneLink())
    override val link: StateFlow<PhoneLink> = _link.asStateFlow()
    override val calls: StateFlow<List<PhoneCall>> get() = TelecomCalls.calls
    override val features = PhoneFeatures(hold = true, swap = true, audioRoute = false)

    private var hfp: BluetoothProfile? = null
    private var started = false

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                c: Context,
                intent: Intent,
            ) = refreshLink()
        }

    override fun start() {
        if (started) return
        started = true
        val filter =
            IntentFilter().apply {
                addAction(ACTION_HFP_CLIENT_CONNECTION)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        try {
            BluetoothAdapter.getDefaultAdapter()?.getProfileProxy(
                context,
                object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(
                        profile: Int,
                        proxy: BluetoothProfile,
                    ) {
                        hfp = proxy
                        refreshLink()
                    }

                    override fun onServiceDisconnected(profile: Int) {
                        hfp = null
                        refreshLink()
                    }
                },
                HEADSET_CLIENT,
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "HFP client: ${e.message}")
        }
        TelecomCalls.onChange = ::refreshLink
        refreshLink()
    }

    override fun stop() {
        if (!started) return
        started = false
        runCatching { context.unregisterReceiver(receiver) }
        hfp?.let { runCatching { BluetoothAdapter.getDefaultAdapter()?.closeProfileProxy(HEADSET_CLIENT, it) } }
        hfp = null
    }

    @SuppressLint("MissingPermission")
    private fun refreshLink() {
        main.post {
            val devices: List<BluetoothDevice> = runCatching { hfp?.connectedDevices.orEmpty() }.getOrDefault(emptyList())
            val default = isDefaultDialer(context)
            _link.value =
                PhoneLink(
                    available = telecom != null,
                    connected = devices.isNotEmpty(),
                    deviceName = devices.firstOrNull()?.let { runCatching { it.name }.getOrNull() }.orEmpty(),
                    message = if (default) "" else context.getString(R.string.phone_not_default),
                    micMuted = TelecomCalls.muted,
                )
        }
    }

    @SuppressLint("MissingPermission")
    override fun dial(number: String): Boolean {
        val tm = telecom ?: return false
        return try {
            tm.placeCall(Uri.fromParts("tel", number, null), Bundle())
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "placeCall: ${e.message}")
            false
        }
    }

    override fun answer(call: PhoneCall) = onCall(call) { it.answer(VideoProfile.STATE_AUDIO_ONLY) }

    override fun reject(call: PhoneCall) = onCall(call) { it.reject(false, null) }

    override fun hangup(call: PhoneCall) = onCall(call) { it.disconnect() }

    override fun hold(
        call: PhoneCall,
        on: Boolean,
    ) = onCall(call) { if (on) it.hold() else it.unhold() }

    /** Takes the held call back; Telecom puts the active one on hold. */
    override fun swap(): Boolean {
        val held = TelecomCalls.raw().firstOrNull { it.state == Call.STATE_HOLDING } ?: return false
        held.unhold()
        return true
    }

    override fun dtmf(key: Char): Boolean {
        val c = TelecomCalls.raw().firstOrNull { it.state == Call.STATE_ACTIVE } ?: return false
        c.playDtmfTone(key)
        main.postDelayed({ c.stopDtmfTone() }, DTMF_MS)
        return true
    }

    override fun setMuted(muted: Boolean): Boolean {
        val service = TelecomCalls.service ?: return false
        service.setMuted(muted)
        return true
    }

    private fun onCall(
        call: PhoneCall,
        action: (Call) -> Unit,
    ): Boolean {
        val c = TelecomCalls.find(call.id) ?: return false
        action(c)
        return true
    }

    companion object {
        private const val TAG = "LibreHU-Dialer"
        private const val DTMF_MS = 200L

        /** BluetoothProfile.HEADSET_CLIENT (hidden constant). */
        private const val HEADSET_CLIENT = 16
        private const val ACTION_HFP_CLIENT_CONNECTION = "android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED"

        fun isDefaultDialer(context: Context): Boolean =
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage == context.packageName
    }
}
