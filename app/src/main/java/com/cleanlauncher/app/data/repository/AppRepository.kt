package com.cleanlauncher.app.data.repository

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ShortcutInfo
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import android.graphics.Bitmap
import com.cleanlauncher.app.R
import com.cleanlauncher.app.data.model.AppCategory
import com.cleanlauncher.app.data.model.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AppRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val prefs: SharedPreferences = context.getSharedPreferences("clean_launcher_prefs", Context.MODE_PRIVATE)

    /**
     * Loads all launchable installed apps and pinned web shortcuts on the device.
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

        val installedApps = resolveInfos
            .filter { it.activityInfo.packageName != myPackageName }
            .map { resolveInfo ->
                val appInfo = resolveInfo.activityInfo.applicationInfo
                val label = resolveInfo.loadLabel(packageManager).toString().trim()
                val pkgName = resolveInfo.activityInfo.packageName
                val activityName = resolveInfo.activityInfo.name
                val icon = resolveInfo.loadIcon(packageManager)
                val iconBitmap = try {
                    icon.toBitmap(96, 96)
                } catch (_: Exception) {
                    null
                }
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

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
                    iconBitmap = iconBitmap,
                    category = category,
                    isSystemApp = isSystem
                )
            }

        // Include saved & pinned web shortcuts (Webpages saved on home screen)
        val webShortcuts = getSavedWebShortcuts()

        (installedApps + webShortcuts).sortedBy { it.label.lowercase() }
    }

    /**
     * Saves a pinned webpage shortcut locally so it persists and is categorized.
     */
    fun saveWebShortcut(
        id: String,
        label: String,
        packageName: String,
        url: String,
        intentUri: String?,
        iconPath: String?
    ) {
        try {
            val jsonArray = getSavedWebShortcutsJson()
            val updated = JSONArray()
            val newItem = JSONObject().apply {
                put("id", id)
                put("label", label)
                put("packageName", packageName)
                put("url", url)
                put("intentUri", intentUri ?: "")
                put("iconPath", iconPath ?: "")
                put("created", System.currentTimeMillis())
            }
            updated.put(newItem)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                if (item.optString("id") != id && item.optString("url") != url) {
                    updated.put(item)
                }
            }
            prefs.edit().putString("saved_web_shortcuts", updated.toString()).apply()
        } catch (_: Exception) {}
    }

    /**
     * Removes a pinned webpage shortcut.
     */
    fun removeWebShortcut(shortcutId: String) {
        try {
            val jsonArray = getSavedWebShortcutsJson()
            val updated = JSONArray()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                if (item.optString("id") == shortcutId) {
                    val iconPath = item.optString("iconPath")
                    if (!iconPath.isNullOrEmpty()) {
                        File(iconPath).delete()
                    }
                } else {
                    updated.put(item)
                }
            }
            prefs.edit().putString("saved_web_shortcuts", updated.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun getSavedWebShortcutsJson(): JSONArray {
        val raw = prefs.getString("saved_web_shortcuts", null) ?: return JSONArray()
        return try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    /**
     * Loads all saved and pinned web shortcuts as AppItem objects.
     */
    fun getSavedWebShortcuts(): List<AppItem> {
        val list = mutableListOf<AppItem>()
        val defaultWebIcon = ContextCompat.getDrawable(context, R.drawable.ic_web_shortcut)
        val customCategories = getCustomCategories()
        val allKnown = AppCategory.DEFAULT_CATEGORIES + customCategories

        try {
            val jsonArray = getSavedWebShortcutsJson()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id")
                val label = obj.optString("label")
                val pkg = obj.optString("packageName", "com.cleanlauncher.web")
                val url = obj.optString("url")
                val intentUri = obj.optString("intentUri").takeIf { it.isNotEmpty() }
                val iconPath = obj.optString("iconPath")

                var iconDrawable: Drawable? = null
                var iconBitmap: Bitmap? = null
                if (!iconPath.isNullOrEmpty()) {
                    val file = File(iconPath)
                    if (file.exists()) {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        if (bitmap != null) {
                            iconBitmap = bitmap
                            iconDrawable = BitmapDrawable(context.resources, bitmap)
                        }
                    }
                }
                if (iconDrawable == null) {
                    iconDrawable = defaultWebIcon
                    iconBitmap = try { defaultWebIcon?.toBitmap(96, 96) } catch (_: Exception) { null }
                }

                // Check user category override for this shortcut
                val savedCategoryVal = prefs.getString("cat_override_$id", null)
                val category = if (savedCategoryVal != null) {
                    allKnown.find { it.id == savedCategoryVal || it.title.equals(savedCategoryVal, ignoreCase = true) }
                        ?: AppCategory.resolveWebShortcut(label, url)
                } else {
                    AppCategory.resolveWebShortcut(label, url)
                }

                list.add(
                    AppItem(
                        id = id,
                        label = label,
                        packageName = pkg,
                        activityName = "",
                        icon = iconDrawable,
                        iconBitmap = iconBitmap,
                        category = category,
                        isShortcut = true,
                        shortcutId = id,
                        shortcutUrl = url,
                        intentUri = intentUri
                    )
                )
            }
        } catch (_: Exception) {}

        // Query system pinned shortcuts on Android 8+ if default launcher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
                if (launcherApps != null && isDefaultLauncher()) {
                    val query = LauncherApps.ShortcutQuery().apply {
                        setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
                    }
                    val pinned = launcherApps.getShortcuts(query, Process.myUserHandle()) ?: emptyList()
                    for (shortcut in pinned) {
                        if (list.none { it.id == shortcut.id }) {
                            val label = (shortcut.shortLabel ?: shortcut.longLabel ?: "Webpage").toString()
                            val icon = try {
                                launcherApps.getShortcutIconDrawable(shortcut, context.resources.displayMetrics.densityDpi)
                            } catch (_: Exception) { defaultWebIcon } ?: defaultWebIcon
                            val category = AppCategory.resolveWebShortcut(label, shortcut.`package`)
                            list.add(
                                AppItem(
                                    id = shortcut.id,
                                    label = label,
                                    packageName = shortcut.`package`,
                                    activityName = "",
                                    icon = icon,
                                    category = category,
                                    isShortcut = true,
                                    shortcutId = shortcut.id,
                                    shortcutUrl = shortcut.intent?.dataString,
                                    intentUri = shortcut.intent?.toUri(Intent.URI_INTENT_SCHEME)
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return list
    }

    companion object {
        private const val PREF_KEY_FREQUENT_APPS = "persistent_frequent_apps_v2"
        private const val PREF_KEY_REMOVED_FREQUENT = "user_removed_frequent_apps_v2"
        private const val PREF_KEY_LAUNCH_COUNT_PREFIX = "launch_count_"
    }

    /**
     * Checks if the app/shortcut is one of the most common apps used on regular working days:
     * WhatsApp, File Manager / Files where files are generally stored, APFRS, Leap Higher,
     * Leap School Education, YouTube, Maps, Gmail, GeoTag Studio, ExamReady, saved web pages,
     * and essential working day apps (Chrome/Browser, Phone, Camera, Calculator).
     */
    fun isCommonEssentialApp(app: AppItem): Boolean {
        // All user-pinned web pages are treated as frequent working shortcuts
        if (app.isShortcut) return true

        val text = "${app.packageName.lowercase()} ${app.label.lowercase()}"

        return when {
            // WhatsApp
            text.contains("whatsapp") -> true

            // File Manager / Files where files are generally stored
            text.contains("filemanager") || text.contains("file explorer") ||
            text.contains("my files") || text.contains("myfiles") ||
            text.contains("com.google.android.apps.nbu.files") ||
            text.contains("com.sec.android.app.myfiles") ||
            text.contains("com.mi.android.globalfileexplorer") ||
            text.contains("com.coloros.filemanager") ||
            text.contains("com.vivo.filemanager") ||
            text.contains("com.oneplus.filemanager") ||
            text.contains("com.android.documentsui") ||
            app.label.equals("Files", ignoreCase = true) ||
            app.label.equals("File Manager", ignoreCase = true) -> true

            // APFRS (Andhra Pradesh Facial Recognition Attendance System)
            text.contains("apfrs") -> true

            // Leap Higher
            text.contains("leap higher") || text.contains("leaphigher") -> true

            // Leap School Education
            text.contains("leap school") || text.contains("leapschooleducation") || text.contains("leap education") -> true

            // YouTube
            text.contains("youtube") || app.packageName == "com.google.android.youtube" -> true

            // Maps
            text.contains("maps") || app.packageName == "com.google.android.apps.maps" -> true

            // Gmail
            text.contains("gmail") || app.packageName == "com.google.android.gm" -> true

            // GeoTag Studio
            text.contains("geotag") -> true

            // ExamReady
            text.contains("examready") || text.contains("exam ready") -> true

            // Regular working day essentials: Default/Primary Browser, Camera, Phone, Calculator
            text.contains("chrome") || text.contains("browser") || text.contains("sbrowser") ||
            text.contains("camera") || text.contains("dialer") || text.contains("phone") ||
            text.contains("calc") || text.contains("calculator") -> true

            else -> false
        }
    }

    /**
     * Records an app/shortcut launch.
     * When launched at least twice, automatically adds to Frequent category permanently.
     */
    fun recordAppLaunch(idOrPackage: String) {
        try {
            val countKey = "$PREF_KEY_LAUNCH_COUNT_PREFIX$idOrPackage"
            val currentCount = prefs.getInt(countKey, 0) + 1
            prefs.edit().putInt(countKey, currentCount).apply()

            val removedSet = prefs.getStringSet(PREF_KEY_REMOVED_FREQUENT, emptySet()) ?: emptySet()
            if (currentCount >= 2 && idOrPackage !in removedSet) {
                val currentFrequent = prefs.getStringSet(PREF_KEY_FREQUENT_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
                if (currentFrequent.add(idOrPackage)) {
                    prefs.edit().putStringSet(PREF_KEY_FREQUENT_APPS, currentFrequent).apply()
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Explicitly adds an app/shortcut to the persistent Frequent category.
     */
    fun addAppToFrequent(idOrPackage: String) {
        try {
            val currentFrequent = prefs.getStringSet(PREF_KEY_FREQUENT_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
            currentFrequent.add(idOrPackage)

            val removedSet = prefs.getStringSet(PREF_KEY_REMOVED_FREQUENT, emptySet())?.toMutableSet() ?: mutableSetOf()
            removedSet.remove(idOrPackage)

            prefs.edit()
                .putStringSet(PREF_KEY_FREQUENT_APPS, currentFrequent)
                .putStringSet(PREF_KEY_REMOVED_FREQUENT, removedSet)
                .apply()
        } catch (_: Exception) {}
    }

    /**
     * Explicitly removes an app/shortcut from the Frequent category.
     * Once removed by user, it will NOT be auto-added again.
     */
    fun removeAppFromFrequent(idOrPackage: String) {
        try {
            val currentFrequent = prefs.getStringSet(PREF_KEY_FREQUENT_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
            currentFrequent.remove(idOrPackage)

            val removedSet = prefs.getStringSet(PREF_KEY_REMOVED_FREQUENT, emptySet())?.toMutableSet() ?: mutableSetOf()
            removedSet.add(idOrPackage)

            prefs.edit()
                .putStringSet(PREF_KEY_FREQUENT_APPS, currentFrequent)
                .putStringSet(PREF_KEY_REMOVED_FREQUENT, removedSet)
                .apply()
        } catch (_: Exception) {}
    }

    /**
     * Returns true if the app is currently in the Frequent category.
     */
    fun isAppInFrequent(app: AppItem): Boolean {
        val identifier = if (app.isShortcut) app.id else app.packageName
        val removedSet = prefs.getStringSet(PREF_KEY_REMOVED_FREQUENT, emptySet()) ?: emptySet()
        if (identifier in removedSet) return false

        val persistentFrequent = prefs.getStringSet(PREF_KEY_FREQUENT_APPS, null)
        if (persistentFrequent != null && identifier in persistentFrequent) {
            return true
        }

        // If not in persistent set yet, check if it is one of the common essential apps
        return isCommonEssentialApp(app)
    }

    /**
     * Returns all apps belonging to the "Frequent" category.
     * Contains common essential apps (WhatsApp, Files, APFRS, YouTube, Maps, Gmail, ExamReady, etc.)
     * and apps used at least twice.
     * Once added, they are NEVER removed automatically unless the user changes their category.
     * Sorted in strict alphabetical order (A-Z).
     */
    fun getFrequentlyUsedApps(allApps: List<AppItem>): List<AppItem> {
        val removedSet = prefs.getStringSet(PREF_KEY_REMOVED_FREQUENT, emptySet()) ?: emptySet()
        val currentFrequent = prefs.getStringSet(PREF_KEY_FREQUENT_APPS, null)?.toMutableSet()
            ?: mutableSetOf()

        // Seed with common essential apps that have not been explicitly removed by the user
        var changed = false
        allApps.forEach { app ->
            val identifier = if (app.isShortcut) app.id else app.packageName
            if (identifier !in removedSet && (identifier in currentFrequent || isCommonEssentialApp(app))) {
                if (currentFrequent.add(identifier)) {
                    changed = true
                }
            }
        }
        if (changed) {
            prefs.edit().putStringSet(PREF_KEY_FREQUENT_APPS, currentFrequent).apply()
        }

        return allApps.filter { app ->
            val identifier = if (app.isShortcut) app.id else app.packageName
            identifier in currentFrequent && identifier !in removedSet
        }.sortedBy { it.label.lowercase() }
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
        val orderString = prefs.getString("categories_display_order_v3", null)
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
        prefs.edit().putString("categories_display_order_v3", str).apply()
    }

    /**
     * Persists manual category reassignment locally.
     */
    fun setAppCategory(identifier: String, newCategory: AppCategory) {
        prefs.edit().putString("cat_override_$identifier", newCategory.id).apply()
    }

    /**
     * Resolves the essential dock apps (Phone, Messages, Default Browser, Camera).
     */
    fun resolveDockApps(allApps: List<AppItem>): List<AppItem> {
        val dockList = mutableListOf<AppItem>()

        // 1. Resolve Default Phone / Dialer App
        findPhoneApp(allApps)?.let { dockList.add(it) }

        // 2. Resolve Default Messaging / SMS App
        findMessagingApp(allApps)?.let { if (it !in dockList) dockList.add(it) }

        // 3. Resolve Default Browser App
        findBrowserApp(allApps)?.let { if (it !in dockList) dockList.add(it) }

        // 4. Resolve Default Camera App
        findCameraApp(allApps)?.let { if (it !in dockList) dockList.add(it) }

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

        return allApps.find { app ->
            val pkg = app.packageName.lowercase()
            val label = app.label.lowercase()
            label == "messages" || label == "message" || label == "messaging" || label == "sms" ||
            pkg.contains("messaging") || pkg.contains("mms") || (pkg.contains("sms") && !pkg.contains("contacts"))
        }
    }

    /**
     * Resolves the user's Default Browser dynamically using RoleManager (Android 10+)
     * and intent resolution, reflecting setting changes instantly.
     */
    fun findBrowserApp(allApps: List<AppItem>): AppItem? {
        // 1. Check system default browser via Intent resolution
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            val resolve = packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val pkg = resolve?.activityInfo?.packageName
            if (pkg != null && pkg != "android" && !pkg.contains("resolver")) {
                val match = allApps.find { it.packageName == pkg }
                if (match != null) return match
            }
        } catch (_: Exception) {}

        // 2. Check browsers registered for HTTP/HTTPS
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            val resolveList = packageManager.queryIntentActivities(browserIntent, 0)
            for (resolve in resolveList) {
                val pkg = resolve.activityInfo.packageName
                if (pkg != "android" && !pkg.contains("resolver")) {
                    val match = allApps.find { it.packageName == pkg }
                    if (match != null) return match
                }
            }
        } catch (_: Exception) {}

        // 3. Fallback to common browsers
        return allApps.find { app ->
            val pkg = app.packageName.lowercase()
            val label = app.label.lowercase()
            label.contains("chrome") || label.contains("browser") || label.contains("firefox") ||
            label.contains("edge") || label.contains("opera") || label.contains("brave") ||
            pkg.contains("chrome") || pkg.contains("browser") || pkg.contains("firefox") ||
            pkg.contains("opera") || pkg.contains("brave")
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
     * Resolves the primary apps on the Home Screen: Settings, Play Store, and
     * the default device Gallery/Photos/Album where camera photos are saved.
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

    /**
     * Resolves the device's native Gallery app where camera photos are saved.
     * Prioritizes OEM device camera gallery/photo/album packages (Xiaomi, Samsung,
     * OnePlus, OPPO, Vivo, Huawei, Sony, Motorola, etc.) and strictly avoids
     * selecting Google Photos when a native gallery/album app is available.
     */
    fun findGalleryApp(allApps: List<AppItem>): AppItem? {
        val oemGalleryPackages = listOf(
            "com.miui.gallery",              // Xiaomi / Redmi / POCO
            "com.sec.android.gallery3d",      // Samsung Gallery
            "com.coloros.gallery3d",          // OPPO / Realme Photos
            "com.vivo.gallery",               // Vivo / iQOO Albums
            "com.oneplus.gallery",            // OnePlus Gallery / Photos
            "com.huawei.photos",              // Huawei / Honor Gallery
            "com.sonyericsson.album",         // Sony Album
            "com.motorola.cn.gallery",        // Motorola Gallery
            "com.transsion.phoenix",          // Transsion (Infinix / Tecno) AI Gallery
            "com.transsion.ai.gallery",
            "com.asus.ephotobook",            // Asus Gallery
            "com.android.gallery3d",          // AOSP Gallery
            "com.android.gallery"
        )

        // 1. Check known native OEM camera photo gallery packages
        for (pkg in oemGalleryPackages) {
            val match = allApps.find { it.packageName == pkg }
            if (match != null) return match
        }

        // 2. Query system gallery intent excluding Google Photos
        try {
            val galleryIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY)
            val resolve = packageManager.resolveActivity(galleryIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val pkg = resolve?.activityInfo?.packageName
            if (pkg != null && pkg != "com.google.android.apps.photos") {
                val match = allApps.find { it.packageName == pkg }
                if (match != null) return match
            }
        } catch (_: Exception) {}

        // 3. Match native OEM app named Gallery, Album, Albums, or Photos (EXCLUDING Google Photos)
        val nonGooglePhotos = allApps.filter { it.packageName != "com.google.android.apps.photos" }
        val oemNamed = nonGooglePhotos.find { app ->
            val l = app.label.lowercase().trim()
            val p = app.packageName.lowercase()
            l == "gallery" || l == "album" || l == "albums" || l == "photos" ||
            p.contains("gallery") || (p.contains("album") && !p.contains("music"))
        }
        if (oemNamed != null) return oemNamed

        // 4. Fallback: only if no OEM gallery exists, use Google Photos or any photo viewer
        return allApps.find { it.packageName == "com.google.android.apps.photos" }
            ?: allApps.find { it.label.contains("photos", ignoreCase = true) || it.packageName.contains("photos") }
    }

    /**
     * Launches the targeted app or saved web shortcut.
     */
    fun launchApp(app: AppItem) {
        if (app.isShortcut) {
            recordAppLaunch(app.id)
            // 1. Try launching through LauncherApps if on Android 8+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && app.shortcutId != null) {
                try {
                    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
                    launcherApps?.startShortcut(app.packageName, app.shortcutId, null, null, Process.myUserHandle())
                    return
                } catch (_: Exception) {}
            }
            // 2. Try launching via intentUri
            if (!app.intentUri.isNullOrEmpty()) {
                try {
                    val intent = Intent.parseUri(app.intentUri, 0).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                } catch (_: Exception) {}
            }
            // 3. Fallback: Launch in default browser via URL
            if (!app.shortcutUrl.isNullOrEmpty()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(app.shortcutUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                } catch (_: Exception) {}
            }
            Toast.makeText(context, "Cannot open shortcut ${app.label}", Toast.LENGTH_SHORT).show()
            return
        }

        // Standard App launch: Prioritize official launch intent to resume existing tasks cleanly
        recordAppLaunch(app.packageName)
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            } else {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = ComponentName(app.packageName, app.activityName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Opens Android System App Info screen (for permissions, storage, uninstall, etc.)
     */
    fun openAppInfo(app: AppItem) {
        if (app.isShortcut) return
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", app.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Requests uninstallation of the selected app or removes web shortcut.
     */
    fun uninstallApp(app: AppItem) {
        if (app.isShortcut) {
            removeWebShortcut(app.id)
            Toast.makeText(context, "Removed ${app.label}", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:${app.packageName}")
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", app.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "Unable to uninstall ${app.label}", Toast.LENGTH_SHORT).show()
            }
        }
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