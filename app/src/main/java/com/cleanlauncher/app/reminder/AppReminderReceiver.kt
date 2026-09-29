package com.cleanlauncher.app.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import java.util.Locale

class AppReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_VOICE_TEXT = "extra_voice_text"
        const val EXTRA_IS_DAILY = "extra_is_daily"

        private const val CHANNEL_ID = "clean_app_reminders_channel"
        private const val CHANNEL_NAME = "App Reminders & Alarms"
    }

    private var tts: TextToSpeech? = null
    private var ringtone: Ringtone? = null

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "App"
        val rawVoiceText = intent.getStringExtra(EXTRA_VOICE_TEXT) ?: "You need to open $appName"
        val isDaily = intent.getBooleanExtra(EXTRA_IS_DAILY, true)

        // Ensure "on your ... app" is spoken so user immediately identifies the source app
        val voiceAnnouncement = formatSpokenAnnouncement(rawVoiceText, appName)

        // Boost alarm stream volume to 100% max for ultra-clear audibility (+200% boost)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val originalAlarmVol = audioManager?.getStreamVolume(AudioManager.STREAM_ALARM) ?: 5
        val maxAlarmVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 15
        try {
            audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVol, 0)
        } catch (_: Exception) {}

        // 1. Buzz the alarm (Vibration pattern)
        buzzVibrator(context)

        // 2. Play Alarm Buzzer Sound
        playAlarmSound(context)

        // 3. Sequence: After 2 seconds of initial buzzer, speak the voice announcement loudly
        // at maximum volume on the ALARM stream so speech is crystal clear and not drowned out
        Handler(Looper.getMainLooper()).postDelayed({
            speakVoiceAnnouncement(context, voiceAnnouncement) {
                // Restore original volume after speech ends
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVol, 0)
                    } catch (_: Exception) {}
                }, 3000)
            }
        }, 2000)

        // 4. Post high-priority Heads-up Notification with direct app launch action
        showReminderNotification(context, reminderId, packageName, appName, voiceAnnouncement)

        // 5. If daily reminder, reschedule for next day
        if (isDaily) {
            val manager = AppReminderManager(context)
            val existing = manager.getAllReminders().find { it.id == reminderId }
            if (existing != null && existing.isEnabled) {
                manager.saveReminder(existing)
            }
        }
    }

    /**
     * Appends "on your [AppName] app" to the voice announcement to make it easy
     * to identify which app the alert belongs to.
     */
    private fun formatSpokenAnnouncement(text: String, appName: String): String {
        val trimmed = text.trim()
        val appSuffix = "on your $appName app"
        return when {
            trimmed.contains(appSuffix, ignoreCase = true) -> trimmed
            trimmed.contains("on your $appName", ignoreCase = true) -> "$trimmed app"
            else -> "$trimmed $appSuffix"
        }
    }

    private fun buzzVibrator(context: Context) {
        try {
            val pattern = longArrayOf(0, 700, 200, 700, 200, 700, 200, 700)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, -1)
                }
            }
        } catch (_: Exception) {}
    }

    private fun playAlarmSound(context: Context) {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            ringtone = RingtoneManager.getRingtone(context, alarmUri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    volume = 1.0f
                }
                play()
            }

            // Lower buzzer after 2.5 seconds so Text-to-Speech voice announcement is clearly heard
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    ringtone?.stop()
                } catch (_: Exception) {}
            }, 2500)
        } catch (_: Exception) {}
    }

    private fun speakVoiceAnnouncement(context: Context, text: String, onFinished: () -> Unit) {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    tts?.setAudioAttributes(audioAttributes)
                }

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        onFinished()
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        onFinished()
                    }
                })

                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                    putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "app_reminder_tts")
                } else {
                    @Suppress("DEPRECATION")
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
                    Handler(Looper.getMainLooper()).postDelayed({ onFinished() }, 4000)
                }
            } else {
                onFinished()
            }
        }
    }

    private fun showReminderNotification(
        context: Context,
        reminderId: String,
        packageName: String,
        appName: String,
        voiceText: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create Channel on Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent alerts and app reminders"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Action Intent to launch the target app directly
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        val launchPendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                context,
                reminderId.hashCode(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ Reminder: $appName")
            .setContentText(voiceText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .apply {
                if (launchPendingIntent != null) {
                    setContentIntent(launchPendingIntent)
                    addAction(android.R.drawable.ic_menu_send, "Open $appName", launchPendingIntent)
                }
            }
            .build()

        notificationManager.notify(reminderId.hashCode(), notification)
    }
}