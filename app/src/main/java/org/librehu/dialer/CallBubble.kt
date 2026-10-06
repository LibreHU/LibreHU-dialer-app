package org.librehu.dialer

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.librehu.dialer.data.PhoneBook
import org.librehu.dialer.phone.CallStatus
import org.librehu.dialer.phone.Phone
import org.librehu.dialer.phone.PhoneCall
import org.librehu.dialer.ui.primaryCall
import kotlin.math.abs

/** Floating call controls over other apps (navigation…) while a call goes on and the dialer is not on screen. */
object CallBubble {
    fun show(context: Context) {
        if (!DialerPreferences.callBubble(context) || !Settings.canDrawOverlays(context)) return
        runCatching { context.startService(Intent(context, CallBubbleService::class.java)) }
    }

    fun hide(context: Context) {
        runCatching { context.stopService(Intent(context, CallBubbleService::class.java)) }
    }
}

/**
 * Draggable pill: name, call time, mute, end; a touch opens the call screen. Plain views: Compose needs a
 * lifecycle owner that an overlay window does not have. Stops by itself when no call is left.
 */
class CallBubbleService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val main = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private var root: LinearLayout? = null
    private var title: TextView? = null
    private var mute: TextView? = null
    private var answer: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var call: PhoneCall? = null
    private var name = ""
    private var muted = false
    private var job: Job? = null

    private val tick =
        object : Runnable {
            override fun run() {
                render()
                main.postDelayed(this, 1000)
            }
        }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        val phone = Phone.get(this)
        job =
            scope.launch {
                combine(phone.calls, phone.link) { calls, link -> calls to link }.collect { (calls, link) ->
                    val c = primaryCall(calls)
                    if (c == null || MainActivity.visible) {
                        stopSelf()
                        return@collect
                    }
                    if (c.number != call?.number) {
                        name = c.name
                        if (name.isBlank()) {
                            scope.launch(Dispatchers.IO) {
                                val n = PhoneBook.lookupName(this@CallBubbleService, c.number)
                                launch(Dispatchers.Main) {
                                    name = n
                                    render()
                                }
                            }
                        }
                    }
                    call = c
                    muted = link.micMuted
                    if (root == null) attach()
                    render()
                }
            }
        main.post(tick)
    }

    override fun onDestroy() {
        main.removeCallbacks(tick)
        job?.cancel()
        scope.cancel()
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun button(
        text: String,
        color: Int,
        onClick: () -> Unit,
    ) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(0xFFFFFFFF.toInt())
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(10), dp(16), dp(10))
        background =
            GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(22).toFloat()
            }
        setOnClickListener { onClick() }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attach() {
        val phone = Phone.get(this)
        val panel =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(8), dp(8), dp(8))
                background =
                    GradientDrawable().apply {
                        setColor(0xF0202124.toInt())
                        cornerRadius = dp(30).toFloat()
                    }
            }
        val t =
            TextView(this).apply {
                textSize = 16f
                setTextColor(0xFFE8EAED.toInt())
                maxLines = 1
                maxWidth = dp(260)
                setPadding(0, 0, dp(12), 0)
            }
        val m =
            button(getString(R.string.call_mute), 0xFF3C4043.toInt()) {
                phone.setMuted(!muted)
            }
        val end =
            button(getString(R.string.call_end), 0xFFD93025.toInt()) {
                call?.let { c -> if (c.status.incoming) phone.reject(c) else phone.hangup(c) }
            }
        // Incoming call: answer from the bubble too (the mute button makes no sense yet).
        val a =
            button(getString(R.string.call_answer), 0xFF1E8E3E.toInt()) {
                call?.let { c -> if (c.status.incoming) phone.answer(c) }
            }
        panel.addView(t)
        panel.addView(a)
        panel.addView(m)
        panel.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        panel.addView(end)
        val lp =
            WindowManager
                .LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = dp(16)
                }
        // Drag anywhere; a tap without movement opens the call screen.
        var startX = 0f
        var startY = 0f
        var baseX = 0
        var baseY = 0
        var moved = false
        panel.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = e.rawX
                    startY = e.rawY
                    baseX = lp.x
                    baseY = lp.y
                    moved = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - startX
                    val dy = e.rawY - startY
                    if (abs(dx) > dp(8) || abs(dy) > dp(8)) moved = true
                    if (moved) {
                        lp.x = baseX + dx.toInt()
                        lp.y = baseY + dy.toInt()
                        runCatching { wm.updateViewLayout(v, lp) }
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .putExtra(MainActivity.EXTRA_SHOW_CALL, true)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        )
                    }
                }
            }
            true
        }
        runCatching { wm.addView(panel, lp) }.onFailure { stopSelf() }
        root = panel
        title = t
        mute = m
        answer = a
        params = lp
    }

    private fun render() {
        val c = call ?: return
        val who = name.ifBlank { c.number.ifBlank { getString(R.string.call_unknown) } }
        val status =
            when (c.status) {
                CallStatus.ACTIVE -> {
                    if (c.activeSince > 0) {
                        val s = (System.currentTimeMillis() - c.activeSince) / 1000
                        "%d:%02d".format(s / 60, s % 60)
                    } else {
                        getString(R.string.status_active)
                    }
                }

                CallStatus.RINGING, CallStatus.WAITING -> {
                    getString(R.string.status_ringing)
                }

                CallStatus.HELD -> {
                    getString(R.string.status_held)
                }

                else -> {
                    getString(R.string.status_dialing)
                }
            }
        title?.text = "$who · $status"
        mute?.text = getString(if (muted) R.string.call_unmute else R.string.call_mute)
        answer?.visibility = if (c.status.incoming) View.VISIBLE else View.GONE
        mute?.visibility = if (c.status.incoming) View.GONE else View.VISIBLE
    }
}
