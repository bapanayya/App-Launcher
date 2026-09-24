package com.cleanlauncher.app.data.model

data class AppReminder(
    val id: String,
    val packageName: String,
    val appName: String,
    val hour: Int,
    val minute: Int,
    val isDaily: Boolean = true,
    val customVoiceText: String = "",
    val isEnabled: Boolean = true
) {
    val formattedTime: String
        get() {
            val period = if (hour >= 12) "PM" else "AM"
            val displayHour = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            val displayMinute = String.format("%02d", minute)
            return "$displayHour:$displayMinute $period"
        }
}
