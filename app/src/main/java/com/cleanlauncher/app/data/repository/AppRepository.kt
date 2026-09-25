package com.cleanlauncher.app.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.widget.Toast
import com.cleanlauncher.app.data.model.AppCategory
import com.cleanlauncher.app.data.model.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val prefs: SharedPreferences = context.getSharedPreferences("clean_launcher_prefs", Context.MODE_PRIVATE)

    /**
     * Loads all launchable installed apps on the device, categorized without network calls.
     * Incorporates user's manual category overrides stored locally.
     */
    suspend fun getInstalledApps(): List<AppItem> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, 0)
        }

        val myPackageName = context.packageName

        val customCategories = getCustomCategories()
        val allKnown = AppCategory.DEFAULT_CATEGORIES + customCategories

        resolveInfos
            .filter { it.activityInfo.packageName != myPackageName }
            .map { resolveInfo ->
                val appInfo = resolveInfo.activityInfo.applicationInfo
                val label = resolveInfo.loadLabel(packageManager).toString().trim()
                val pkgName = resolveInfo.activityInfo.packageName
                val activityName = resolveInfo.activityInfo.name
                val icon = resolveInfo.loadIcon(packageManager)
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                // Check if user manually reassigned this app to a category
                val savedCategoryVal = prefs.getString("cat_override_$pkgName", null)
                val category = if (savedCategoryVal != null) {
                    allKnown.find { it.id == savedCategoryVal || it.title.equals(savedCategoryVal, ignoreCase = true) }
                        ?: AppCategory.resolve(appInfo, label)
                } else {
                    AppCategory.resolve(appInfo, label)
                }

                AppItem(
                    id = "$pkgName/$activityName",
                    label = if (label.isNotEmpty()) label else pkgName,
                    packageName = pkgName,
                    activityName = activityName,
                    icon = icon,
                    category = category,
                    isSystemApp = isSystem
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Retrieves all user-created custom categories.
     */
    fun getCustomCategories(): List<AppCategory> {
        val titles = prefs.getStringSet("custom_category_titles", emptySet()) ?: emptySet()
        return titles.map { AppCategory.createCustom(it) }.sortedBy { it.title.lowercase() }
    }

    /**
     * Creates and stores a new custom category.
     */
    fun addCustomCategory(title: String): AppCategory {
        val cat = AppCategory.createCustom(title)
        val current = prefs.getStringSet("custom_category_titles", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(cat.title)
        prefs.edit().putStringSet("custom_category_titles", current).apply()
        return cat
    }

    fun getAllCategories(): List<AppCategory> {
        val baseList = AppCategory.DEFAULT_CATEGORIES + getCustomCategories()
        val orderString = prefs.getString("categories_display_order", null)
        if (orderString != null) {
            val orderIds = orderString.split(",")
            val map = baseList.associateBy { it.id }
            val ordered = orderIds.mapNotNull { map[it] }.toMutableList()
            baseList.forEach { if (it !in ordered) ordered.add(it) }
            return ordered
        }
        return baseList
    }

    fun saveCategoryOrder(order: List<AppCategory>) {
        val str = order.joinToString(",") { it.id }
        prefs.edit().putString("categories_display_order", str).apply()
    }

    /**
     * Persists manual category reassignment locally.
     */
    fun setAppCategory(packageName: String, newCategory: AppCategory) {
        prefs.edit().putString("cat_override_$packageName", newCategory.id).apply()
    }

    /**
     * Resolves the essential dock apps (Phone, Messages, Browser, Camera)
     * using official Android system default intents with robust fallbacks.
     */
    fun resolveDockApps(allApps: List<AppItem>): List<AppItem> {
        val dockList = mutableListOf<AppItem>()

        // 1. Resolve Default Phone / Dialer App
        val phoneApp = findPhoneApp(allApps)
        if (phoneApp != null) dockList.add(phoneApp)

        // 2. Resolve Default Messaging / SMS App
        val messagingApp = findMessagingApp(allApps)
        if (messagingApp != null && messagingApp !in dockList) dockList.add(messagingApp)

        // 3. Resolve Default Browser App
        val browserApp = findBrowserApp(allApps)
        if (browserApp != null && browserApp !in dockList) dockList.add(browserApp)

        // 4. Resolve Default Camera App
        val cameraApp = findCameraApp(allApps)
        if (cameraApp != null && cameraApp !in dockList) dockList.add(cameraApp)

        return dockList
    }

    private fun findPhoneApp(allApps: List<AppItem>): AppItem? {
        try {
            val dialIntent = Intent(Intent.ACTION_DIAL)
            val resolve = packageManager.resolveActivity(dialIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val pkg = resolve?.activityInfo?.packageName
            val match = allApps.find { it.packageName == pkg }
            if (match != null) return match
        } catch (_: Exception) {}

        // Fallback by package name or label
        return allApps.find { app ->
            val pkg = app.packageName.lowercase()
            val label = app.label.lowercase()
            label == "phone" || label == "call" || label == "dialer" ||
            pkg.contains("dialer") || (pkg.contains("android.phone") && !pkg.contains("telephony"))
        }
    }

    private fun findMessagingApp(allApps: List<AppItem>): AppItem? {
        try {
            val defaultSmsPkg = Telephony.Sms.getDefaultSmsPackage(context)
            if (defaultSmsPkg != null) {
                val match = allApps.find { it.packageName == defaultSmsPkg }
                if (match != null) return match
            }
        } catch (_: Exception) {}

        try {
            val sendIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
            val resolve = packageManager.resolveActivity(sendIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val pkg = resolve?.activityInfo?.packageName
            val match = allApps.find { it.packageName == pkg }
            if (match != null) return match
        } catch (_: Exception) {}

        // Fallback by package name or label
        return allApps.find { app ->
            val pkg = app.packageName.lowercase()
            val label = app.label.lowercase()
            label == "messages" || label == "message" || label == "messaging" || label == "sms" ||
            pkg.contains("messaging") || pkg.contains("mms") || (pkg.contains("sms") && !pkg.contains("contacts"))
        }
    }

    private fun findBrowserApp(allApps: List<AppItem>): AppItem? {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com"))
            val resolve = packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val pkg = resolve?.activityInfo?.packageName
            val match = allApps.find { it.packageName == pkg }
            if (match != null) return match
        } catch (_: Exception) {}

        return allApps.find { app ->
            val pkg = app.packageName.lowercase()
            val label = app.label.lowercase()
            label.contains("chrome") || label.contains("browser") || pkg.contains("chrome") || pkg.contains("browser")
        }
    }

    private fun findCameraApp(allApps: List<AppItem>): AppItem? {
        try {
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val resolve = packageManager.resolveActivity(cameraIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val pkg = resolve?.activityInfo?.packageName
            val match = allApps.find { it.packageName == pkg }
            if (match != null) return match
        } catch (_: Exception) {}

        return allApps.find { app ->
            val pkg = app.packageName.lowercase()
            val label = app.label.lowercase()
            label == "camera" || pkg.contains("camera")
        }
    }

    /**
     * Resolves the primary apps to display on the ultra-clean Home Screen:
     * Settings, Play Store, and Gallery/Photos.
     */
    fun resolveHomeScreenApps(allApps: List<AppItem>): List<AppItem> {
        val list = mutableListOf<AppItem>()
        findSettingsApp(allApps)?.let { list.add(it) }
        findPlayStoreApp(allApps)?.let { list.add(it) }
        findGalleryApp(allApps)?.let { list.add(it) }
        return list
    }

    private fun findSettingsApp(allApps: List<AppItem>): AppItem? {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            val resolve = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            val pkg = resolve?.activityInfo?.packageName
            val match = allApps.find { it.packageName == pkg }
            if (match != null) return match
        } catch (_: Exception) {}
        return allApps.find { it.packageName.contains("settings") || it.label.equals("settings", ignoreCase = true) }
    }

    private fun findPlayStoreApp(allApps: List<AppItem>): AppItem? {
        return allApps.find { 
            it.packageName == "com.android.vending" || 
            it.label.contains("play store", ignoreCase = true) ||
            it.packageName.contains("vending")
        }
    }

    private fun findGalleryApp(allApps: List<AppItem>): AppItem? {
        return allApps.find { app ->
            val pkg = app.packageName.lowercase()
            val label = app.label.lowercase()
            label == "gallery" || label == "photos" ||
            pkg.contains("gallery") || pkg.contains("photos")
        }
    }

    /**
     * Launches the targeted app using explicit ComponentName for instant launch.
     */
    fun launchApp(app: AppItem) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(app.packageName, app.activityName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallbackIntent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (fallbackIntent != null) {
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallbackIntent)
            } else {
                Toast.makeText(context, "Cannot open ${app.label}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Opens Android System App Info screen (for permissions, storage, uninstall, etc.)
     */
    fun openAppInfo(app: AppItem) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", app.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Requests uninstallation of the selected app.
     */
    fun uninstallApp(app: AppItem) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.fromParts("package", app.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Checks if CleanLauncher is currently the default Home application.
     */
    fun isDefaultLauncher(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                return roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)
            }
        }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    /**
     * Prompts the user to set CleanLauncher as their default home app.
     */
    fun requestSetDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME) && !roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            }
        }
        try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    /**
     * Retrieves the persisted layout view mode (SECTIONS, GRID, MINIMAL_LIST).
     */
    fun getSavedViewMode(): String {
        return prefs.getString("launcher_view_mode", "SECTIONS") ?: "SECTIONS"
    }

    /**
     * Persists the selected layout view mode.
     */
    fun saveViewMode(modeName: String) {
        prefs.edit().putString("launcher_view_mode", modeName).apply()
    }
}
