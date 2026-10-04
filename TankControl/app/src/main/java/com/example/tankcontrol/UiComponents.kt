package com.example.tankcontrol

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sin


/** Shared vertical/horizontal drawing bounds so the tank body, its height arrow,
 *  its pump-threshold arrows, and its diameter arrow all line up exactly --
 *  they must all use these same fractions against their own Canvas size. */
private fun tankVBounds(hPx: Float): Pair<Float, Float> = (hPx * 0.07f) to (hPx - 8f)
private fun tankHBounds(wPx: Float): Pair<Float, Float> = (wPx * 0.14f) to (wPx * 0.86f)

private fun formatTime(epochMillis: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(epochMillis))
}

private fun formatDate(epochMillis: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(epochMillis))
}

/** Soft translucent gradient "liquid glass" look, built from plain Compose
 *  gradients (no platform-version-dependent blur needed). */
private fun Modifier.liquidGlass(corner: Dp = 18.dp): Modifier = this
    .clip(RoundedCornerShape(corner))
    .background(
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.16f),
                AccentBlue.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.05f)
            )
        )
    )
    .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(corner))

// ================= ROOT =================

private fun screenOrder(state: AppState): List<Screen> =
    buildList {
        add(Screen.Home)
        for (i in 0 until state.tankCount) add(Screen.Tank(i))
        add(Screen.Settings)
    }

@Composable
fun TankControlApp(state: AppState) {
    // Physical layout stays fixed (left/right) regardless of language or device locale;
    // Persian text still shapes/reads right-to-left inside each Text composable on its own.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxSize()) {
            TechBackground()
            Column(
                Modifier
                    .fillMaxSize()
                    .then(if (state.recoveryLoading || state.recommitActive) Modifier.blur(4.dp) else Modifier)
            ) {
                val order = remember(state.tankCount) { screenOrder(state) }
                var dragAccum by remember { mutableStateOf(0f) }
                Box(
                    Modifier
                        .weight(1f)
                        .pointerInput(order) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragAccum = 0f },
                                onDragEnd = {
                                    val idx = order.indexOf(state.screen)
                                    if (idx >= 0) {
                                        if (dragAccum <= -120f && idx < order.size - 1) {
                                            state.screen = order[idx + 1]
                                        } else if (dragAccum >= 120f && idx > 0) {
                                            state.screen = order[idx - 1]
                                        }
                                    }
                                    dragAccum = 0f
                                },
                                onHorizontalDrag = { change, amount ->
                                    dragAccum += amount
                                    change.consume()
                                }
                            )
                        }
                ) {
                    when (val s = state.screen) {
                        Screen.Language -> LanguageScreen(state)
                        Screen.ReliabilitySetup -> ReliabilitySetupScreen(state)
                        Screen.Setup -> SetupScreen(state)
                        Screen.Home -> HomeScreen(state)
                        is Screen.Tank -> TankScreen(state, s.index)
                        Screen.Settings -> SettingsMenuScreen(state)
                        Screen.SettingsAccount -> SettingsAccountScreen(state)
                        Screen.SettingsLanguage -> SettingsLanguageScreen(state)
                        Screen.SettingsSim -> SettingsSimScreen(state)
                        Screen.SettingsTankCount -> SettingsTankCountScreen(state)
                        Screen.SettingsHelp -> SettingsHelpScreen(state)
                        Screen.SettingsAbout -> SettingsAboutScreen(state)
                        Screen.SettingsAutoUpdate -> SettingsAutoUpdateScreen(state)
                        Screen.SettingsReliability -> SettingsReliabilityScreen(state)
                    }
                }
                if (showBottomNav(state.screen)) {
                    BottomNav(state)
                }
            }
            DefaultsReminderBanner(state)
            AllSavedBanner(state)
            PartialRecoveryBanner(state)
            RecoveryLoadingOverlay(state)
            RecommitProgressOverlay(state)
            SmsPermissionWarningBanner(state)
        }
    }
}

private fun showBottomNav(screen: Screen) =
    screen !is Screen.Language && screen !is Screen.Setup && screen != Screen.ReliabilitySetup

private fun isSettingsScreen(s: Screen) =
    s == Screen.Settings || s == Screen.SettingsAccount || s == Screen.SettingsLanguage ||
        s == Screen.SettingsSim || s == Screen.SettingsTankCount || s == Screen.SettingsHelp ||
        s == Screen.SettingsAbout || s == Screen.SettingsAutoUpdate || s == Screen.SettingsReliability

@Composable
fun TechBackground() {
    Box(
        Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        Image(
            painter = painterResource(id = R.drawable.bg_circuit),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.18f,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun SignalBars(score: Int?, max: Int, modifier: Modifier = Modifier) {
    val level = if (score == null) 0 else {
        val ratio = score.toFloat() / max.toFloat()
        when {
            ratio >= 0.95f -> 4
            ratio >= 0.75f -> 3
            ratio >= 0.3f -> 2
            else -> 1
        }
    }
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        for (bar in 1..4) {
            val active = bar <= level
            Box(
                Modifier
                    .padding(horizontal = 1.dp)
                    .width(4.dp)
                    .height((6 + bar * 4).dp)
                    .background(if (active) PumpGreen else Color(0xFF565D64))
            )
        }
    }
}

@Composable
fun RecoveryLoadingOverlay(state: AppState) {
    if (!state.recoveryLoading) return
    val strings = state.strings()
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.48f))
            .clickable(enabled = true) { /* block interaction while registering */ },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                strings.registeringDefaults,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = AccentBlue,
                textAlign = TextAlign.Center,
                style = LocalTextStyle.current.copy(
                    shadow = Shadow(AccentBlue, blurRadius = 18f)
                )
            )
            Spacer(Modifier.height(18.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(42.dp),
                strokeWidth = 4.dp,
                color = AccentBlue
            )
        }
    }
}

