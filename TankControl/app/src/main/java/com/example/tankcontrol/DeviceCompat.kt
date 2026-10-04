package com.example.tankcontrol

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** True once this app is exempt from Doze/App-Standby battery optimizations.
 *  Always true below API 23, where that system doesn't exist yet. */
fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

/** Opens the system dialog that lets the user exempt this app from battery
 *  optimizations, so stock/near-stock Android doesn't freeze it (and its SMS
 *  reply receiver) in the background. No-op below API 23 or once already exempt. */
fun requestIgnoreBatteryOptimizations(context: Context) {
    if (isIgnoringBatteryOptimizations(context)) return
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        openAppSettings(context)
    }
}

/** Many Chinese-OEM Android skins (MIUI, EMUI/Magic UI, ColorOS, FuntouchOS, Oxygen
 *  OS, One UI, Flyme, ...) kill background apps unless the user manually whitelists
 *  them on a vendor-specific "autostart" / "protected apps" screen. There's no
 *  public, documented API for this across vendors, so this tries each known
 *  vendor screen in turn and falls back to the app's own system settings page
 *  if none of them resolve on this particular phone/ROM version. */
fun openAutostartSettings(context: Context) {
    val manufacturer = Build.MANUFACTURER.lowercase()
    val candidates = mutableListOf<Intent>()

    fun add(pkg: String, cls: String) {
        candidates += Intent().setClassName(pkg, cls)
    }

    when {
        manufacturer.contains("xiaomi") -> {
            add("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        }
        manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
            add("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
            add("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
        }
        manufacturer.contains("oppo") -> {
            add("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
            add("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
        }
        manufacturer.contains("vivo") -> {
            add("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
        }
        manufacturer.contains("oneplus") -> {
            add("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
        }
        manufacturer.contains("samsung") -> {
            add("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
        }
        manufacturer.contains("meizu") -> {
            add("com.meizu.safe", "com.meizu.safe.security.SHOW_APPSEC")
        }
        manufacturer.contains("letv") || manufacturer.contains("leeco") -> {
            add("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")
        }
        manufacturer.contains("asus") -> {
            add("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")
        }
    }

    for (intent in candidates) {
        try {
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            // try the next candidate for this vendor, or fall through below
        }
    }
    openAppSettings(context)
}

fun openAppSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // nothing more we can do on this device
    }
}

/** True once the user has granted this app the special "Notification access"
 *  permission that SmsNotificationListener needs to see message notifications. */
fun isNotificationListenerEnabled(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/** Opens the system screen where the user grants (or revokes) notification
 *  access for this app -- there's no runtime-permission dialog for this one. */
fun openNotificationListenerSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    } catch (e: ActivityNotFoundException) {
        openAppSettings(context)
    }
}
