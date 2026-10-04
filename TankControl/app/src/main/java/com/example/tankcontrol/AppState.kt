package com.example.tankcontrol

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Screens in the app. Tank is parameterised by a 0-based tank index.
 */
sealed class Screen {
    object Language : Screen()
    object Setup : Screen()
    object Home : Screen()
    data class Tank(val index: Int) : Screen()
    object Settings : Screen()
    object SettingsAccount : Screen()
    object SettingsLanguage : Screen()
    object SettingsSim : Screen()
    object SettingsTankCount : Screen()
    object SettingsHelp : Screen()
    object SettingsAbout : Screen()
    object SettingsAutoUpdate : Screen()
    object SettingsReliability : Screen()
    object ReliabilitySetup : Screen()
}

enum class PendingType { TANKS_SET, HEIGHT, PERCENTS, CHECK, ANT }

/** wizardAdvance: true when this CHECK was auto-triggered right after a tank's
 *  defaults were confirmed during the guided setup, so the reply handler knows
 *  to advance to the next tank page (or finish up) once it lands.
 *  startupChain: true when this CHECK is part of the silent "check every tank,
 *  then the antenna" sequence that runs once whenever the app is opened -- no
 *  screen navigation, it just quietly refreshes Home's numbers. */
data class PendingOp(
    val type: PendingType,
    val tankIndex: Int,
    val wizardAdvance: Boolean = false,
    val startupChain: Boolean = false,
    val recoveryChain: Boolean = false,
    val initialRecovery: Boolean = false,
    /** true while this TANKS_SET/HEIGHT/PERCENTS op is part of silently re-pushing
     *  already-known tank settings back to the board after a mid-recovery TANKERS
     *  SET had to be issued (see AppState.recommitActive). */
    val recommit: Boolean = false
)

/**
 * Per-tank UI + persisted state. Field names/keys are prefixed with the
 * tank index (0-based) so all 4 tank slots share one SharedPreferences file.
 */
class TankUiState(private val prefs: SharedPreferences, val index: Int) {
    var height by mutableStateOf(prefs.getInt(k("height"), 0))
    var onPercent by mutableStateOf(prefs.getInt(k("on"), 0))
    var offPercent by mutableStateOf(prefs.getInt(k("off"), 0))
    var settings1Ready by mutableStateOf(prefs.getBoolean(k("s1"), false))
    var settings2Ready by mutableStateOf(prefs.getBoolean(k("s2"), false))

    /** Only ever set from a CHECK reply -- never from a live preview while typing. */
    var waterPercent by mutableStateOf(prefs.getInt(k("water"), -1).takeIf { it >= 0 })
    var distanceCm by mutableStateOf(prefs.getInt(k("dist"), -1).takeIf { it >= 0 })
    var nrfScore by mutableStateOf(prefs.getInt(k("nrf"), -1).takeIf { it >= 0 })

    /** Device-clock timestamp (epoch millis, no network needed) of the last
     *  successful Check reply for this tank -- shown as "last updated HH:mm". */
    var lastCheckedAt by mutableStateOf(prefs.getLong(k("checkedAt"), -1L).takeIf { it >= 0 })

    /** Local-only, never sent over SMS -- used just to compute liters from percent. */
    var diameterCm by mutableStateOf(prefs.getInt(k("diameter"), -1).takeIf { it >= 0 })

    /** True while an ENTER for height/percents has sent an SMS and we're waiting on a reply. */
    var loading by mutableStateOf(false)

    /** True while a Check for this tank is in flight. */
    var checkLoading by mutableStateOf(false)

    /** Transient (not persisted): true while the user has an inline editor open for
     *  that value. Closes itself once the new value saves, times out if left idle,
     *  or is cancelled by tapping elsewhere on the page. */
    var editingHeight by mutableStateOf(false)
    var editingPercents by mutableStateOf(false)
    var editingDiameter by mutableStateOf(false)

