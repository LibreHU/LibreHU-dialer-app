package org.librehu.dialer.phone

import android.content.Context

/** The backend of this build, shared by the activity, the call bubble and the widgets. */
object Phone {
    @Volatile
    private var instance: PhoneBackend? = null

    fun get(context: Context): PhoneBackend =
        instance ?: synchronized(this) {
            instance ?: PhoneBackends.create(context.applicationContext).also {
                instance = it
                it.start()
            }
        }
}
