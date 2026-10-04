package com.example.tankcontrol

/** Compares two phone numbers by their trailing subscriber digits, ignoring
 *  "+country code" / "00 country code" / leading national "0" / spacing
 *  differences between how the board's number was saved in the app and how a
 *  carrier or a messaging app's notification reports the sender. Shared by
 *  SmsReceiver (direct SMS_RECEIVED broadcast) and SmsNotificationListener
 *  (notification-based fallback) so both match numbers exactly the same way. */
fun phoneNumbersMatch(a: String, b: String): Boolean {
    val da = suffixDigits(a)
    val db = suffixDigits(b)
    return da.isNotEmpty() && da == db
}

private fun suffixDigits(s: String): String {
    val digitsOnly = s.filter { it.isDigit() }
    return if (digitsOnly.length > 9) digitsOnly.takeLast(9) else digitsOnly
}