    /** Re-reads everything from SharedPreferences -- used to pick up values that
     *  the headless background check chain (BackgroundChain) wrote while this
     *  TankUiState instance was alive but not the one driving those requests. */
    fun reloadFromPrefs() {
        height = prefs.getInt(k("height"), height)
        onPercent = prefs.getInt(k("on"), onPercent)
        offPercent = prefs.getInt(k("off"), offPercent)
        settings1Ready = prefs.getBoolean(k("s1"), settings1Ready)
        settings2Ready = prefs.getBoolean(k("s2"), settings2Ready)
        waterPercent = prefs.getInt(k("water"), -1).takeIf { it >= 0 }
        distanceCm = prefs.getInt(k("dist"), -1).takeIf { it >= 0 }
        nrfScore = prefs.getInt(k("nrf"), -1).takeIf { it >= 0 }
        diameterCm = prefs.getInt(k("diameter"), -1).takeIf { it >= 0 }
        lastCheckedAt = prefs.getLong(k("checkedAt"), -1L).takeIf { it >= 0 }
    }

    private fun k(field: String) = "tank${index}_$field"

    fun save() {
        prefs.edit()
            .putInt(k("height"), height)
            .putInt(k("on"), onPercent)
            .putInt(k("off"), offPercent)
            .putBoolean(k("s1"), settings1Ready)
            .putBoolean(k("s2"), settings2Ready)
            .putInt(k("water"), waterPercent ?: -1)
            .putInt(k("dist"), distanceCm ?: -1)
            .putInt(k("nrf"), nrfScore ?: -1)
            .putInt(k("diameter"), diameterCm ?: -1)
            .putLong(k("checkedAt"), lastCheckedAt ?: -1L)
            .apply()
    }

    /** The mother board wipes ALL tank settings whenever a new "TANKERS SET" is received. */
    fun resetConfig() {
        height = 0
        onPercent = 0
        offPercent = 0
        settings1Ready = false
        settings2Ready = false
        waterPercent = null
        distanceCm = null
        lastCheckedAt = null
        loading = false
        checkLoading = false
        editingHeight = false
        editingPercents = false
        editingDiameter = false
        save()
    }

    /** Liters, if a diameter has been entered locally; cylinder volume from height*percent. */
    fun liters(): Double? {
        val d = diameterCm ?: return null
        val h = height
        val pct = waterPercent ?: return null
        if (d <= 0 || h <= 0) return null
        val radiusCm = d / 2.0
        val filledHeightCm = h * (pct / 100.0)
        val volumeCm3 = Math.PI * radiusCm * radiusCm * filledHeightCm
        return volumeCm3 / 1000.0 // 1 liter = 1000 cm^3
    }

    /** Best-effort pump state, inferred (the mother board doesn't report it directly):
     *  at/below the ON threshold the pump should be running; at/above OFF it should
     *  have stopped; in between we can't know for sure without the device telling us,
     *  so it's left null (no badge shown) rather than guessed. */
    fun pumpOn(): Boolean? {
        val p = waterPercent ?: return null
        return when {
            p <= onPercent -> true
            p >= offPercent -> false
            else -> null
        }
    }
}

private val faTankOrdinals = listOf("اول", "دوم", "سوم", "چهارم")

enum class AutoUpdateMode { OFF, ON_OPEN }

/**
 * Whole-app state. Owns SharedPreferences persistence and the SMS
 * request/reply state machine. `sendSms` is provided by MainActivity and
 * actually transmits the text via SmsManager.
 */