private fun recommitHeadingText(state: AppState): String {
    val n = state.recommitTankCount
    return if (state.language == Lang.FA) {
        val words = listOf("یک", "دو", "سه", "چهار")
        val word = words.getOrElse(n - 1) { n.toString() }
        "در حال ثبت اطلاعات $word تانکر"
    } else {
        "Registering tank${if (n == 1) "" else "s"} info ($n)"
    }
}

/** Shown while already-known tank settings are being silently re-pushed to the
 *  board (see AppState.recommitActive) -- a "liquid glass" bar fills with yellow
 *  as each SETTINGS SMS gets its reply. */
@Composable
fun RecommitProgressOverlay(state: AppState) {
    if (!state.recommitActive) return
    val progress = if (state.recommitStepsTotal > 0) {
        state.recommitStepsDone.toFloat() / state.recommitStepsTotal.toFloat()
    } else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(500),
        label = "recommitProgress"
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(PageBackground.copy(alpha = 0.92f))
            .clickable(enabled = true) { /* block interaction while re-registering */ },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth(0.8f)
        ) {
            Text(
                recommitHeadingText(state),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Yellow,
                textAlign = TextAlign.Center,
                style = LocalTextStyle.current.copy(
                    shadow = Shadow(Color.Yellow, blurRadius = 16f)
                )
            )
            Spacer(Modifier.height(22.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .liquidGlass(corner = 8.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFFD54A), Color(0xFFFFC107))
                            )
                        )
                )
            }
        }
    }
}

/** Persistent (never auto-dismisses, no fade) warning strip pinned to the top of
 *  every screen whenever RECEIVE_SMS/READ_SMS isn't granted -- outgoing commands
 *  still send, but the board's replies can never come back, so the app would
 *  otherwise look like it's silently doing nothing. Clears itself automatically
 *  once MainActivity detects the permission is granted (after the request
 *  dialog, or after the user grants it from phone Settings and returns). */
@Composable
fun SmsPermissionWarningBanner(state: AppState) {
    if (!state.smsPermissionMissing) return
    val strings = state.strings()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Text(
            strings.smsPermissionWarning,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFB3261E))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

@Composable
fun DefaultsReminderBanner(state: AppState) {
    if (!state.showDefaultsReminder) return
    val strings = state.strings()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
        delay(3300)
        visible = false
        delay(700)
        state.showDefaultsReminder = false
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(700), label = "defaultsReminderAlpha")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(strings.defaultsReminder, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.alpha(alpha).background(SurfaceDark.copy(alpha = .92f), RoundedCornerShape(16.dp)).padding(horizontal = 24.dp, vertical = 18.dp))
    }
}

private fun defaultPhonePrefix(context: android.content.Context): String {
    val iso = (context.getSystemService(android.content.Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager)?.simCountryIso?.uppercase().orEmpty()
    val codes = mapOf("IR" to "+98", "NL" to "+31", "US" to "+1", "CA" to "+1", "GB" to "+44", "DE" to "+49", "FR" to "+33", "IT" to "+39", "ES" to "+34", "TR" to "+90", "AE" to "+971", "SA" to "+966", "IQ" to "+964", "AF" to "+93", "IN" to "+91", "PK" to "+92", "AU" to "+61", "CN" to "+86", "JP" to "+81", "KR" to "+82", "RU" to "+7")
    return codes[iso] ?: "+98"
}

@Composable
fun AllSavedBanner(state: AppState) {
    if (!state.showAllSavedBanner) return
    val strings = state.strings()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
        delay(3000)
        visible = false
        delay(700)
        state.showAllSavedBanner = false
        if (state.recoveryCompletionPendingAnt) {
            state.onRecoveryCompletionBannerFinished()
        } else {
            state.screen = Screen.Home
        }
    }
    val alpha by animateFloatAsState(targetValue = if (visible) 1f else 0f, animationSpec = tween(700), label = "savedBannerAlpha")
    Box(
        Modifier
            .fillMaxSize()
            .background(PageBackground.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            strings.allSavedMessage,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = AccentBlue,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(alpha),
            style = LocalTextStyle.current.copy(
                shadow = Shadow(AccentBlue, blurRadius = 20f)
            )
        )
    }
}

private fun partialRecoveryMessage(state: AppState): String {
    val n = state.partialRecoveryIndex
    val count = n.coerceAtLeast(1)
    if (state.language == Lang.FA) {
        val ord = listOf("اول", "دوم", "سوم", "چهارم")
        val saved = (0 until count).joinToString(" و ") { "تانکر ${ord.getOrElse(it) { (it + 1).toString() }}" }
        val current = "تانکر ${ord.getOrElse(n) { (n + 1).toString() }}"
        return "اطلاعات $saved بازیابی شدند\nلطفاً دیفالت‌های $current را ثبت بکنید"
    }
    val saved = (0 until count).joinToString(" and ") { "Tank ${it + 1}" }
    val current = "Tank ${n + 1}"
    return "$saved information was restored.\nPlease enter the defaults for $current"
}

