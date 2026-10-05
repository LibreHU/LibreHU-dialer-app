package org.librehu.dialer.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.librehu.dialer.CallBubble
import org.librehu.dialer.DialerPreferences
import org.librehu.dialer.MainActivity
import org.librehu.dialer.R

/**
 * For the backends that are not Telecom (`ivi`, `librehu-service`): keeps the backend listening and brings the call
 * screen up when a call starts. With Telecom, [DialerInCallService] does that and this service never runs.
 * Foreground (low importance notification): Android would otherwise stop the listener in the background.
 */
class CallWatcherService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val seen = HashSet<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, notification())
        val phone = Phone.get(this)
        scope.launch {
            phone.calls.collect { calls ->
                val live = calls.filter { it.status.live }
                val fresh = live.filter { it.id !in seen }
                seen.retainAll(live.map { it.id }.toSet())
                fresh.forEach { seen += it.id }
                if (MainActivity.visible) return@collect
                if (fresh.isNotEmpty() && DialerPreferences.openOnCall(this@CallWatcherService)) {
                    startActivity(
                        Intent(this@CallWatcherService, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_SHOW_CALL, true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    )
                } else if (live.isNotEmpty()) {
                    CallBubble.show(this@CallWatcherService)
                }
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ) = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.watcher_channel), NotificationManager.IMPORTANCE_MIN))
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return Notification
            .Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_dialer)
            .setContentTitle(getString(R.string.watcher_title))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "watcher"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            if (PhoneBackends.NEEDS_DEFAULT_DIALER) return
            runCatching { context.startForegroundService(Intent(context, CallWatcherService::class.java)) }
        }
    }
}

/** Starts the watcher after boot (non Telecom backends). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) CallWatcherService.start(context)
    }
}