class AppState(
    private val prefs: SharedPreferences,
    private val sendSms: (String) -> Unit,
) {

    var language by mutableStateOf(
        if (prefs.contains("lang")) Lang.valueOf(prefs.getString("lang", "EN") ?: "EN") else Lang.EN
    )
    private val languageChosen = prefs.contains("lang")
    private val simSet = (prefs.getString("sim_number", "") ?: "").isNotBlank()
    /** True once the user has gotten past the one-time "SMS reliability" prompt
     *  (battery optimization / OEM autostart / notification access) shown right
     *  after picking a language -- these matter enough that they shouldn't be
     *  something the user only stumbles into from Settings later. */
    private var reliabilityIntroShown = prefs.getBoolean("reliability_intro_shown", false)

    var screen by mutableStateOf(
        when {
            !languageChosen -> Screen.Language
            !reliabilityIntroShown -> Screen.ReliabilitySetup
            !simSet -> Screen.Setup
            else -> Screen.Home
        }
    )

    var simNumber by mutableStateOf(prefs.getString("sim_number", "") ?: "")
    var contactName by mutableStateOf(prefs.getString("contact_name", "") ?: "")
    var contactNumber by mutableStateOf(prefs.getString("contact_number", "") ?: "")

    /** Which SIM subscription (dual-SIM phones) outgoing SMS is sent from. -1 = system default. */
    var chosenSubscriptionId by mutableStateOf(prefs.getInt("sub_id", -1))

    /** Bumped whenever MainActivity's runtime permission result comes back, so any
     *  screen reading the SIM subscription list (which needs READ_PHONE_STATE)
     *  recomputes it instead of being stuck with whatever it saw before the grant. */
    var permissionTick by mutableStateOf(0)

    var tankCount by mutableStateOf(prefs.getInt("tank_count", 0))
    var tankCountConfigured by mutableStateOf(prefs.getInt("tank_count", 0) > 0)
    var tankCountLoading by mutableStateOf(false)

    var gsmCsq by mutableStateOf(prefs.getInt("gsm_csq", -1).takeIf { it >= 0 })
    var antLoading by mutableStateOf(false)

    // Only two modes are supported: OFF or refresh when the app opens.
    // Treat any old SCHEDULED value from previous versions as ON_OPEN.
    var autoUpdateMode by mutableStateOf(
        if ((prefs.getString("auto_update_mode", "ON_OPEN") ?: "ON_OPEN") == "OFF")
            AutoUpdateMode.OFF
        else
            AutoUpdateMode.ON_OPEN
    )

    var pending by mutableStateOf<PendingOp?>(null)

    /** True from the moment the tank count is (re)applied until every tank's
     *  defaults have been entered -- drives the guided auto-advance between tank pages. */
    var setupWizardActive by mutableStateOf(false)

    /** True for ~5s right after the last tank in the wizard finishes, to show the
     *  fade in/out "All defaults saved" banner. The banner itself clears it. */
    var showAllSavedBanner by mutableStateOf(false)
    var showDefaultsReminder by mutableStateOf(false)
    var showPartialRecoveryBanner by mutableStateOf(false)
    var partialRecoveryIndex by mutableStateOf(0)
    var recoveryCompletionPendingAnt by mutableStateOf(false)
    var recoveryMode by mutableStateOf(false)
    /** True while the initial CHECK chain is trying to restore previously saved defaults. */
    var recoveryLoading by mutableStateOf(false)

    /** True while already-known tank settings (recovered via CHECK, but wiped again
     *  because a fresh TANKERS SET had to be sent) are being silently re-pushed to
     *  the board one SMS at a time. Drives the yellow "registering info" progress screen. */
    var recommitActive by mutableStateOf(false)
    /** How many tanks (from index 0) are being re-pushed in the current recommit pass. */
    var recommitTankCount by mutableStateOf(0)
    var recommitStepsTotal by mutableStateOf(0)
    var recommitStepsDone by mutableStateOf(0)
    /** Tank index to land on (with the partial-recovery banner) once the recommit pass finishes. */
    private var recommitResumeIndex = 0

    /** Set to a message when a reply looks wrong/unexpected; UI shows a Toast then clears it. */
    var lastError by mutableStateOf<String?>(null)

    /** True when RECEIVE_SMS or READ_SMS isn't granted -- outgoing commands still send
     *  fine, but the mother board's replies can never arrive. Drives a persistent
     *  (non-dismissable) warning banner; MainActivity keeps this in sync with the
     *  real permission state after every request result and on onResume. */
    var smsPermissionMissing by mutableStateOf(false)

    fun updateSmsPermissionStatus(missing: Boolean) {
        smsPermissionMissing = missing
    }

    val tanks = List(4) { i -> TankUiState(prefs, i) }

    fun strings(): AppStrings = stringsFor(language)

    /** "Tank1"/"Tank2".. in English; "تانکر اول"/"تانکر دوم".. in Persian. */
    fun tankLabel(index: Int): String =
        if (language == Lang.FA) {
            "تانکر ${faTankOrdinals.getOrElse(index) { (index + 1).toString() }}"
        } else {
            "${strings().tankTab}${index + 1}"
        }

    fun bumpPermissionTick() {
        permissionTick++
    }

    fun changeLanguage(l: Lang) {
        language = l
        prefs.edit().putString("lang", l.name).apply()
        if (screen == Screen.Language) {
            screen = when {
                !reliabilityIntroShown -> Screen.ReliabilitySetup
                simSet -> Screen.Home
                else -> Screen.Setup
            }
        }
    }

    fun onContinueFromReliabilitySetup() {
        reliabilityIntroShown = true
        prefs.edit().putBoolean("reliability_intro_shown", true).apply()
        screen = if (simSet) Screen.Home else Screen.Setup
    }

    fun onEnterSimNumber(num: String) {
        val n = num.trim()
        if (n.isEmpty()) return
        simNumber = n
        prefs.edit().putString("sim_number", n).apply()
        if (screen == Screen.Setup) screen = Screen.Home
    }

    fun onChooseSubscription(id: Int) {
        chosenSubscriptionId = id
        prefs.edit().putInt("sub_id", id).apply()
    }

    fun onSaveAccount(name: String) {
        contactName = name.trim()
        prefs.edit().putString("contact_name", contactName).apply()
    }

    fun onSaveAutoUpdate(mode: AutoUpdateMode) {
        autoUpdateMode = mode
        prefs.edit().putString("auto_update_mode", mode.name).apply()
    }

    /** Re-syncs everything from SharedPreferences -- call this on Activity resume,
     *  since the scheduled background chain (BackgroundChain) may have updated
     *  tank/antenna data directly in prefs while this AppState instance wasn't
     *  the one driving those SMS round-trips. */
    fun refreshFromPrefs() {
        gsmCsq = prefs.getInt("gsm_csq", -1).takeIf { it >= 0 }
        tanks.forEach { it.reloadFromPrefs() }
    }

    fun onEnterTankCount(n: Int) {
        if (pending != null) return
        if (n !in 1..4) {
            lastError = strings().tankCountRangeError
            return
        }

        tankCount = n

        // First-time tank-count entry: do not overwrite the controller's saved
        // defaults. Probe CHECK1..CHECKn first, one at a time, so existing
        // defaults can be restored automatically.
        if (!tankCountConfigured) {
            tankCountConfigured = true
            recoveryMode = true
            recoveryLoading = true
            tanks.forEach { it.resetConfig() }
            prefs.edit().putInt("tank_count", n).apply()
            tanks[0].checkLoading = true
            pending = PendingOp(PendingType.CHECK, 0, recoveryChain = true, initialRecovery = true)
            sendSms("CHECK1")
            return
        }

        // Changing the tank count later keeps the established setup flow.
        tankCountLoading = true
        pending = PendingOp(PendingType.TANKS_SET, -1)
        sendSms("TANKERS SET$n")
    }

    fun onStartEditHeight(i: Int) {
        tanks.getOrNull(i)?.editingHeight = true
    }

    fun onStartEditPercents(i: Int) {
        tanks.getOrNull(i)?.editingPercents = true
    }

    fun onStartEditDiameter(i: Int) {
        tanks.getOrNull(i)?.editingDiameter = true
    }

    fun onEnterHeight(i: Int, h: Int) {
        if (pending != null || h <= 0) return
        val t = tanks.getOrNull(i) ?: return
        t.height = h
        t.loading = true
        pending = PendingOp(PendingType.HEIGHT, i)
        sendSms("TANK${i + 1} SETTINGS1 $h")
    }

    fun onEnterOnPercent(i: Int, p: Int) {
        val t = tanks.getOrNull(i) ?: return
        if (p !in 0..100) return
        t.onPercent = p
    }

    fun onEnterOffPercent(i: Int, off: Int) {
        if (pending != null) return
        val t = tanks.getOrNull(i) ?: return
        if (off !in 0..100 || off <= t.onPercent) {
            lastError = strings().offMustExceedOnError
            return
        }
        t.offPercent = off
        t.loading = true
        pending = PendingOp(PendingType.PERCENTS, i)
        sendSms("TANK${i + 1} SETTINGS2 ${t.onPercent}/$off")
    }

    /** Editing via the ON/OFF arrows always resends BOTH values together in one
     *  SETTINGS2 command (that's the only command the firmware understands),
     *  so touching one threshold always re-confirms the other too. */
    fun onConfirmThresholds(i: Int, on: Int, off: Int) {
        if (pending != null) return
        val t = tanks.getOrNull(i) ?: return
        if (on !in 0..100 || off !in 0..100 || off <= on) {
            lastError = strings().offMustExceedOnError
            return
        }
        t.onPercent = on
        t.offPercent = off
        t.loading = true
        pending = PendingOp(PendingType.PERCENTS, i)
        sendSms("TANK${i + 1} SETTINGS2 $on/$off")
    }

    fun onCheckTank(i: Int) {
        if (pending != null) return
        val t = tanks.getOrNull(i) ?: return
        if (!t.settings1Ready || !t.settings2Ready) return
        t.checkLoading = true
        pending = PendingOp(PendingType.CHECK, i)
        sendSms("CHECK${i + 1}")
    }

    fun onSetDiameter(i: Int, d: Int) {
        val t = tanks.getOrNull(i) ?: return
        if (d <= 0) return
        t.diameterCm = d
        t.editingDiameter = false
        t.save()
    }

    fun onAntCheck() {
        if (pending != null) return
        antLoading = true
        pending = PendingOp(PendingType.ANT, -1)
        sendSms("ANT CHECK")
    }

    /** Called once when the app is opened (if setup is already done): silently
     *  checks tank 1, then 2, then 3, then 4 (as many as exist) one at a time,
     *  and finally refreshes the antenna status -- no screen navigation, just
     *  fresh numbers by the time the person looks at Home. */
    fun startAppOpenChecks() {
        if (!tankCountConfigured || tankCount <= 0) return
        if (pending != null || setupWizardActive) return
        val t = tanks.getOrNull(0) ?: return
        if (!t.settings1Ready || !t.settings2Ready) return
        t.checkLoading = true
        pending = PendingOp(PendingType.CHECK, 0, startupChain = true)
        sendSms("CHECK1")
    }

    /** Called by the UI if an edit was opened (height/percents/diameter) but left
     *  idle (no confirm, no send in flight), or the person tapped elsewhere on the
     *  page -- reverts to the last saved values. */
    fun onEditTimeout(i: Int) {
        val t = tanks.getOrNull(i) ?: return
        if (t.editingHeight && !t.loading) t.editingHeight = false
        if (t.editingPercents && !t.loading) t.editingPercents = false
        if (t.editingDiameter) t.editingDiameter = false
    }

    /** Called by MainActivity after a timeout with no reply, so the UI never gets stuck. */
    fun clearTimedOutPending() {
        val p = pending ?: return
        pending = null
        when (p.type) {
            PendingType.TANKS_SET -> {
                tankCountLoading = false
                if (p.recommit) recommitActive = false
            }
            PendingType.HEIGHT, PendingType.PERCENTS -> {
                tanks.getOrNull(p.tankIndex)?.loading = false
                if (p.recommit) recommitActive = false
            }
            PendingType.CHECK -> {
                tanks.getOrNull(p.tankIndex)?.checkLoading = false
                if (p.recoveryChain) {
                    handleRecoveryFailure(p.tankIndex)
                    return
                }
            }
            PendingType.ANT -> antLoading = false
        }
        lastError = strings().noReply
    }

    /** Reply text formats below match the mother-board firmware exactly:
     *   TANKERS SETn  -> "n tanks set. Please send the next settings."
     *   TANKi SETTINGS1 h -> "h cm tank i height saved"
     *   TANKi SETTINGS2 on/off -> "Saved. Pump ON at on% and OFF at off%"
     *   ANT CHECK -> "GSM: <quality> (CSQ: n)\nTank i NRF: s/100\n..."
     *   CHECKi -> "Tank i\n<per-tank-node reply>\nH=..\nON=..%\nOFF=..%"
     * The per-tank-node reply text inside CHECKi is produced by the remote
     * tank's own firmware, which wasn't provided, so parsing of the water
     * percentage there is a best-effort generic match.
     */
    fun handleReply(body: String) {
        val p = pending ?: return
        when (p.type) {
            PendingType.TANKS_SET -> {
                tankCountLoading = false
                pending = null
                if (body.contains("tanks set", ignoreCase = true)) {
                    prefs.edit().putInt("tank_count", tankCount).apply()
                    tankCountConfigured = true
                    if (p.recommit) {
                        // The board just wiped every tank's config. Keep the numbers we
                        // already recovered for tanks 0 until recommitTankCount (just mark
                        // them unconfirmed again), and fully reset anything beyond that.
                        tanks.forEachIndexed { idx, t ->
                            if (idx < recommitTankCount) {
                                t.settings1Ready = false
                                t.settings2Ready = false
                                t.loading = false
                                t.checkLoading = false
                                t.editingHeight = false
                                t.editingPercents = false
                                t.editingDiameter = false
                                t.waterPercent = null
                                t.distanceCm = null
                                t.lastCheckedAt = null
                                t.save()
                            } else {
                                t.resetConfig()
                            }
                        }
                        advanceRecommit()
                    } else {
                        tanks.forEach { it.resetConfig() }
                        recoveryLoading = false
                        recoveryMode = false
                        // guided setup: briefly remind the user to re-enter defaults, then open tank 1.
                        setupWizardActive = true
                        showDefaultsReminder = true
                        screen = Screen.Tank(0)
                    }
                } else {
                    lastError = body.ifBlank { "Unexpected reply" }
                    if (p.recommit) recommitActive = false
                }
            }
            PendingType.HEIGHT -> {
                val t = tanks[p.tankIndex]
                t.loading = false
                pending = null

                // SETTINGS1: the exact reply text is intentionally ignored.
                // Any reply confirms receipt; the value entered by the user is
                // the value stored locally.
                t.settings1Ready = true
                t.editingHeight = false
                t.save()

                if (p.recommit) {
                    recommitStepsDone++
                    advanceRecommit()
                }
            }
            PendingType.PERCENTS -> {
                val t = tanks[p.tankIndex]
                t.loading = false
                pending = null

                // SETTINGS2: same rule as SETTINGS1. Any reply is enough.
                // CHECK replies remain strict and are parsed separately.
                t.settings2Ready = true
                t.editingPercents = false
                t.save()

                if (p.recommit) {
                    recommitStepsDone++
                    advanceRecommit()
                } else if (setupWizardActive) {
                    // auto-check this tank's fresh status before moving on
                    t.checkLoading = true
                    pending = PendingOp(PendingType.CHECK, p.tankIndex, wizardAdvance = true)
                    sendSms("CHECK${p.tankIndex + 1}")
                }
            }
            PendingType.CHECK -> {
                val t = tanks[p.tankIndex]
                t.checkLoading = false
                pending = null
                val defaultsRecovered = parseCheck(body, p.tankIndex)
                if (p.recoveryChain) {
                    if (!defaultsRecovered) {
                        handleRecoveryFailure(p.tankIndex)
                    } else {
                        val next = p.tankIndex + 1
                        if (next < tankCount) {
                            tanks[next].checkLoading = true
                            pending = PendingOp(PendingType.CHECK, next, recoveryChain = true, initialRecovery = p.initialRecovery)
                            sendSms("CHECK${next + 1}")
                        } else {
                            recoveryMode = false
                            recoveryLoading = false
                            recoveryCompletionPendingAnt = true
                            showAllSavedBanner = true
                            pending = null
                        }
                    }
                } else if (p.wizardAdvance) {
                    val next = p.tankIndex + 1
                    if (next < tankCount) {
                        screen = Screen.Tank(next)
                    } else {
                        setupWizardActive = false
                        showAllSavedBanner = true
                        antLoading = true
                        pending = PendingOp(PendingType.ANT, -1)
                        sendSms("ANT CHECK")
                    }
                } else if (p.startupChain) {
                    val next = p.tankIndex + 1
                    val nextTank = tanks.getOrNull(next)
                    if (next < tankCount && nextTank != null && nextTank.settings1Ready && nextTank.settings2Ready) {
                        nextTank.checkLoading = true
                        pending = PendingOp(PendingType.CHECK, next, startupChain = true)
                        sendSms("CHECK${next + 1}")
                    } else {
                        antLoading = true
                        pending = PendingOp(PendingType.ANT, -1)
                        sendSms("ANT CHECK")
                    }
                }
            }
            PendingType.ANT -> {
                pending = null
                antLoading = false
                parseAnt(body)
            }
        }
    }

    private fun parseCheck(body: String, i: Int): Boolean {
        val t = tanks.getOrNull(i) ?: return false
        Regex("""(\d{1,3})\s*%""").find(body)?.groupValues?.get(1)?.toIntOrNull()?.let {
            t.waterPercent = it.coerceIn(0, 100)
        }
        Regex("""dist(?:ance)?[:=]?\s*(\d+)""", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)?.toIntOrNull()?.let {
            t.distanceCm = it
        }
        Regex("""(?m)^H\s*=\s*(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull()?.let {
            t.height = it
            t.settings1Ready = true
        }
        Regex("""(?m)^ON\s*=\s*(\d+)\s*%?""", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)?.toIntOrNull()?.let {
            t.onPercent = it.coerceIn(0, 100)
        }
        Regex("""(?m)^OFF\s*=\s*(\d+)\s*%?""", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)?.toIntOrNull()?.let {
            t.offPercent = it.coerceIn(0, 100)
        }
        if (t.onPercent in 0..100 && t.offPercent in 1..100 && t.offPercent > t.onPercent) {
            t.settings2Ready = true
        }
        t.lastCheckedAt = System.currentTimeMillis()
        t.save()
        return t.settings1Ready && t.settings2Ready
    }

    private fun handleRecoveryFailure(index: Int) {
        val firstTank = index == 0
        recoveryMode = false
        recoveryLoading = false
        pending = null

        if (firstTank) {
            // No usable saved defaults were recovered from CHECK1. Reset the
            // controller's tank configuration and start the normal guided setup.
            tankCountLoading = true
            recoveryLoading = true
            pending = PendingOp(PendingType.TANKS_SET, -1)
            sendSms("TANKERS SET$tankCount")
        } else {
            // Tanks 0 until index were recovered successfully from their CHECK
            // replies -- but the board wipes ALL tank config the moment it gets a
            // fresh TANKERS SET, and it still needs one now so it knows the real
            // tank count. So: register the count, then silently re-push the
            // already-known settings for those recovered tanks (progress screen),
            // and only then hand the user off to the first tank that still needs
            // fresh defaults.
            recommitActive = true
            recommitTankCount = index
            recommitStepsTotal = index * 2
            recommitStepsDone = 0
            recommitResumeIndex = index
            pending = PendingOp(PendingType.TANKS_SET, -1, recommit = true)
            sendSms("TANKERS SET$tankCount")
        }
    }

    /** Sends the next SMS in the post-recommit-TANKERS-SET re-push queue (SETTINGS1
     *  then SETTINGS2 for each already-recovered tank, in order), or wraps the whole
     *  pass up once every step has a confirmed reply. */
    private fun advanceRecommit() {
        val tankIdx = recommitStepsDone / 2
        val stage = recommitStepsDone % 2
        if (tankIdx >= recommitTankCount) {
            recommitActive = false
            setupWizardActive = true
            screen = Screen.Tank(recommitResumeIndex)
            partialRecoveryIndex = recommitResumeIndex
            showPartialRecoveryBanner = true
            return
        }
        val t = tanks[tankIdx]
        if (stage == 0) {
            pending = PendingOp(PendingType.HEIGHT, tankIdx, recommit = true)
            sendSms("TANK${tankIdx + 1} SETTINGS1 ${t.height}")
        } else {
            pending = PendingOp(PendingType.PERCENTS, tankIdx, recommit = true)
            sendSms("TANK${tankIdx + 1} SETTINGS2 ${t.onPercent}/${t.offPercent}")
        }
    }

    fun onRecoveryCompletionBannerFinished() {
        if (!recoveryCompletionPendingAnt) return
        recoveryCompletionPendingAnt = false
        antLoading = true
        pending = PendingOp(PendingType.ANT, -1)
        sendSms("ANT CHECK")
    }

    private fun parseAnt(body: String) {
        Regex("""CSQ:\s*(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull()?.let {
            gsmCsq = it
            prefs.edit().putInt("gsm_csq", it).apply()
        }
        Regex("""Tank\s+(\d+)\s+NRF:\s*(\d+)\s*/\s*100""", RegexOption.IGNORE_CASE).findAll(body).forEach { mr ->
            val idx = (mr.groupValues[1].toIntOrNull() ?: 0) - 1
            val score = mr.groupValues[2].toIntOrNull()
            if (idx in 0..3 && score != null) {
                tanks[idx].nrfScore = score
                tanks[idx].save()
            }
        }
    }
}