@Composable
fun PartialRecoveryBanner(state: AppState) {
    if (!state.showPartialRecoveryBanner) return
    val strings = state.strings()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(state.partialRecoveryIndex) {
        visible = true
        delay(4500)
        visible = false
        delay(700)
        state.showPartialRecoveryBanner = false
    }
    val alpha by animateFloatAsState(targetValue = if (visible) 1f else 0f, animationSpec = tween(700), label = "partialRecoveryAlpha")
    Box(
        Modifier
            .fillMaxSize()
            .background(PageBackground.copy(alpha = 0.90f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            partialRecoveryMessage(state),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Yellow,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .alpha(alpha),
            style = LocalTextStyle.current.copy(
                shadow = Shadow(Color.Yellow, blurRadius = 18f)
            )
        )
    }
}

@Composable
fun CompactNumberField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    enabled: Boolean = true,
    width: Dp = 54.dp,
    height: Dp = 40.dp
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(SurfaceDarkAlt, RoundedCornerShape(6.dp))
            .border(1.dp, AccentBlue, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = TextPrimary, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(AccentBlue),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )
    }
}

private fun selectedNumber(n: Int): TextFieldValue {
    val s = n.toString()
    return TextFieldValue(s, selection = TextRange(0, s.length))
}

@Composable
fun EditTimeout(active: Boolean, onTimeout: () -> Unit) {
    LaunchedEffect(active) {
        if (active) {
            delay(150_000)
            onTimeout()
        }
    }
}

// ================= ONBOARDING =================

@Composable
fun LanguageScreen(state: AppState) {
    val strings = state.strings()
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(strings.chooseLanguageTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(28.dp))
        Button(onClick = { state.changeLanguage(Lang.EN) }, modifier = Modifier.fillMaxWidth(0.7f)) {
            Text(EnStrings.english)
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { state.changeLanguage(Lang.FA) }, modifier = Modifier.fillMaxWidth(0.7f)) {
            Text(FaStrings.farsi)
        }
    }
}


