package org.librehu.dialer.phone

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
            ) = refresh()
        }

    fun start() {
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
            _state.value =
                HfpState(
                    connected = devices.isNotEmpty(),
                    deviceName = devices.firstOrNull()?.let { runCatching { it.name }.getOrNull() }.orEmpty(),
                    available = p != null,
                )
        }
    }

    companion object {
        /** BluetoothProfile.HEADSET_CLIENT (hidden constant). */
        const val HEADSET_CLIENT = 16
        const val ACTION_HFP_CLIENT_CONNECTION = "android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED"
    }
}
