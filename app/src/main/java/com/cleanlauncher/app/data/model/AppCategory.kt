package com.cleanlauncher.app.data.model

import android.content.pm.ApplicationInfo
import android.os.Build

data class AppCategory(
    val id: String,
    val title: String,
    val order: Int = 100,
    val isCustom: Boolean = false
) {
    companion object {
        val ALL = AppCategory("all", "All", 0)
        val COMMUNICATION = AppCategory("communication", "Social & Chat", 1)
        val MEDIA = AppCategory("media", "Media & Entertainment", 2)
        val PRODUCTIVITY = AppCategory("productivity", "Work & Study", 3)
        val UTILITIES = AppCategory("utilities", "System & Tools", 4)
        val FINANCE = AppCategory("finance", "Finance & Shopping", 5)
        val GAMES = AppCategory("games", "Games", 6)
        val OTHER = AppCategory("other", "Other Apps", 7)

        val DEFAULT_CATEGORIES = listOf(
            ALL, COMMUNICATION, MEDIA, PRODUCTIVITY, UTILITIES, FINANCE, GAMES, OTHER
        )

        fun createCustom(title: String): AppCategory {
            val cleanTitle = title.trim()
            val id = "custom_" + cleanTitle.lowercase().replace("\\s+".toRegex(), "_")
            return AppCategory(id = id, title = cleanTitle, order = 10, isCustom = true)
        }

        fun fromId(id: String, customList: List<AppCategory> = emptyList()): AppCategory {
            return DEFAULT_CATEGORIES.find { it.id == id }
                ?: customList.find { it.id == id }
                ?: OTHER
        }

        /**
         * Resolves the category using Android's native ApplicationInfo.category (API 26+)
         * with heuristic keyword fallbacks for apps with undefined categories.
         */
        fun resolve(appInfo: ApplicationInfo, label: String): AppCategory {
            // 1. Check Android's native classification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                when (appInfo.category) {
                    ApplicationInfo.CATEGORY_GAME -> return GAMES
                    ApplicationInfo.CATEGORY_AUDIO,
                    ApplicationInfo.CATEGORY_VIDEO,
                    ApplicationInfo.CATEGORY_IMAGE -> return MEDIA
                    ApplicationInfo.CATEGORY_SOCIAL -> return COMMUNICATION
                    ApplicationInfo.CATEGORY_MAPS,
                    ApplicationInfo.CATEGORY_NEWS,
                    ApplicationInfo.CATEGORY_PRODUCTIVITY -> return PRODUCTIVITY
                }
            }

            // 2. Heuristic fallback based on package name and app label
            val text = "${appInfo.packageName.lowercase()} ${label.lowercase()}"

            return when {
                text.containsAny("game", "play", "puzzle", "arcade", "rpg", "racing") -> GAMES
                text.containsAny("whatsapp", "telegram", "signal", "discord", "messenger", "twitter", "instagram", "facebook", "reddit", "call", "dialer", "contacts", "sms", "message", "chat") -> COMMUNICATION
                text.containsAny("spotify", "youtube", "music", "netflix", "prime", "camera", "gallery", "photos", "player", "podcast", "sound", "radio", "stream") -> MEDIA
                text.containsAny("mail", "gmail", "outlook", "drive", "docs", "sheets", "calendar", "notes", "keep", "notion", "slack", "zoom", "teams", "office", "todo", "task") -> PRODUCTIVITY
                text.containsAny("bank", "pay", "wallet", "paypal", "crypto", "finance", "money", "amazon", "ebay", "shop", "cart", "store") -> FINANCE
                text.containsAny("settings", "tool", "calc", "clock", "timer", "files", "manager", "browser", "chrome", "firefox", "cleaner", "wifi", "bluetooth", "security") -> UTILITIES
                else -> OTHER
            }
        }

        private fun String.containsAny(vararg keywords: String): Boolean {
            return keywords.any { this.contains(it) }
        }
    }
}
