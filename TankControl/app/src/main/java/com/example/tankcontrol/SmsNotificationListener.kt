package com.example.tankcontrol

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Fallback path for the board's SMS replies. On some phones/ROMs the direct
 *  SMS_RECEIVED broadcast to SmsReceiver gets suppressed, delayed, or the app
 *  gets killed before it's delivered (aggressive OEM background restrictions,
 *  in particular) -- but the phone's own messaging app almost always still
 *  shows a notification for that same incoming SMS. This listens for that
 *  notification and feeds its text into the exact same reply pipeline as a
 *  direct SMS, so defaults/status still get registered even when the primary
 *  path is missed. Requires the separate "Notification access" special
 *  permission (see DeviceCompat.openNotificationListenerSettings) -- this
 *  can't be requested as a normal runtime permission dialog. */
class SmsNotificationListener : NotificationListenerService() {

    @Suppress("DEPRECATION")
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        // Nearly every messaging app (regardless of OEM/package name) tags its
        // incoming-message notifications this way -- far more reliable than
        // trying to enumerate every vendor's Messages app package name.
        if (notification.category != Notification.CATEGORY_MESSAGE) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        var body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // Conversation-style (MessagingStyle) notifications carry the real text in
        // EXTRA_MESSAGES instead of EXTRA_TEXT -- take the most recent one if present.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (messages != null && messages.isNotEmpty()) {
                Notification.MessagingStyle.Message.getMessagesFromBundleArray(messages)
                    .lastOrNull()?.text?.toString()?.let { body = it }
            }
        }
        if (body.isBlank() || title.isBlank()) return

        val prefs = getSharedPreferences("tank_control", Context.MODE_PRIVATE)
        val target = prefs.getString("sim_number", "") ?: ""
        if (target.isBlank()) return
        // The title is the raw sender number for an unsaved contact, which the
        // board's SIM normally is. If that number was saved as a phone contact,
        // the title becomes a name instead and this fallback simply can't match it.
        if (!phoneNumbersMatch(title, target)) return

        sendBroadcast(Intent("com.example.tankcontrol.SMS_REPLY").apply {
            putExtra("body", body)
        })
    }
}
