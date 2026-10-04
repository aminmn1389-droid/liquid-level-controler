# Liquid level controler (Android app)
Open this folder in Android Studio (Giraffe+) and build with Gradle, or push it
to a GitHub repo and let the included `.github/workflows/build.yml` build the
debug APK for you (Actions tab -> download the `TankControl-debug-apk` artifact).

Build locally: `./gradlew assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`

## Flow
1. **First run:** choose English or Persian (English listed first / default),
   then enter the SIM number installed on the mother board -> unlocks the app.
2. **Home:** top-left, a compact box to set/change the tank count (1-4) ->
   `TANKERS SETn`. Top-right, GSM signal (score/31 + 4 bars) and each
   registered tank's NRF signal (score/100 + 4 bars). The refresh icon on the
   left (slightly above the vertical middle) re-requests all of that via
   `ANT CHECK` -- this is the only place that triggers a signal refresh.
3. **Each tank** gets its own page, reachable from the bottom bar
   (`Tank1`, `Tank2`, ...):
   - Height (cm) -> `TANKi SETTINGS1 h`. Once set, a permanent two-headed
     arrow along the tank shows the height in cm.
   - Pump ON% then pump OFF% -> `TANKi SETTINGS2 on/off`. Once set, two
     permanent horizontal markers sit at those levels on the tank: green
     "on% ON" and red "off% OFF". Whichever threshold the last Check crossed
     glows (green if the water is below ON%, red if above OFF%).
   - A circular Check icon above the tank sends `CHECKi` on tap. The tank's
     water fill/percentage is driven **only** by that reply, never by typed
     values. Enter a tank diameter (local only, not sent over SMS) to also
     see the amount in liters (cylinder volume from height x diameter x %).
   - That tank's NRF score/100 is shown here too, but only Home's refresh
     icon re-requests it.
4. **Settings** (bottom bar), replacing the old "SIM" screen: Account (your
   name/number, stored locally), Language (English/Persian, switches the
   whole app instantly), SIM card (the mother board's number), and Guide
   (a short step-by-step walkthrough).
5. Settings > About us shows the credit line "ساخته شده توسط محمد امین نوروزی"
   (Built by Mohammad Amin Norouzi in English); no logo or web link.

## Known limitation
`CHECKi`'s reply is built by the mother board as
`"Tank i\n" + <text from the remote tank node> + "\nH=..\nON=..%\nOFF=..%"`.
The remote tank node's own firmware (what actually produces the water-level
text in the middle) wasn't provided, so `AppState.parseCheck()` uses a
best-effort generic regex (first `NN%` found) to read the water percentage.
Share that node's firmware to tighten this to the exact field layout.

## Assumption worth double-checking
Liters are computed from a tank diameter that's entered **only in the app**
(not part of the SMS protocol), since the firmware only ever transmits
height. If a fixed/standard diameter should be assumed instead, or if the
mother board should carry it in `SETTINGS1`, let me know and I'll adjust.

## Latest round of changes
- Dark "classic gray" tech theme with the circuit-board artwork you sent
  layered in as a faint background (its own built-in center glow/edge fade
  is what shows through at low opacity), all text/controls recolored to
  contrast against it.
- Each tank's height and ON/OFF percents can be re-opened for editing (pencil
  icon next to the saved value) after the initial setup; the editor closes
  itself again once the new value is confirmed by the mother board.
- The ON%/OFF% arrow labels now live in their own lane to the left of the
  tank -- only the arrowhead touches the tank wall, so the text never
  overlaps the tank artwork.
- Tanks now have a gradient-shaded body + top rim (pseudo-3D) and a
  two-layer animated wave.
- After applying the tank count, the app walks you through Tank1, Tank2, ...
  automatically as each one's height + percents are confirmed, then shows a
  "All defaults saved" message that fades in/out for ~5s before returning
  to Home.
- The per-tank Check icon is labelled "Update tank status"; Home's antenna
  refresh icon now sits inside the same bordered panel as the GSM/NRF rows
  (that border's height follows however many rows are in it) and is
  labelled "Update antenna status". Tank count now sits clearly below that
  panel, and its number field no longer clips.
- On the SIM setup screen, if the phone has more than one active SIM, you
  can pick which one the app sends commands from (requires READ_PHONE_STATE,
  requested alongside the SMS permissions).
- App icon left untouched, per request.

## Editing height / ON% / OFF% via the arrows
Once a tank is fully configured, tap the height dimension arrow or either
green/red pump arrow right on the tank page to edit it in place:
- A small field (+ a check-mark button) appears right where you tapped,
  pre-filled with the current value.
- Tapping either ON or OFF arrow opens BOTH fields together, because the
  mother board only accepts them as one combined `SETTINGSi on/off`
  command -- so confirming always resends both, even if you only changed one.
- Left open with nothing confirmed for about 2.5 minutes, the edit closes
  itself and reverts to the last saved value.

## About suppressing the mother board's SMS notifications
I looked into this and it isn't something this app can do reliably, so I
held off rather than ship something that only half-works:

Android only lets the phone's **default SMS app** intercept a text before
the rest of the system sees it. Every other app -- including this one --
is notified *after* the default app has already shown its notification, on
a separate broadcast that carries no way to cancel that notification. A
background receiver simply isn't given that power (this is deliberate --
otherwise any app could silently hide someone's texts, which is exactly
what stalkerware does).

The only real way to stop notifications for the mother board's number is
one of:
1. **Mute that one thread** in your default Messages app (every mainstream
   Android messaging app supports muting notifications per-conversation) --
   zero code needed, works today.
