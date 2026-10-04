package com.example.tankcontrol

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val prefs by lazy { getSharedPreferences("tank_control", Context.MODE_PRIVATE) }
    private lateinit var appState: AppState

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (::appState.isInitialized) {
                appState.bumpPermissionTick()
                appState.updateSmsPermissionStatus(!hasSmsReceivePermission())
            }
        }

    private fun hasSmsReceivePermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    private val replyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            appState.handleReply(intent?.getStringExtra("body") ?: "")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_PHONE_STATE
            )
        )

        appState = AppState(
            prefs,
            sendSms = { text -> sendSmsToBoard(text) }
        )
        appState.updateSmsPermissionStatus(!hasSmsReceivePermission())

        // During first-run setup, Back returns to the language-selection page
        // instead of skipping setup and opening Home.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                appState.screen = when (appState.screen) {
                    Screen.Setup -> Screen.Language
                    Screen.ReliabilitySetup -> Screen.Language
                    Screen.Language -> Screen.Language
                    else -> Screen.Home
                }
            }
        })

        if (appState.autoUpdateMode == AutoUpdateMode.ON_OPEN) {
            appState.startAppOpenChecks()
        }

        val filter = IntentFilter("com.example.tankcontrol.SMS_REPLY")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(replyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(replyReceiver, filter)
        }

        setContent {
            TankControlTheme {
                val context = LocalContext.current

                // Show a Toast whenever the state machine records an unexpected/error reply.
                LaunchedEffect(appState.lastError) {
                    appState.lastError?.let {
                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                        appState.lastError = null
                    }
                }

                // Safety net: if the mother board never replies (out of SMS coverage, etc.),
                // don't leave a field stuck in its loading state forever.
                LaunchedEffect(appState.pending) {
                    if (appState.pending != null) {
                        delay(25_000)
                        appState.clearTimedOutPending()
                    }
                }

                TankControlApp(appState)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Pick up anything the scheduled background chain wrote while this
        // AppState instance wasn't the one driving those SMS round-trips.
        if (::appState.isInitialized) {
            appState.refreshFromPrefs()
            appState.updateSmsPermissionStatus(!hasSmsReceivePermission())
        }
    }

    override fun onDestroy() {
        unregisterReceiver(replyReceiver)
        super.onDestroy()
    }

    private fun sendSmsToBoard(text: String) {
        val number = prefs.getString("sim_number", "") ?: ""
        if (number.isBlank()) {
            Toast.makeText(this, "Set the SIM number first", Toast.LENGTH_SHORT).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            val subId = prefs.getInt("sub_id", -1)
            // SmsManager.getSmsManagerForSubscriptionId(Int) only exists from API 31
            // onward -- calling it unconditionally would crash on older phones for
            // anyone who picked a specific SIM in Settings > Account.
            val smsManager = if (subId != -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SmsManager.getSmsManagerForSubscriptionId(subId)
            } else {
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(number, null, text, null, null)
        } else {
            Toast.makeText(this, "SMS permission is required", Toast.LENGTH_SHORT).show()
        }
    }
}
