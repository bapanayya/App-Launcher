package com.cleanlauncher.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    // Listens for newly installed or uninstalled apps in real-time
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.loadApps()
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
                LauncherScreen(viewModel = viewModel)
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
