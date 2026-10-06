package org.librehu.dialer

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
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
import androidx.compose.ui.graphics.toArgb
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
import org.librehu.dialer.ui.DialerColors
import org.librehu.dialer.ui.primaryCall
import kotlin.math.abs

/**
 * Call over the other apps (navigation…) while the dialer is not on screen: a card for an incoming call (answer /
 * decline, Android Auto style), then a small draggable pill while the call goes on.
 */
object CallBubble {
    /** The overlay can show something for a call in this state. */
    fun wanted(
        context: Context,
        incoming: Boolean,
    ): Boolean =
        Settings.canDrawOverlays(context) &&
            if (incoming) DialerPreferences.incomingOverlay(context) else DialerPreferences.callBubble(context)

    fun show(
        context: Context,
        incoming: Boolean = false,
    ) {
        if (!wanted(context, incoming)) return
        runCatching { context.startService(Intent(context, CallBubbleService::class.java)) }
    }

    fun hide(context: Context) {
        runCatching { context.stopService(Intent(context, CallBubbleService::class.java)) }
    }
}

/**
 * Plain views: Compose needs a lifecycle owner that an overlay window does not have. Follows the calls; stops by
 * itself when no call is left or the dialer comes on screen.
 */
class CallBubbleService : Service() {
    private enum class Mode { INCOMING, ONGOING }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val main = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private var root: View? = null
    private var mode: Mode? = null
    private var title: TextView? = null
    private var subtitle: TextView? = null
    private var avatar: TextView? = null
    private var mute: TextView? = null
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
                    val want = if (c.status.incoming) Mode.INCOMING else Mode.ONGOING
                    if (want != mode) {
                        detach()
                        if (CallBubble.wanted(this@CallBubbleService, want == Mode.INCOMING)) {
                            if (want == Mode.INCOMING) attachIncoming() else attachPill()
                        }
                        mode = want
                    }
                    render()
                }
            }
        main.post(tick)
    }

    override fun onDestroy() {
        main.removeCallbacks(tick)
        job?.cancel()
        scope.cancel()
        detach()
        super.onDestroy()
    }

    private fun detach() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        title = null
        subtitle = null
        avatar = null
        mute = null
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(
        color: Int,
        radius: Int,
    ) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun button(
        text: String,
        color: Int,
        big: Boolean = false,
        onClick: () -> Unit,
    ) = TextView(this).apply {
        this.text = text
        textSize = if (big) 19f else 15f
        typeface = if (big) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setTextColor(0xFFFFFFFF.toInt())
        gravity = Gravity.CENTER
        if (big) setPadding(dp(28), dp(18), dp(28), dp(18)) else setPadding(dp(16), dp(10), dp(16), dp(10))
        background = rounded(color, if (big) 32 else 22)
        setOnClickListener { onClick() }
    }

    private fun openCallScreen() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SHOW_CALL, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    private fun overlayParams(y: Int) =
        WindowManager
            .LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                this.y = dp(y)
            }

    /** Incoming call card: avatar, name, number, decline / answer; a touch on the caller opens the call screen. */
    private fun attachIncoming() {
        val phone = Phone.get(this)
        val p = DialerColors.palette
        val fg = p.text.toArgb()
        val dim = p.textDim.toArgb()
        val card =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(20), dp(24), dp(20))
                background = rounded((p.surface.toArgb() and 0x00FFFFFF) or 0xF5000000.toInt(), 32)
                elevation = dp(12).toFloat()
                minimumWidth = dp(520)
            }
        val top =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener { openCallScreen() }
            }
        val a =
            TextView(this).apply {
                textSize = 26f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(p.onAccent.toArgb())
                background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(p.accent.toArgb())
                    }
            }
        top.addView(a, LinearLayout.LayoutParams(dp(64), dp(64)))
        val texts =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), 0, 0, 0)
            }
        val t =
            TextView(this).apply {
                textSize = 24f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(fg)
                maxLines = 1
                maxWidth = dp(420)
            }
        val st =
            TextView(this).apply {
                textSize = 16f
                setTextColor(dim)
                maxLines = 1
                maxWidth = dp(420)
            }
        texts.addView(t)
        texts.addView(st)
        top.addView(texts)
        card.addView(top)
        val buttons =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, dp(18), 0, 0)
            }
        buttons.addView(
            button(getString(R.string.call_decline), 0xFFD93025.toInt(), big = true) {
                call?.let { c -> if (c.status.incoming) phone.reject(c) }
            },
        )
        buttons.addView(View(this), LinearLayout.LayoutParams(dp(16), 1))
        buttons.addView(
            button(getString(R.string.call_answer), 0xFF1E8E3E.toInt(), big = true) {
                call?.let { c -> if (c.status.incoming) phone.answer(c) }
            },
        )
        card.addView(buttons, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        runCatching { wm.addView(card, overlayParams(24)) }.onFailure { stopSelf() }
        root = card
        title = t
        subtitle = st
        avatar = a
    }

    /** Ongoing call pill: name and time, mute, end. Drag anywhere; a tap without movement opens the call screen. */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachPill() {
        val phone = Phone.get(this)
        val panel =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(8), dp(8), dp(8))
                background = rounded(0xF0202124.toInt(), 30)
            }
        val t =
            TextView(this).apply {
                textSize = 16f
                setTextColor(0xFFE8EAED.toInt())
                maxLines = 1
                maxWidth = dp(260)
                setPadding(0, 0, dp(12), 0)
            }
        val m = button(getString(R.string.call_mute), 0xFF3C4043.toInt()) { phone.setMuted(!muted) }
        val end = button(getString(R.string.call_end), 0xFFD93025.toInt()) { call?.let { phone.hangup(it) } }
        panel.addView(t)
        panel.addView(m)
        panel.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        panel.addView(end)
        val lp = overlayParams(16)
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
                    if (!moved) openCallScreen()
                }
            }
            true
        }
        runCatching { wm.addView(panel, lp) }.onFailure { stopSelf() }
        root = panel
        title = t
        mute = m
    }

    private fun render() {
        val c = call ?: return
        val who = name.ifBlank { c.number.ifBlank { getString(R.string.call_unknown) } }
        if (mode == Mode.INCOMING) {
            title?.text = who
            val number = if (name.isNotBlank() && c.number.isNotBlank()) "${c.number} · " else ""
            subtitle?.text = number + getString(if (c.status == CallStatus.WAITING) R.string.incoming_waiting else R.string.status_ringing)
            avatar?.text = who.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
            return
        }
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

                CallStatus.HELD -> {
                    getString(R.string.status_held)
                }

                else -> {
                    getString(R.string.status_dialing)
                }
            }
        title?.text = "$who · $status"
        mute?.text = getString(if (muted) R.string.call_unmute else R.string.call_mute)
    }
}
