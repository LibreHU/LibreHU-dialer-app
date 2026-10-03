package org.librehu.dialer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.librehu.dialer.backend.jancar.JancarBluetoothClient

/**
 * Movable call controls rendered above other apps. Requires the user-granted overlay permission.
 * The Jancar client is owned here so call controls keep working when MainActivity is stopped.
 */
class CallOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var btClient: JancarBluetoothClient
    private lateinit var audioManager: AudioManager
    private var root: LinearLayout? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var callName = "Call"
    private var callNumber = ""
    private var muted = false
    private var keypadVisible = false
    private var carAudioSelected = true
    private var statusText: TextView? = null
    private var muteButton: Button? = null
    private var keypadPanel: GridLayout? = null
    private val refreshStatus = object : Runnable {
        override fun run() {
            val state = btClient.state.value
            val message = state.lastError ?: state.lastCommandResult ?: state.callEvent ?: "Call controls active"
            statusText?.text = message
            statusText?.setTextColor(if (state.lastError != null) 0xFFFF8A80.toInt() else 0xFFB8C4D8.toInt())
            handler.postDelayed(this, 700)
        }
    }
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        btClient = JancarBluetoothClient(this)
        btClient.bind()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            runCatching { btClient.hangPhone() }
            stopSelf()
            return START_NOT_STICKY
        }
        callName = intent?.getStringExtra(EXTRA_NAME)?.takeIf { it.isNotBlank() } ?: callName
        callNumber = intent?.getStringExtra(EXTRA_NUMBER)?.takeIf { it.isNotBlank() } ?: callNumber
        startInForeground()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (root == null) showOverlay() else updateHeader()
        handler.removeCallbacks(refreshStatus)
        handler.post(refreshStatus)
        return START_STICKY
    }

    private fun startInForeground() {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dialer)
            .setContentTitle("LibreHU call controls")
            .setContentText(if (callName.isBlank()) callNumber else "$callName · $callNumber")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun showOverlay() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(0xF21B1D22.toInt())
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), 0xFF4B5565.toInt())
            }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(4), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setOnTouchListener(makeDragListener())
        }
        header.addView(title)
        header.addView(makeButton("▁") { toggleCollapsed(panel) })
        header.addView(makeButton("×") { stopSelf() })
        panel.addView(header, LinearLayout.LayoutParams(-1, -2))

        statusText = TextView(this).apply {
            text = "Connecting to Jancar…"
            textSize = 11f
            setTextColor(0xFFB8C4D8.toInt())
            setPadding(dp(4), dp(2), dp(4), dp(8))
        }
        panel.addView(statusText)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        muteButton = makeButton("Mute") { toggleMute() }
        actions.addView(muteButton, weightedButton())
        actions.addView(makeButton("Keypad") { toggleKeypad() }, weightedButton())
        actions.addView(makeButton("Autoradio") { selectAudioRoute(true) }, weightedButton())
        actions.addView(makeButton("HP local") { selectAudioRoute(false) }, weightedButton())
        actions.addView(makeButton("Raccrocher") { endCall() }, weightedButton())
        panel.addView(actions, LinearLayout.LayoutParams(-1, -2))

        keypadPanel = GridLayout(this).apply {
            columnCount = 3
            rowCount = 4
            visibility = View.GONE
            setPadding(0, dp(8), 0, 0)
        }
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#").forEach { digit ->
            keypadPanel?.addView(makeButton(digit) {
                val numeric = digit.toIntOrNull()
                if (numeric != null) {
                    val ok = btClient.sendDtmf(numeric)
                    statusText?.text = if (ok) "DTMF $digit envoyé" else btClient.state.value.lastError ?: "Échec DTMF"
                } else {
                    statusText?.text = "DTMF $digit non pris en charge par l'API actuellement validée"
                }
            }, GridLayout.LayoutParams().apply {
                width = dp(76)
                height = dp(42)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
        panel.addView(keypadPanel)

        val params = WindowManager.LayoutParams(
            dp(490),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(30)
            y = dp(70)
        }
        root = panel
        windowParams = params
        updateHeader(title)
        runCatching { windowManager.addView(panel, params) }.onFailure {
            root = null
            windowParams = null
            statusText?.text = "Impossible d'afficher l'overlay : ${it.message}"
        }
    }

    private fun updateHeader(target: TextView? = null) {
        val title = target ?: (root?.getChildAt(0) as? LinearLayout)?.getChildAt(0) as? TextView
        title?.text = if (callName.isBlank()) callNumber else "$callName\n$callNumber"
    }

    private fun makeDragListener() = View.OnTouchListener { _, event ->
        val params = windowParams ?: return@OnTouchListener false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = params.x
                dragStartY = params.y
                touchStartX = event.rawX
                touchStartY = event.rawY
                true
            }
            MotionEvent.ACTION_MOVE -> {
                params.x = dragStartX + (event.rawX - touchStartX).toInt()
                params.y = dragStartY + (event.rawY - touchStartY).toInt()
                runCatching { root?.let { windowManager.updateViewLayout(it, params) } }
                true
            }
            else -> true
        }
    }

    private var dragStartX = 0
    private var dragStartY = 0
    private var touchStartX = 0f
    private var touchStartY = 0f

    private fun toggleMute() {
        val next = !muted
        if (btClient.muteMic(next)) {
            muted = next
            muteButton?.text = if (muted) "Micro ON" else "Mute"
            statusText?.text = if (muted) "Demande de coupure micro envoyée" else "Demande de réactivation micro envoyée"
        } else {
            statusText?.text = btClient.state.value.lastError ?: "Commande mute refusée"
        }
    }

    private fun toggleKeypad() {
        keypadVisible = !keypadVisible
        keypadPanel?.visibility = if (keypadVisible) View.VISIBLE else View.GONE
    }

    private fun toggleCollapsed(panel: LinearLayout) {
        val collapsed = keypadPanel?.visibility == View.GONE && panel.childCount == 3
        if (collapsed) {
            keypadPanel?.visibility = if (keypadVisible) View.VISIBLE else View.GONE
            panel.getChildAt(1).visibility = View.VISIBLE
            panel.getChildAt(2).visibility = View.VISIBLE
            panel.getChildAt(3).visibility = View.VISIBLE
            panel.getChildAt(4).visibility = View.VISIBLE
        } else {
            keypadPanel?.visibility = View.GONE
            panel.getChildAt(1).visibility = View.GONE
            panel.getChildAt(2).visibility = View.GONE
            panel.getChildAt(3).visibility = View.GONE
            panel.getChildAt(4).visibility = View.GONE
        }
    }

    private fun selectAudioRoute(useCarAudio: Boolean) {
        // Legacy SCO routing is available on the UJC201's Android 9 base. This requests
        // the head unit's Bluetooth communication path or the local device speaker.
        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (useCarAudio) {
                audioManager.isSpeakerphoneOn = false
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                carAudioSelected = true
                statusText?.text = "Sortie demandée : autoradio (Bluetooth SCO)"
            } else {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                audioManager.isSpeakerphoneOn = true
                carAudioSelected = false
                statusText?.text = "Sortie demandée : haut-parleur local"
            }
        }.onFailure {
            statusText?.text = "Routage audio indisponible : ${it.message}"
        }
    }

    private fun endCall() {
        val ok = btClient.hangPhone()
        statusText?.text = if (ok) "Demande de raccrochage envoyée" else btClient.state.value.lastError ?: "Échec du raccrochage"
        if (ok) {
            handler.postDelayed({ stopSelf() }, 350)
        }
    }

    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 11f
        setPadding(dp(4), dp(2), dp(4), dp(2))
        setOnClickListener { action() }
    }

    private fun weightedButton() = LinearLayout.LayoutParams(0, dp(46), 1f).apply {
        setMargins(dp(2), dp(2), dp(2), dp(2))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun removeOverlay() {
        handler.removeCallbacks(refreshStatus)
        root?.let { view -> runCatching { windowManager.removeView(view) } }
        root = null
        windowParams = null
    }

    override fun onDestroy() {
        removeOverlay()
        runCatching {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        }
        btClient.unbind()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "librehu_call_overlay"
        private const val NOTIFICATION_ID = 7314
        private const val ACTION_STOP = "org.librehu.dialer.action.END_CALL"
        private const val EXTRA_NAME = "org.librehu.dialer.extra.CALL_NAME"
        private const val EXTRA_NUMBER = "org.librehu.dialer.extra.CALL_NUMBER"

        fun start(context: Context, name: String, number: String) {
            val intent = Intent(context, CallOverlayService::class.java)
                .putExtra(EXTRA_NAME, name)
                .putExtra(EXTRA_NUMBER, number)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Call controls", NotificationManager.IMPORTANCE_LOW))
        }
    }
}
