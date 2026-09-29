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
        val FREQUENT = AppCategory("frequent", "Frequent", 1)
        val WEBPAGES = AppCategory("webpages", "Webpages", 2)
        val COMMUNICATION = AppCategory("communication", "Communication", 3)
        val SOCIAL_MEDIA = AppCategory("social_media", "Social Media", 4)
        val ENTERTAINMENT = AppCategory("entertainment", "Entertainment", 5)
        val GAMES = AppCategory("games", "Games", 6)
        val SHOPPING = AppCategory("shopping", "Shopping", 7)
        val BANKING_PAYMENTS = AppCategory("banking_payments", "Banking & Payments", 8)
        val PRODUCTIVITY = AppCategory("productivity", "Work & Productivity", 9)
        val UTILITIES = AppCategory("utilities", "Utility & Tools", 10)
        val EDUCATION = AppCategory("education", "Education & Reference", 11)
        val LIFESTYLE = AppCategory("lifestyle", "Lifestyle", 12)
        val HEALTH = AppCategory("health", "Health", 13)
        val TRAVEL = AppCategory("travel", "Travel", 14)
        val OTHER = AppCategory("other", "Others", 15)

        val DEFAULT_CATEGORIES = listOf(
            ALL,
            FREQUENT,
            WEBPAGES,
            COMMUNICATION,
            SOCIAL_MEDIA,
            ENTERTAINMENT,
            GAMES,
            SHOPPING,
            BANKING_PAYMENTS,
            PRODUCTIVITY,
            UTILITIES,
            EDUCATION,
            LIFESTYLE,
            HEALTH,
            TRAVEL,
            OTHER
        )

        fun createCustom(title: String): AppCategory {
            val cleanTitle = title.trim()
            val id = "custom_" + cleanTitle.lowercase().replace("\\s+".toRegex(), "_")
            return AppCategory(id = id, title = cleanTitle, order = 50, isCustom = true)
        }

        fun fromId(id: String, customList: List<AppCategory> = emptyList()): AppCategory {
            // Legacy mapping support for previous version category IDs
            val mappedId = when (id) {
                "media" -> "entertainment"
                "finance" -> "banking_payments"
                else -> id
            }
            return DEFAULT_CATEGORIES.find { it.id == mappedId }
                ?: customList.find { it.id == id || it.id == mappedId }
                ?: OTHER
        }

        /**
         * Resolves the category using Android's native ApplicationInfo.category (API 26+)
         * with comprehensive keyword heuristics for all 15 categories.
         */
        fun resolve(appInfo: ApplicationInfo, label: String): AppCategory {
            // 1. Android native classification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                when (appInfo.category) {
                    ApplicationInfo.CATEGORY_GAME -> return GAMES
                    ApplicationInfo.CATEGORY_AUDIO,
                    ApplicationInfo.CATEGORY_VIDEO,
                    ApplicationInfo.CATEGORY_IMAGE -> return ENTERTAINMENT
                    ApplicationInfo.CATEGORY_SOCIAL -> return SOCIAL_MEDIA
                    ApplicationInfo.CATEGORY_MAPS -> return TRAVEL
                    ApplicationInfo.CATEGORY_NEWS -> return LIFESTYLE
                    ApplicationInfo.CATEGORY_PRODUCTIVITY -> return PRODUCTIVITY
                }
            }

            // 2. Keyword heuristic mapping based on package name and app title
            val text = "${appInfo.packageName.lowercase()} ${label.lowercase()}"

            return when {
                // Games
                text.containsAny("game", "play", "puzzle", "arcade", "rpg", "racing", "chess", "ludo", "cards", "pubg", "freefire", "action", "candy", "clash", "strike") -> GAMES

                // Social Media
                text.containsAny("whatsapp", "telegram", "signal", "discord", "instagram", "facebook", "twitter", "x.com", "threads", "snapchat", "reddit", "linkedin", "tiktok", "wechat", "pinterest", "sharechat") -> SOCIAL_MEDIA

                // Communication
                text.containsAny("call", "dialer", "phone", "contacts", "sms", "message", "messaging", "mms", "telephony", "mail", "gmail", "outlook", "email", "inbox") -> COMMUNICATION

                // Banking & Payments
                text.containsAny("bank", "pay", "payment", "upi", "wallet", "paypal", "gpay", "phonepe", "paytm", "bhim", "yono", "sbi", "hdfc", "icici", "axis", "crypto", "finance", "money", "cred", "zerodha", "groww", "angel", "upstox") -> BANKING_PAYMENTS

                // Shopping
                text.containsAny("shop", "store", "cart", "market", "amazon", "flipkart", "myntra", "meesho", "ajio", "ebay", "order", "delivery", "blinkit", "zepto", "swiggy", "zomato", "instamart", "bigbasket") -> SHOPPING

                // Entertainment
                text.containsAny("spotify", "youtube", "music", "netflix", "prime", "video", "hotstar", "camera", "gallery", "photos", "player", "podcast", "sound", "radio", "stream", "tv", "movie", "cinema", "jio", "zee5", "vlc") -> ENTERTAINMENT

                // Work & Productivity
                text.containsAny("docs", "sheets", "slides", "drive", "notes", "keep", "notion", "slack", "zoom", "teams", "office", "todo", "task", "word", "excel", "powerpoint", "pdf", "scanner", "adobe", "workspace", "evernote") -> PRODUCTIVITY

                // Education & Reference
                text.containsAny("school", "college", "exam", "study", "learn", "course", "udemy", "coursera", "duolingo", "wikipedia", "dictionary", "book", "library", "edu", "academy", "class", "prep", "testbook", "unacademy") -> EDUCATION

                // Health
                text.containsAny("health", "fitness", "gym", "workout", "yoga", "doctor", "medicine", "pharmacy", "1mg", "apollo", "practo", "step", "calorie", "hospital", "fitbit", "meditation") -> HEALTH

                // Travel
                text.containsAny("travel", "map", "maps", "gps", "navigation", "uber", "ola", "rapido", "irctc", "rail", "train", "flight", "bus", "booking", "makemytrip", "goibibo", "hotel", "metro", "redbus") -> TRAVEL

                // Lifestyle
                text.containsAny("home", "food", "cooking", "recipe", "fashion", "beauty", "salon", "horoscope", "astro", "weather", "news", "times", "hindu", "daily", "chronicle") -> LIFESTYLE

                // Utility & Tools
                text.containsAny("settings", "tool", "calc", "clock", "timer", "alarm", "files", "manager", "browser", "chrome", "firefox", "edge", "opera", "cleaner", "wifi", "bluetooth", "security", "compass", "flashlight", "torch", "assist", "system") -> UTILITIES

                else -> OTHER
            }
        }

        /**
         * Resolves category for web shortcuts; defaults to WEBPAGES.
         */
        fun resolveWebShortcut(label: String, url: String): AppCategory {
            return WEBPAGES
        }

        private fun String.containsAny(vararg keywords: String): Boolean {
            return keywords.any { this.contains(it) }
        }
    }
}