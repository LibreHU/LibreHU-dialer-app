package org.librehu.dialer

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import org.librehu.dialer.phone.CallWatcherService
import org.librehu.dialer.phone.Phone
import org.librehu.dialer.phone.PhoneBackend
import org.librehu.dialer.phone.PhoneBackends
import org.librehu.dialer.phone.TelecomBackend
import org.librehu.dialer.ui.DialerApp
import org.librehu.dialer.ui.DialerColors
import org.librehu.dialer.ui.DialerPalette
import org.librehu.dialer.ui.DialerState
import org.librehu.dialer.ui.DialerTheme
import org.librehu.dialer.ui.SettingsActions
import org.librehu.dialer.ui.Tab

/** Phone app: favourites, recent calls, contacts, keypad, the call screen and settings. */
class MainActivity : ComponentActivity() {
    private lateinit var themeFollower: ThemeFollower
    private lateinit var phone: PhoneBackend
    private val state =
        DialerState(
            tab = mutableStateOf(Tab.FAVORITES),
            number = mutableStateOf(""),
            showCall = mutableIntStateOf(0),
            dataVersion = mutableIntStateOf(0),
        )

    /** Number to call once CALL_PHONE is granted. */
    private var pendingDial: String? = null

    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            state.dataVersion.value++
            val n = pendingDial
            pendingDial = null
            val needed = PhoneBackends.DIAL_PERMISSION
            if (n != null && (needed == null || granted(needed))) dial(n)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        phone = Phone.get(this)
        CallWatcherService.start(this)
        themeFollower =
            ThemeFollower(this) { dark, accent ->
                DialerColors.palette = DialerPalette.fromLauncher(dark, accent)
                val bg = DialerColors.Bg
                @Suppress("DEPRECATION")
                window.statusBarColor =
                    android.graphics.Color.rgb((bg.red * 255).toInt(), (bg.green * 255).toInt(), (bg.blue * 255).toInt())
                @Suppress("DEPRECATION")
                window.navigationBarColor = window.statusBarColor
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    if (dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        savedInstanceState?.getString(STATE_TAB)?.let { Tab.parse(it) }?.let { state.tab.value = it }
        handle(intent)
        if (savedInstanceState == null) {
            val missing = basePermissions().filterNot(::granted)
            if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
        }
        val settings =
            SettingsActions(
                requestPermissions = { permissions.launch(basePermissions().toTypedArray()) },
                makeDefaultDialer = ::requestDefaultDialer,
                isDefaultDialer = { TelecomBackend.isDefaultDialer(this) },
                openOverlaySettings = {
                    open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                },
                refreshTheme = { themeFollower.refreshNow() },
            )
        setContent {
            DialerTheme {
                DialerApp(phone, state, ::dial, { permissions.launch(basePermissions().toTypedArray()) }, settings)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    override fun onStart() {
        super.onStart()
        visible = true
        themeFollower.start()
        CallBubble.hide(this)
        state.dataVersion.value++
    }

    override fun onStop() {
        visible = false
        themeFollower.stop()
        if (phone.calls.value.any { it.status.live }) CallBubble.show(this)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TAB, state.tab.value.name)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        Tab.parse(intent.getStringExtra(EXTRA_OPEN_TAB))?.let { state.tab.value = it }
        if (intent.getBooleanExtra(EXTRA_SHOW_CALL, false)) state.showCall.value++
        val data = intent.data
        if ((intent.action == Intent.ACTION_DIAL || intent.action == Intent.ACTION_VIEW) && data?.scheme == "tel") {
            state.number.value = data.schemeSpecificPart.orEmpty()
            state.tab.value = Tab.KEYPAD
        }
    }

    private fun dial(number: String) {
        val n = number.trim()
        if (n.isEmpty()) return
        val needed = PhoneBackends.DIAL_PERMISSION
        if (needed != null && !granted(needed)) {
            pendingDial = n
            permissions.launch(arrayOf(needed))
            return
        }
        if (phone.dial(n)) {
            state.number.value = ""
            state.showCall.value++
        } else {
            Toast.makeText(this, getString(R.string.call_failed, n), Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun requestDefaultDialer() {
        val intent =
            if (Build.VERSION.SDK_INT >= 29) {
                getSystemService(android.app.role.RoleManager::class.java).createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER)
            } else {
                Intent(
                    TelecomManager.ACTION_CHANGE_DEFAULT_DIALER,
                ).putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            }
        if (!open(intent)) open(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    }

    private fun open(intent: Intent): Boolean =
        try {
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }

    private fun basePermissions(): List<String> =
        listOfNotNull(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG, PhoneBackends.DIAL_PERMISSION)

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        /** Tab to open: FAVORITES, RECENTS, CONTACTS, KEYPAD or SETTINGS (any case). */
        const val EXTRA_OPEN_TAB = "org.librehu.dialer.extra.OPEN_TAB"

        /** Brings the call screen up. */
        const val EXTRA_SHOW_CALL = "org.librehu.dialer.extra.SHOW_CALL"
        private const val STATE_TAB = "tab"

        /** The activity is on screen (the call bubble and the backends check it). */
        @Volatile
        var visible = false
    }
}
