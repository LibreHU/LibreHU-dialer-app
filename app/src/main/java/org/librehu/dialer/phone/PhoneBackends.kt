package org.librehu.dialer.phone

import android.content.Context

/** Backend of the `librehu-service` branch: LibreHU-service Bluetooth API. */
object PhoneBackends {
    fun create(context: Context): PhoneBackend = LibreHuBackend(context)

    /** LibreHU-service drives the calls: this app does not need to be the default phone app. */
    const val NEEDS_DEFAULT_DIALER = false

    /** LibreHU-service places the calls itself, no Android permission needed. */
    val DIAL_PERMISSION: String? = null

    /** Shown in Settings → About. */
    const val NAME = "LibreHU-service"
}