2. **Make this app the phone's default SMS app.** That's a real option, but
   it's a much bigger change: Android requires a default SMS app to handle
   *all* SMS/MMS on the device (compose, view, etc.), so your regular
   Messages app would stop receiving texts at all unless you switch back.
   It's buildable if you want it, but I'd rather confirm that's really the
   trade-off you want before building a full default-SMS-app flow into
   this project.

## Latest round (this message)
- Fixed the cramped right-hand column that was wrapping "Update tank status"
  and the NRF text one word per line: NRF now sits top-right of the tank
  page, and the Check icon + "Update tank status" label now sit just above
  the bottom-left corner.
- The tank's top rim is now a left-right double arrow showing the diameter;
  tap it to edit (same pattern as the other arrows).
- Fixed the inline editors for height/ON/OFF/diameter: they're now a small
  self-drawn field (not a squeezed Material text field), so the digits are
  always fully visible and centered, and the old value is pre-selected so
  typing immediately replaces it.
- Tapping anywhere else on the tank page while an editor is open cancels it
  and restores the previously saved value (in addition to the ~2.5 minute
  idle timeout).
- Guided setup now double-checks each tank before moving to the next: right
  after a tank's ON/OFF are confirmed, the app sends that tank's Check,
  blurs the screen while it waits, updates the tank's status, and only then
  moves to the next tank's page. After the last tank, it also refreshes the
  antenna status before showing "All defaults saved".
- Settings gained "Change tank count" (Home's tank-count box now only shows
  before the very first setup); changing it re-runs the same guided,
  auto-checking wizard.
- Account no longer edits "your own number" -- it now offers the same
  SIM-picker cards as the onboarding screen (fixed the underlying bug: the
  SIM list wasn't refreshing after the permission prompt actually granted
  access).
- Every Settings sub-screen's Save now returns to Home (except Change tank
  count, which goes into the guided wizard instead, which is more useful).
- Home now lists each tank's percent/liters at the bottom in evenly-sized
  sections (1 tank = full width, 2 = halves, 3 = thirds, 4 = quarters).
- Guide text updated to match all of the above.

## Latest round (bug fixes + new features)
- **Fixed the arrow/water misalignment bug**: the tank body, height arrow,
  pump ON/OFF arrows, and diameter arrow were each computing their own
  top/bottom/left/right margins slightly differently, so a registered 70%
  threshold could visually land near the actual 89% water level. All four
  now share the exact same bound functions (`tankVBounds`/`tankHBounds`), so
  they always line up.
- **Fixed the diameter arrow being wider than the tank**: it now spans the
  same left/right fraction as the tank body instead of the whole box.
- **Home tank cards are now "liquid glass" style**: rounded, softly
  translucent gradient cards, each showing percent, liters, a green ON /
  red OFF pump-state badge (inferred: below the ON% threshold means the
  pump should be running, above OFF% means it should have stopped; between
  the two we can't know for sure, so no badge is shown), and the last-
  updated time (device clock, no internet needed).
- **Auto-check on every app open**: as soon as the app starts (if already
  set up), it silently checks Tank1, then Tank2, etc. one at a time, then
  refreshes the antenna status -- all before you even look at Home.
- Background is darker and the circuit-board overlay is more visible now
  (opacity roughly doubled).
- Tank names are now "تانکر اول/دوم/سوم/چهارم" in Persian, and stay
  "Tank1/Tank2/.." in English, everywhere they're shown.
- Settings > Guide rewritten for clarity, and the Persian version no
  longer mixes in raw English words (GSM -> جی‌اس‌ام, etc).
- Settings > About us now shows only the credit line (built by Mohammad Amin
  Norouzi) instead of the logo and web link.

## Build note (no gradlew in this zip)
This project intentionally does **not** include `gradlew` / `gradlew.bat` / a
`gradle/wrapper` folder -- generating a real `gradle-wrapper.jar` requires
downloading it, and this environment has no internet access to do that
honestly (a fake/empty jar would just fail on your machine too).

Instead, `.github/workflows/build.yml` uses GitHub's own
`gradle/actions/setup-gradle` action to install Gradle 8.9 on the runner
(which does have internet access) and runs `gradle assembleDebug` directly
-- no wrapper needed. Push this to GitHub and download the APK from the
Actions tab, same as before.

If you build locally in Android Studio instead: opening the project will
prompt Android Studio to generate the wrapper for you automatically (it has
internet access on your machine), or you can run `gradle wrapper` yourself
once you have any Gradle install.

## This round's fixes and additions
- **Height arrow** is now two separate arrows (one pointing to the tank's
  top, one to its bottom) with a gap in the middle for the "N cm" label,
  instead of one continuous double-headed line.
- Confirmed the ON/OFF threshold arrows land exactly where they should
  (50% = dead center, 80% = 20% from the top) -- they already used the
  corrected shared bounds from the previous round.
- The ON -> OFF step during a tank's very first setup now has a slide/fade
  transition (the arrow-tap editor, which shows both fields at once, is
  unaffected).
- The bottom-left Check/percent/liters cluster on each tank page is now a
  liquid-glass card like Home's.
- Home's tank cards now show the Gregorian date under the last-updated time.
- New Settings > "Automatic status update": three selectable circles --
  Off, By schedule (pick an HH:MM, checks every tank in order then the
  antenna, every day at that time -- works even with the app fully closed,
  via AlarmManager + a headless background chain), and When the app opens
  (the existing behavior, now opt-in rather than automatic).
- SIM number placeholder changed to `+98**********`.
- Swipe left/right now moves between Home -> Tank1 -> Tank2 -> ... ->
  Settings (and back).
- The phone's own Back button always returns to Home instead of closing
  the app.
