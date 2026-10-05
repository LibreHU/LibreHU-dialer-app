package org.librehu.dialer.phone

import android.content.Context

/** Backend of the `ivi` branch: Jancar ivi-btservice (stock UJC201 ROM). */
object PhoneBackends {
    fun create(context: Context): PhoneBackend = JancarBackend(context)

    /** btservice drives the calls: this app does not need to be the default phone app. */
    const val NEEDS_DEFAULT_DIALER = false

    /** btservice places the calls itself, no Android permission needed. */
    val DIAL_PERMISSION: String? = null

    /** Shown in Settings → About. */
    const val NAME = "Jancar ivi-btservice"
}
