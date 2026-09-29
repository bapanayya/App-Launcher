package com.cleanlauncher.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.cleanlauncher.app.data.model.AppCategory
import com.cleanlauncher.app.data.model.AppItem
import com.cleanlauncher.app.data.repository.AppRepository
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Transparent Activity that handles:
 * 1. android.content.pm.action.CONFIRM_PIN_SHORTCUT (Chrome / Browser "Add to Home screen")
 * 2. android.intent.action.SEND (Share Webpage Link to App Launcher)
 */
class PinShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = AppRepository(applicationContext)

        when (intent?.action) {
            "android.content.pm.action.CONFIRM_PIN_SHORTCUT" -> {
                handlePinShortcut(repository)
            }
            Intent.ACTION_SEND -> {
                handleSendIntent(repository)
            }
            else -> {
                finish()
            }
        }
    }

    private fun handlePinShortcut(repository: AppRepository) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
                val pinItemRequest = launcherApps?.getPinItemRequest(intent)

                if (pinItemRequest != null && pinItemRequest.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
                    val shortcutInfo: ShortcutInfo? = pinItemRequest.shortcutInfo
                    if (shortcutInfo != null) {
                        // Accept the request so the system registers it
                        pinItemRequest.accept()

                        val label = (shortcutInfo.shortLabel ?: shortcutInfo.longLabel ?: "Webpage").toString()
                        val shortcutId = shortcutInfo.id
                        val pkgName = shortcutInfo.`package`
                        val intentUri = shortcutInfo.intent?.toUri(Intent.URI_INTENT_SCHEME)
                        val webUrl = shortcutInfo.intent?.dataString ?: ""

                        // Extract and persist icon to internal storage
                        val iconDrawable: Drawable? = try {
                            launcherApps.getShortcutIconDrawable(shortcutInfo, resources.displayMetrics.densityDpi)
                        } catch (_: Exception) {
                            null
                        }

                        val iconPath = saveIconToFile(shortcutId, iconDrawable)

                        repository.saveWebShortcut(
                            id = shortcutId,
                            label = label,
                            packageName = pkgName,
                            url = webUrl,
                            intentUri = intentUri,
                            iconPath = iconPath
                        )

                        Toast.makeText(this, "Saved \"$label\" to App Launcher", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        finish()
    }

    private fun handleSendIntent(repository: AppRepository) {
        try {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val sharedSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""

            // Extract URL from shared text
            val url = extractUrl(sharedText) ?: sharedText.trim()
            if (url.startsWith("http://") || url.startsWith("https://")) {
                val label = if (sharedSubject.isNotBlank()) {
                    sharedSubject.trim()
                } else {
                    deriveTitleFromUrl(url)
                }

                val shortcutId = "web_" + UUID.randomUUID().toString().take(8)
                repository.saveWebShortcut(
                    id = shortcutId,
                    label = label,
                    packageName = "com.cleanlauncher.web",
                    url = url,
                    intentUri = null,
                    iconPath = null
                )

                Toast.makeText(this, "Saved \"$label\" to App Launcher", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        finish()
    }

    private fun saveIconToFile(shortcutId: String, drawable: Drawable?): String? {
        if (drawable == null) return null
        return try {
            val dir = File(filesDir, "shortcut_icons").apply { if (!exists()) mkdirs() }
            val file = File(dir, "${shortcutId.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")}.png")
            val bitmap = drawableToBitmap(drawable)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 128
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 128
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun extractUrl(text: String): String? {
        val regex = "(https?://[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]+)".toRegex()
        return regex.find(text)?.value
    }

    private fun deriveTitleFromUrl(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host?.replace("www.", "") ?: url
            host.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        } catch (_: Exception) {
            "Webpage"
        }
    }
}