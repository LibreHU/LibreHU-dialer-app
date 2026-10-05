package org.librehu.dialer.phone

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import org.librehu.dialer.MainActivity

/**
 * Bound by Telecom while this app is the default phone app and a call exists: feeds [TelecomCalls] and brings the
 * call screen up for incoming and outgoing calls.
 */
class DialerInCallService : InCallService() {
    override fun onCreate() {
        super.onCreate()
        TelecomCalls.service = this
    }

    override fun onDestroy() {
        TelecomCalls.clear()
        TelecomCalls.service = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        TelecomCalls.add(call)
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SHOW_CALL, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    override fun onCallRemoved(call: Call) = TelecomCalls.remove(call)

    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        TelecomCalls.muted = audioState.isMuted
    }
}
