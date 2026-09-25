# ProGuard / R8 rules for App Launcher
# ====================================

# Jetpack Compose rules
-keepclassmembers class androidx.compose.ui.platform.AndroidComposeView {
    *** *;
}

# Preserve data models used for SharedPreferences serialization and UI state
-keep class com.cleanlauncher.app.data.model.** { *; }
-keepclassmembers class com.cleanlauncher.app.data.model.** { *; }

# Preserve ViewMode enum values
-keepclassmembers enum com.cleanlauncher.app.ui.ViewMode {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Preserve broadcast receivers and managers
-keep class com.cleanlauncher.app.reminder.AppReminderReceiver { *; }
-keep class com.cleanlauncher.app.reminder.BootReceiver { *; }
-keep class com.cleanlauncher.app.reminder.AppReminderManager { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }

# AndroidX Core
-dontwarn androidx.core.**
