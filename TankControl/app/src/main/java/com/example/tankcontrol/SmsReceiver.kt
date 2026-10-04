package com.example.tankcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (msgs.isEmpty()) return
        val from = msgs.first().originatingAddress ?: return
        val body = msgs.joinToString("") { it.messageBody ?: "" }
        val prefs = context.getSharedPreferences("tank_control", Context.MODE_PRIVATE)
        val target = prefs.getString("sim_number", "") ?: ""
        if (phoneNumbersMatch(from, target)) {
            context.sendBroadcast(Intent("com.example.tankcontrol.SMS_REPLY").apply {
                putExtra("body", body)
            })
        }
    }
}