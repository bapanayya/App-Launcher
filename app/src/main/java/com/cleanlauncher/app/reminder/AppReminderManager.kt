package com.cleanlauncher.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import com.cleanlauncher.app.data.model.AppReminder
import java.util.Calendar

class AppReminderManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_reminders_prefs", Context.MODE_PRIVATE)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun saveReminder(reminder: AppReminder) {
        val all = getAllReminders().toMutableList()
        all.removeAll { it.id == reminder.id }
        all.add(reminder)
        persistReminders(all)

        if (reminder.isEnabled) {
            scheduleAlarm(reminder)
        } else {
            cancelAlarm(reminder.id)
        }
    }

    fun deleteReminder(reminderId: String) {
        val all = getAllReminders().toMutableList()
        all.removeAll { it.id == reminderId }
        persistReminders(all)
        cancelAlarm(reminderId)
    }

    fun getRemindersForApp(packageName: String): List<AppReminder> {
        return getAllReminders().filter { it.packageName == packageName }
    }

    fun getAllReminders(): List<AppReminder> {
        val set = prefs.getStringSet("all_reminders", emptySet()) ?: emptySet()
        return set.mapNotNull { deserialize(it) }.sortedBy { it.hour * 60 + it.minute }
    }

    fun rescheduleAll() {
        getAllReminders().filter { it.isEnabled }.forEach { scheduleAlarm(it) }
    }

    private fun persistReminders(list: List<AppReminder>) {
        val set = list.map { serialize(it) }.toSet()
        prefs.edit().putStringSet("all_reminders", set).apply()
    }

    private fun scheduleAlarm(reminder: AppReminder) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If time has already passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, AppReminderReceiver::class.java).apply {
            putExtra(AppReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
            putExtra(AppReminderReceiver.EXTRA_PACKAGE_NAME, reminder.packageName)
            putExtra(AppReminderReceiver.EXTRA_APP_NAME, reminder.appName)
            putExtra(AppReminderReceiver.EXTRA_VOICE_TEXT, reminder.customVoiceText)
            putExtra(AppReminderReceiver.EXTRA_IS_DAILY, reminder.isDaily)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            // Fallback for devices restricting exact alarms
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    private fun cancelAlarm(reminderId: String) {
        val intent = Intent(context, AppReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun serialize(r: AppReminder): String {
        val encodedText = android.util.Base64.encodeToString(
            r.customVoiceText.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        return "${r.id}|${r.packageName}|${r.appName}|${r.hour}|${r.minute}|${r.isDaily}|${r.isEnabled}|$encodedText"
    }

    private fun deserialize(str: String): AppReminder? {
        return try {
            val parts = str.split("|")
            if (parts.size < 8) return null
            val customText = String(
                android.util.Base64.decode(parts[7], android.util.Base64.NO_WRAP),
                Charsets.UTF_8
            )
            AppReminder(
                id = parts[0],
                packageName = parts[1],
                appName = parts[2],
                hour = parts[3].toInt(),
                minute = parts[4].toInt(),
                isDaily = parts[5].toBoolean(),
                isEnabled = parts[6].toBoolean(),
                customVoiceText = customText
            )
        } catch (_: Exception) {
            null
        }
    }
}
