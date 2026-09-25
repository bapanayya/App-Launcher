package com.cleanlauncher.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.cleanlauncher.app.data.repository.AppRepository
import com.cleanlauncher.app.reminder.AppReminderManager
import com.cleanlauncher.app.ui.LauncherViewModel
import com.cleanlauncher.app.ui.screens.LauncherScreen

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: LauncherViewModel

    // Handles the result of the system "Set as default Home app" prompt
    private val defaultRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.loadApps()
    }

    // Listens for newly installed or uninstalled apps in real-time
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.loadApps()
        }
    }

    /**
     * Prompts the user to set App Launcher as the default Home app immediately
     * using the native in-app system prompt, avoiding the need to manually navigate through Settings.
     */
    fun requestSetDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                if (roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                    Toast.makeText(this, "App Launcher is already your default Home app", Toast.LENGTH_SHORT).show()
                    viewModel.loadApps()
                    return
                }
                try {
                    val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME)
                    defaultRoleLauncher.launch(intent)
                    return
                } catch (e: Exception) {
                    // Fallback to intent chooser
                }
            }
        }

        // Direct Home Intent prompt (shows system chooser dialog directly)
        try {
            packageManager.clearPackagePreferredActivities(packageName)
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Render behind status and navigation bars for ultra-clean look
        enableEdgeToEdge()

        // Tell Android to display the system wallpaper behind this launcher activity
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.decorView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        window.setBackgroundDrawableResource(android.R.color.transparent)

        val repository = AppRepository(applicationContext)
        val reminderManager = AppReminderManager(applicationContext)

        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LauncherViewModel(repository, reminderManager) as T
            }
        })[LauncherViewModel::class.java]

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme()
            ) {
                LauncherScreen(
                    viewModel = viewModel,
                    onRequestSetDefaultLauncher = { requestSetDefaultLauncher() }
                )
            }
        }

        // Register package installation broadcast receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        registerReceiver(packageChangeReceiver, filter)
    }

    override fun onResume() {
        super.onResume()
        // Refresh apps list and check default status
        viewModel.loadApps()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(packageChangeReceiver)
        } catch (_: Exception) {}
    }
}