@Composable
fun SetupScreen(state: AppState) {
    val strings = state.strings()
    val context = LocalContext.current
    val defaultPrefix = remember { defaultPhonePrefix(context) }
    var countryCode by remember { mutableStateOf(defaultPrefix) }
    var localNumber by remember { mutableStateOf("") }
    val simOptions = remember(state.permissionTick) { getSimSubscriptions(context) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text(strings.setupTitle, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(32.dp))
        Text(strings.setupPrompt, textAlign = TextAlign.Center, fontSize = 16.sp, color = TextPrimary)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = countryCode,
                onValueChange = { countryCode = it.filter { c -> c == '+' || c.isDigit() }.take(5) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.width(82.dp)
            )
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(
                value = localNumber,
                onValueChange = { localNumber = it.filter(Char::isDigit) },
                placeholder = { Text("**********") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { state.onEnterSimNumber(countryCode + localNumber) }) { Text(strings.enter) }
        }

        if (simOptions.size > 1) {
            Spacer(Modifier.height(28.dp))
            Text(strings.chooseSimTitle, fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            SimPickerList(state, simOptions)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ================= HOME =================

@Composable
fun HomeScreen(state: AppState) {
    val strings = state.strings()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, BorderGray),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AntCheckButton(loading = state.antLoading, enabled = state.pending == null) { state.onAntCheck() }
                    Spacer(Modifier.width(10.dp))
                    Text(strings.updateAntennaStatus, fontSize = 12.sp, color = TextSecondary)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${strings.gsm} ${state.gsmCsq ?: 0}/31", fontSize = 13.sp, color = TextPrimary)
                    Spacer(Modifier.width(8.dp))
                    SignalBars(state.gsmCsq, 31)
                }
                for (i in 0 until state.tankCount) {
                    val t = state.tanks[i]
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${state.tankLabel(i)} ${strings.nrf} ${t.nrfScore ?: 0}/100", fontSize = 12.sp, color = TextSecondary)
                        Spacer(Modifier.width(8.dp))
                        SignalBars(t.nrfScore, 100)
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        if (!state.tankCountConfigured) {
            TankCountControl(state)
        }

        if (state.tankCount > 0) {
            Spacer(Modifier.height(32.dp))
            TankStatusStrip(state)
        }
    }
}

/** Two cards per row (the original side-by-side layout for up to 2 tanks),
 *  wrapping to a new row below for the 3rd/4th -- instead of squeezing all
 *  four into one increasingly narrow row. */
@Composable
fun TankStatusStrip(state: AppState) {
    val indices = (0 until state.tankCount).toList()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        indices.chunked(2).forEach { rowIndices ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowIndices.forEach { i ->
                    TankGlassCard(state, i, modifier = Modifier.weight(1f))
                }
                if (rowIndices.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun TankGlassCard(state: AppState, index: Int, modifier: Modifier = Modifier) {
    val strings = state.strings()
    val t = state.tanks[index]
    Box(
        modifier = modifier
            .aspectRatio(0.85f)
            .liquidGlass(18.dp)
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(state.tankLabel(index), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(1.dp))
            Text(t.waterPercent?.let { "$it%" } ?: strings.notSetYet, fontSize = 13.sp, color = TextSecondary)
            val liters = t.liters()
            if (liters != null) {
                Spacer(Modifier.height(0.dp))
                Text("${"%.1f".format(liters)} ${strings.liters}", fontSize = 11.sp, color = TextSecondary)
            }
            val pumpState = t.pumpOn()
            when (pumpState) {
                true -> {
                    Spacer(Modifier.height(1.dp))
                    Text("ON", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PumpGreen)
                }
                false -> {
                    Spacer(Modifier.height(1.dp))
                    Text("OFF", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PumpRed)
                }
                null -> {}
            }
            // Keep the update information very compact so all details fit inside the glass card.
            val ts = t.lastCheckedAt
            if (ts != null) {
                Spacer(Modifier.height(1.dp))
                Text(strings.lastUpdated, fontSize = 9.sp, color = TextSecondary)
                Spacer(Modifier.height(1.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(formatDate(ts), fontSize = 9.sp, color = TextSecondary)
                    Text(formatTime(ts), fontSize = 9.sp, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun AntCheckButton(loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val clickable = !loading && enabled
    Box(
        modifier = Modifier
            .size(40.dp)
            .alpha(if (clickable) 1f else 0.4f)
            .clip(CircleShape)
            .background(SurfaceDarkAlt)
            .then(if (clickable) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Filled.Refresh, contentDescription = "Check", tint = AccentBlue)
        }
    }
}

@Composable
fun TankCountControl(state: AppState) {
    val strings = state.strings()
    var value by remember { mutableStateOf("") }
    val busy = state.tankCountLoading || state.recoveryLoading

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceDark,
        border = BorderStroke(1.dp, BorderGray),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                strings.tanksPrompt,
                fontSize = 13.sp,
                color = TextPrimary,
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .alpha(if (busy) 0.4f else 1f)
            )
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(if (busy) 0.4f else 1f)
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.filter(Char::isDigit).take(1) },
                    enabled = !busy,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(76.dp)
                )
                Spacer(Modifier.width(10.dp))
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Button(onClick = { value.toIntOrNull()?.let { state.onEnterTankCount(it) } }) {
                        Text(strings.enter)
                    }
                }
            }
        }
    }
}

// ================= TANK PAGE =================

@Composable
fun TankScreen(state: AppState, index: Int) {
    val strings = state.strings()
    val tank = state.tanks.getOrNull(index) ?: return
    val cylinderHeight = 220.dp
    val anyEditing = tank.editingHeight || tank.editingPercents || tank.editingDiameter
    val wizardChecking = state.pending?.let { it.type == PendingType.CHECK && it.wizardAdvance } == true

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .pointerInput(anyEditing) {
                    detectTapGestures {
                        if (anyEditing) state.onEditTimeout(index)
                    }
                }
                .then(if (wizardChecking) Modifier.blur(8.dp) else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(state.tankLabel(index), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(18.dp))

            if (!tank.settings1Ready) {
                HeightEntryPanel(state, index)
            }
            if (tank.settings1Ready && !tank.settings2Ready) {
                Spacer(Modifier.height(16.dp))
                PercentEntryPanel(state, index)
            }

            Spacer(Modifier.height(26.dp))

            Row(verticalAlignment = Alignment.Top) {
                if (tank.settings1Ready) {
                    HeightDimensionDisplay(
                        state, index, cylinderHeight, strings,
                        modifier = Modifier.padding(top = 34.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                if (tank.settings2Ready) {
                    ThresholdArrows(
                        state, index, cylinderHeight,
                        modifier = Modifier.padding(top = 34.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                }
                Column {
                    DiameterArrow(state, index, width = 150.dp)
                    Spacer(Modifier.height(4.dp))
                    TankCylinderView(tank = tank, modifier = Modifier.width(150.dp).height(cylinderHeight))
                }
            }

            Spacer(Modifier.height(120.dp))
        }

        if (tank.nrfScore != null) {
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text("${strings.nrf} ${tank.nrfScore}/100", fontSize = 11.sp, color = TextSecondary)
                Spacer(Modifier.height(2.dp))
                SignalBars(tank.nrfScore, 100)
            }
        }

        if (tank.settings1Ready && tank.settings2Ready) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 14.dp)
                    .liquidGlass(16.dp)
                    .padding(12.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    tank.waterPercent?.let { "$it%" } ?: strings.notSetYet,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                val liters = tank.liters()
                if (liters != null) {
                    Text("${"%.1f".format(liters)} ${strings.liters}", fontSize = 12.sp, color = TextSecondary)
                }
                Spacer(Modifier.height(8.dp))
                AntCheckButton(loading = tank.checkLoading, enabled = state.pending == null) {
                    state.onCheckTank(index)
                }
                Spacer(Modifier.height(4.dp))
                Text(strings.updateTankStatus, fontSize = 10.sp, color = TextSecondary)
            }
        }

        if (wizardChecking) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(PageBackground.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentBlue)
            }
        }
    }
}

@Composable
fun HeightEntryPanel(state: AppState, index: Int) {
    val strings = state.strings()
    val tank = state.tanks[index]
    var value by remember { mutableStateOf("") }
    Column(Modifier.alpha(if (tank.loading) 0.4f else 1f)) {
        Text(strings.tankHeightPrompt, fontSize = 13.sp, color = TextPrimary)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter(Char::isDigit).take(4) },
                enabled = !tank.loading,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(100.dp)
            )
            Spacer(Modifier.width(12.dp))
            if (tank.loading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Button(onClick = { value.toIntOrNull()?.let { state.onEnterHeight(index, it) } }) {
                    Text(strings.enter)
                }
            }
        }
    }
}

/** ON -> OFF stage switch during FIRST-time entry gets an animated
 *  slide/fade transition (arrow-tap editing later shows both fields at
 *  once, so this transition only applies here). */
