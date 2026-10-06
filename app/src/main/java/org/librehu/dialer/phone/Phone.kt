package org.librehu.dialer.phone

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** The backend of this build, shared by the activity, the call bubble and the widgets. */
object Phone {
    @Volatile
    private var instance: PhoneBackend? = null

    /** Network indicators of the phone (signal, operator, battery), from Android's HFP client. */
    fun network(context: Context): StateFlow<HfpState> = (get(context) as CheckedBackend).hfpState

    fun get(context: Context): PhoneBackend =
        instance ?: synchronized(this) {
            instance ?: CheckedBackend(PhoneBackends.create(context.applicationContext), HfpWatcher(context.applicationContext)).also {
                instance = it
                it.start()
            }
        }
}

/**
 * [backend] whose link is cross-checked with Android's HFP client: connected when either says so, with a message when
 * Android sees the phone and the backend does not (backend not following the Bluetooth stack).
 */
class CheckedBackend(
    private val backend: PhoneBackend,
    private val hfp: HfpWatcher,
) : PhoneBackend by backend {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _link = MutableStateFlow(backend.link.value)
    override val link: StateFlow<PhoneLink> = _link.asStateFlow()

    /** Android's own view, for the settings. */
    val hfpState: StateFlow<HfpState> get() = hfp.state

    override fun start() {
        backend.start()
        hfp.start()
        scope.launch {
            combine(backend.link, hfp.state) { l, h ->
                when {
                    l.connected || !h.connected -> {
                        l
                    }

                    else -> {
                        l.copy(
                            connected = true,
                            deviceName = l.deviceName.ifBlank { h.deviceName },
                            message = l.message.ifBlank { MISMATCH },
                        )
                    }
                }
            }.collect { _link.value = it }
        }
    }

    override fun stop() {
        hfp.stop()
        backend.stop()
    }

    companion object {
        /** Replaced by a translated text in the UI. */
        const val MISMATCH = "hfp-mismatch"
    }
}
