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
        val COMMUNICATION = AppCategory("communication", "Communication", 2)
        val SOCIAL_MEDIA = AppCategory("social_media", "Social Media", 3)
        val ENTERTAINMENT = AppCategory("entertainment", "Entertainment", 4)
        val GAMES = AppCategory("games", "Games", 5)
        val SHOPPING = AppCategory("shopping", "Shopping", 6)
        val BANKING_PAYMENTS = AppCategory("banking_payments", "Banking & Payments", 7)
        val PRODUCTIVITY = AppCategory("productivity", "Work & Productivity", 8)
        val UTILITIES = AppCategory("utilities", "Utility & Tools", 9)
        val EDUCATION = AppCategory("education", "Education & Reference", 10)
        val LIFESTYLE = AppCategory("lifestyle", "Lifestyle", 11)
        val HEALTH = AppCategory("health", "Health", 12)
        val TRAVEL = AppCategory("travel", "Travel", 13)
        val WEBPAGES = AppCategory("webpages", "Webpages", 14)
        val OTHER = AppCategory("other", "Others", 15)

        val DEFAULT_CATEGORIES = listOf(
            ALL,
            FREQUENT,
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
            WEBPAGES,
            OTHER
        )

        fun createCustom(title: String): AppCategory {
            val cleanTitle = title.trim()
            val id = "custom_" + cleanTitle.lowercase().replace("\\s+".toRegex(), "_")
            return AppCategory(id = id, title = cleanTitle, order = 50, isCustom = true)
        }

        fun fromId(id: String, customList: List<AppCategory> = emptyList()): AppCategory {
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
         * with comprehensive keyword heuristics tailored to user classification.
         */
        fun resolve(appInfo: ApplicationInfo, label: String): AppCategory {
            val text = "${appInfo.packageName.lowercase()} ${label.lowercase()}"

            // 1. Explicit Games check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo.category == ApplicationInfo.CATEGORY_GAME) {
                return GAMES
            }
            if (text.containsAny("game", "candy", "crush", "pubg", "bgmi", "krafton", "play", "puzzle", "arcade", "rpg", "racing", "chess", "ludo", "cards", "freefire", "action", "clash", "strike", "asphalt", "roblox", "minecraft", "subway", "surfers", "temple run")) {
                return GAMES
            }

            // 2. Communication
            // Gmail, other e-mailing apps, Messaging apps, WhatsApp, Truecaller, Telegram, calling apps, Video calling apps, chatting apps, Facebook Messenger, Snapchat
            if (text.containsAny(
                    "whatsapp", "truecaller", "telegram", "messenger", "signal", "viber", "wechat", "line",
                    "gmail", "email", "mail", "outlook", "yahoo", "zoho", "proton", "inbox", "exchange",
                    "sms", "mms", "messaging", "message", "chat", "dialer", "phone", "contacts", "telephony",
                    "calling", "video call", "meet", "skype", "botim"
                )) {
                return COMMUNICATION
            }

            // 3. Social Media
            // Facebook, Instagram, X, Snapchat, Sharechat, LinkedIn
            if (text.containsAny(
                    "facebook", "katana", "instagram", "twitter", "x.com", "snapchat", "sharechat", "linkedin",
                    "threads", "reddit", "pinterest", "tiktok", "tumblr", "bluesky", "mastodon"
                )) {
                return SOCIAL_MEDIA
            }

            // 4. Shopping
            // Flipkart, Amazon, Meesho, First Cry, Snapdeal, Myntra, Ajio, Nykaa Fashion, Lenskart, Swiggy, Blinkit, Bigbasket, JioMart, Swiggy Instamart, etc.
            if (text.containsAny(
                    "flipkart", "amazon", "meesho", "firstcry", "first cry", "snapdeal", "myntra", "ajio",
                    "nykaa", "lenskart", "swiggy", "blinkit", "bigbasket", "bbdaily", "jiomart", "instamart",
                    "zepto", "zomato", "shop", "shopping", "store", "cart", "market", "ebay", "shopsy", "tatacliq"
                )) {
                return SHOPPING
            }

            // 5. Banking & Payments
            // All official banking apps, all Net Banking apps, Credit card apps, Digital payment & UPI (CRED, PhonePe, GPay, BHIM, Paytm, Mobikwik), Stock market (Angel One, Zerodha Kite, Upstox, 5Paisa, Fyers)
            if (text.containsAny(
                    "sbi", "yono", "hdfc", "icici", "imobile", "axis", "pnb", "kotak", "811", "bob", "bobworld",
                    "canara", "union", "indusind", "idfc", "bank", "banking", "netbanking", "cred", "phonepe",
                    "gpay", "tez", "paisa", "bhim", "paytm", "mobikwik", "freecharge", "navi", "pay", "payment",
                    "upi", "wallet", "paypal", "credit card", "card", "onecard", "angelone", "angel", "zerodha",
                    "kite", "upstox", "5paisa", "fyers", "groww", "dhan", "indmoney", "etmoney", "crypto", "binance", "coindcx"
                )) {
                return BANKING_PAYMENTS
            }

            // 6. Travel
            // IRCTC, Rail One, MakeMyTrip, Goibibo, EaseMyTrip, Yatra, Where Is My Train, redBus, Uber, Ola, Rapido, Booking.com, Agoda, Google maps, navigation apps, compass
            if (text.containsAny(
                    "irctc", "rail one", "railone", "where is my train", "whereismytrain", "train", "rail",
                    "makemytrip", "goibibo", "easemytrip", "yatra", "redbus", "booking.com", "booking", "agoda",
                    "uber", "ola", "olacabs", "rapido", "maps", "map", "navigation", "gps", "waze", "metro", "flight"
                )) {
                return TRAVEL
            }

            // 7. Education & Reference
            // BYJU'S, Physics Wallah, Khan Academy India, Vedantu, DIKSHA App, NCERT e-Pathshala, Swayam, National Digital Library of India (NDLI), Unacademy, Coursera, Udemy, Books apps
            if (text.containsAny(
                    "byju", "physics wallah", "physicswallah", "khan academy", "khanacademy", "vedantu", "diksha",
                    "ncert", "epathshala", "e-pathshala", "swayam", "ndli", "digital library", "unacademy", "coursera",
                    "udemy", "duolingo", "testbook", "wikipedia", "dictionary", "book", "books", "library", "kindle", "kobo",
                    "exam", "study", "learn", "course", "education", "reference"
                )) {
                return EDUCATION
            }

            // 8. Health
            // Practo, Apollo 24/7, all health tracking apps, all Yoga, meditation apps, medical apps
            if (text.containsAny(
                    "practo", "apollo", "health", "fitness", "fitbit", "step", "pedometer", "calorie", "run", "strava",
                    "workout", "gym", "yoga", "meditation", "mindfulness", "headspace", "calm", "cult", "medicine",
                    "pharmacy", "1mg", "pharmeasy", "netmeds", "doctor", "hospital", "medical"
                )) {
                return HEALTH
            }

            // 9. Work & Productivity
            // Google products, Calendars, Google Workspace (Docs, Sheets, Slides, etc.), Microsoft 365 (Word, Excel, PowerPoint, etc.), office work, Adobe Acrobat Reader, AI apps (ChatGPT, Gemini, Grok AI, Perplexity, etc.)
            if (text.containsAny(
                    "docs", "sheets", "slides", "drive", "keep", "notes", "calendar", "word", "excel", "powerpoint",
                    "office", "onenote", "adobe", "acrobat", "reader", "pdf", "chatgpt", "openai", "gemini", "grok",
                    "perplexity", "claude", "copilot", "notion", "slack", "trello", "asana", "todo", "task", "workspace",
                    "evernote", "clickup", "productivity"
                )) {
                return PRODUCTIVITY
            }

            // 10. Utility & Tools
            // DigiLocker, mAadhaar, mParivahan, UMANG, Scanner apps, System tools, Camera apps, Find My Device, all Browsers, Calculator, compass, basic system utilities
            if (text.containsAny(
                    "digilocker", "maadhaar", "aadhaar", "uidai", "mparivahan", "parivahan", "umang", "scanner",
                    "camscanner", "scan", "camera", "cam", "findmydevice", "find my device", "find phone", "browser",
                    "chrome", "firefox", "edge", "opera", "brave", "duckduckgo", "safari", "sbrowser", "kiwi", "vivaldi",
                    "calc", "calculator", "compass", "settings", "tool", "tools", "clock", "timer", "alarm", "files",
                    "filemanager", "cleaner", "wifi", "bluetooth", "security", "torch", "flashlight", "system", "backup"
                )) {
                return UTILITIES
            }

            // 11. Lifestyle
            // Food and cooking apps, smart home apps, smart work apps, lifestyle
            if (text.containsAny(
                    "food", "cooking", "recipe", "cook", "kitchen", "chef", "tasty", "smart home", "smarthome",
                    "smart life", "smartlife", "tuya", "alexa", "home", "mi home", "google home", "iot", "lifestyle",
                    "habit", "routine", "horoscope", "astro", "salon", "beauty", "fashion", "weather", "news"
                )) {
                return LIFESTYLE
            }

            // 12. Entertainment
            // YouTube, Netflix, Prime Video, Jio Hotstar, ZEE5, SonyLIV, Sun NXT, aha, BookMyShow, Kuku TV, all Music (JioSaavn, Spotify, etc.)
            if (text.containsAny(
                    "youtube", "netflix", "prime video", "primevideo", "hotstar", "disney", "zee5", "sonyliv",
                    "sunnxt", "sun nxt", "arha", "aha", "bookmyshow", "kuku", "kukufm", "saavn", "jiosaavn",
                    "spotify", "gaana", "wynk", "music", "audio", "podcast", "radio", "sound", "cinema", "movie",
                    "tv", "stream", "vlc", "mxplayer", "player", "entertainment"
                )) {
                return ENTERTAINMENT
            }

            // Android native category fallbacks
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                when (appInfo.category) {
                    ApplicationInfo.CATEGORY_AUDIO,
                    ApplicationInfo.CATEGORY_VIDEO,
                    ApplicationInfo.CATEGORY_IMAGE -> return ENTERTAINMENT
                    ApplicationInfo.CATEGORY_SOCIAL -> return SOCIAL_MEDIA
                    ApplicationInfo.CATEGORY_MAPS -> return TRAVEL
                    ApplicationInfo.CATEGORY_NEWS -> return LIFESTYLE
                    ApplicationInfo.CATEGORY_PRODUCTIVITY -> return PRODUCTIVITY
                }
            }

            return OTHER
        }

        fun resolveWebShortcut(label: String, url: String): AppCategory {
            return WEBPAGES
        }

        private fun String.containsAny(vararg keywords: String): Boolean {
            return keywords.any { this.contains(it) }
        }
    }
}