@Composable
fun PercentEntryPanel(state: AppState, index: Int) {
    val strings = state.strings()
    val tank = state.tanks[index]
    var onValue by remember { mutableStateOf("") }
    var offValue by remember { mutableStateOf("") }
    val onConfirmed = tank.onPercent > 0

    Column(Modifier.alpha(if (tank.loading) 0.4f else 1f)) {
        AnimatedContent(
            targetState = onConfirmed,
            transitionSpec = {
                (slideInHorizontally(initialOffsetX = { it }) + fadeIn()) togetherWith
                    (slideOutHorizontally(targetOffsetX = { -it }) + fadeOut())
            },
            label = "percentStageTransition"
        ) { confirmed ->
            if (!confirmed) {
                Column {
                    Text(strings.pumpOnPrompt, fontSize = 13.sp, color = TextPrimary)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = onValue,
                            onValueChange = { onValue = it.filter(Char::isDigit).take(3) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(100.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = { onValue.toIntOrNull()?.let { state.onEnterOnPercent(index, it) } }) {
                            Text(strings.enter)
                        }
                    }
                }
            } else {
                Column {
                    Text(strings.pumpOffPrompt, fontSize = 13.sp, color = TextPrimary)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = offValue,
                            onValueChange = { offValue = it.filter(Char::isDigit).take(3) },
                            enabled = !tank.loading,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(100.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        if (tank.loading) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Button(onClick = { offValue.toIntOrNull()?.let { state.onEnterOffPercent(index, it) } }) {
                                Text(strings.enter)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ================= DRAWING =================

/** Two SEPARATE arrows (not one continuous double-headed line): an upper one
 *  pointing up to the tank's top edge, a lower one pointing down to the tank's
 *  bottom edge, with a gap between them where the "N cm" label sits. */
@Composable
fun DimensionArrow(height: Dp, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .width(20.dp)
            .height(height)
    ) {
        val (top, bottom) = tankVBounds(size.height)
        val x = size.width / 2f
        val arrow = 10f
        val mid = (top + bottom) / 2f
        val gap = 22f

        drawLine(AccentBlue, Offset(x, mid - gap / 2), Offset(x, top), strokeWidth = 3f)
        drawLine(AccentBlue, Offset(x, top), Offset(x - arrow / 2, top + arrow), strokeWidth = 3f)
        drawLine(AccentBlue, Offset(x, top), Offset(x + arrow / 2, top + arrow), strokeWidth = 3f)

        drawLine(AccentBlue, Offset(x, mid + gap / 2), Offset(x, bottom), strokeWidth = 3f)
        drawLine(AccentBlue, Offset(x, bottom), Offset(x - arrow / 2, bottom - arrow), strokeWidth = 3f)
        drawLine(AccentBlue, Offset(x, bottom), Offset(x + arrow / 2, bottom - arrow), strokeWidth = 3f)
    }
}

@Composable
fun HeightDimensionDisplay(
    state: AppState, index: Int, height: Dp, strings: AppStrings, modifier: Modifier = Modifier
) {
    val tank = state.tanks[index]
    var draft by remember(tank.editingHeight) { mutableStateOf(selectedNumber(tank.height)) }

    Box(
        modifier = modifier
            .width(60.dp)
            .height(height)
            .then(if (!tank.editingHeight) Modifier.clickable { state.onStartEditHeight(index) } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        DimensionArrow(height = height)
        if (!tank.editingHeight) {
            Surface(color = PageBackground, shape = RoundedCornerShape(4.dp)) {
                Text(
                    "${tank.height} ${strings.cm}",
                    fontSize = 10.sp,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CompactNumberField(
                    value = draft,
                    onValueChange = { draft = it.copy(text = it.text.filter(Char::isDigit).take(4)) },
                    enabled = !tank.loading,
                    width = 54.dp,
                    height = 40.dp
                )
                Spacer(Modifier.height(4.dp))
                if (tank.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(
                        onClick = { draft.text.toIntOrNull()?.let { state.onEnterHeight(index, it) } },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Confirm", tint = AccentBlue)
                    }
                }
            }
        }
    }
}

@Composable
fun ThresholdArrows(state: AppState, index: Int, boxHeight: Dp, modifier: Modifier = Modifier) {
    val tank = state.tanks[index]
    val glowGreen = tank.waterPercent != null && tank.waterPercent!! < tank.onPercent
    val glowRed = tank.waterPercent != null && tank.waterPercent!! > tank.offPercent
    var onDraft by remember(tank.editingPercents) { mutableStateOf(selectedNumber(tank.onPercent)) }
    var offDraft by remember(tank.editingPercents) { mutableStateOf(selectedNumber(tank.offPercent)) }

    Box(
        modifier
            .width(96.dp)
            .height(boxHeight)
            .then(if (!tank.editingPercents) Modifier.clickable { state.onStartEditPercents(index) } else Modifier)
    ) {
        Canvas(Modifier.matchParentSize()) {
            val (top, bottom) = tankVBounds(size.height)
            val w = size.width
            val shaftStartX = w * 0.7f
            val tipX = w
            val onY = bottom - (bottom - top) * (tank.onPercent / 100f)
            val offY = bottom - (bottom - top) * (tank.offPercent / 100f)
            val greenColor = PumpGreen.copy(alpha = if (glowGreen) 1f else 0.45f)
            val redColor = PumpRed.copy(alpha = if (glowRed) 1f else 0.45f)
            val greenW = if (glowGreen) 5f else 3f
            val redW = if (glowRed) 5f else 3f

            drawLine(greenColor, Offset(shaftStartX, onY), Offset(tipX, onY), strokeWidth = greenW)
            drawLine(greenColor, Offset(tipX, onY), Offset(tipX - 8f, onY - 5f), strokeWidth = greenW)
            drawLine(greenColor, Offset(tipX, onY), Offset(tipX - 8f, onY + 5f), strokeWidth = greenW)

            drawLine(redColor, Offset(shaftStartX, offY), Offset(tipX, offY), strokeWidth = redW)
            drawLine(redColor, Offset(tipX, offY), Offset(tipX - 8f, offY - 5f), strokeWidth = redW)
            drawLine(redColor, Offset(tipX, offY), Offset(tipX - 8f, offY + 5f), strokeWidth = redW)
        }

        // Keep labels/fields on the exact same Y scale as the tank Canvas:
        // tankVBounds() uses 7% top inset and 8px bottom inset.
        val topInset = boxHeight * 0.07f
        val bottomInset = 8.dp
        val usable = boxHeight - topInset - bottomInset

        if (!tank.editingPercents) {
            Text(
                "${tank.onPercent}% ON",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                color = if (glowGreen) PumpGreen else PumpGreen.copy(alpha = 0.55f),
                modifier = Modifier
                    .width(64.dp)
                    .offset(y = topInset + usable * (1f - tank.onPercent / 100f) - 6.dp)
            )
            Text(
                "${tank.offPercent}% OFF",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                color = if (glowRed) PumpRed else PumpRed.copy(alpha = 0.55f),
                modifier = Modifier
                    .width(64.dp)
                    .offset(y = topInset + usable * (1f - tank.offPercent / 100f) - 6.dp)
            )
        } else {
            Box(Modifier.offset(y = topInset + usable * (1f - tank.onPercent / 100f) - 20.dp)) {
                CompactNumberField(
                    value = onDraft,
                    onValueChange = { onDraft = it.copy(text = it.text.filter(Char::isDigit).take(3)) },
                    enabled = !tank.loading,
                    width = 52.dp,
                    height = 40.dp
                )
            }
            Box(Modifier.offset(y = topInset + usable * (1f - tank.offPercent / 100f) - 20.dp)) {
                CompactNumberField(
                    value = offDraft,
                    onValueChange = { offDraft = it.copy(text = it.text.filter(Char::isDigit).take(3)) },
                    enabled = !tank.loading,
                    width = 52.dp,
                    height = 40.dp
                )
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(bottom = 2.dp)) {
                if (tank.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(
                        onClick = {
                            val on = onDraft.text.toIntOrNull()
                            val off = offDraft.text.toIntOrNull()
                            if (on != null && off != null) state.onConfirmThresholds(index, on, off)
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Confirm", tint = AccentBlue)
                    }
                }
            }
        }
    }
}

@Composable
fun DiameterArrow(state: AppState, index: Int, width: Dp) {
    val strings = state.strings()
    val tank = state.tanks[index]
    var draft by remember(tank.editingDiameter) { mutableStateOf(selectedNumber(tank.diameterCm ?: 0)) }

    Box(
        Modifier
            .width(width)
            .height(30.dp)
            .then(if (!tank.editingDiameter) Modifier.clickable { state.onStartEditDiameter(index) } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (!tank.editingDiameter) {
            Canvas(Modifier.fillMaxSize()) {
                val (leftX, rightX) = tankHBounds(size.width)
                val yMid = size.height / 2f
                val arrow = 8f
                drawLine(AccentBlue, Offset(leftX, yMid), Offset(rightX, yMid), strokeWidth = 3f)
                drawLine(AccentBlue, Offset(leftX, yMid), Offset(leftX + arrow, yMid - arrow * 0.7f), strokeWidth = 3f)
                drawLine(AccentBlue, Offset(leftX, yMid), Offset(leftX + arrow, yMid + arrow * 0.7f), strokeWidth = 3f)
                drawLine(AccentBlue, Offset(rightX, yMid), Offset(rightX - arrow, yMid - arrow * 0.7f), strokeWidth = 3f)
                drawLine(AccentBlue, Offset(rightX, yMid), Offset(rightX - arrow, yMid + arrow * 0.7f), strokeWidth = 3f)
            }
            Surface(color = PageBackground, shape = RoundedCornerShape(4.dp)) {
                Text(
                    tank.diameterCm?.let { "$it ${strings.cm}" } ?: strings.diameterPrompt,
                    fontSize = 10.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompactNumberField(
                    value = draft,
                    onValueChange = { draft = it.copy(text = it.text.filter(Char::isDigit).take(4)) },
                    width = 54.dp,
                    height = 30.dp
                )
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = { draft.text.toIntOrNull()?.let { state.onSetDiameter(index, it) } },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Confirm", tint = AccentBlue)
                }
            }
        }
    }
}

@Composable
fun TankCylinderView(tank: TankUiState, modifier: Modifier = Modifier) {
    val displayPercent = tank.waterPercent
    val animatedPercent by animateFloatAsState(
        targetValue = (displayPercent ?: 0).coerceIn(0, 100).toFloat(),
        label = "waterLevel"
    )
    val infinite = rememberInfiniteTransition(label = "wave")
    val wavePhase1 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "wavePhase1"
    )
    val wavePhase2 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing)),
        label = "wavePhase2"
    )

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val (left, right) = tankHBounds(w)
            val (top, bottom) = tankVBounds(h)
            val cr = w * 0.14f
            val outline = RoundRect(Rect(left, top, right, bottom), CornerRadius(cr, cr))
            val clip = Path().apply { addRoundRect(outline) }

            fun wavePath(phase: Float, amp: Float, fillTop: Float): Path = Path().apply {
                moveTo(left, bottom)
                lineTo(left, fillTop)
                val segments = 8
                val segWidth = (right - left) / segments
                for (i in 0 until segments) {
                    val startX = left + segWidth * i
                    val endX = (startX + segWidth).coerceAtMost(right)
                    val midX = (startX + endX) / 2f
                    val cy = fillTop + sin(phase + i).toFloat() * amp
                    quadraticTo(midX, cy, endX, fillTop)
                }
                lineTo(right, bottom)
                close()
            }

            clipPath(clip) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF20262B), Color(0xFF3A424A), Color(0xFF20262B))
                    ),
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top)
                )
                if (displayPercent != null) {
                    val fillTop = bottom - (bottom - top) * (animatedPercent / 100f)
                    drawPath(wavePath(wavePhase2, 4f, fillTop), color = WaterBlue.copy(alpha = 0.55f))
                    drawPath(
                        wavePath(wavePhase1, 7f, fillTop),
                        brush = Brush.verticalGradient(listOf(Color(0xFF7FCBFF), WaterBlue))
                    )
                }
                drawRect(
                    color = Color.White.copy(alpha = 0.05f),
                    topLeft = Offset(left + (right - left) * 0.1f, top),
                    size = Size((right - left) * 0.16f, bottom - top)
                )
            }
            drawOval(
                brush = Brush.verticalGradient(listOf(Color(0xFF525C65), Color(0xFF2E353B))),
                topLeft = Offset(left, top - (right - left) * 0.05f),
                size = Size(right - left, (right - left) * 0.11f)
            )
            drawRoundRect(
                color = Color(0xFF525C65),
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                cornerRadius = CornerRadius(cr, cr),
                style = Stroke(width = 3f)
            )
        }
        if (displayPercent != null) {
            Text(
                "$displayPercent%",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            )
        }
    }
}

