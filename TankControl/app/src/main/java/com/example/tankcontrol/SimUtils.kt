package com.example.tankcontrol

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

data class SimOption(val subscriptionId: Int, val label: String)

/** Lists the phone's active SIM subscriptions (for dual-SIM number selection).
 *  Returns an empty list if the permission isn't granted, the device is single-SIM,
 *  or the platform refuses (some OEMs restrict this) -- callers should treat an
 *  empty list as "nothing to choose, just use the system default". */
fun getSimSubscriptions(context: Context): List<SimOption> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
        != PackageManager.PERMISSION_GRANTED
    ) return emptyList()
    return try {
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        val list = sm.activeSubscriptionInfoList ?: return emptyList()
        list.map { info ->
            val carrier = info.carrierName?.toString()?.takeIf { it.isNotBlank() }
            val label = "SIM ${info.simSlotIndex + 1}" + (carrier?.let { " ($it)" } ?: "")
            SimOption(info.subscriptionId, label)
        }
    } catch (e: SecurityException) {
        emptyList()
    }
}
