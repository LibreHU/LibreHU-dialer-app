package org.librehu.dialer.phone

import android.Manifest
import android.content.Context

/** Backend of the `main` branch: Android Telecom. */
object PhoneBackends {
    fun create(context: Context): PhoneBackend = TelecomBackend(context)

    /** This build needs to be Android's default phone app to see calls (Telecom InCallService). */
    const val NEEDS_DEFAULT_DIALER = true

    /** Runtime permission needed to place calls, null when none. */
    val DIAL_PERMISSION: String? = Manifest.permission.CALL_PHONE

    /** Shown in Settings → About. */
    const val NAME = "Android Telecom"
}