// ================= SETTINGS =================

@Composable
fun BackRow(title: String, backLabel: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("< $backLabel") }
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}

@Composable
fun SettingsMenuScreen(state: AppState) {
    val strings = state.strings()
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(strings.settingsTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(16.dp))
        SettingsMenuItem(strings.settingsAccount) { state.screen = Screen.SettingsAccount }
        SettingsMenuItem(strings.settingsLanguage) { state.screen = Screen.SettingsLanguage }
        SettingsMenuItem(strings.settingsSim) { state.screen = Screen.SettingsSim }
        SettingsMenuItem(strings.settingsTankCount) { state.screen = Screen.SettingsTankCount }
        SettingsMenuItem(strings.settingsAutoUpdate) { state.screen = Screen.SettingsAutoUpdate }
        SettingsMenuItem(strings.settingsReliability) { state.screen = Screen.SettingsReliability }
        SettingsMenuItem(strings.settingsHelp) { state.screen = Screen.SettingsHelp }
        SettingsMenuItem(strings.settingsAbout) { state.screen = Screen.SettingsAbout }
    }
}

/** Lets the user pick which SIM subscription outgoing SMS is sent from, when the
 *  device has more than one active SIM. Shown inline wherever the number of
 *  SIMs is relevant -- no navigation of its own. */
