package org.librehu.dialer.phone

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Phone connected for calls according to Android's HFP client profile itself, whatever the backend says. */
data class HfpState(
    val connected: Boolean = false,
    val deviceName: String = "",
    /** The profile proxy could be obtained (Bluetooth present, HFP client enabled in this ROM). */
    val available: Boolean = false,
    /** Indicators the phone sends (HFP AG events): 0..5, -1 unknown. */
    val signal: Int = -1,
    val battery: Int = -1,
    val operator: String = "",
    /** Network service: false = no service, null unknown. */
    val service: Boolean? = null,
    val roaming: Boolean = false,
)

/**
 * Android's view of the hands-free link (BluetoothProfile 16, HEADSET_CLIENT, hidden constant): the backends of
 * every branch are cross-checked with it, so that "no phone connected" never hides a connected phone.
 */
class HfpWatcher(
    private val context: Context,
) {
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(HfpState())
    val state: StateFlow<HfpState> = _state.asStateFlow()
    private var proxy: BluetoothProfile? = null
    private var started = false

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                c: Context,
                intent: Intent,
            ) {
                if (intent.action == ACTION_AG_EVENT) intent.extras?.let(::applyEvents) else refresh()
            }
        }

    fun start() {
        if (started) return
        started = true
        val filter =
            IntentFilter().apply {
                addAction(ACTION_HFP_CLIENT_CONNECTION)
                addAction(ACTION_AG_EVENT)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        try {
            BluetoothAdapter.getDefaultAdapter()?.getProfileProxy(
                context,
                object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(
                        profile: Int,
                        p: BluetoothProfile,
                    ) {
                        proxy = p
                        refresh()
                    }

                    override fun onServiceDisconnected(profile: Int) {
                        proxy = null
                        refresh()
                    }
                },
                HEADSET_CLIENT,
            )
        } catch (e: SecurityException) {
            Log.w("LibreHU-Dialer", "HFP client: ${e.message}")
        }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { context.unregisterReceiver(receiver) }
        proxy?.let { runCatching { BluetoothAdapter.getDefaultAdapter()?.closeProfileProxy(HEADSET_CLIENT, it) } }
        proxy = null
    }

    @SuppressLint("MissingPermission")
    private fun refresh() {
        main.post {
            val p = proxy
            val devices: List<BluetoothDevice> = runCatching { p?.connectedDevices.orEmpty() }.getOrDefault(emptyList())
            val device = devices.firstOrNull()
            val old = _state.value
            _state.value =
                (if (device == null) HfpState() else old).copy(
                    connected = device != null,
                    deviceName = device?.let { runCatching { it.name }.getOrNull() }.orEmpty(),
                    available = p != null,
                )
            if (device != null) currentEvents(device)?.let(::applyEvents)
        }
    }

    /** Current indicators, hidden `BluetoothHeadsetClient.getCurrentAgEvents(device)`. */
    private fun currentEvents(device: BluetoothDevice): Bundle? =
        runCatching {
            proxy?.javaClass?.getMethod("getCurrentAgEvents", BluetoothDevice::class.java)?.invoke(proxy, device) as? Bundle
        }.getOrNull()

    private fun applyEvents(b: Bundle) {
        main.post {
            var s = _state.value
            if (b.containsKey(EXTRA_SIGNAL)) s = s.copy(signal = b.getInt(EXTRA_SIGNAL, -1))
            if (b.containsKey(EXTRA_BATTERY)) s = s.copy(battery = b.getInt(EXTRA_BATTERY, -1))
            if (b.containsKey(EXTRA_OPERATOR)) s = s.copy(operator = b.getString(EXTRA_OPERATOR).orEmpty())
            if (b.containsKey(EXTRA_SERVICE)) s = s.copy(service = b.getInt(EXTRA_SERVICE, 1) != 0)
            if (b.containsKey(EXTRA_ROAMING)) s = s.copy(roaming = b.getInt(EXTRA_ROAMING, 0) == 1)
            _state.value = s
        }
    }

    companion object {
        /** BluetoothProfile.HEADSET_CLIENT (hidden constant). */
        const val HEADSET_CLIENT = 16
        const val ACTION_HFP_CLIENT_CONNECTION = "android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED"
        const val ACTION_AG_EVENT = "android.bluetooth.headsetclient.profile.action.AG_EVENT"
        private const val EXTRA_SIGNAL = "android.bluetooth.headsetclient.extra.NETWORK_SIGNAL_STRENGTH"
        private const val EXTRA_BATTERY = "android.bluetooth.headsetclient.extra.BATTERY_LEVEL"
        private const val EXTRA_OPERATOR = "android.bluetooth.headsetclient.extra.OPERATOR_NAME"
        private const val EXTRA_SERVICE = "android.bluetooth.headsetclient.extra.NETWORK_STATUS"
        private const val EXTRA_ROAMING = "android.bluetooth.headsetclient.extra.NETWORK_ROAMING"
    }
}