@Composable
fun SimPickerList(state: AppState, options: List<SimOption>) {
    Column(Modifier.fillMaxWidth()) {
        options.forEach { option ->
            val selected = state.chosenSubscriptionId == option.subscriptionId
            Surface(
                onClick = { state.onChooseSubscription(option.subscriptionId) },
                color = if (selected) SurfaceDarkAlt else SurfaceDark,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (selected) AccentBlue else BorderGray),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected, onClick = { state.onChooseSubscription(option.subscriptionId) })
                    Spacer(Modifier.width(8.dp))
                    Text(option.label, fontSize = 14.sp, color = TextPrimary)
                }
            }
        }
    }
}

@Composable
fun SettingsMenuItem(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderGray),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 15.sp, color = TextPrimary)
            Text(">", color = TextSecondary)
        }
    }
}

@Composable
fun SettingsAccountScreen(state: AppState) {
    val strings = state.strings()
    val context = LocalContext.current
    var name by remember { mutableStateOf(state.contactName) }
    val simOptions = remember(state.permissionTick) { getSimSubscriptions(context) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        BackRow(strings.accountTitle, strings.back) { state.screen = Screen.Settings }
        Spacer(Modifier.height(20.dp))
        Text(strings.contactName, fontSize = 12.sp, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth())

        if (simOptions.size > 1) {
            Spacer(Modifier.height(24.dp))
            Text(strings.chooseSimTitle, fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(10.dp))
            SimPickerList(state, simOptions)
        }

        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            state.onSaveAccount(name)
            state.screen = Screen.Home
        }) { Text(strings.save) }
    }
}

@Composable
fun SettingsLanguageScreen(state: AppState) {
    val strings = state.strings()
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        BackRow(strings.settingsLanguage, strings.back) { state.screen = Screen.Settings }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { state.changeLanguage(Lang.EN); state.screen = Screen.Home },
            modifier = Modifier.fillMaxWidth()
        ) { Text(EnStrings.english) }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { state.changeLanguage(Lang.FA); state.screen = Screen.Home },
            modifier = Modifier.fillMaxWidth()
        ) { Text(FaStrings.farsi) }
    }
}

@Composable
fun SettingsSimScreen(state: AppState) {
    val strings = state.strings()
    var value by remember { mutableStateOf(state.simNumber) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        BackRow(strings.settingsSim, strings.back) { state.screen = Screen.Settings }
        Spacer(Modifier.height(20.dp))
        Text(strings.simTitle, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(strings.simPrompt, fontSize = 12.sp, color = TextSecondary)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Button(onClick = {
                state.onEnterSimNumber(value)
                state.screen = Screen.Home
            }) { Text(strings.save) }
        }
    }
}

@Composable
fun SettingsTankCountScreen(state: AppState) {
    val strings = state.strings()
    var value by remember { mutableStateOf(if (state.tankCount > 0) state.tankCount.toString() else "") }
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        BackRow(strings.settingsTankCount, strings.back) { state.screen = Screen.Settings }
        Spacer(Modifier.height(20.dp))
        Text(strings.tanksPrompt, fontSize = 13.sp, color = TextPrimary)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter(Char::isDigit).take(1) },
                enabled = !state.tankCountLoading,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(80.dp)
            )
            Spacer(Modifier.width(10.dp))
            if (state.tankCountLoading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Button(onClick = { value.toIntOrNull()?.let { state.onEnterTankCount(it) } }) {
                    Text(strings.enter)
                }
            }
        }
    }
}

/** Two choices only: disable automatic refresh, or refresh when the app opens. */
@Composable
fun SettingsAutoUpdateScreen(state: AppState) {
    val strings = state.strings()
    var mode by remember { mutableStateOf(state.autoUpdateMode) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        BackRow(strings.settingsAutoUpdate, strings.back) { state.screen = Screen.Settings }
        Spacer(Modifier.height(20.dp))

        ModeCircle(mode == AutoUpdateMode.OFF, strings.autoOff) { mode = AutoUpdateMode.OFF }
        ModeCircle(mode == AutoUpdateMode.ON_OPEN, strings.autoOnOpen) { mode = AutoUpdateMode.ON_OPEN }

        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            state.onSaveAutoUpdate(mode)
            state.screen = Screen.Home
        }) { Text(strings.save) }
    }
}

@Composable
private fun ReliabilityChecklist(state: AppState) {
    val strings = state.strings()
    val context = LocalContext.current
    var batteryExempt by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var notifAccess by remember { mutableStateOf(isNotificationListenerEnabled(context)) }

    // Re-check after returning from any of these system settings screens, since
    // none of them give us a direct callback.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryExempt = isIgnoringBatteryOptimizations(context)
                notifAccess = isNotificationListenerEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Text(strings.reliabilityIntro, fontSize = 14.sp, color = TextSecondary)
    Spacer(Modifier.height(24.dp))

    Button(
        onClick = {
            requestIgnoreBatteryOptimizations(context)
            batteryExempt = isIgnoringBatteryOptimizations(context)
        },
        enabled = !batteryExempt,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (batteryExempt) strings.reliabilityBatteryDone else strings.reliabilityBatteryButton)
    }

    Spacer(Modifier.height(20.dp))
    Text(strings.reliabilityAutostartDesc, fontSize = 13.sp, color = TextSecondary)
    Spacer(Modifier.height(12.dp))
    Button(onClick = { openAutostartSettings(context) }, modifier = Modifier.fillMaxWidth()) {
        Text(strings.reliabilityAutostartButton)
    }

    Spacer(Modifier.height(20.dp))
    Text(strings.reliabilityNotificationDesc, fontSize = 13.sp, color = TextSecondary)
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = { openNotificationListenerSettings(context) },
        enabled = !notifAccess,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (notifAccess) strings.reliabilityNotificationDone else strings.reliabilityNotificationButton)
    }
}

@Composable
fun SettingsReliabilityScreen(state: AppState) {
    val strings = state.strings()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        BackRow(strings.reliabilityTitle, strings.back) { state.screen = Screen.Settings }
        Spacer(Modifier.height(20.dp))
        ReliabilityChecklist(state)
    }
}

/** Shown once, right after language selection, before the SIM-number entry
 *  screen -- these permissions matter enough for reply delivery that they
 *  shouldn't only be something the user might stumble into from Settings
 *  later. Revisiting them afterward is still possible from Settings. */
@Composable
fun ReliabilitySetupScreen(state: AppState) {
    val strings = state.strings()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(strings.reliabilityTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(20.dp))
        ReliabilityChecklist(state)
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { state.onContinueFromReliabilitySetup() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(strings.continueLabel)
        }
    }
}

@Composable
fun ModeCircle(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp)
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .then(if (selected) Modifier.background(PumpGreen.copy(alpha = 0.25f), CircleShape) else Modifier)
                .border(if (selected) 3.dp else 2.dp, if (selected) PumpGreen else AccentBlue, CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 14.sp, color = TextPrimary)
    }
}

@Composable
fun SettingsHelpScreen(state: AppState) {
    val strings = state.strings()
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        BackRow(strings.settingsHelp, strings.back) { state.screen = Screen.Settings }
        Spacer(Modifier.height(14.dp))
        Text(strings.helpIntro, fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.height(18.dp))
        HelpStep(strings.helpStep1Title, strings.helpStep1Body)
        HelpStep(strings.helpStep2Title, strings.helpStep2Body)
        HelpStep(strings.helpStep3Title, strings.helpStep3Body)
        HelpStep(strings.helpStep4Title, strings.helpStep4Body)
        HelpStep(strings.helpStep5Title, strings.helpStep5Body)
    }
}

@Composable
fun HelpStep(title: String, body: String) {
    Column(Modifier.padding(bottom = 16.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(body, fontSize = 13.sp, color = TextSecondary)
    }
}

@Composable
fun SettingsAboutScreen(state: AppState) {
    val strings = state.strings()
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        BackRow(strings.settingsAbout, strings.back) { state.screen = Screen.Settings }
        Spacer(Modifier.height(48.dp))
        Text(
            strings.aboutCredit,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ================= BOTTOM NAV =================

@Composable
fun BottomNav(state: AppState) {
    val strings = state.strings()
    Surface(color = SurfaceDark, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(strings.home, state.screen == Screen.Home) { state.screen = Screen.Home }
            for (i in 0 until state.tankCount) {
                NavItem(state.tankLabel(i), state.screen == Screen.Tank(i)) { state.screen = Screen.Tank(i) }
            }
            NavItem(strings.settings, isSettingsScreen(state.screen)) { state.screen = Screen.Settings }
        }
    }
}

@Composable
fun NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(horizontal = 2.dp)) {
        Text(
            label,
            fontSize = 12.sp,
            color = if (selected) AccentBlue else TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
